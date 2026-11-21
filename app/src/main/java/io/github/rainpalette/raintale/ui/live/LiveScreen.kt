package io.github.rainpalette.raintale.ui.live

import android.app.Activity
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rainpalette.raintale.R
import io.github.rainpalette.raintale.camera.CameraController
import io.github.rainpalette.raintale.camera.CharacterRenderer
import io.github.rainpalette.raintale.camera.OrientationLock
import io.github.rainpalette.raintale.character.SpriteCharacterController
import io.github.rainpalette.raintale.live.StaticMode
import io.github.rainpalette.raintale.live.UsageMeter
import io.github.rainpalette.raintale.record.ScreenRecorder
import io.github.rainpalette.raintale.record.rememberScreenRecordController
import io.github.rainpalette.raintale.util.NetworkUtil
import io.github.rainpalette.raintale.tts.Speaker
import io.github.rainpalette.raintale.tts.TtsSpeaker
import io.github.rainpalette.raintale.ui.camera.CameraViewModel
import io.github.rainpalette.raintale.ui.camera.CharacterCaptureSpec
import io.github.rainpalette.raintale.ui.camera.effectiveRotation
import io.github.rainpalette.raintale.ui.camera.rememberDeviceRotation
import io.github.rainpalette.raintale.ui.camera.uprightDegrees
import io.github.rainpalette.raintale.ui.character.CharacterCaptureInfo
import io.github.rainpalette.raintale.ui.character.CharacterOverlay
import io.github.rainpalette.raintale.ui.character.CharacterTransform
import io.github.rainpalette.raintale.ui.components.CaptureMode
import io.github.rainpalette.raintale.ui.components.CommentBubble
import io.github.rainpalette.raintale.ui.components.ModeStrip
import io.github.rainpalette.raintale.ui.components.RotatedStage
import io.github.rainpalette.raintale.ui.components.RotatingMessageHost
import kotlinx.coroutines.launch

/**
 * 实时点评界面（debug 阶段）：相机预览 + 角色叠层 + 定时点评 + 评论气泡。
 *
 * 录影（阶段2）：设置「实时点评·录影」开启时，「开始点评」同步开始屏幕录制，并把
 * **停止点评 + 拍照按钮隐藏**（alpha 0，仍可点，点原位置即停止/拍照）；关闭则一切照旧。
 * 拍照复用 [CameraViewModel] 存一张合照（快照，不触发 AI 评论，避免与实时气泡打架）。
 */
