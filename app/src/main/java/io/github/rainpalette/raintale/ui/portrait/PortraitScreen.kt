package io.github.rainpalette.raintale.ui.portrait

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rainpalette.raintale.camera.CameraController
import io.github.rainpalette.raintale.camera.CharacterRenderer
import io.github.rainpalette.raintale.camera.OrientationLock
import io.github.rainpalette.raintale.camera.PreviewAspect
import io.github.rainpalette.raintale.character.SpriteCharacterController
import io.github.rainpalette.raintale.ui.camera.AiCommentOverlay
import io.github.rainpalette.raintale.ui.camera.CameraViewModel
import io.github.rainpalette.raintale.ui.camera.CharacterCaptureSpec
import io.github.rainpalette.raintale.ui.camera.PersonForegroundOverlay
import io.github.rainpalette.raintale.ui.camera.PhotoResultSheet
import io.github.rainpalette.raintale.ui.camera.effectiveRotation
import io.github.rainpalette.raintale.ui.camera.rememberDeviceRotation
import io.github.rainpalette.raintale.ui.camera.sharePhoto
import io.github.rainpalette.raintale.ui.camera.uprightDegrees
import io.github.rainpalette.raintale.ui.camera.viewPhoto
import io.github.rainpalette.raintale.ui.character.CharacterCaptureInfo
import io.github.rainpalette.raintale.ui.character.CharacterOverlay
import io.github.rainpalette.raintale.ui.character.CharacterTransform
import io.github.rainpalette.raintale.ui.components.CaptureMode
import io.github.rainpalette.raintale.ui.components.ModeStrip
import io.github.rainpalette.raintale.ui.components.ShootingScaffold
import io.github.rainpalette.raintale.ui.components.ShutterButton
import io.github.rainpalette.raintale.ui.components.ToolbarColumn
import io.github.rainpalette.raintale.vision.PersonSegmenter
import kotlinx.coroutines.launch

/**
 * 人像模式（M4 上限·实时预览人像分层 + 拍照路合成）：进入此页才启动相机预览 + ML Kit 人像分割，
 * 把人抠出来叠在角色之上（"人在前"）。独立成页 = 按需启用（想拍人才开，省电、避开误识别）+ 生命周期干净。
 *
 * 与主拍照页共用 [CameraViewModel] 与 [AiCommentOverlay]：快门拍照→**对成片单独分割**→合成"人在前"→
 * 存相册→AI 看图评论。保留完整角色操作（拖动/缩放、动作条、角色条、前后切换）。
 */
