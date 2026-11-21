package io.github.rainpalette.raintale.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import io.github.rainpalette.raintale.debug.DebugLog
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 端上"感知层"的起点（对齐 phase2-design §12.5）：用 ML Kit 人像分割，把相机预览帧里的
 * **人**抠成一张背景透明的前景图，供 [io.github.rainpalette.raintale.ui.camera.PersonForegroundOverlay]
 * 叠在角色之上，实现"人挡住角色"。
 *
 * 设计要点：
 * - **连续流**：暴露一个 [analyzer] 接到 CameraX 的 `ImageAnalysis`，每帧（节流后）跑一次分割。
 * - **不阻塞**：分割是异步 Task，结果在 [workExecutor] 上构建前景图，再推进 [cutout] 流。
 * - **对齐靠复用**：前景图已旋正/镜像到与预览同方向；显示层用 `ContentScale.Crop` 填满，
 *   与 `PreviewView.FILL_CENTER` 自动对齐，无需手算坐标矩阵。
 * - **可替换**：人脸检测、图像标注日后可挂进同一感知层。
 *
 * 仅做"人在前"。失败/无人时 [cutout] 为 null，显示层不画 → 兜底回"角色盖最上"。
 */
class PersonSegmenter {

    private val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        .enableRawSizeMask() // 取模型原生小尺寸蒙版，构建开销小；放大交给 Canvas。
        .build()
    private val segmenter = Segmentation.getClient(options)

    // 构建前景图的后台线程（ML Kit 回调默认在主线程，显式指定到这里避免阻塞 UI）。
    private val workExecutor = Executors.newSingleThreadExecutor()

    private val _cutout = MutableStateFlow<Bitmap?>(null)
    /** 最新的人像前景（背景透明，已旋正/镜像）。无人/失败/未启用时为 null。 */
    val cutout: StateFlow<Bitmap?> = _cutout

    // 前置摄像头预览是镜像的，前景图也要镜像以保持所见即所得。
    @Volatile private var mirror: Boolean = false

    // 一次只处理一帧（process 是异步的，防止积压）。
    private val inFlight = AtomicBoolean(false)
    private var lastProcessMs = 0L

    // 界面销毁后置真：阻止在途帧再向已关闭的 segmenter / 已终止的线程池提交任务（防崩溃）。
    @Volatile private var closed = false

    // 软节流间隔：默认 12fps；高帧率模式切到 30fps（减残影、更耗电）。
    @Volatile private var minIntervalMs = INTERVAL_12FPS_MS

    // 是否启用连续分割。"人在后"模式无需前景，置 false 让分析器早退、省电（不重绑相机）。
    @Volatile private var active = true

    fun setLensFacing(lensFacing: Int) {
        mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
    }

    /** 是否跑连续分割（预览前景）。"人在后"时关掉省电；一次性 [cutoutFor] 不受此影响。 */
    fun setActive(value: Boolean) {
        active = value
    }

    /** 高帧率模式：开 = 30fps，关 = 12fps。提帧可减残影，代价是耗电。 */
    fun setHighFps(high: Boolean) {
        minIntervalMs = if (high) INTERVAL_30FPS_MS else INTERVAL_12FPS_MS
    }

    /** 清空当前前景（关闭功能或切走时调用）。 */
    fun clear() {
        _cutout.value = null
    }

