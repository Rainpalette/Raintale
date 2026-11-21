package io.github.rainpalette.raintale.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import io.github.rainpalette.raintale.camera.CameraController
import io.github.rainpalette.raintale.camera.CharacterRenderer
import io.github.rainpalette.raintale.camera.OrientationLock
import io.github.rainpalette.raintale.camera.PreviewAspect
import io.github.rainpalette.raintale.character.SpriteCharacterController
import io.github.rainpalette.raintale.location.LocationProvider
import kotlinx.coroutines.launch
import io.github.rainpalette.raintale.ui.character.CharacterCaptureInfo
import io.github.rainpalette.raintale.ui.character.CharacterOverlay
import io.github.rainpalette.raintale.ui.character.CharacterTransform
import io.github.rainpalette.raintale.ui.components.CaptureMode
import io.github.rainpalette.raintale.ui.components.ModeStrip
import io.github.rainpalette.raintale.ui.components.ShootingScaffold
import io.github.rainpalette.raintale.ui.components.ShutterButton
import io.github.rainpalette.raintale.ui.components.ToolbarColumn

/**
 * M0 的主屏：相机权限闸门 + 占位。
 *
 * 职责仅限于"拿到相机权限并把状态展示出来"。真实的相机预览（CameraX `PreviewView`）
 * 将在 M1 接入，届时把 [CameraReadyPlaceholder] 替换为预览组件即可，权限逻辑无需改动。
 */
@Composable
fun CameraScreen(
    captureMode: CaptureMode,
    selectedMode: CaptureMode,
    onSelectMode: (CaptureMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
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

    // 当前是否已授予相机权限。用可变状态驱动 UI；在多个时机刷新（见下方）。
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    // 是否已经主动请求过一次。用于区分"首次进入"与"用户拒绝后"。
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        hasRequestedOnce = true
    }

    // 首次进入自动发起一次权限请求。
    DisposableEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }

    // 回到前台时重新检查授权状态（例如用户去系统设置里授权后返回）。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasPermission) {
        CameraContent(
            captureMode = captureMode,
            selectedMode = selectedMode,
            onSelectMode = onSelectMode,
            onOpenSettings = onOpenSettings,
            onOpenGallery = onOpenGallery,
            aiEnabled = aiEnabled,
            ttsEnabled = ttsEnabled,
            characterBarEnabled = characterBarEnabled,
            toolbarDefaultExpanded = toolbarDefaultExpanded,
            previewAspect = previewAspect,
            locationEnabled = locationEnabled,
            timeGreetingEnabled = timeGreetingEnabled,
            knowledgeLinkEnabled = knowledgeLinkEnabled,
            orientationLock = orientationLock,
            geminiApiKey = geminiApiKey,
            modifier = modifier,
        )
    } else {
        // 区分"可再次询问"与"被永久拒绝（勾了不再询问）"。
        // shouldShowRequestPermissionRationale 在永久拒绝后返回 false。
        val activity = context as? Activity
        val permanentlyDenied = hasRequestedOnce &&
            activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

        PermissionRequest(
            modifier = modifier,
            permanentlyDenied = permanentlyDenied,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }
        )
    }
}

/**
 * 已授权后的相机主屏：CameraX 实时预览 + 角色叠层 + 快门 + AI 评论气泡。
 */
