package io.github.rainpalette.raintale.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.rainpalette.raintale.R
import io.github.rainpalette.raintale.debug.DebugLog
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 屏幕录制前台服务：持有 MediaProjection + MediaRecorder + VirtualDisplay，录**整屏**存到
 * 系统相册 `Movies/PhotoBuddy`（video/mp4）。
 *
 * 为什么必须是前台服务：API 29+ 起 MediaProjection 只能在
 * `foregroundServiceType="mediaProjection"` 的前台服务里跑；API 34+ 更要求先启前台服务再
 * `getMediaProjection`。故 UI 只经 [ScreenRecorder] 观察状态，实际录制都在这里。
 *
 * 当前录整屏（过渡版本，配合录影页 FILL_CENTER 满屏预览）；理想的 16:9 录制为后续升级项。
 * 音频（TTS/麦克风）暂不录，列为后续项。
 */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // MediaProjection 停止（用户在系统 UI 撤销投屏等）时也要收尾。
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            // 停止前也先确保处于前台（避免"未及时 startForeground"），再收尾。
            ACTION_STOP -> {
                startForegroundNotification()
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        startForegroundNotification()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        val width = intent.getIntExtra(EXTRA_WIDTH, 1080).let { it - it % 2 }
        val height = intent.getIntExtra(EXTRA_HEIGHT, 1920).let { it - it % 2 }
        val dpi = intent.getIntExtra(EXTRA_DPI, 480)
        if (data == null) {
            stopSelfCleanly()
            return
        }
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val proj = mpm.getMediaProjection(resultCode, data)
            if (proj == null) {
                stopSelfCleanly()
                return
            }
            projection = proj
            proj.registerCallback(projectionCallback, mainHandler)
            val surface = prepareRecorder(width, height)
            virtualDisplay = proj.createVirtualDisplay(
                "PhotoBuddyRecord", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null,
            )
            recorder?.start()
            ScreenRecorder.onStarted()
        } catch (e: Exception) {
            DebugLog.e(TAG, "录制启动失败", e)
            stopRecording()
        }
    }

    /** 建 MediaStore 视频条目 + 配置 MediaRecorder，返回录制输入 Surface。 */
    private fun prepareRecorder(width: Int, height: Int): Surface {
        val name = "PhotoBuddy_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/$ALBUM_NAME")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法在相册创建视频条目")
        outputUri = uri
        val descriptor = resolver.openFileDescriptor(uri, "w")
            ?: throw IllegalStateException("无法打开视频输出流")
        pfd = descriptor

        val rec = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        rec.setVideoSize(width, height)
        rec.setVideoFrameRate(30)
        rec.setVideoEncodingBitRate(8_000_000)
        rec.setOutputFile(descriptor.fileDescriptor)
        rec.prepare()
        recorder = rec
        return rec.surface
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // 太快停止/没录到帧时 stop 会抛，忽略但记日志。
            DebugLog.e(TAG, "录制停止异常（可能未录到帧）", e)
        }
        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
        try {
            pfd?.close()
        } catch (_: Exception) {
        }
        pfd = null

        val uri = outputUri
        if (uri != null) {
            // 取消 pending，视频才对相册可见。
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            try {
                contentResolver.update(uri, done, null, null)
            } catch (_: Exception) {
            }
        }
        ScreenRecorder.onStopped(uri)
        outputUri = null

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 启动失败/无授权时的干净收尾（不留 pending 条目）。 */
    private fun stopSelfCleanly() {
        val uri = outputUri
        if (uri != null) {
            try {
                contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            outputUri = null
        }
        ScreenRecorder.onStopped(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "屏幕录制", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("雨中逸事 正在录制")
            .setContentText("录制屏幕中…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // 保险：异常销毁时复位状态，避免 UI 卡在"录制中"。
        if (ScreenRecorder.isRecording.value) ScreenRecorder.onStopped(outputUri)
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val CHANNEL_ID = "screen_record"
        private const val NOTIF_ID = 1001
        private const val ALBUM_NAME = "PhotoBuddy"
        private const val RESULT_CANCELED = 0

        private const val ACTION_START = "io.github.rainpalette.raintale.record.START"
        private const val ACTION_STOP = "io.github.rainpalette.raintale.record.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_WIDTH = "width"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_DPI = "dpi"

        /** 授权拿到后启动录制（传入系统录屏授权结果 + 屏幕尺寸）。 */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val metrics = context.resources.displayMetrics
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_WIDTH, metrics.widthPixels)
                putExtra(EXTRA_HEIGHT, metrics.heightPixels)
                putExtra(EXTRA_DPI, metrics.densityDpi)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
