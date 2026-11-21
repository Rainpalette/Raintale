package io.github.rainpalette.raintale.record

import android.app.Activity
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 屏幕录制的共享状态（单例，像 [io.github.rainpalette.raintale.live.UsageMeter]）：
 * [ScreenRecordService] 更新，UI 收集。**这是"通用录制"的对外状态**——录影页与实时点评页共用。
 */
object ScreenRecorder {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _lastVideoUri = MutableStateFlow<Uri?>(null)
    val lastVideoUri: StateFlow<Uri?> = _lastVideoUri.asStateFlow()

    /** 由 Service 调用：录制真正开始。 */
    fun onStarted() {
        _isRecording.value = true
    }

    /** 由 Service 调用：录制停止（uri 为存好的视频，失败时 null）。 */
    fun onStopped(uri: Uri?) {
        _isRecording.value = false
        if (uri != null) _lastVideoUri.value = uri
    }
}

/**
 * 录制控制器：[start] 申请录屏授权并开录，[stop] 停录。录影页 / 实时点评页共用（通用类）。
 * 由 [rememberScreenRecordController] 在 Composable 里构造（需要 Activity 作用域申请授权）。
 */
class ScreenRecordController(
    val isRecording: Boolean,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
) {
    fun start() = onStart()
    fun stop() = onStop()
}

/**
 * 在 Composable 里取一个录制控制器。内部用 [ActivityResultContracts.StartActivityForResult]
 * 弹系统录屏授权框，授权后启动 [ScreenRecordService]（前台服务，MediaProjection 必须跑在其中）。
 */
@Composable
fun rememberScreenRecordController(): ScreenRecordController {
    val context = LocalContext.current
    val isRecording by ScreenRecorder.isRecording.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecordService.start(context, result.resultCode, data)
        }
    }

    return ScreenRecordController(
        isRecording = isRecording,
        onStart = {
            val mpm = context.getSystemService(MediaProjectionManager::class.java)
            // Android 14+：强制"整个屏幕"（跳过"单一应用"选项，直接整屏授权）。旧版本本就录整屏。
            // 注：录屏授权是一次性 token，无法像相机权限那样持久化——每次开录都要重新授权（Android 安全设计）。
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                mpm.createScreenCaptureIntent()
            }
            launcher.launch(intent)
        },
        onStop = { ScreenRecordService.stop(context) },
    )
}
