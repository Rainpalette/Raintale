package io.github.rainpalette.raintale.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.rainpalette.raintale.BuildConfig
import io.github.rainpalette.raintale.debug.DebugLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃报告：捕获未处理异常，把堆栈 + 设备/版本信息写到 app 私有文件；
 * 下次启动检测到就提示用户「查看 / 分享」（分享走系统 Intent，由用户自愿贴到 GitHub issue）。
 *
 * **零数据收集**：不接任何云端崩溃上报（如 Crashlytics）。报告只存本机，是否分享由用户决定。
 * 堆栈写入前用 [DebugLog.redact] 脱敏（万一异常消息里带 API key）。
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"

    /**
     * 安装全局未捕获异常处理器。应在进程早期调用（MainActivity.onCreate）。
     * 写完报告后**交还给原有 handler**（系统崩溃对话框 / 结束进程），不吞掉崩溃。
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            append("雨中逸事 (Rain Tales) 崩溃报告\n")
            append("时间: $time\n")
            append("应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("设备: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("线程: ${thread.name}\n\n")
            append(Log.getStackTraceString(throwable))
        }
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        // 脱敏后落盘：万一堆栈里带 key，也不会写进文件/被分享出去。
        File(dir, FILE).writeText(DebugLog.redact(report))
    }

    /** 上次是否留有崩溃报告；有则返回其文本，无则 null。 */
    fun pendingReport(context: Context): String? {
        val f = File(File(context.filesDir, DIR), FILE)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    /** 清除崩溃报告（用户查看/关闭后调用，避免每次启动都提示）。 */
    fun clear(context: Context) {
        runCatching { File(File(context.filesDir, DIR), FILE).delete() }
    }

    /** 构造分享崩溃报告文本的 Intent（用户自愿贴到 issue）。 */
    fun shareIntent(report: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "雨中逸事 崩溃报告")
            putExtra(Intent.EXTRA_TEXT, report)
        }
}