@Composable
private fun CameraContent(
    captureMode: CaptureMode,
    selectedMode: CaptureMode,
    onSelectMode: (CaptureMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
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

    // 位置开启时：已授权则预热（提前缓存地名）；未授权则申请粗略定位权限。
    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) LocationProvider.prewarm(context) }
    LaunchedEffect(locationEnabled) {
        if (locationEnabled) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                LocationProvider.prewarm(context)
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    val controller = remember { CameraController() }
    val characterController = remember { SpriteCharacterController() }
    val character by characterController.character.collectAsStateWithLifecycle()
    val pose by characterController.pose.collectAsStateWithLifecycle()
    // 角色的用户变换（拖动/缩放）。hoist 到此层，M4 合成时可直接读。
    var characterTransform by remember { mutableStateOf(CharacterTransform()) }
    // 角色叠层几何（拍照时用于全分辨率合成）。
    val captureInfo = remember { CharacterCaptureInfo() }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    // 拍后面板（查看/修改/分享）是否打开。点左上缩略图触发，不自动弹。
    var showPhotoSheet by rememberSaveable { mutableStateOf(false) }
    // 工具条展开：初值取设置里的"默认展开"；会话内点把手可临时切换（不写回设置）。
    var toolbarExpanded by rememberSaveable(toolbarDefaultExpanded) { mutableStateOf(toolbarDefaultExpanded) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // 拍照闪光反馈。
    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 绑定预览；前后置切换或比例变化时重新绑定。
    LaunchedEffect(lensFacing, previewAspect) {
        controller.bindToLifecycle(
            context, lifecycleOwner, previewView, lensFacing, aspect = previewAspect,
        )
    }
    // 设备朝向（重力感应，锁屏下仍有效）。结合"屏幕方向"设置：锁定时忽略传感器、用固定方向。
    val sensorRotation by rememberDeviceRotation()
    val deviceRotation = effectiveRotation(orientationLock, sensorRotation)
    LaunchedEffect(deviceRotation) { controller.setTargetRotation(deviceRotation) }
    LaunchedEffect(Unit) { characterController.playIdle() }

    // 拍照结果提示改由 ShootingScaffold 的会转向提示条显示（横拿转正）。

    // 快门瞬间组装角色合成参数：基础矩形（相对叠层）+ 当前变换 + 立绘渲染器。
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

    // 返回键：先关掉打开的面板（比 AppRoot 更深、优先拦截），退无可退再交给上层。
    BackHandler(enabled = uiState.captionPhoto != null) { viewModel.cancelCaption() }
    BackHandler(enabled = showPhotoSheet) { showPhotoSheet = false }

    Box(modifier = modifier.fillMaxSize()) {
        ShootingScaffold(
            previewAspect = previewAspect,
            controlRotation = uprightDegrees(deviceRotation),
            message = uiState.message,
            onMessageShown = { viewModel.onMessageShown() },
            // 预览区：CameraX 预览 + 角色叠层 + 闪光。角色/合成都钉在这框上——移动框不影响合成（几何相对框计算）。
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
            // 可收起的「角色 / 动作」工具条（默认收起；展开=动作条+角色条+下收按钮）。
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
                        if (captureMode == CaptureMode.Caption) {
                            viewModel.onCaptionShutter(
                                controller = controller,
                                character = captureSpec(),
                                context = context,
                                locationEnabled = locationEnabled,
                                characterId = character.id,
                            )
                        } else {
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
                                offlineLines = character.offlineLines,
                            )
                        }
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
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = "切换前后摄像头",
                        tint = Color.White,
                    )
                }
            },
            // 最近照片缩略图（拍过才出现）；点开拍后面板（查看/修改/分享）。
            secondaryStart = {
                uiState.lastPhotoThumbnail?.let { thumb ->
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "最近照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showPhotoSheet = true }
                    )
                }
            },
            // 左上角：打开相册。
            topStart = {
                IconButton(onClick = onOpenGallery) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = "相册",
                        tint = Color.White,
                    )
                }
            },
            // 进入设置。
            topEnd = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = Color.White,
                    )
                }
            },
        )

        // AI 评论气泡 + 维基弹窗 + 纠错（共享组件，自带 TTS）。全屏兄弟叠层。
        AiCommentOverlay(
            viewModel = viewModel,
            knowledgeLinkEnabled = knowledgeLinkEnabled,
            ttsEnabled = ttsEnabled,
            controlRotation = uprightDegrees(deviceRotation),
        )

        // 留言面板（拍过留言照片后弹出）。
        uiState.captionPhoto?.let { photo ->
            CaptionSheet(
                photo = photo,
                onSubmit = { caption ->
                    viewModel.submitCaption(caption, pose, character.persona, geminiApiKey, aiEnabled, locationEnabled, timeGreetingEnabled, context, character.displayName, character.offlineLines)
                },
                onCancel = { viewModel.cancelCaption() },
            )
        }

        // 拍后结果面板：点左上缩略图打开（查看/修改/分享）。
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

/**
 * 留言面板：显示刚拍的照片 + 留言输入（打字或系统语音 RecognizerIntent）。
 */
@Composable
private fun CaptionSheet(
    photo: android.graphics.Bitmap,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var captionText by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                captionText = if (captionText.isBlank()) text else "$captionText $text"
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("加一句留言", style = MaterialTheme.typography.titleMedium)
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "刚拍的照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp),
                )
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    label = { Text("留言（打字，或点麦克风语音输入）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出留言")
                        }
                        runCatching { voiceLauncher.launch(intent) }
                    }) {
                        Icon(Icons.Filled.Mic, contentDescription = "语音输入")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("取消") }
                    Button(
                        onClick = { onSubmit(captionText) },
                        enabled = captionText.isNotBlank(),
                    ) { Text("发给 AI") }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = if (permanentlyDenied) {
                "相机权限已被拒绝。请到系统设置中手动开启，才能拍照合影。"
            } else {
                "雨中逸事需要使用相机，让虚拟角色和你一起合影。"
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) {
                Text("打开应用设置")
            }
        } else {
            Button(onClick = onRequest) {
                Text("授予相机权限")
            }
        }
    }
}
