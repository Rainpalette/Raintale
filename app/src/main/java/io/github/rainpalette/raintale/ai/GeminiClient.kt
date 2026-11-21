package io.github.rainpalette.raintale.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 直连 Gemini Developer API（REST）做单次多模态生成。封装在 ai 包，
 * 阶段二要换实时流时只动这一层（新增 LiveSessionRepository），UI 不变。
 *
 * 不依赖已弃用的旧版 Android 生成式 SDK，用内置 HttpURLConnection + org.json。
 */
class GeminiClient {

    /**
     * 把照片 + 提示发给模型，返回模型文本。失败抛异常。需在调用前确保有网络。
     * @param apiKey 调用时传入的 key（支持 BYOK 动态切换）。
     * @param responseSchema 非 null 时启用 JSON 模式（返回的文本即为符合该 schema 的 JSON 串）。
     */
    suspend fun generateComment(
        apiKey: String,
        bitmap: Bitmap,
        prompt: String,
        responseSchema: JSONObject? = null,
        systemInstruction: String? = null,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IllegalStateException("未配置 API Key")

        val base64Image = bitmap.toJpegBase64(quality = 85)
        val body = buildRequestBody(prompt, base64Image, responseSchema, systemInstruction)
        post(apiKey, body)
    }

    /**
     * 纯文本多轮对话（设定助手用）：把 [turns]（user/model 交替，旧→新）+ [systemInstruction]
     * 发给模型，返回回复文本。需在调用前确保有网络。
     */
    suspend fun generateChat(
        apiKey: String,
        systemInstruction: String,
        turns: List<ChatTurn>,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IllegalStateException("未配置 API Key")
        post(apiKey, buildChatRequestBody(systemInstruction, turns))
    }

    /** 共享的 POST + 解析（调用方须已在 IO 线程）。 */
    private fun post(apiKey: String, body: String): String {
        // 安全：key 用 x-goog-api-key **请求头**传，不拼进 URL——
        // 避免 key 随 URL 泄进异常消息 / 堆栈 / 代理日志（脱敏根因修复，见 DebugLog）。
        // 模型名取自可配置的 [model]（默认 DEFAULT_MODEL，用户可在设置覆盖）。
        val url = URL("$API_BASE/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // 显示完整响应体，便于看清具体配额指标与 retryDelay。
                val detail = response.take(2000)
                if (code == 429) throw IOException("配额用尽/限流(429)。详情：$detail")
                throw IOException("HTTP $code: $detail")
            }
            return parseComment(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildRequestBody(
        prompt: String,
        base64Image: String,
        responseSchema: JSONObject?,
        systemInstruction: String?,
    ): String {
        val text = JSONObject().put("text", prompt)
        val inlineData = JSONObject().put(
            "inline_data",
            JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", base64Image)
        )
        val parts = org.json.JSONArray().put(text).put(inlineData)
        val content = JSONObject().put("parts", parts)
        val generationConfig = JSONObject()
            .put("temperature", 1.0)
            .put("maxOutputTokens", 200)
        if (responseSchema != null) {
            generationConfig.put("responseMimeType", "application/json")
            generationConfig.put("responseSchema", responseSchema)
        }
        val root = JSONObject()
            .put("contents", org.json.JSONArray().put(content))
            .put("generationConfig", generationConfig)
            .put("safetySettings", buildSafetySettings())
        // 角色性格 → systemInstruction（设定模型扮演谁、怎么说话）。
        if (!systemInstruction.isNullOrBlank()) {
            root.put(
                "system_instruction",
                JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
        }
        return root.toString()
    }

    private fun buildChatRequestBody(systemInstruction: String, turns: List<ChatTurn>): String {
        val contents = org.json.JSONArray()
        turns.forEach { t ->
            contents.put(
                JSONObject()
                    .put("role", t.role)
                    .put("parts", org.json.JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        val generationConfig = JSONObject()
            .put("temperature", 0.7)
            .put("maxOutputTokens", 600)
        val root = JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
            .put("safetySettings", buildSafetySettings())
        if (systemInstruction.isNotBlank()) {
            root.put(
                "system_instruction",
                JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
        }
        return root.toString()
    }

    /** Gemini 安全设置：四类有害内容按 [SAFETY_THRESHOLD] 阈值拦截。 */
    private fun buildSafetySettings(): org.json.JSONArray {
        val categories = listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT",
        )
        val arr = org.json.JSONArray()
        categories.forEach {
            arr.put(JSONObject().put("category", it).put("threshold", SAFETY_THRESHOLD))
        }
        return arr
    }

    private fun parseComment(response: String): String {
        val root = JSONObject(response)
        val candidates = root.optJSONArray("candidates")
        // prompt 级被安全策略拦截：candidates 常为空 + promptFeedback.blockReason。
        if (candidates == null || candidates.length() == 0) {
            val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw SafetyBlockedException("请求被安全策略拦截（${reason.ifBlank { "无 candidates" }}）")
        }
        val candidate = candidates.getJSONObject(0)
        val finish = candidate.optString("finishReason")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        // 回复级被拦截：finishReason=SAFETY 或缺 content/parts。
        if (finish == "SAFETY" || parts == null) {
            throw SafetyBlockedException("回复被安全策略拦截（finishReason=${finish.ifBlank { "无内容" }}）")
        }
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        return sb.toString().trim().ifBlank { throw IOException("模型返回空文本") }
    }

    private fun Bitmap.toJpegBase64(quality: Int): String {
        // 提速：发给模型前缩到 MAX_DIM 内（评论不需要全分辨率，缩小后上传+处理都快）。
        val scaled = downscaled(MAX_IMAGE_DIM)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== this) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun Bitmap.downscaled(maxDim: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxDim) return this
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    companion object {
        // 默认模型：Gemini Flash 多模态（flash-lite 支持图片输入，免费层每日额度较宽松）。
        // 用户未在设置里覆盖时用它。**Google 退役模型后，用户在设置改模型名即可续用、无需升级 app**（release-checklist #11）。
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        // 当前使用的模型名，从设置同步（见 AppRoot）。空值自动回落 DEFAULT_MODEL。
        // @Volatile：可能被 UI 线程写、IO 线程读。
        @Volatile
        var model: String = DEFAULT_MODEL
            set(value) { field = value.trim().ifBlank { DEFAULT_MODEL } }

        private const val MAX_IMAGE_DIM = 1024 // 发给 Gemini 的图片最长边上限
        // 安全拦截阈值：BLOCK_MEDIUM_AND_ABOVE（较稳）；嫌误伤正常照片可改 BLOCK_ONLY_HIGH。
        private const val SAFETY_THRESHOLD = "BLOCK_MEDIUM_AND_ABOVE"
        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}

/** 被 Gemini 安全策略拦截（区别于网络/配额错误）：上层据此给一句安全兜底话，而非静默失败。 */
class SafetyBlockedException(message: String) : Exception(message)

/** 一轮对话（设定助手用）。[role] 为 "user" 或 "model"。 */
data class ChatTurn(val role: String, val text: String)
