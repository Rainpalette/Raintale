package io.github.rainpalette.raintale.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import io.github.rainpalette.raintale.debug.DebugLog
import java.util.Locale

/**
 * 封装 Android 内建 [TextToSpeech] 朗读。
 *
 * 这是**可替换的发声层**：日后要换成云端克隆音色（如 ElevenLabs），只改这一层、UI 不动。
 * 想换"声音"感觉，先调 [PITCH] / [SPEECH_RATE]。
 */
class TtsSpeaker(context: Context) : Speaker {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                DebugLog.e(TAG, "TTS 初始化失败：status=$status")
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val langResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                DebugLog.e(TAG, "中文 TTS 不可用（缺语音数据或不支持）；请在系统设置中安装中文 TTS 数据")
                return@TextToSpeech
            }
            engine.setPitch(PITCH)
            engine.setSpeechRate(SPEECH_RATE)
            ready = true
            // 就绪前若已请求朗读，这里补念。
            pending?.let { speak(it) }
            pending = null
        }
    }

    /** 朗读一句。引擎尚未就绪时先排队，就绪后自动念最后一次请求。 */
    override fun speak(text: String) {
        val engine = tts ?: return
        if (!ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** 停止当前朗读。 */
    override fun stop() {
        tts?.stop()
    }

    /** 释放引擎（界面销毁时调用）。 */
    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val TAG = "TtsSpeaker"
        private const val UTTERANCE_ID = "comment"

        // 角色感：音高略高、语速正常。换"声音"先调这两个数。
        private const val PITCH = 1.15f
        private const val SPEECH_RATE = 1.0f
    }
}
