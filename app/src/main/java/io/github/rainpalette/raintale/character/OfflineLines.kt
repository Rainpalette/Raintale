package io.github.rainpalette.raintale.character

import io.github.rainpalette.raintale.util.TimeUtil

/**
 * 离线台词的**场景线索**：取值全部来自端上已知信息（动作、人像分割结果、系统时钟、留言），
 * **零上传、零延迟**。
 *
 * 这是「演示模式」不同于单纯兜底的关键——扁平台词池只能写得通用含蓄（否则在奇怪场景下
 * 显得不可理喻），而按场景分桶后可以写具体的话，体验从「复读机」变成「她好像看了一眼」。
 */
data class OfflineContext(
    val pose: CharacterPose = CharacterPose.IDLE,
    /** 画面里有没有人：来自 ML Kit 人像分割（目前仅人像页会分割）。`null` = 未知，不参与选句。 */
    val hasPerson: Boolean? = null,
    /**
     * 时段，取值同 [TimeUtil.currentTimeOfDay]（"早上" / "中午" / "下午" / "晚上" / "深夜"）。
     *
     * **不受设置里「时段信息」开关约束**：那个开关管的是「把时段作为情境线索**发给 AI**」，
     * 而离线台词只在本机选句、什么都不外发，因此读系统时钟没有隐私含义。
     */
    val timeOfDay: String? = null,
    /** 留言拍照：用户写了留言（留言本身不参与选句，只作为"她在回应你"这个场景的标记）。 */
    val hasCaption: Boolean = false,
    /**
     * 大致地名（开了定位才有）。
     * **骨架阶段只预留、不参与选句**——地名要有用得先按地点类型分类（室内/街道/风景…），
     * 单凭一个地名字符串没法分桶。等台词写够再设计。
     */
    val area: String? = null,
)

/**
 * 一个角色的离线台词池，按场景分桶。
 *
 * [anytime] 是任何场景都说得通的保底句，**必须非空**（其它桶全空时就只剩它）。
 * 其余桶由 [OfflineContext] 命中才参与候选，且**命中的专属句优先于 [anytime]**。
 *
 * 加台词只改这个数据结构，不碰选句逻辑；每桶 3~5 句即可明显压掉复读感。
 */
data class OfflineLinePool(
    val anytime: List<String>,
    val byPose: Map<CharacterPose, List<String>> = emptyMap(),
    val withPerson: List<String> = emptyList(),
    val withoutPerson: List<String> = emptyList(),
    /** 键取值见 [TimeUtil.currentTimeOfDay]；拼错的键只是永不命中，不会崩。 */
    val byTimeOfDay: Map<String, List<String>> = emptyMap(),
    val withCaption: List<String> = emptyList(),
) {
    /** 命中 [ctx] 的专属台词（不含 [anytime]）。 */
    fun specificFor(ctx: OfflineContext): List<String> = buildList {
        byPose[ctx.pose]?.let(::addAll)
        when (ctx.hasPerson) {
            true -> addAll(withPerson)
            false -> addAll(withoutPerson)
            null -> {}
        }
        ctx.timeOfDay?.let { byTimeOfDay[it] }?.let(::addAll)
        if (ctx.hasCaption) addAll(withCaption)
    }
}

/** 离线台词的选句入口与通用兜底池。 */
object OfflineLines {

    /**
     * 通用兜底池：角色没配专属台词时用（自定义角色、陪伴模式）。
     *
     * 这些句子**刻意不带性格**——它们要能配上用户自己写的任意 persona（温柔的、毒舌的、话少的），
     * 所以只写最朴素的在场与收下。想要有性格的台词，给那个角色配自己的 [OfflineLinePool]。
     */
    val generic = OfflineLinePool(
        anytime = listOf(
            "……嗯。",
            "我看到了。",
            "这一刻，留下了。",
            "慢慢来，不着急。",
            "我在呢。",
            "拍好了。",
            "这张收着吧。",
            "我一直在这儿。",
        ),
    )

    /**
     * 按场景挑一句。
     *
     * @param pool 角色的台词池；`null` 用 [generic]。
     * @param ctx 端上已知的场景线索；命中专属桶就在命中句里挑，否则回落 [OfflineLinePool.anytime]。
     * @param avoid 上一次说过的那句——候选多于一句时避开它，避免连拍时复读（池子小的时候最伤第一印象）。
     */
    fun pick(
        pool: OfflineLinePool?,
        ctx: OfflineContext = OfflineContext(),
        avoid: String? = null,
    ): String {
        val p = pool ?: generic
        val candidates = p.specificFor(ctx)
            .ifEmpty { p.anytime }
            .ifEmpty { generic.anytime }
        val fresh = candidates.filterNot { it == avoid }
        return (if (fresh.isEmpty()) candidates else fresh).random()
    }
}
