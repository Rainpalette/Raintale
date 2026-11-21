package io.github.rainpalette.raintale.tts

/**
 * 发声层抽象：`speak/stop/shutdown`。当前唯一实现是 [TtsSpeaker]（设备内建 TTS）。
 *
 * 接口保留是为了**换声音时不动 UI**：日后要接角色专属音色（云端克隆，或重新评估端上方案），
 * 只加一个实现并换掉构造处即可。端上神经语音（sherpa-onnx）已于 2026-11-20 下架，
 * 原因见 MILESTONE 同日条目。
 */
interface Speaker {
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
