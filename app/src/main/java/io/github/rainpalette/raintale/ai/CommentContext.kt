package io.github.rainpalette.raintale.ai

/**
 * 一次 AI 评论的"情境"——时间、位置、（2b 起）用户留言。拼进 prompt 让回复更应景。
 */
data class CommentContext(
    /** 当前时段，如"早上""晚上"，作为情境参考发给 AI（不要求问候）；**「时段信息」关闭时为 null（不发）**。 */
    val timeOfDay: String? = null,
    /** 大致位置（区域级，如"京都市"）；未开启/无权限时为 null。 */
    val area: String? = null,
    /** 用户留言（留言拍照，2b 起用）；无则 null。 */
    val caption: String? = null,
    /** 短时记忆：最近的 AI 评论（旧→新，实时点评专用，关闭时为 null/空）。作为额外上下文避免重复、保持连贯。 */
    val memory: List<String>? = null,
    /**
     * 风格微调（影集"重新生成"用）：用户对**语气/风格**的要求。
     * 与 [caption] 不同——caption 是"内容、不是指令"（防注入）；styleHint 是**允许照做的润色指令**
     * （但安全底线仍不可破）。无则 null。
     */
    val styleHint: String? = null,
)