@Composable
fun PortraitScreen(
    selectedMode: CaptureMode,
    onSelectMode: (CaptureMode) -> Unit,
    onOpenGallery: () -> Unit,
    highFpsEnabled: Boolean,
    aiEnabled: Boolean,
    ttsEnabled: Boolean,
    characterBarEnabled: Boolean,
    toolbarDefaultExpanded: Boolean,
    previewAspect: PreviewAspect,
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

    val controller = remember { CameraController() }
    val personSegmenter = remember { PersonSegmenter() }
    val personCutout by personSegmenter.cutout.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { personSegmenter.close() } }

    val characterController = remember { SpriteCharacterController() }
    val character by characterController.character.collectAsStateWithLifecycle()
    val pose by characterController.pose.collectAsStateWithLifecycle()
    var characterTransform by remember { mutableStateOf(CharacterTransform()) }
    val captureInfo = remember { CharacterCaptureInfo() }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    // 深度：人在前（抠人盖角色）/ 人在后（角色盖人，即不画前景）。取景界面按钮切换，记住选择。
    var personInFront by rememberSaveable { mutableStateOf(true) }
    // 拍后面板（查看/修改/分享）。点缩略图触发。
    var showPhotoSheet by rememberSaveable { mutableStateOf(false) }
    // 工具条展开：初值取设置里的"默认展开"；会话内点把手可临时切换。
    var toolbarExpanded by rememberSaveable(toolbarDefaultExpanded) { mutableStateOf(toolbarDefaultExpanded) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 前后切换 / 高帧率 / 比例变化时重绑；进入即挂分割分析器。
    LaunchedEffect(lensFacing, highFpsEnabled, previewAspect) {
        personSegmenter.setLensFacing(lensFacing)
        personSegmenter.setHighFps(highFpsEnabled)
        controller.bindToLifecycle(
            context, lifecycleOwner, previewView, lensFacing,
            analyzer = personSegmenter.analyzer,
            aspect = previewAspect,
        )
    }
    LaunchedEffect(Unit) { characterController.playIdle() }
    // 人在后时停跑预览分割省电（无需前景）。
    LaunchedEffect(personInFront) { personSegmenter.setActive(personInFront) }
    // 设备朝向（重力感应，锁屏下仍有效）。结合"屏幕方向"设置：锁定时忽略传感器、用固定方向。
    val sensorRotation by rememberDeviceRotation()
    val deviceRotation = effectiveRotation(orientationLock, sensorRotation)
    LaunchedEffect(deviceRotation) { controller.setTargetRotation(deviceRotation) }

    // 快门瞬间组装角色合成参数（全分辨率）。
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

    // 返回键：先关掉拍后面板（比 AppRoot 更深、优先拦截）。
    BackHandler(enabled = showPhotoSheet) { showPhotoSheet = false }

    // 拍照结果提示改由 ShootingScaffold 的会转向提示条显示（横拿转正）。

    Box(modifier = modifier.fillMaxSize()) {
        ShootingScaffold(
            previewAspect = previewAspect,
            controlRotation = uprightDegrees(deviceRotation),
            message = uiState.message,
            onMessageShown = { viewModel.onMessageShown() },
            // 预览区：CameraX 预览 + 角色叠层 + 人像前景层 + 闪光。角色/人像前景/合成都钉在这框上。
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
                // 人像前景层：仅"人在前"时画（盖在角色之上让人挡住角色）；"人在后"则不画 → 角色盖人。
                PersonForegroundOverlay(
                    cutout = if (personInFront) personCutout else null,
                    modifier = Modifier.fillMaxSize(),
                )
                if (flashAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = flashAlpha.value)),
                    )
                }
            },
            // 可收起的「角色 / 动作」工具条。
            toolbar = {
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
            },
            modeStrip = {
                ModeStrip(selected = selectedMode, onSelect = onSelectMode)
            },
            shutter = {
                ShutterButton(
                    isCapturing = uiState.isCapturing,
                    onClick = {
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
                            // 人像页特有：仅"人在前"时对成片分割出人像前景，合成时画在角色之上；"人在后"传 null。
                            segmentPerson = if (personInFront) {
                                { personSegmenter.cutoutFor(it) }
                            } else {
                                null
                            },
                            offlineLines = character.offlineLines,
                        )
                    },
                )
            },
            // 前后摄像头切换。
            secondaryEnd = {
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
            },
            // 最近照片缩略图（拍过才出现）；点开拍后面板。
            secondaryStart = {
                uiState.lastPhotoThumbnail?.let { thumb ->
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "最近照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showPhotoSheet = true },
                    )
                }
            },
            // 深度切换：人在前 / 人在后。图标随状态切换：FlipToFront=人置顶层 / FlipToBack=人置底层。
            topStart = {
                Button(onClick = { personInFront = !personInFront }) {
                    Icon(
                        imageVector = if (personInFront) Icons.Filled.FlipToFront else Icons.Filled.FlipToBack,
                        contentDescription = null, // 含义由相邻文字承载，避免重复播报
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(if (personInFront) "人在前" else "人在后")
                }
            },
            // 帧率指示。
            topEnd = {
                Text(
                    text = "人像 · " + (if (highFpsEnabled) "30fps" else "12fps"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            },
        )

        // AI 评论气泡 + 维基弹窗 + 纠错（共享组件，自带 TTS）。全屏兄弟叠层。
        AiCommentOverlay(
            viewModel = viewModel,
            knowledgeLinkEnabled = knowledgeLinkEnabled,
            ttsEnabled = ttsEnabled,
            controlRotation = uprightDegrees(deviceRotation),
        )

        // 拍后结果面板：点缩略图打开（查看/修改/分享）。
        if (showPhotoSheet) {
            val uri = uiState.lastPhotoUri
            val thumb = uiState.lastPhotoThumbnail
            if (uri != null && thumb != null) {
                PhotoResultSheet(
                    photo = thumb,
                    onView = { viewPhoto(context, uri) },
                    onEdit = { Toast.makeText(context, "修图功能敬请期待", Toast.LENGTH_SHORT).show() },
                    onShare = { sharePhoto(context, uri) },
                    onOpenGallery = { showPhotoSheet = false; onOpenGallery() },
                    onDismiss = { showPhotoSheet = false },
                )
            }
        }
    }
}
