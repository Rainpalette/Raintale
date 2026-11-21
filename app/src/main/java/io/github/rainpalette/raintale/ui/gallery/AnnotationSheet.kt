package io.github.rainpalette.raintale.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainpalette.raintale.ai.AiCommentRepository
import io.github.rainpalette.raintale.ai.CommentContext
import io.github.rainpalette.raintale.character.CharacterPose
import io.github.rainpalette.raintale.character.Characters
import io.github.rainpalette.raintale.data.character.CustomCharacterRepository
import io.github.rainpalette.raintale.data.gallery.AnnotationSource
import io.github.rainpalette.raintale.data.gallery.GalleryPhoto
import io.github.rainpalette.raintale.data.gallery.GalleryRepository
import io.github.rainpalette.raintale.data.gallery.PhotoAnnotation
import io.github.rainpalette.raintale.util.NetworkUtil
import io.github.rainpalette.raintale.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 影集批注面板（底部滑出）：看/加批注 + 让角色评论（可加"补充留言 + 风格微调"两个槽）。
 * AI 评旧图沿用照片存下的地点、时段用当下；生成后**手动「留下这条」**才存（新旧并存）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationSheet(
    photo: GalleryPhoto,
    repo: GalleryRepository,
    apiKey: String,
    aiEnabled: Boolean,
    timeGreetingEnabled: Boolean,
    autoGenerate: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val annotations by repo.annotations(photo.uri).collectAsStateWithLifecycle(initialValue = emptyList())
    val meta by repo.metaFlow(photo.uri).collectAsStateWithLifecycle(initialValue = null)

    var noteText by remember { mutableStateOf("") }
    var showExtras by remember { mutableStateOf(false) }
    var captionSlot by remember { mutableStateOf("") }
    var styleSlot by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<String?>(null) }
    var draftAuthor by remember { mutableStateOf("") }

    val aiRepo = remember { AiCommentRepository() }
    val customRepo = remember { CustomCharacterRepository(context) }

    fun generate() {
        if (generating) return
        // 安全闸门：全局「AI 评论」关闭时，绝不上传照片（无论哪个入口调到这里）。
        if (!aiEnabled) {
            Toast.makeText(context, "AI 评论已关闭（设置→AI 与评论）", Toast.LENGTH_SHORT).show()
            return
        }
        if (apiKey.isBlank()) {
            Toast.makeText(context, "还没配置 Gemini key（设置→账户）", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            generating = true
            try {
                if (!NetworkUtil.isOnline(context)) {
                    Toast.makeText(context, "未联网，暂时评不了", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                // 找回拍这张照片的角色：陪伴照片存的是"custom:{id}"（自定义角色）→ 走 companion；
                // 其余是官方角色 id（或空，回退默认）。
                val cid = repo.characterIdOf(photo.uri)
                val personaText: String
                val authorName: String
                val companion: Boolean
                if (cid != null && cid.startsWith("custom:")) {
                    val cc = cid.removePrefix("custom:").toLongOrNull()?.let { customRepo.byId(it) }
                    if (cc != null) {
                        personaText = cc.persona(); authorName = cc.name; companion = true
                    } else {
                        val d = Characters.default; personaText = d.persona; authorName = d.displayName; companion = false
                    }
                } else {
                    val c = Characters.byId(cid); personaText = c.persona; authorName = c.displayName; companion = false
                }
                val bmp = withContext(Dispatchers.IO) { loadScaledBitmap(context, photo.uri) }
                if (bmp == null) {
                    Toast.makeText(context, "读取照片失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val ctx = CommentContext(
                    timeOfDay = if (timeGreetingEnabled) TimeUtil.currentTimeOfDay() else null,
                    area = repo.areaOf(photo.uri),
                    caption = captionSlot.ifBlank { null },
                    styleHint = styleSlot.ifBlank { null },
                )
                val result = aiRepo.comment(apiKey, bmp, CharacterPose.IDLE, ctx, persona = personaText, companion = companion)
                draft = result.text
                draftAuthor = authorName
            } catch (e: Exception) {
                Toast.makeText(context, "评论失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                generating = false
            }
        }
    }

    LaunchedEffect(Unit) { if (autoGenerate) generate() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("影集批注", style = MaterialTheme.typography.titleMedium)
            meta?.let { m ->
                val place = m.area?.let { " · $it" } ?: ""
                Text(
                    "拍摄于 ${formatTime(m.capturedAt)}$place",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (annotations.isEmpty() && draft == null) {
                Text(
                    "还没有批注～让角色说点什么，或自己写一条。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            annotations.forEach { a ->
                AnnotationCard(
                    a = a,
                    onEdit = { Toast.makeText(context, "编辑功能敬请期待", Toast.LENGTH_SHORT).show() },
                    onDelete = { scope.launch { repo.deleteAnnotation(a) } },
                )
            }

            // AI 生成草稿预览：手动留下/丢弃。
            draft?.let { d ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("$draftAuthor（草稿）", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(d, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { draft = null }) { Text("丢弃") }
                            Button(onClick = {
                                scope.launch {
                                    repo.addCharacterComment(photo.uri, draftAuthor, d)
                                    draft = null
                                    captionSlot = ""; styleSlot = ""; showExtras = false
                                }
                            }) { Text("留下这条") }
                        }
                    }
                }
            }

            // 让角色评论 / 重新生成。**仅在全局「AI 评论」开启时显示**——关闭时整块隐藏（不上传照片）。
            if (aiEnabled) {
                val hasCharacterComment = annotations.any { it.source == AnnotationSource.CHARACTER }
                Button(
                    onClick = { generate() },
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    if (generating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("角色思考中…")
                    } else {
                        Text(if (hasCharacterComment) "让角色再评一次" else "让角色评论")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("添加额外要求", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = showExtras, onCheckedChange = { showExtras = it })
                }
                if (showExtras) {
                    OutlinedTextField(
                        value = captionSlot,
                        onValueChange = { captionSlot = it },
                        label = { Text("补充留言（想让它知道的事）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = styleSlot,
                        onValueChange = { styleSlot = it },
                        label = { Text("风格微调（想要的语气，如：更俏皮）") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            } else {
                Text(
                    "「AI 评论」已关闭——开启后（设置 → AI 与评论）可让角色评论这张照片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 自己写批注。
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("写点批注…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (noteText.isNotBlank()) {
                        scope.launch { repo.addUserNote(photo.uri, noteText); noteText = "" }
                    }
                },
                enabled = noteText.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("保存我的批注") }
        }
    }
}

@Composable
private fun AnnotationCard(
    a: PhotoAnnotation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isCharacter = a.source == AnnotationSource.CHARACTER
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCharacter) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCharacter) "🦊 ${a.authorName}" else "🙂 ${a.authorName}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTime(a.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(a.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("编辑") }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

/** 拿一个可读时间串（本地时区）。 */
private fun formatTime(millis: Long): String =
    if (millis <= 0) "" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/** 从 uri 解码一张缩放过的软件位图（发 AI 用；GeminiClient 会再缩到 1024）。 */
private fun loadScaledBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest > 0 && longest / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}
