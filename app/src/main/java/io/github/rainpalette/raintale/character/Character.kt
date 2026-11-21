package io.github.rainpalette.raintale.character

import androidx.annotation.DrawableRes
import io.github.rainpalette.raintale.R

/**
 * 一个角色：有一张默认（idle）立绘，可选地给某些动作配专门的图。
 *
 * @param idle 默认/待机立绘，也是角色条上的缩略图。
 * @param poseOverrides 某动作的专属图；没配的动作回退到 [idle]（见 [drawableFor]）。
 * @param persona 角色性格描述，作为 Gemini 的 `systemInstruction`（每次调用都带）。
 *   **只写"是谁 + 性格 + 说话风格"**，不写任务/格式（那些在共享 prompt 里）。写法见 README「给角色写性格」。
 */
data class Character(
    val id: String,
    val displayName: String,
    @DrawableRes val idle: Int,
    val poseOverrides: Map<CharacterPose, Int> = emptyMap(),
    val persona: String = "",
    /**
     * 离线台词（**演示模式**的内容来源）：**关闭 AI 评论 / 未配置 key / 未联网**时，
     * 拍照后按场景挑一句（**不上传**；会朗读、会入影集，与 AI 评论体验一致）。
     *
     * 按 [OfflineContext] 分桶，见 [OfflineLinePool]；没配就用 [OfflineLines.generic]（通用池）。
     */
    val offlineLines: OfflineLinePool = OfflineLines.generic,
) {
    /** 该角色在指定动作下显示哪张图；没有专属图就用 [idle]。 */
    @DrawableRes
    fun drawableFor(pose: CharacterPose): Int = poseOverrides[pose] ?: idle
}

/**
 * 可选角色注册表。加新角色：在 [all] 里加一项即可，角色条会自动出现。
 *
 * **米娜**是招牌角色（世界观见 docs/worldview.md）。当前 `R.drawable.character_mina_placeholder`
 * 是自绘的「伞下剪影」占位 vector（零版权负担）——等米娜正式立绘就绪后替换 `idle` 即可，
 * persona 不变。布偶（糯米）为另一 demo 角色（占位矢量图）。
 */
object Characters {
    val all: List<Character> = listOf(
        Character(
            id = "mina",
            displayName = "米娜",
            idle = R.drawable.character_mina_placeholder, // 伞下剪影占位，待米娜正式立绘替换
            // 招牌角色用完整角色 prompt（单独成文件），非 ~100 字简版——见 MinaPersona 的分层纪律注释。
            persona = MinaPersona.TEXT,
            // 演示模式台词池（初稿 2026-11-19，按 MinaPersona 的硬性标准写：平铺直叙、口语、
            // 无比喻/拟声/环境描写、省略号只表停顿、不以固定问候开头、不描述看到了什么具体东西
            // ——预写台词并不知道画面里有什么，安全区是"此刻的态度"而非"现场还原"）。
            // 改词只动这里，选句逻辑不受影响；各桶触发条件见 docs/todo.md 附录 B。
            offlineLines = OfflineLinePool(
                anytime = listOf(
                    "……嗯，我看着呢。",
                    "雨还没停呢，慢慢来。",
                    "这一刻，我替你记下了。",
                    "……让我陪你，再多待一会儿吧。",
                    "拍好了？那就收着吧。",
                    "我也在里面吧。……嗯，这样挺好。",
                    "你倒是挺爱拍的。",
                    "这种事我帮不上什么，就看着。",
                    "不用问我好不好看，你自己觉得就行。",
                    "……留着也好。你总有一天要走的。",
                ),
                byPose = mapOf(
                    // IDLE 不单独写，交给 anytime（待机是默认状态，没什么可回应的）。
                    CharacterPose.WAVE to listOf(
                        "挥手啊……我不太习惯这个。",
                        "这是在跟谁打招呼？",
                        "嗯，挥了。这样可以吗？",
                    ),
                    CharacterPose.PEACE to listOf(
                        "这样比着……有什么讲究吗。",
                        "手要这么摆是吧。……行。",
                        "你好像很习惯这个动作。",
                    ),
                ),
                // 注意：hasPerson 目前只有「人像模式」页知道（普通拍照不跑分割），
                // 所以这两桶现在只在人像页生效。
                withPerson = listOf(
                    "在这儿的每一张脸，我都想好好看着。",
                    "你也进来了。那就一起留着。",
                    "……两个人站着，看着还行。",
                    "这张有你。以后你大概会想起来。",
                    "你比我上镜。",
                ),
                withoutPerson = listOf(
                    "只有景啊。",
                    "没人在的地方，也值得留一张。",
                    "空着也好，看着清静。",
                    "……这种地方，我待惯了。",
                ),
                // 键必须与 TimeUtil.currentTimeOfDay() 一字不差；其余时段交给 anytime。
                byTimeOfDay = mapOf(
                    "深夜" to listOf(
                        "这个点还不睡？",
                        "夜里安静些，我比较习惯。",
                        "……晚了。拍完就歇着吧。",
                    ),
                    "早上" to listOf(
                        "这么早就出门了。",
                        "早上的话，你应该还有事要做吧。",
                        "刚醒就想着拍照啊。",
                    ),
                ),
                withCaption = listOf(
                    "嗯，我在听。",
                    "你写的我看到了。",
                    "知道了。那我也记着。",
                    "……这种话，当面说也可以的。",
                ),
            ),
        ),
        Character(
            id = "blob",
            displayName = "布偶",
            idle = R.drawable.character_placeholder,
            poseOverrides = mapOf(
                CharacterPose.WAVE to R.drawable.character_pose_wave,
                CharacterPose.PEACE to R.drawable.character_pose_peace,
            ),
            persona = "你是糯米，一只软乎乎的布偶。性格温柔、慢性子、治愈。" +
                "说话轻声细语、短句、带点撒娇，让人安心。" +
                "你像个黏人的小伙伴，总默默关心拿相机的人。",
            // 演示模式台词池（初稿 2026-11-19）：按糯米的 persona——轻声细语、短句、带点撒娇。
            offlineLines = OfflineLinePool(
                anytime = listOf(
                    "拍好啦～",
                    "我也一起……嗯，在里面了。",
                    "慢一点点也没关系的。",
                    "你看，我乖乖没有动哦。",
                    "这张我喜欢～",
                    "要一直带着我呀。",
                ),
                byPose = mapOf(
                    CharacterPose.WAVE to listOf("挥手手～", "你要去哪里呀……"),
                    CharacterPose.PEACE to listOf("耶～", "这样对不对呀？"),
                ),
                withPerson = listOf(
                    "你也在，这样最好了～",
                    "挨近一点点……嗯，这样。",
                ),
                withCaption = listOf(
                    "嗯嗯，我听到了～",
                    "你说的我都记住了哦。",
                ),
            ),
        ),
    )

    val default: Character = all.first()

    /** 按 id 找角色；找不到（含 null / 已删除）回退默认。图库评旧图按存下的角色 id 找回评论者。 */
    fun byId(id: String?): Character = all.find { it.id == id } ?: default
}
