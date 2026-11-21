package io.github.rainpalette.raintale.live

import android.graphics.Bitmap
import androidx.core.graphics.scale

/**
 * 本地画面变化检测（感知哈希 aHash）。把帧缩到 8×8 灰度、按均值二值化成 64 位哈希，
 * 与上一帧比汉明距离，超过 [threshold] 即判为"变了"。**本地、免费、毫秒级**，
 * 用来在画面没变时跳过 AI 调用，省 token 又避免重复骚扰。
 *
 * threshold 越小越灵敏。debug 阶段固定"中"（见 [SENSITIVITY_MEDIUM]）。
 */
class FrameChangeDetector(private val threshold: Int = SENSITIVITY_MEDIUM) {

    private var lastHash: Long? = null

    /** 与上一帧比：变了（或首帧）返回 true，并把当前帧记为新的"上一帧"。 */
    fun isChanged(bitmap: Bitmap): Boolean {
        val hash = aHash(bitmap)
        val prev = lastHash
        lastHash = hash
        if (prev == null) return true
        val distance = java.lang.Long.bitCount(hash xor prev)
        return distance >= threshold
    }

    fun reset() {
        lastHash = null
    }

    private fun aHash(bitmap: Bitmap): Long {
        val small = bitmap.scale(SIZE, SIZE)
        val pixels = IntArray(SIZE * SIZE)
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (small != bitmap) small.recycle()

        val gray = IntArray(pixels.size)
        var sum = 0L
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            gray[i] = lum
            sum += lum
        }
        val avg = sum / pixels.size

        var hash = 0L
        for (i in gray.indices) {
            if (gray[i] >= avg) hash = hash or (1L shl i)
        }
        return hash
    }

    companion object {
        private const val SIZE = 8 // 8×8 = 64 位哈希

        // 汉明距离阈值（0~64）。越小越灵敏。
        const val SENSITIVITY_HIGH = 4
        const val SENSITIVITY_MEDIUM = 8
        const val SENSITIVITY_LOW = 14
    }
}
