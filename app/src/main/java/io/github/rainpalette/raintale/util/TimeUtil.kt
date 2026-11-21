package io.github.rainpalette.raintale.util

import java.util.Calendar

/** 时间相关工具。 */
object TimeUtil {

    /** 当前时段（用于让 AI 用对应问候开头）。 */
    fun currentTimeOfDay(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "早上"
            in 11..13 -> "中午"
            in 14..17 -> "下午"
            in 18..22 -> "晚上"
            else -> "深夜"
        }
    }
}
