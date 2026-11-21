package io.github.rainpalette.raintale.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 把角色立绘（PNG 或矢量）**按指定像素尺寸光栅化**成透明背景 Bitmap，供全分辨率合成用。
 *
 * 关键：在**成片分辨率**下渲染（而非把屏幕截图放大），所以角色不再发软；矢量图也按目标尺寸清晰绘制。
 * 立绘按 `ContentScale.Fit` 语义居中适配进 [width]×[height]（与 [CharacterOverlay] 一致）。
 */
object CharacterRenderer {

    fun render(context: Context, @DrawableRes resId: Int, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val drawable = ContextCompat.getDrawable(context, resId) ?: return bitmap

        val iw = drawable.intrinsicWidth.coerceAtLeast(1)
        val ih = drawable.intrinsicHeight.coerceAtLeast(1)
        // Fit：等比缩放使立绘完整放入 w×h，居中。
        val scale = min(w.toFloat() / iw, h.toFloat() / ih)
        val dw = (iw * scale).roundToInt().coerceAtLeast(1)
        val dh = (ih * scale).roundToInt().coerceAtLeast(1)
        val left = (w - dw) / 2
        val top = (h - dh) / 2
        drawable.setBounds(left, top, left + dw, top + dh)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
