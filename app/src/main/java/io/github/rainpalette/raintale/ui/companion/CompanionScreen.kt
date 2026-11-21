package io.github.rainpalette.raintale.ui.companion

import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rainpalette.raintale.camera.CameraController
import io.github.rainpalette.raintale.camera.OrientationLock
import io.github.rainpalette.raintale.camera.PreviewAspect
import io.github.rainpalette.raintale.data.character.CustomCharacterRepository
import io.github.rainpalette.raintale.ui.camera.CameraViewModel
import io.github.rainpalette.raintale.ui.camera.effectiveRotation
import io.github.rainpalette.raintale.ui.camera.rememberDeviceRotation
import io.github.rainpalette.raintale.ui.camera.uprightDegrees
import io.github.rainpalette.raintale.ui.components.CaptureMode
import io.github.rainpalette.raintale.ui.components.CommentBubble
import io.github.rainpalette.raintale.ui.components.ModeStrip
import io.github.rainpalette.raintale.ui.components.ShootingScaffold
import io.github.rainpalette.raintale.ui.components.ShutterButton
import kotlinx.coroutines.launch

/**
 * 陪伴模式：**无立绘**的相机页，配自定义（纯 prompt）角色。
 * 快门 → 存普通照片 → 顶部弹一句**纯文字**（无 TTS）由所选自定义角色说。没有自定义角色则引导去创建。
 */
@Composable
fun CompanionScreen(
    selectedMode: CaptureMode,
    onSelectMode: (CaptureMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenCustomCharacters: () -> Unit,
    aiEnabled: Boolean,
    locationEnabled: Boolean,
    timeGreetingEnabled: Boolean,
    orientationLock: OrientationLock,
    previewAspect: PreviewAspect,
    geminiApiKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: CameraViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val charRepo = remember { CustomCharacterRepository(context) }
    val customChars by charRepo.all.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = customChars.find { it.id == selectedId } ?: customChars.firstOrNull()

    val controller = remember { CameraController() }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lensFacing, previewAspect) {
        controller.bindToLifecycle(context, lifecycleOwner, previewView, lensFacing, aspect = previewAspect)
    }
    val sensorRotation by rememberDeviceRotation()
    val deviceRotation = effectiveRotation(orientationLock, sensorRotation)
    LaunchedEffect(deviceRotation) { controller.setTargetRotation(deviceRotation) }

    // 提示改由 ShootingScaffold 的会转向提示条显示（横拿转正）。

    Box(modifier = modifier.fillMaxSize()) {
        ShootingScaffold(
            previewAspect = previewAspect,
            controlRotation = uprightDegrees(deviceRotation),
            message = uiState.message,
            onMessageShown = { viewModel.onMessageShown() },
            preview = {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                if (flashAlpha.value > 0f) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))
                }
            },
            // 自定义角色选择条（陪伴模式没有立绘/动作，只选"谁陪你"）。
            toolbar = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    customChars.forEach { c ->
                        FilterChip(
                            selected = c.id == selected?.id,
                            onClick = { selectedId = c.id },
                            label = { Text(c.name) },
                        )
                    }
                }
            },
            modeStrip = { ModeStrip(selected = selectedMode, onSelect = onSelectMode) },
            shutter = {
                ShutterButton(
                    isCapturing = uiState.isCapturing,
                    onClick = {
                        val ch = selected
                        if (ch != null) {
                            scope.launch {
                                flashAlpha.snapTo(0.85f)
                                flashAlpha.animateTo(0f, animationSpec = tween(350))
                            }
                            viewModel.onCompanionShutter(
                                controller = controller,
                                persona = ch.persona(),
                                characterName = ch.name,
                                characterId = "custom:${ch.id}",
                                aiEnabled = aiEnabled,
                                apiKey = geminiApiKey,
                                locationEnabled = locationEnabled,
                                timeGreetingEnabled = timeGreetingEnabled,
                                context = context,
                            )
                        }
                    },
                )
            },
            secondaryEnd = {
                IconButton(onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "切换前后摄像头", tint = Color.White)
                }
            },
            topStart = {
                IconButton(onClick = onOpenGallery) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "相册", tint = Color.White)
                }
            },
            topEnd = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
                }
            },
        )

        // 角色说的一句话（纯文字、无 TTS、无知识链接）。
        CommentBubble(
            text = uiState.aiComment,
            isLoading = uiState.isCommenting,
            onDismiss = { viewModel.dismissComment() },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp, start = 16.dp, end = 16.dp),
        )

        // 没有自定义角色 → 引导去创建。
        if (customChars.isEmpty()) {
            Surface(
                color = Color(0xCC000000),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "陪伴模式需要一个你自己写的角色",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "写一个（只需填几栏 prompt），或之后再说。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onSelectMode(CaptureMode.Normal) }) { Text("之后再说") }
                        Button(onClick = onOpenCustomCharacters) { Text("去自定义") }
                    }
                }
            }
        }
    }
}
