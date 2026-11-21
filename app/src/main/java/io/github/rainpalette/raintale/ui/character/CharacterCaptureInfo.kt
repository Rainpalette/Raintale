package io.github.rainpalette.raintale.ui.character

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/**
 * 角色叠层在拍照时上报的几何信息，供**全分辨率合成**把角色画到成片正确位置。
 *
 * 由 [CharacterOverlay] 通过 `onGloballyPositioned` 实时更新；拍照瞬间读取。
 * - `overlay*`：叠层 Box 在 root 坐标的位置与尺寸（≈屏幕）。
 * - `base*`：角色立绘的**布局矩形**（root 坐标，px），**不含用户变换**（呼吸/拖动/缩放）——
 *   `onGloballyPositioned` 放在 `graphicsLayer` 之前，故不含其视觉变换。用户变换在合成时由
 *   已知的 `CharacterTransform` 手动套用，呼吸忽略（静态照片无意义）。
 */
class CharacterCaptureInfo {
    var overlayLeft = 0f
    var overlayTop = 0f
    var overlayWidth = 0
    var overlayHeight = 0
    var baseLeft = 0f
    var baseTop = 0f
    var baseRight = 0f
    var baseBottom = 0f

    val valid: Boolean get() = overlayWidth > 0 && baseRight > baseLeft && baseBottom > baseTop

    fun setOverlay(position: Offset, size: IntSize) {
        overlayLeft = position.x
        overlayTop = position.y
        overlayWidth = size.width
        overlayHeight = size.height
    }

    fun setBase(rect: Rect) {
        baseLeft = rect.left
        baseTop = rect.top
        baseRight = rect.right
        baseBottom = rect.bottom
    }
}
