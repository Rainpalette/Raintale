package io.github.rainpalette.raintale.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用量计（debug 阶段）。
 *
 * Gemini 公开 API **没有"查剩余配额"的接口**，无法实际抓取剩余用量，
 * 故 debug 阶段用本地计数：每次 AI 调用记 10%（即 10 次满）。
 * 正式版应替换为后端按用户计量。
 */
object UsageMeter {

    private const val PER_CALL = 0.10f

    private val _usage = MutableStateFlow(0f)
    /** 0f ~ 1f。 */
    val usage: StateFlow<Float> = _usage.asStateFlow()

    /** 记一次 AI 调用（+10%，封顶 100%）。 */
    fun record() {
        _usage.value = (_usage.value + PER_CALL).coerceAtMost(1f)
    }

    fun reset() {
        _usage.value = 0f
    }
}
