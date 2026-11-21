package io.github.rainpalette.raintale.data.character

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自建的**纯 prompt 角色**（无立绘、无语音）——用于陪伴模式。
 *
 * 分栏存储（而非一整段 persona）是为了**引导用户写出好 prompt** + 编辑时回填。
 * [persona] 把非空栏拼成喂给 Gemini 的 systemInstruction（写法对齐 README「给角色写性格」）。
 */
@Entity(tableName = "custom_character")
data class CustomCharacter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 角色名（必填）；也用作影集批注的作者名。 */
    val name: String,
    /** 身份 / 外形（一句话）。 */
    val identity: String = "",
    /** 性格（几个关键词）。 */
    val personality: String = "",
    /** 说话风格 / 口头禅（最影响效果）。 */
    val speech: String = "",
    /** 和拿相机的人的关系。 */
    val relationship: String = "",
    /** 额外补充（选填）。 */
    val extra: String = "",
    val createdAt: Long = 0,
) {
    /** 拼成 persona（空栏跳过）。只写"是谁 + 性格 + 说话 + 关系 + 补充"，任务/格式由共享 prompt 负责。 */
    fun persona(): String = buildString {
        append("你是$name")
        if (identity.isNotBlank()) append("，$identity")
        append("。")
        if (personality.isNotBlank()) append("性格$personality。")
        if (speech.isNotBlank()) append("说话$speech。")
        if (relationship.isNotBlank()) append("你把拿相机的人当成$relationship。")
        if (extra.isNotBlank()) append(extra)
    }
}
