package io.github.rainpalette.raintale.camera

import androidx.camera.core.resolutionselector.AspectRatioStrategy

/**
 * 4:3/16:9 预览框的竖直对齐偏移（0=居中，负=上偏）。
 * 取 -0.25 → 上小下大：顶部黑边小、底部黑边大，让模式条/快门落在底部黑框里。**调这个数改上下黑边比例**。
 */
const val PREVIEW_VERTICAL_BIAS = -0.25f

/**
 * 取景/拍照比例。FULL = 满屏裁切（现状）；4:3 / 16:9 = 居中固定比例 + 黑边（像正常相机）。
 * 横屏时比例翻转由布局层按方向取（本步先做竖屏）。
 */
enum class PreviewAspect(val label: String) {
    FULL("全屏"),
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9");

    /** 预览框的"宽/高"；按方向翻转（竖屏 4:3→3:4、横屏→4:3）。FULL 返回 null（满屏、不留边）。 */
    fun boxRatio(landscape: Boolean): Float? = when (this) {
        FULL -> null
        RATIO_4_3 -> if (landscape) 4f / 3f else 3f / 4f
        RATIO_16_9 -> if (landscape) 16f / 9f else 9f / 16f
    }

    /** 让相机直接出该比例、少裁切（FULL 用 16:9，反正满屏会再裁）。 */
    val aspectStrategy: AspectRatioStrategy
        get() = when (this) {
            RATIO_4_3 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            else -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        }
}
