package io.github.rainpalette.raintale.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

/**
 * 「横屏舞台」：在**锁定竖屏**的窗口里，为横拿手机提供一个内部是**横屏坐标系**的透明叠层。
 *
 * 为什么需要它：`Modifier.rotate` 只转画面、不改布局尺寸。一个占满宽度的元素（如评论气泡）
 * 在竖屏布局里横向已填满，靠 `align`/`BiasAlignment` 永远移不到横拿时的物理边缘，`rotate` 也只是绕自身
 * 中心转，中心仍在横向正中。
 *
 * 本组件把一个**交换了宽高**的 Box 居中后整体旋转 [rotationDegrees]（传 `uprightDegrees(deviceRotation)`）：
 * 旋转后正好覆盖竖屏窗口，而其内部坐标系是横屏的——`Alignment.TopCenter` 就是**横拿时的物理顶边**
 * （旋转已让内容转正，转正内容的"顶"即物理"上"；两个横屏方向都自动正确，无需翻正负号）。
 *
 * 直屏（0°）时退化为普通 `fillMaxSize`，**行为与原来完全一致**（满足"直屏不用改"）。
 *
 * 用法：`RotatedStage(uprightDegrees(deviceRotation)) { Bubble(Modifier.align(Alignment.TopCenter)) }`
 */
@Composable
fun RotatedStage(
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (rotationDegrees % 180f == 0f) {
        // 直屏 0°（或倒置 180°）：无需交换宽高，直接铺满（0° 时 rotate 为空操作）。
        Box(modifier.fillMaxSize().rotate(rotationDegrees), content = content)
    } else {
        // 横拿 ±90°：用交换了宽高的舞台，居中后整体旋转 → 覆盖竖屏窗口，内部为横屏坐标系。
        BoxWithConstraints(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(width = maxHeight, height = maxWidth)
                    .rotate(rotationDegrees),
                content = content,
            )
        }
    }
}
