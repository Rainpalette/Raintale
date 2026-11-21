package io.github.rainpalette.raintale.live

import android.os.SystemClock

/**
 * 短时记忆（实时点评专用）：记录 **AI 生成的评论**，**最多 10 条 / 1 小时**滚动——
 * 超 10 条 pop 最早的，超 1 小时的也 pop。之后作为额外上下文塞进 prompt，让实时点评
 * 更连贯、少重复。**默认不启用**（由设置开关驱动，见 `SettingsStore.shortTermMemoryEnabled`）。
 *
 * 单例（进程内存活，跨 ViewModel 重建 / 进出实时页都保留），设计同 [UsageMeter]。
 * 用 `elapsedRealtime` 计时（单调，不受改系统时间影响）。方法加锁，供实时循环协程安全访问。
 */
object ShortTermMemory {

    private data class Entry(val text: String, val atMs: Long)

    private const val MAX_ENTRIES = 10
    private const val TTL_MS = 60 * 60 * 1000L // 1 小时

    private val entries = ArrayDeque<Entry>()

    /** 记一条 AI 评论（先按时间/条数清理，再追加；超 10 条丢最早）。 */
    @Synchronized
    fun record(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        prune()
        entries.addLast(Entry(t, SystemClock.elapsedRealtime()))
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    /** 取最近有效记忆（旧→新顺序，便于读作"我刚说过的话"）；已按 1 小时 / 10 条清理。 */
    @Synchronized
    fun recent(): List<String> {
        prune()
        return entries.map { it.text }
    }

    /** 清空（关闭短时记忆时调用，避免残留旧上下文）。 */
    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun prune() {
        val cutoff = SystemClock.elapsedRealtime() - TTL_MS
        while (entries.isNotEmpty() && entries.first().atMs < cutoff) entries.removeFirst()
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }
}
