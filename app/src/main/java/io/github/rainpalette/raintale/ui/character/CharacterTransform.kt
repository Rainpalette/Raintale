package io.github.rainpalette.raintale.ui.character

import androidx.compose.ui.geometry.Offset

/**
 * 角色在屏幕上的用户变换：相对初始位置的位移 + 缩放倍数。
 *
 * 由手势更新（拖动改 [offset]、捏合改 [scale]）。M4 合成时会据此把角色
 * 画到照片的对应位置/大小，所以状态 hoist 到 CameraContent 层持有。
 */
data class CharacterTransform(
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
) {
    companion object {
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 3.0f
    }
}
