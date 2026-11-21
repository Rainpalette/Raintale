package io.github.rainpalette.raintale.ui.camera

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainpalette.raintale.tts.Speaker
import io.github.rainpalette.raintale.tts.TtsSpeaker
import io.github.rainpalette.raintale.ui.components.CommentBubble
import io.github.rainpalette.raintale.ui.components.RotatedStage
import java.util.Locale

/**
 * AI 评论的完整 UI 层（可复用）：评论气泡 + 内建 TTS 朗读 + 知识链接验证 + 维基简介弹窗 + 「不是这个地点」纠错。
 *
 * 全部绑定到 [CameraViewModel] 的 `uiState`，自带 TTS 朗读器（随本组件创建/释放）。
 * 拍照页与人像页共用——拍照路的 AI 评论由 ViewModel 驱动，这里只负责呈现与朗读。
 *
 * 作为一个 `fillMaxSize` 透明叠层放进父 `Box`：空白区不拦截触摸，气泡固定在顶部。
 */
@Composable
fun AiCommentOverlay(
    viewModel: CameraViewModel,
    knowledgeLinkEnabled: Boolean,
    ttsEnabled: Boolean,
    modifier: Modifier = Modifier,
    // 角色评论气泡的转正角（横拿时=±90，传 uprightDegrees(deviceRotation)）。直屏为 0，不变。
    controlRotation: Float = 0f,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 朗读器：系统内建 TTS（端上神经语音已下架）。
    val ttsSpeaker: Speaker = remember { TtsSpeaker(context) }
    DisposableEffect(ttsSpeaker) { onDispose { ttsSpeaker.shutdown() } }
    // 有新评论且开关打开时朗读。
    LaunchedEffect(uiState.aiComment) {
        val comment = uiState.aiComment
        // 演示模式的预写台词同样朗读：它是正式档位而非降级态，念出来才是完整闭环。
        if (ttsEnabled && comment != null) ttsSpeaker.speak(comment)
    }
    // 知识链接：仅当 Gemini 判为专有实体（judge1）时，后台验证维基是否有合格词条（judge2）。
    LaunchedEffect(uiState.aiEntity, uiState.aiLinkworthy, knowledgeLinkEnabled) {
        val e = uiState.aiEntity
        if (knowledgeLinkEnabled && e != null && uiState.aiLinkworthy) {
            viewModel.verifyEntityLink(e)
        }
    }

    // 横屏舞台：横拿时内部即横屏坐标系，TopCenter = 物理顶边（直屏 0° 时退化为普通铺满，行为不变）。
    RotatedStage(rotationDegrees = controlRotation, modifier = modifier) {
        CommentBubble(
            text = uiState.aiComment,
            isLoading = uiState.isCommenting,
            onDismiss = {
                ttsSpeaker.stop()
                viewModel.dismissComment()
            },
            // 两道闸都过（Gemini 判专有 + 维基验证有词条）才显示链接。
            entity = if (knowledgeLinkEnabled && uiState.linkReady) uiState.aiEntity else null,
            onEntityClick = { viewModel.showEntityWiki() },
            // 预写台词明确标出来源（演示模式 / 这次没接上），不冒充 AI 的话。
            badge = uiState.offlineReason?.badge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
        )
    }

    // 知识链接·维基简介弹窗。
    if (uiState.wikiOpen) {
        AlertDialog(
            onDismissRequest = { ttsSpeaker.stop(); viewModel.dismissWiki() },
            title = { Text(uiState.wikiTitle ?: "了解") },
            text = {
                Column {
                    if (!uiState.wikiLoading) {
                        Text(
                            text = "不是这个地点？点此更正",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.openWikiCorrection() }
                                .padding(bottom = 8.dp),
                        )
                    }
                    if (uiState.wikiLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("查询中…", modifier = Modifier.padding(start = 10.dp))
                        }
                    } else {
                        Text(
                            text = uiState.wikiExtract.orEmpty(),
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ttsSpeaker.stop(); viewModel.dismissWiki() }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(
                    onClick = { uiState.wikiExtract?.let { ttsSpeaker.speak(it) } },
                    enabled = !uiState.wikiLoading && uiState.wikiExtract != null,
                ) { Text("念") }
            },
        )
    }

    // 「不是这个地点」纠错输入。
    if (uiState.wikiCorrectionOpen) {
        WikiCorrectionDialog(
            onSubmit = { viewModel.submitWikiCorrection(it) },
            onCancel = { viewModel.dismissWikiCorrection() },
        )
    }
}

/**
 * 「不是这个地点」纠错：手填正确地名（打字或语音）后重查。
 */
@Composable
private fun WikiCorrectionDialog(
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) name = text
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("不是这个地点？") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("正确的地点名") },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出地点名")
                        }
                        runCatching { voiceLauncher.launch(intent) }
                    }) {
                        Icon(Icons.Filled.Mic, contentDescription = "语音输入")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name) }, enabled = name.isNotBlank()) { Text("重查") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
