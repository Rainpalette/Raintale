package io.github.rainpalette.raintale.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.rainpalette.raintale.camera.PREVIEW_VERTICAL_BIAS
import io.github.rainpalette.raintale.camera.PreviewAspect

// ===== 控件摆位常量（横竖屏共用同一套——控件不随方向重排）=====
private val SHUTTER_BOTTOM = 32.dp
private val MODE_BOTTOM = 124.dp
private val TOOLBAR_BOTTOM = 168.dp
private val FLIP_END = 28.dp
private val FLIP_BOTTOM = 48.dp
private val THUMB_START = 24.dp
private val THUMB_BOTTOM = 40.dp

/**
 * 拍摄页共享布局脚手架：抽出拍照页 / 人像页重复的控件摆位，只负责"摆放"，内容由各页填槽。
 *
 * **设计取向（经讨论定）：控件布局与屏幕方向无关**——横竖屏控件待在**同一套位置**（底部一摞 + 两角），
 * 不重排。旋转时窗口仍随方向旋转（`MainActivity` 的 `configChanges`），所以：
 * - 预览 / 角色 / icon 在旋转后的画布里**天然是正的**，无需手动转正；
 * - 横拿自动产出横构图照片（`CameraController` 的 `targetRotation` 已处理）；
 * - **唯一随方向变的是预览框**：横屏比例翻转（变宽），这是"横图"取景与角色合成对齐的前提。
 *
 * 槽位：`preview`（CameraX 预览 + 角色叠层 +（人像前景）+ 闪光）/ `shutter` / `modeStrip` / `toolbar` /
 * `secondaryStart`（缩略图）/ `secondaryEnd`（前后翻转）/ `topStart` / `topEnd`。
 * 全屏弹窗（AI 评论气泡、留言/结果面板、对话框）由各页作为本脚手架的**兄弟叠层**渲染，不进槽位。
 *
 * 角色合成几何相对预览框计算，故预览框随方向变宽不影响合成。
 * 备选「硬锁竖屏」方案（UI 完全不旋转、靠传感器侦测横拿出横图）见 docs/phase2-design.md，未采用。
 */
@Composable
fun ShootingScaffold(
    previewAspect: PreviewAspect,
    shutter: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    // 图标类槽位（设置/翻转/缩略图）的「转正」角度：锁定竖屏下横拿时抵消视觉旋转。
    // 文字类条带（模式条/工具条）不转——和系统相机一样保持水平可读。
    controlRotation: Float = 0f,
    topStart: @Composable () -> Unit = {},
    topEnd: @Composable () -> Unit = {},
    modeStrip: @Composable () -> Unit = {},
    toolbar: @Composable () -> Unit = {},
    secondaryStart: @Composable () -> Unit = {},
    secondaryEnd: @Composable () -> Unit = {},
    // 拍摄页状态提示：会跟手机转向的应用内提示条（替代系统 Toast，横拿转正）。message 非空时显示，onMessageShown 清空来源。
    message: String? = null,
    onMessageShown: () -> Unit = {},
    preview: @Composable BoxScope.() -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier.fillMaxSize()) {
        // 预览框：FULL 满屏；固定比例则居中 + 竖直上偏（黑边大头在下，给底部控件让位）。
        // 比例按方向翻转（横屏变宽），但偏移始终竖直——因控件恒在底部。
        val boxModifier = previewAspect.boxRatio(landscape)
            ?.let { Modifier.aspectRatio(it) }
            ?: Modifier.fillMaxSize()
        val previewAlign = if (previewAspect == PreviewAspect.FULL) {
            Alignment.Center
        } else {
            BiasAlignment(0f, PREVIEW_VERTICAL_BIAS)
        }
        Box(modifier = boxModifier.align(previewAlign), content = preview)

        // 控件：横竖屏同一套位置（底部一摞 + 两侧 + 两顶角），不随方向重排。
        // 文字类条带（模式/工具）不转正；图标类（缩略图/翻转/两顶角）套 controlRotation 转正。
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = TOOLBAR_BOTTOM)) { toolbar() }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = MODE_BOTTOM)) { modeStrip() }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = SHUTTER_BOTTOM)) { shutter() }
        Box(Modifier.align(Alignment.BottomStart).padding(start = THUMB_START, bottom = THUMB_BOTTOM).rotate(controlRotation)) { secondaryStart() }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = FLIP_END, bottom = FLIP_BOTTOM).rotate(controlRotation)) { secondaryEnd() }
        Box(Modifier.align(Alignment.TopStart).padding(8.dp).rotate(controlRotation)) { topStart() }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).rotate(controlRotation)) { topEnd() }

        // 状态提示浮层（会跟手机转向）——用 controlRotation 转正，和图标同一套。
        RotatingMessageHost(message = message, rotationDegrees = controlRotation, onShown = onMessageShown)
    }
}
