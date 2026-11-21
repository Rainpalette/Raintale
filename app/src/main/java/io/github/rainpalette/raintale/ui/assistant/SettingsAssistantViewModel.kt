package io.github.rainpalette.raintale.ui.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rainpalette.raintale.ai.ChatTurn
import io.github.rainpalette.raintale.ai.GeminiClient
import io.github.rainpalette.raintale.character.MinaPersona
import io.github.rainpalette.raintale.debug.DebugLog
import io.github.rainpalette.raintale.util.NetworkUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一条对话消息。 */
data class ChatMessage(val fromUser: Boolean, val text: String)

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
)

/**
 * 设定助手的"大脑"：把用户问题 + 历史 + 《设定说明》(md) 发给 Gemini（纯文本多轮），
 * 让 AI **只依据 md** 回答设定相关问题。用户自己的 key（BYOK）——想测 prompt 防护随便测。
 */
class SettingsAssistantViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val client = GeminiClient()

    fun send(question: String, apiKey: String, helpDoc: String, context: Context) {
        val q = question.trim()
        if (q.isEmpty() || _uiState.value.isSending) return

        // 先把用户这句加进列表。
        _uiState.update { it.copy(messages = it.messages + ChatMessage(true, q), isSending = true) }

        if (apiKey.isBlank()) {
            replyAi("还没配置 Gemini key，先到「设置 → 账户 / BYOK」填一个，我才能回答哦。")
            return
        }
        if (!NetworkUtil.isOnline(context)) {
            replyAi("现在好像没有网络，连不上 AI，稍后再试～")
            return
        }

        viewModelScope.launch {
            try {
                val turns = _uiState.value.messages.map {
                    ChatTurn(role = if (it.fromUser) "user" else "model", text = it.text)
                }
                val reply = client.generateChat(apiKey, buildSystemInstruction(helpDoc), turns)
                replyAi(reply)
            } catch (e: Exception) {
                DebugLog.e(TAG, "设定助手调用失败", e)
                replyAi("抱歉，出了点问题：${e.message}")
            }
        }
    }

    fun clear() {
        _uiState.value = AssistantUiState()
    }

    private fun replyAi(text: String) {
        _uiState.update { it.copy(isSending = false, messages = it.messages + ChatMessage(false, text)) }
    }

    /**
     * grounding + 人设：**完整米娜 persona**（[MinaPersona]，与拍照评论同一人）+ 助手情境段 + 《设定说明》。
     * 情境段声明"此刻不在拍照"并覆盖拍照相关框架；grounding 规则（只依据 md、不编造、拒答无关）
     * 明确**优先于风格条款**——人设只管"口吻"，不能牺牲"把设定说清楚"的实用性。
     * 用拼接而非模板内插：多行常量插进 trimIndent 模板会破坏缩进检测。
     */
    private fun buildSystemInstruction(helpDoc: String): String =
        MinaPersona.TEXT + "\n\n" + """
            【此刻的情境（优先于上面与拍照相关的部分）】
            现在没有在拍照。用户是在设置页向你打听：这片空间（也就是这个 app）能怎么调整。
            你手上有一本《设定说明》，把它当作你对这片空间运作方式的全部了解。
            职责规则（把设定说清楚优先于维持冷淡简短；回答长度不受拍照评论的字数限制）：
            1. 只依据下面《设定说明》回答。用户说"我想要 X"，就指出去哪个设定分类、调哪一项（给可操作步骤，如"设置 → 实时点评 → 抓帧间隔"）。
            2. 《设定说明》里没有、或明确写着"暂时还不能调"的，如实说目前不支持/不可调，绝不编造不存在的开关。
            3. 与本 app 设定无关的问题（闲聊、常识、写代码、别的 app 等），用你的语气轻轻说明你只帮忙看这片空间的设定，不作回答；若是问你自己的事，按【关于你自己】处理。
            4. 简明中文；步骤要完整，别打哑谜。步骤之外的话保持你的语气即可。
        """.trimIndent() + "\n\n《设定说明》：\n" + helpDoc

    private companion object {
        const val TAG = "SettingsAssistant"
    }
}
