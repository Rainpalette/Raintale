package io.github.rainpalette.raintale.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 应用内日志缓冲。错误（及关键事件）都汇到这里，由 Debug 页面展示。
 * 同时镜像到 Logcat，方便用 Android Studio 查看。
 */
object DebugLog {

    enum class Level { INFO, ERROR }

    data class Entry(
        val time: String,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private const val MAX_ENTRIES = 500
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun i(tag: String, message: String) = add(Level.INFO, tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        add(Level.ERROR, tag, full)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun add(level: Level, tag: String, message: String) {
        // 脱敏：无论调用方传什么，写进 Debug 页 / Logcat 前都掩掉像 API key 的片段。
        // 用户报 issue 常截 Debug 页，这是防止 key 意外外泄的最后一道闸（防御性，根因已在 GeminiClient 用请求头传 key）。
        val safe = redact(message)
        when (level) {
            Level.INFO -> Log.i(tag, safe)
            Level.ERROR -> Log.e(tag, safe)
        }
        val entry = Entry(timeFmt.format(System.currentTimeMillis()), level, tag, safe)
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
    }

    /** 掩掉常见的 API key 形态（URL 查询参数里的 key、Google 明文 key 格式）。崩溃报告等也复用。 */
    fun redact(s: String): String = s
        // URL 查询参数：?key=xxx / &key=xxx → 保留 key= 掩掉值。
        .replace(Regex("(?i)([?&]key=)[^&\\s\"'>]+"), "$1***")
        // Google API key 明文：老格式 AIza… / 新格式 AQ.… 。
        .replace(Regex("AIza[0-9A-Za-z_\\-]{10,}"), "AIza***")
        .replace(Regex("AQ\\.[0-9A-Za-z_\\-]{10,}"), "AQ.***")
}