    /**
     * 对一张**已旋正**的图跑一次分割，返回人像前景（背景透明）。供拍照路用——
     * 对全分辨率成片单独分割，拿到锐利人像。失败/已关闭返回 null（退化为"角色在上"）。
     * 不回收 [bitmap]（调用方合成时还要用）。
     */
    suspend fun cutoutFor(bitmap: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        if (closed) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            segmenter.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(workExecutor) { mask ->
                    val cut = runCatching { buildCutout(bitmap, mask) }
                        .getOrElse { DebugLog.e(TAG, "成片分割构建失败", it); null }
                    if (cont.isActive) cont.resume(cut)
                }
                .addOnFailureListener(workExecutor) { e ->
                    DebugLog.e(TAG, "成片分割失败", e)
                    if (cont.isActive) cont.resume(null)
                }
        } catch (e: RejectedExecutionException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** 释放底层资源。界面销毁时调。 */
    fun close() {
        closed = true // 先置真，让在途帧的分析器早退，避免向已终止的线程池提交。
        clear()
        runCatching { segmenter.close() }
        runCatching { workExecutor.shutdown() }
    }

    /** 接到 CameraX `ImageAnalysis` 的连续分析器。在相机分析线程上被调用。 */
    val analyzer = ImageAnalysis.Analyzer { proxy ->
        val now = SystemClock.uptimeMillis()
        if (closed || !active || inFlight.get() || now - lastProcessMs < minIntervalMs) {
            proxy.close()
            return@Analyzer
        }

        val raw = try {
            proxy.toBitmap()
        } catch (e: Throwable) {
            proxy.close()
            DebugLog.e(TAG, "toBitmap 失败", e)
            return@Analyzer
        }
        val rotation = proxy.imageInfo.rotationDegrees
        proxy.close() // 已拿到自有 Bitmap 副本，尽早释放相机帧。

        lastProcessMs = now
        inFlight.set(true)
        val upright = upright(raw, rotation, mirror)

        // 取帧期间可能已被关闭（离开页面）：别再向已终止的线程池提交，否则崩溃。
        if (closed) {
            upright.recycle()
            inFlight.set(false)
            return@Analyzer
        }

        try {
            segmenter.process(InputImage.fromBitmap(upright, 0))
                .addOnSuccessListener(workExecutor) { mask ->
                    if (closed) return@addOnSuccessListener
                    runCatching { _cutout.value = buildCutout(upright, mask) }
                        .onFailure { DebugLog.e(TAG, "构建前景失败", it) }
                }
                .addOnFailureListener(workExecutor) { e ->
                    if (!closed) DebugLog.e(TAG, "分割失败", e)
                }
                .addOnCompleteListener(workExecutor) {
                    upright.recycle()
                    inFlight.set(false)
                }
        } catch (e: RejectedExecutionException) {
            // 关闭与在途帧的竞态兜底：线程池已终止。
            upright.recycle()
            inFlight.set(false)
        }
    }

    /** 用分割蒙版把 [src] 的人像抠出（背景透明）。蒙版是模型原生小尺寸，按比例放大套用。 */
    private fun buildCutout(src: Bitmap, mask: SegmentationMask): Bitmap {
        val mw = mask.width
        val mh = mask.height
        val buffer = mask.buffer.apply { rewind() }
        // 把逐像素前景置信度转成 ARGB 蒙版（只用 alpha 通道；软边）。
        val pixels = IntArray(mw * mh)
        for (i in pixels.indices) {
            val a = (buffer.float * 255f).toInt().coerceIn(0, 255)
            pixels[i] = a shl 24
        }
        val maskBmp = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
        maskBmp.setPixels(pixels, 0, mw, 0, 0, mw, mh)

        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        // DST_IN：保留 src 中蒙版 alpha 覆盖处（即人），其余变透明。放大走 Skia，原生高效。
        val paint = Paint().apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(maskBmp, Rect(0, 0, mw, mh), Rect(0, 0, src.width, src.height), paint)
        maskBmp.recycle()
        return result
    }

    /** 旋正并（前置时）水平镜像，得到与预览同方向的位图。无需变换则原样返回。 */
    private fun upright(src: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
        if (degrees == 0 && !mirror) return src
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out != src) src.recycle()
        return out
    }

    private companion object {
        const val TAG = "PersonSegmenter"
        // 软节流间隔：12fps 平衡流畅与耗电；30fps 减残影、更耗电。
        const val INTERVAL_12FPS_MS = 83L
        const val INTERVAL_30FPS_MS = 33L
    }
}