@Composable
fun LiveScreen(
    selectedMode: CaptureMode,
    onSelectMode: (CaptureMode) -> Unit,
    aiEnabled: Boolean,
    staticMode: StaticMode,
    locationEnabled: Boolean,
    timeGreetingEnabled: Boolean,
    orientationLock: OrientationLock,
    geminiApiKey: String,
    liveRecordingEnabled: Boolean,
    shortTermMemoryEnabled: Boolean,
    liveIntervalSec: Int,
    /** 是否已看过"实时点评持续上传"须知（版本化，见 SettingsStore.NoticeVersions.LIVE）。未看过时首次开始前弹。 */
    liveNoticeSeen: Boolean,
    onLiveNoticeSeen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: LiveViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val usage by UsageMeter.usage.collectAsStateWithLifecycle()

    // 拍照复用相机 ViewModel（拍→合成→存盘）；与主页共享 Activity 作用域实例。
    val photoViewModel: CameraViewModel = viewModel()
    val photoState by photoViewModel.uiState.collectAsStateWithLifecycle()

    // 屏幕录制控制器（通用类）。
    val recordController = rememberScreenRecordController()
    val recording = recordController.isRecording
    val lastVideoUri by ScreenRecorder.lastVideoUri.collectAsStateWithLifecycle()
    var showRecordDialog by rememberSaveable { mutableStateOf(false) }
    var showLiveNotice by rememberSaveable { mutableStateOf(false) }

    val controller = remember { CameraController() }
    val characterController = remember { SpriteCharacterController() }
    val character by characterController.character.collectAsStateWithLifecycle()
    val pose by characterController.pose.collectAsStateWithLifecycle()
    var characterTransform by remember { mutableStateOf(CharacterTransform()) }
    val captureInfo = remember { CharacterCaptureInfo() }
    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 朗读器：系统内建 TTS（端上神经语音已下架）。
    val tts: Speaker = remember { TtsSpeaker(context) }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(Unit) {
        controller.bindToLifecycle(
            context, lifecycleOwner, previewView,
            CameraSelector.LENS_FACING_BACK, withAnalysis = true,
        )
        characterController.playIdle()
    }
    // 设备朝向（重力感应，锁屏下仍有效）。结合"屏幕方向"设置：锁定时忽略传感器、用固定方向。
    val sensorRotation by rememberDeviceRotation()
    val deviceRotation = effectiveRotation(orientationLock, sensorRotation)
    LaunchedEffect(deviceRotation) { controller.setTargetRotation(deviceRotation) }
    // 同步静止处理模式 / 位置开关（用户在设置页可切换）。
    LaunchedEffect(staticMode) { viewModel.setStaticMode(staticMode) }
    LaunchedEffect(locationEnabled) { viewModel.setLocationEnabled(locationEnabled) }
    LaunchedEffect(timeGreetingEnabled) { viewModel.setTimeGreetingEnabled(timeGreetingEnabled) }
    LaunchedEffect(shortTermMemoryEnabled) { viewModel.setShortTermMemoryEnabled(shortTermMemoryEnabled) }
    LaunchedEffect(liveIntervalSec) { viewModel.setIntervalMs(liveIntervalSec * 1000L) }
    LaunchedEffect(geminiApiKey) { viewModel.setApiKey(geminiApiKey) }
    // 新评论朗读。
    LaunchedEffect(uiState.comment) { uiState.comment?.let { tts.speak(it) } }
    // 提示改用会转向的应用内提示条（横拿转正）。实时点评 + 拍照两路汇入一个 liveMessage。
    var liveMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { liveMessage = it; viewModel.onMessageShown() }
    }
    LaunchedEffect(photoState.message) {
        photoState.message?.let { liveMessage = it; photoViewModel.onMessageShown() }
    }
    LaunchedEffect(lastVideoUri) {
        if (lastVideoUri != null && !recording) {
            Toast.makeText(context, "已保存录像到相册", Toast.LENGTH_SHORT).show()
        }
    }
    // 录制时进沉浸模式（隐藏系统栏），停止/离开恢复。
    val view = LocalView.current
    LaunchedEffect(recording) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val insets = WindowCompat.getInsetsController(window, view)
        if (recording) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stop()
            if (ScreenRecorder.isRecording.value) recordController.stop()
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
            // tts 的释放由上面 DisposableEffect(tts) 负责（切换语音来源时也能正确释放旧的）。
        }
    }

    fun captureSpec() = CharacterCaptureSpec(
        overlayWidth = captureInfo.overlayWidth,
        overlayHeight = captureInfo.overlayHeight,
        baseLeft = captureInfo.baseLeft - captureInfo.overlayLeft,
        baseTop = captureInfo.baseTop - captureInfo.overlayTop,
        baseRight = captureInfo.baseRight - captureInfo.overlayLeft,
        baseBottom = captureInfo.baseBottom - captureInfo.overlayTop,
        scale = characterTransform.scale,
        offsetX = characterTransform.offset.x,
        offsetY = characterTransform.offset.y,
        render = { w, h -> CharacterRenderer.render(context, character.drawableFor(pose), w, h) },
        deviceRotation = deviceRotation,
    )

    fun takePhoto() {
        scope.launch {
            flashAlpha.snapTo(0.85f)
            flashAlpha.animateTo(0f, animationSpec = tween(350))
        }
        // 快照：不触发 AI 评论（实时点评已在出评论）。
        photoViewModel.onShutter(
            controller = controller,
            character = captureSpec(),
            pose = pose,
            persona = character.persona,
            aiEnabled = false,
            apiKey = geminiApiKey,
            locationEnabled = locationEnabled,
            timeGreetingEnabled = timeGreetingEnabled,
            context = context,
        )
    }

    fun startLive() {
        viewModel.start(controller, characterController, context)
        if (liveRecordingEnabled) recordController.start()
    }

    fun stopLive() {
        viewModel.stop()
        if (ScreenRecorder.isRecording.value) recordController.stop()
    }

    // 录制中：停止点评 + 拍照键 alpha 0（不可视但可点）。
    val hidden = if (recording) 0f else 1f

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CharacterOverlay(
            controller = characterController,
            transform = characterTransform,
            onTransformChange = { characterTransform = it },
            captureInfo = captureInfo,
            characterRotation = uprightDegrees(deviceRotation),
            modifier = Modifier.fillMaxSize(),
        )
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }

        // 横屏舞台：横拿时 TopCenter = 物理顶边；直屏不变。
        RotatedStage(rotationDegrees = uprightDegrees(deviceRotation)) {
            CommentBubble(
                text = uiState.comment,
                isLoading = uiState.isCommenting,
                onDismiss = { tts.stop(); viewModel.dismissComment() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            )
        }

        // 模式条（开始点评后隐藏，取景干净；停止后再现）。
        if (!uiState.isRunning) {
            ModeStrip(
                selected = selectedMode,
                onSelect = onSelectMode,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 124.dp),
            )
        }

        // ===== DEBUG-ONLY 组件：静止次数指标 + 用量进度条。正式版移除（搜 "DEBUG-ONLY"）。=====
        // 录制时一并隐藏（不录进视频）。
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).alpha(hidden),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "静止: ${uiState.staticCount}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "用量 ${(usage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(
                    progress = { usage },
                    modifier = Modifier.width(120.dp).padding(top = 2.dp),
                )
            }
        }
        // ===== DEBUG-ONLY 组件结束 =====

        // 拍照按钮（点评进行中才出现；位置同录影页——右下角）。录制中 alpha 0 但仍可点。
        if (uiState.isRunning) {
            IconButton(
                onClick = { takePhoto() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 48.dp)
                    .alpha(hidden),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "拍照", tint = Color.White)
            }
        }

        // 实时点评只能在线使用。AI 评论关闭时用常驻文字说明（像陪伴模式），不给启动入口 → 不会调 AI。
        if (!aiEnabled) {
            Text(
                "实时点评需要开启「AI 评论」\n（设置 → AI 与评论）",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
                    .alpha(hidden),
            )
        } else {
            // 开始 / 停止点评。录制中 alpha 0 但仍可点（点原位置停止）。
            Button(
                onClick = {
                    if (uiState.isRunning) {
                        stopLive()
                    } else if (!NetworkUtil.isOnline(context)) {
                        liveMessage = "未联网，实时点评需要联网才能使用"
                    } else if (!liveNoticeSeen) {
                        showLiveNotice = true // 首次使用：先弹"持续上传"须知，确认后再开始
                    } else {
                        if (liveRecordingEnabled) showRecordDialog = true else startLive()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .alpha(hidden),
            ) {
                Text(if (uiState.isRunning) "停止点评" else "开始点评")
            }
        }

        // 实时点评首次使用须知：告知"持续定期上传 + 旁人隐私"（与单张拍照的质的区别）。
        // 确认即记录已看（版本化），并直接进入开始流程；取消则不开始、下次再弹。
        if (showLiveNotice) {
            AlertDialog(
                onDismissRequest = { showLiveNotice = false },
                title = { Text(stringResource(R.string.live_notice_title)) },
                text = { Text(stringResource(R.string.live_notice_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLiveNotice = false
                        onLiveNoticeSeen()
                        if (liveRecordingEnabled) showRecordDialog = true else startLive()
                    }) { Text(stringResource(R.string.live_notice_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLiveNotice = false }) { Text(stringResource(R.string.live_notice_cancel)) }
                },
            )
        }

        if (showRecordDialog) {
            AlertDialog(
                onDismissRequest = { showRecordDialog = false },
                title = { Text("开始点评并录制") },
                text = {
                    Text(
                        "点评时会同步录屏，界面控件将隐藏、只保留画面和角色。\n\n" +
                            "· 点屏幕底部中央（停止键位置）停止点评并结束录制\n" +
                            "· 点右下角（拍照键位置）拍一张照片\n\n" +
                            "接下来系统会请求录屏授权。（可在设置→实时点评关闭录影）",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRecordDialog = false
                        startLive()
                    }) { Text("知道了，开始") }
                },
                dismissButton = {
                    TextButton(onClick = { showRecordDialog = false }) { Text("取消") }
                },
            )
        }

        // 会转向的应用内提示条（实时点评 + 拍照 + 未联网 提示汇入，横拿转正）。
        RotatingMessageHost(
            message = liveMessage,
            rotationDegrees = uprightDegrees(deviceRotation),
            onShown = { liveMessage = null },
        )
    }
}
