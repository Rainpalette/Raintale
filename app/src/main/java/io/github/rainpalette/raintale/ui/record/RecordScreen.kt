package io.github.rainpalette.raintale.ui.record

import android.app.Activity
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rainpalette.raintale.camera.CameraController
import io.github.rainpalette.raintale.camera.CharacterRenderer
import io.github.rainpalette.raintale.camera.OrientationLock
import io.github.rainpalette.raintale.camera.PreviewAspect
import io.github.rainpalette.raintale.character.SpriteCharacterController
import io.github.rainpalette.raintale.record.ScreenRecorder
import io.github.rainpalette.raintale.record.rememberScreenRecordController
import io.github.rainpalette.raintale.ui.camera.AiCommentOverlay
import io.github.rainpalette.raintale.ui.camera.CameraViewModel
import io.github.rainpalette.raintale.ui.camera.CharacterCaptureSpec
import io.github.rainpalette.raintale.ui.camera.effectiveRotation
import io.github.rainpalette.raintale.ui.camera.rememberDeviceRotation
import io.github.rainpalette.raintale.ui.camera.uprightDegrees
import io.github.rainpalette.raintale.ui.character.CharacterCaptureInfo
import io.github.rainpalette.raintale.ui.character.CharacterOverlay
import io.github.rainpalette.raintale.ui.character.CharacterTransform
import io.github.rainpalette.raintale.ui.components.CaptureMode
import io.github.rainpalette.raintale.ui.components.ModeStrip
import io.github.rainpalette.raintale.ui.components.ShootingScaffold
import io.github.rainpalette.raintale.ui.components.ToolbarColumn
import kotlinx.coroutines.launch

/**
 * 录影模式（阶段1·独立录影）：MediaProjection 屏幕录制。
 *
 * - 预览**固定 FILL_CENTER 满屏**（过渡版本，忽略画面比例设置）——理想的 16:9 录制为后续升级项。
 * - 中央=录制开始/停止；未录制时右下=翻转相机，**录制中右下变为拍照按钮**（点一下存一张合照）。
 * - **录制中隐藏所有控件**（仅角色可见）：控件设 alpha 0 → 不可视但**点击仍有效**（含停止键）；
 *   同时进沉浸模式隐藏系统栏。开录前弹窗告知"控件将隐藏、点原位置可停止/拍照"。
 *
 * 复用 [CameraViewModel]（拍照→合成→存盘→AI 评论）+ [AiCommentOverlay] + [ShootingScaffold]。
 */
