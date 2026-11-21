package io.github.rainpalette.raintale.ui.live

/**
 * 实时点评界面状态。
 */
data class LiveUiState(
    /** 点评循环是否运行中。 */
    val isRunning: Boolean = false,
    /** 当前连续静止次数（debug 指标）。 */
    val staticCount: Int = 0,
    /** 当前评论。 */
    val comment: String? = null,
    /** 是否正在请求 AI。 */
    val isCommenting: Boolean = false,
    /** 一次性提示。 */
    val message: String? = null,
)
