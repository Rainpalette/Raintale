package io.github.rainpalette.raintale.ui.character

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainpalette.raintale.character.CharacterController
import kotlin.math.cos
import kotlin.math.sin

/**
 * 把角色叠在相机预览之上的透明叠层。
 *
 * 纯 Compose 叠层天然透明，无需 GLSurfaceView 的 z-order 处理。
 * M3：支持单指拖动、双指捏合缩放（[transform] / [onTransformChange] 由上层 hoist），
 * 用户变换与 Idle"呼吸"叠加。
 * M4 上限（全分辨率角色）：通过 [captureInfo] 上报角色的**基础布局矩形**与叠层尺寸，拍照时据此把
 * 立绘按成片分辨率重新绘制（不再截屏放大）。`onGloballyPositioned` 放在 `graphicsLayer` 之前，
 * 故上报的矩形不含用户变换/呼吸——那些在合成时由已知 transform 手动套用。
 */
@Composable
fun CharacterOverlay(
    controller: CharacterController,
    transform: CharacterTransform,
    onTransformChange: (CharacterTransform) -> Unit,
    modifier: Modifier = Modifier,
    captureInfo: CharacterCaptureInfo? = null,
    // 锁定竖屏下，横拿时把角色「转正站直」（绕脚下=底部中心旋转）。0=竖屏不转。
    characterRotation: Float = 0f,
) {
    val character by controller.character.collectAsStateWithLifecycle()
    val pose by controller.pose.collectAsStateWithLifecycle()

    // pointerInput(Unit) 的手势回调只初始化一次，需用 rememberUpdatedState 读最新值，
    // 否则会捕获到首次组合时的旧 transform。
    val currentTransform by rememberUpdatedState(transform)
    val currentOnChange by rememberUpdatedState(onTransformChange)
    // 横拿时角色转了，拖动手势的 pan 也要随之旋转，否则方向错乱（输入仍按竖屏坐标）。
    val currentRotation by rememberUpdatedState(characterRotation)

    // Idle 呼吸：0→1 缓慢往返，驱动轻微缩放与上下浮动。
    val transition = rememberInfiniteTransition(label = "idle")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val breathScale = 1f + 0.04f * breath
    val breathTranslateY = -6f * breath

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { captureInfo?.setOverlay(it.positionInRoot(), it.size) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painter = painterResource(character.drawableFor(pose)),
            contentDescription = "${character.displayName} · ${pose.label}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                // 初始抬高一点，给底部控件让位；之后可被拖动覆盖。
                .padding(bottom = 96.dp)
                .fillMaxHeight(0.4f)
                // 放在 graphicsLayer 之前：上报的是**不含用户变换**的基础矩形（供全分辨率合成）。
                .onGloballyPositioned { captureInfo?.setBase(it.boundsInRoot()) }
                .graphicsLayer {
                    // 用户缩放 × 呼吸缩放；平移 = 用户位移 + 呼吸浮动。
                    scaleX = transform.scale * breathScale
                    scaleY = transform.scale * breathScale
                    translationX = transform.offset.x
                    translationY = transform.offset.y + breathTranslateY
                    // 横拿时转正；竖拿为 0。
                    rotationZ = characterRotation
                    // 竖拿：锚点底部中心（缩放/呼吸时"站稳"）。横拿转正时锚点改为中心 → **原地转**（不绕脚扫弧）。
                    transformOrigin =
                        if (characterRotation != 0f) TransformOrigin(0.5f, 0.5f) else TransformOrigin(0.5f, 1f)
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val t = currentTransform
                        val newScale = (t.scale * zoom)
                            .coerceIn(CharacterTransform.MIN_SCALE, CharacterTransform.MAX_SCALE)
                        // 把 pan 按角色转正角旋转，使横拿时"用户视角的右"对应角色实际向右。
                        currentOnChange(
                            t.copy(
                                offset = t.offset + rotateOffset(pan, currentRotation),
                                scale = newScale,
                            )
                        )
                    }
                },
        )
    }
}

/** 把位移向量绕原点旋转 [degrees] 度（屏幕 y 向下）。真机若拖动方向仍反，翻 degrees 正负号。 */
private fun rotateOffset(o: Offset, degrees: Float): Offset {
    if (degrees == 0f) return o
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return Offset(o.x * c - o.y * s, o.x * s + o.y * c)
}
