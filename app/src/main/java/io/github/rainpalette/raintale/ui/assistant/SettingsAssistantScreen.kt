package io.github.rainpalette.raintale.ui.assistant

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 「询问米娜」对话页：以米娜的口吻回答本 app 设定相关问题（消息列表 + 输入框）。
 * 底部快捷问题是**可关组件**——觉得用不上把 [SHOW_QUICK_QUESTIONS] 改成 false 即整块隐藏。
 */
@Composable
fun SettingsAssistantScreen(
    geminiApiKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: SettingsAssistantViewModel = viewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val helpDoc = remember { loadHelpDoc(context) }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 新消息滚到底。
    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        val total = uiState.messages.size + if (uiState.isSending) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("询问米娜", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.messages.isEmpty()) {
                item { EmptyHint() }
            }
            items(uiState.messages) { msg -> MessageBubble(msg) }
            if (uiState.isSending) {
                item {
                    Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text("思考中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 快捷常见问题（可关组件）。
        if (SHOW_QUICK_QUESTIONS && !uiState.isSending) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_QUESTIONS.forEach { q ->
                    AssistChip(
                        onClick = { vm.send(q, geminiApiKey, helpDoc, context) },
                        label = { Text(q) },
                    )
                }
            }
        }

        // 输入栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("问问设定怎么调…") },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    vm.send(input, geminiApiKey, helpDoc, context)
                    input = ""
                },
                enabled = input.isNotBlank() && !uiState.isSending,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "……是我，米娜。☂️\n这片空间要怎么调整，尽管问我吧，比如：让角色说话别那么频繁。\n（我只帮你看这片空间的设定，别的事就不知道啦）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val alignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private fun loadHelpDoc(context: Context): String =
    runCatching {
        context.assets.open(HELP_ASSET).bufferedReader().use { it.readText() }
    }.getOrDefault("")

private const val HELP_ASSET = "settings-help.md"

// ===== 快捷常见问题：觉得用不上，把下面改成 false 即整块隐藏 =====
private const val SHOW_QUICK_QUESTIONS = true
private val QUICK_QUESTIONS = listOf(
    "怎么让角色说话别那么频繁？",
    "AI 回复太慢怎么办？",
    "怎么换角色的声音？",
    "怎么少发点数据、保护隐私？",
    "拍出来的照片比例怎么调？",
)
