package io.github.rainpalette.raintale.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val SHOW_MS = 2200L

/**
 * 会跟手机转向的**应用内提示条**（拍摄页用它替代系统 Toast）。
 *
 * 系统 Toast 在锁定竖屏下永远竖着显示，横拿手机看就是歪的；此组件把提示画进 app 内，
 * 整条按 [rotationDegrees] 旋转——传 `controlRotation` / `uprightDegrees(deviceRotation)`，
 * 横拿时提示与图标一起转正、打横显示。
 *
 * [message] 非空时淡入，[SHOW_MS] 后淡出并调 [onShown]（清空来源，行为等价原来的 `onMessageShown()`）。
 * 本组件是 `fillMaxSize` 透明浮层，除提示条外不拦截触摸（快门等照常可点）。
 */
@Composable
fun RotatingMessageHost(
    message: String?,
    rotationDegrees: Float,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // text 在淡出动画期间仍保留内容（visible=false 但 text 未清）；visible 控制显隐。
    var text by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            text = message
            visible = true
            delay(SHOW_MS)
            visible = false
            onShown()
        }
    }

    // 横屏舞台：横拿时 BottomCenter = 物理正下方；直屏为普通底部居中。旋转由舞台统一处理（提示条不再自转）。
    RotatedStage(rotationDegrees = rotationDegrees, modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        ) {
            Surface(
                color = Color(0xE6202A33),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(
                    text.orEmpty(),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
