package io.github.rainpalette.raintale.ai

/**
 * Gemini 结构化输出的结果。
 *
 * @param text 给用户的那句话（没把握时为问句）。
 * @param entity 画面里识别到的地标/品牌/物体名；没有则 null（可挂知识链接）。
 * @param entityConfident 对 [entity] 名称是否有把握。
 */
data class AiComment(
    val text: String,
    val entity: String? = null,
    val entityConfident: Boolean = false,
    /** entity 是否值得挂知识链接：专有/知名实体（地标/品牌/知名作品）为 true；通用物体为 false。 */
    val linkworthy: Boolean = false,
)
