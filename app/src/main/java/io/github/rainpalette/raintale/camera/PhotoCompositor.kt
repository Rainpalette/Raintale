package io.github.rainpalette.raintale.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.Surface
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 把相机帧与角色叠层合成一张图（M4 的核心）。纯函数、可单测。
 *
 * 简化版对齐策略（见 README「合成方案」）：
 * 角色叠层是按屏幕原样截下来的整层 Bitmap（所见即所得）。预览用 FILL_CENTER（中心
 * 裁切填满屏幕），所以这里对相机原图做**同样的中心裁切**到叠层的宽高比——裁完即与屏幕
 * 一一对应，"屏幕→原图"退化成纯等比缩放，于是把叠层等比铺满裁切后的相机图即可。
 */
object PhotoCompositor {

    /**
     * 全分辨率合成（M4 上限）：不再用屏幕分辨率的角色截图，而是按**成片分辨率**重新绘制角色立绘。
     *
     * 坐标映射：叠层（屏幕）→ 成片是单一等比缩放 `s`（简化版已建立的对应关系）。角色的最终位置由
     * 基础矩形 + 用户变换（绕底部中心缩放 + 平移；呼吸忽略）算出屏幕矩形，再乘 `s` 得成片矩形。
     *
     * @param cameraBitmap 相机高清原图（已旋正）。
     * @param overlayWidth/overlayHeight 叠层（屏幕）尺寸 px。
     * @param charBase* 角色基础布局矩形（叠层坐标 px，不含用户变换）。
     * @param charScale/charOffsetX/charOffsetY 用户缩放与平移（px）。
     * @param renderCharacter 给定目标像素尺寸返回光栅化好的立绘（按成片分辨率，清晰）。
     * @param personForeground 可选人像前景（全分辨率、背景透明），非 null 时画在角色之上（人在前）。
     */
    fun composeFullRes(
        cameraBitmap: Bitmap,
        overlayWidth: Int,
        overlayHeight: Int,
        charBaseLeft: Float,
        charBaseTop: Float,
        charBaseRight: Float,
        charBaseBottom: Float,
        charScale: Float,
        charOffsetX: Float,
        charOffsetY: Float,
        renderCharacter: (widthPx: Int, heightPx: Int) -> Bitmap,
        personForeground: Bitmap? = null,
        deviceRotation: Int = Surface.ROTATION_0,
    ): Bitmap {
        val landscape = deviceRotation == Surface.ROTATION_90 || deviceRotation == Surface.ROTATION_270
        // 横拿时成片要跟着变宽 → 裁切比例转置（竖屏 3:4 → 横屏 4:3），与横屏预览所见一致、保住全分辨率。
        val targetAspect = if (landscape) {
            overlayHeight.toFloat() / overlayWidth
        } else {
            overlayWidth.toFloat() / overlayHeight
        }
        val cropped = centerCropToAspect(cameraBitmap, targetAspect)

        val result = cropped.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }

        val lw = charBaseRight - charBaseLeft
        val lh = charBaseBottom - charBaseTop
        if (lw > 0f && lh > 0f) {
            // 角色"脚点"（底部中心 + 用户平移）与尺寸，均在叠层（竖屏 buffer）坐标系。
            val originX = charBaseLeft + lw / 2f
            val originY = charBaseBottom
            val newW = lw * charScale
            val newH = lh * charScale
            val feetX = originX + charOffsetX
            val feetY = originY + charOffsetY

            if (!landscape) {
                // 竖拿：叠层→成片单一等比缩放 s。
                val s = result.width.toFloat() / overlayWidth
                val outW = (newW * s).roundToInt().coerceAtLeast(1)
                val outH = (newH * s).roundToInt().coerceAtLeast(1)
                val charBmp = renderCharacter(outW, outH)
                canvas.drawBitmap(charBmp, (feetX - newW / 2f) * s, (feetY - newH) * s, paint)
            } else {
                // 横拿（Phase 2b）：立绘**正立**，角色**中心**从竖屏 buffer 坐标经 90° 旋转映射到横向成片
                // （绕中心=原地转，与屏上一致）。s 用转置比例（buffer 高 ↔ 成片宽）。
                // 两种横向（90/270）方向相反；真机若左右/上下反了，**对调这两支公式**。
                val s = result.width.toFloat() / overlayHeight
                val centerX = feetX               // = originX + 用户平移x
                val centerY = feetY - lh / 2f      // box 中心（绕中心缩放 → 中心不随 scale 动）
                val cpx: Float
                val cpy: Float
                if (deviceRotation == Surface.ROTATION_90) {
                    cpx = (overlayHeight - centerY) * s
                    cpy = centerX * s
                } else { // ROTATION_270
                    cpx = centerY * s
                    cpy = (overlayWidth - centerX) * s
                }
                val spriteW = (newW * s).roundToInt().coerceAtLeast(1)
                val spriteH = (newH * s).roundToInt().coerceAtLeast(1)
                val charBmp = renderCharacter(spriteW, spriteH)
                // 中心对齐 (cpx, cpy)。
                canvas.drawBitmap(charBmp, cpx - spriteW / 2f, cpy - spriteH / 2f, paint)
            }
        }

        // 人像前景与相机图同尺寸，按同一宽高比中心裁切 → 与裁切后的相机图严丝合缝；画在角色之上。
        if (personForeground != null) {
            val croppedPerson = centerCropToAspect(personForeground, targetAspect)
            val softPerson = croppedPerson.toSoftwareBitmap()
            canvas.drawBitmap(softPerson, null, Rect(0, 0, result.width, result.height), paint)
        }
        return result
    }

    /** 若是 HARDWARE 配置则拷贝成软件 ARGB_8888，否则原样返回。 */
    private fun Bitmap.toSoftwareBitmap(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    /** 把 [src] 居中裁切到指定宽高比（宽/高）。已匹配则原样返回。 */
    private fun centerCropToAspect(src: Bitmap, targetAspect: Float): Bitmap {
        val srcAspect = src.width.toFloat() / src.height
        if (abs(srcAspect - targetAspect) < 1e-3f) return src

        return if (srcAspect > targetAspect) {
            // 原图更宽 → 裁掉左右。
            val newWidth = (src.height * targetAspect).roundToInt().coerceAtMost(src.width)
            val x = (src.width - newWidth) / 2
            Bitmap.createBitmap(src, x, 0, newWidth, src.height)
        } else {
            // 原图更高 → 裁掉上下。
            val newHeight = (src.width / targetAspect).roundToInt().coerceAtMost(src.height)
            val y = (src.height - newHeight) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newHeight)
        }
    }
}