@Composable
fun RecordScreen(
    selectedMode: CaptureMode,
    onSelectMode: (CaptureMode) -> Unit,
    onOpenSettings: () -> Unit,
    aiEnabled: Boolean,
    ttsEnabled: Boolean,
    characterBarEnabled: Boolean,
    toolbarDefaultExpanded: Boolean,
    locationEnabled: Boolean,
    timeGreetingEnabled: Boolean,
    knowledgeLinkEnabled: Boolean,
    orientationLock: OrientationLock,
    geminiApiKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: CameraViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val recordController = rememberScreenRecordController()
    val isRecording = recordController.isRecording
    val lastVideoUri by ScreenRecorder.lastVideoUri.collectAsStateWithLifecycle()
    var showRecordDialog by rememberSaveable { mutableStateOf(false) }

    val controller = remember { CameraController() }
    val characterController = remember { SpriteCharacterController() }
    val character by characterController.character.collectAsStateWithLifecycle()
    val pose by characterController.pose.collectAsStateWithLifecycle()
    var characterTransform by remember { mutableStateOf(CharacterTransform()) }
    val captureInfo = remember { CharacterCaptureInfo() }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var toolbarExpanded by rememberSaveable(toolbarDefaultExpanded) { mutableStateOf(toolbarDefaultExpanded) }

    // 录影固定满屏，让画面铺满录制区（黑边会被录进去）。
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lensFacing) {
        controller.bindToLifecycle(
            context, lifecycleOwner, previewView, lensFacing, aspect = PreviewAspect.FULL,
        )
    }
    LaunchedEffect(Unit) { characterController.playIdle() }

    val sensorRotation by rememberDeviceRotation()
    val deviceRotation = effectiveRotation(orientationLock, sensorRotation)
    LaunchedEffect(deviceRotation) { controller.setTargetRotation(deviceRotation) }

    // 录制时进沉浸模式（隐藏系统栏），离开/停止恢复。
    val view = LocalView.current
    LaunchedEffect(isRecording) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val insets = WindowCompat.getInsetsController(window, view)
        if (isRecording) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 离开录影页：停止录制 + 恢复系统栏。
            if (ScreenRecorder.isRecording.value) recordController.stop()
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 拍照结果提示改由 ShootingScaffold 的会转向提示条显示（横拿转正）。
    // 录像存好后 Toast（跳过初始 null）。
    LaunchedEffect(lastVideoUri) {
        if (lastVideoUri != null && !isRecording) {
            Toast.makeText(context, "已保存录像到相册", Toast.LENGTH_SHORT).show()
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
        viewModel.onShutter(
            controller = controller,
            character = captureSpec(),
            pose = pose,
            persona = character.persona,
            aiEnabled = aiEnabled,
            apiKey = geminiApiKey,
            locationEnabled = locationEnabled,
            timeGreetingEnabled = timeGreetingEnabled,
            context = context,
            characterName = character.displayName,
            characterId = character.id,
        )
    }

    // 录制中：所有控件 alpha 0（不可视但可点，含停止键）。角色叠层始终可见。
    val hidden = if (isRecording) 0f else 1f

    Box(modifier = modifier.fillMaxSize()) {
        ShootingScaffold(
            previewAspect = PreviewAspect.FULL,
            controlRotation = uprightDegrees(deviceRotation),
            message = uiState.message,
            onMessageShown = { viewModel.onMessageShown() },
            preview = {
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
            },
            toolbar = {
                Box(Modifier.alpha(hidden)) {
                    ToolbarColumn(
                        expanded = toolbarExpanded,
                        onExpandedChange = { toolbarExpanded = it },
                        characterBarEnabled = characterBarEnabled,
                        pose = pose,
                        onSelectPose = { characterController.setPose(it) },
                        character = character,
                        onSelectCharacter = { characterController.setCharacter(it) },
                        collapsible = !toolbarDefaultExpanded,
                    )
                }
            },
            modeStrip = {
                Box(Modifier.alpha(hidden)) {
                    ModeStrip(selected = selectedMode, onSelect = onSelectMode)
                }
            },
            // 中央录制键：开始/停止。录制中 alpha 0 但仍可点（点原位置停止）。
            shutter = {
                Box(Modifier.alpha(hidden)) {
                    RecordButton(
                        isRecording = isRecording,
                        onClick = {
                            if (isRecording) recordController.stop() else showRecordDialog = true
                        },
                    )
                }
            },
            // 右下：未录制=翻转相机；录制中=拍照按钮（点一下拍一张）。
            secondaryEnd = {
                Box(Modifier.alpha(hidden)) {
                    if (isRecording) {
                        IconButton(onClick = { takePhoto() }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "拍照", tint = Color.White)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Cameraswitch, contentDescription = "切换前后摄像头", tint = Color.White)
                        }
                    }
                }
            },
            // 最近照片缩略图。
            secondaryStart = {
                uiState.lastPhotoThumbnail?.let { thumb ->
                    Box(Modifier.alpha(hidden)) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "最近照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            },
            topEnd = {
                Box(Modifier.alpha(hidden)) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
                    }
                }
            },
        )

        AiCommentOverlay(
            viewModel = viewModel,
            knowledgeLinkEnabled = knowledgeLinkEnabled,
            ttsEnabled = ttsEnabled,
            controlRotation = uprightDegrees(deviceRotation),
        )

        if (showRecordDialog) {
            AlertDialog(
                onDismissRequest = { showRecordDialog = false },
                title = { Text("开始录制") },
                text = {
                    Text(
                        "录制时界面控件会隐藏，只保留画面和角色。\n\n" +
                            "· 点屏幕底部中央（录制键位置）停止录制\n" +
                            "· 点右下角（原翻转键位置）拍一张照片\n\n" +
                            "接下来系统会请求录屏授权。",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRecordDialog = false
                        recordController.start()
                    }) { Text("知道了，开始") }
                },
                dismissButton = {
                    TextButton(onClick = { showRecordDialog = false }) { Text("取消") }
                },
            )
        }
    }
}

/** 录制键：待机=红圆点，录制中=红圆角方块（外圈半透明白）。 */
@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 28.dp else 56.dp)
                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                .background(Color.Red)
        )
    }
}
