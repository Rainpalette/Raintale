package io.github.rainpalette.raintale.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainpalette.raintale.BuildConfig
import io.github.rainpalette.raintale.R
import io.github.rainpalette.raintale.ai.GeminiClient
import io.github.rainpalette.raintale.camera.OrientationLock
import io.github.rainpalette.raintale.data.gallery.GalleryRepository
import io.github.rainpalette.raintale.camera.PreviewAspect
import io.github.rainpalette.raintale.live.StaticMode
import io.github.rainpalette.raintale.location.LocationProvider
import kotlinx.coroutines.launch

private enum class SettingsSection { Ai, Voice, Privacy, Shoot, Live, Portrait, Character, Byok, About }

/** 项目主页（关于页「开源地址 / 隐私政策」用）。 */
private const val REPO_URL = "https://github.com/Rainpalette/raintale"

/**
 * 设置页：首页是分类列表，点一项进子页（系统设置那种 list → detail）。
 * 内部用一个 [section] 状态做导航，不引 Navigation 库；子页带返回。
 * 开关值由上层（[AppRoot][io.github.rainpalette.raintale.ui.AppRoot]）从 DataStore 提供，这里只展示与回调。
 */
@Composable
fun SettingsScreen(
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    ttsEnabled: Boolean,
    onTtsEnabledChange: (Boolean) -> Unit,
    characterBarEnabled: Boolean,
    onCharacterBarEnabledChange: (Boolean) -> Unit,
    staticMode: StaticMode,
    onStaticModeChange: (StaticMode) -> Unit,
    shortTermMemoryEnabled: Boolean,
    onShortTermMemoryEnabledChange: (Boolean) -> Unit,
    liveIntervalSec: Int,
    onLiveIntervalSecChange: (Int) -> Unit,
    locationEnabled: Boolean,
    onLocationEnabledChange: (Boolean) -> Unit,
    timeGreetingEnabled: Boolean,
    onTimeGreetingEnabledChange: (Boolean) -> Unit,
    knowledgeLinkEnabled: Boolean,
    onKnowledgeLinkEnabledChange: (Boolean) -> Unit,
    /** 知识链接/位置的首次开启告知（版本化，见 SettingsStore.NoticeVersions）：未看过时开启前先弹。 */
    knowledgeNoticeSeen: Boolean,
    onKnowledgeNoticeSeen: () -> Unit,
    locationNoticeSeen: Boolean,
    onLocationNoticeSeen: () -> Unit,
    highFpsEnabled: Boolean,
    onHighFpsEnabledChange: (Boolean) -> Unit,
    toolbarDefaultExpanded: Boolean,
    onToolbarDefaultExpandedChange: (Boolean) -> Unit,
    previewAspect: PreviewAspect,
    onPreviewAspectChange: (PreviewAspect) -> Unit,
    orientationLock: OrientationLock,
    onOrientationLockChange: (OrientationLock) -> Unit,
    geminiApiKey: String,
    onGeminiApiKeyChange: (String) -> Unit,
    geminiModel: String,
    onGeminiModelChange: (String) -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenCustomCharacters: () -> Unit,
    onReplayTutorial: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val context = LocalContext.current

    // 功能首次开启告知：拦截"关→开"，先弹说明（确认才真正开启，取消保持关闭）。
    var showKnowledgeNotice by rememberSaveable { mutableStateOf(false) }
    var showLocationNotice by rememberSaveable { mutableStateOf(false) }

    // 返回键：在子页时先退回分类列表（比 AppRoot 的返回处理更深、优先拦截）。
    BackHandler(enabled = section != null) { section = null }

    if (showKnowledgeNotice) {
        FirstEnableNoticeDialog(
            titleRes = R.string.knowledge_notice_title,
            bodyRes = R.string.knowledge_notice_body,
            onEnable = {
                showKnowledgeNotice = false
                onKnowledgeNoticeSeen()
                onKnowledgeLinkEnabledChange(true)
            },
            onCancel = { showKnowledgeNotice = false },
        )
    }
    if (showLocationNotice) {
        FirstEnableNoticeDialog(
            titleRes = R.string.location_notice_title,
            bodyRes = R.string.location_notice_body,
            onEnable = {
                showLocationNotice = false
                onLocationNoticeSeen()
                onLocationEnabledChange(true)
            },
            onCancel = { showLocationNotice = false },
        )
    }

    when (section) {
        null -> SettingsHome(
            onBack = onBack,
            onOpenSection = { section = it },
            onOpenDebug = onOpenDebug,
            onOpenAssistant = onOpenAssistant,
            modifier = modifier,
        )
        SettingsSection.Ai -> SectionScaffold("AI 与评论", { section = null }, modifier) {
            ToggleRow(
                "AI 评论",
                if (aiEnabled) "开：拍照后让角色看图说一句（用你的 key 上传照片）"
                else "关＝演示模式：角色说预写台词，零上传、不需要 key",
                aiEnabled,
                onAiEnabledChange,
            )
            ToggleRow("知识链接", if (knowledgeLinkEnabled) "评论可挂「了解」入口（地标/品牌）" else "关", knowledgeLinkEnabled) { enabled ->
                // 首次开启先弹告知（维基是第二个接收方）；看过后直接开关。
                if (enabled && !knowledgeNoticeSeen) showKnowledgeNotice = true
                else onKnowledgeLinkEnabledChange(enabled)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            ModelField(geminiModel, onGeminiModelChange)
        }
        SettingsSection.Voice -> SectionScaffold("语音", { section = null }, modifier) {
            ToggleRow("朗读评论 (TTS)", if (ttsEnabled) "出评论时用设备语音念出来" else "关", ttsEnabled, onTtsEnabledChange)
            Text(
                "用系统 TTS 朗读（设备自带、离线、不联网）。音色由系统语音引擎决定；角色专属音色尚未实现。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingsSection.Privacy -> SectionScaffold("隐私", { section = null }, modifier) {
            PrivacyOverview(aiEnabled, locationEnabled, timeGreetingEnabled, knowledgeLinkEnabled)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ToggleRow("位置信息", if (locationEnabled) "开：把大致位置发给 AI（更应景）" else "关", locationEnabled) { enabled ->
                // 首次开启先弹告知（坐标去向 + Gemini 只收大致地名）；看过后直接开关。
                if (enabled && !locationNoticeSeen) showLocationNotice = true
                else onLocationEnabledChange(enabled)
            }
            CurrentLocationLine(locationEnabled)
            ToggleRow("时段信息", if (timeGreetingEnabled) "开：把当前时段发给 AI 当情境参考" else "关：不发时间", timeGreetingEnabled, onTimeGreetingEnabledChange)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            MemoryBackupSection()
        }
        SettingsSection.Shoot -> SectionScaffold("拍摄", { section = null }, modifier) {
            Text("画面比例", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewAspect.entries.forEach { a ->
                    FilterChip(selected = a == previewAspect, onClick = { onPreviewAspectChange(a) }, label = { Text(a.label) })
                }
            }
            Text(
                "4:3/16:9 居中显示、上下留黑边、拍出标准比例；全屏满屏裁切。实时点评固定全屏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("屏幕方向", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrientationLock.entries.forEach { o ->
                    FilterChip(selected = o == orientationLock, onClick = { onOrientationLockChange(o) }, label = { Text(o.label) })
                }
            }
            Text(
                "自动=照片方向随手机倾斜（横拿出横图）；锁定后方向固定、不随倾斜改变。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingsSection.Live -> SectionScaffold("实时点评", { section = null }, modifier) {
            // 录影暂下架（功能未完善）：移除「录影」开关；实时点评不再录屏（引擎代码保留，见 ModeStrip 注释）。
            ToggleRow(
                "短时记忆",
                if (shortTermMemoryEnabled) "开：记最近 AI 评论（10 条/1 小时）当额外上下文，更连贯少重复" else "关：每句只看当前画面",
                shortTermMemoryEnabled,
                onShortTermMemoryEnabledChange,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("抓帧间隔（${fmtInterval(liveIntervalSec)}）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                INTERVAL_PRESETS.forEach { sec ->
                    FilterChip(selected = sec == liveIntervalSec, onClick = { onLiveIntervalSecChange(sec) }, label = { Text(fmtInterval(sec)) })
                }
            }
            Text(
                "多久看一眼画面说一句：调小说话更勤、调大更安静省额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("静止处理", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StaticMode.entries.forEach { mode ->
                    FilterChip(selected = mode == staticMode, onClick = { onStaticModeChange(mode) }, label = { Text(mode.label) })
                }
            }
        }
        SettingsSection.Portrait -> SectionScaffold("人像拍照", { section = null }, modifier) {
            ToggleRow("高帧率 (30fps)", if (highFpsEnabled) "开：减残影，更耗电" else "关：12fps", highFpsEnabled, onHighFpsEnabledChange)
        }
        SettingsSection.Character -> SectionScaffold("角色", { section = null }, modifier) {
            ToggleRow("角色条", if (characterBarEnabled) "展开工具条时显示角色选择条" else "隐藏", characterBarEnabled, onCharacterBarEnabledChange)
            ToggleRow(
                "默认展开角色/动作条",
                if (toolbarDefaultExpanded) "开：取景时工具条默认展开" else "关：默认收起（取景更干净）",
                toolbarDefaultExpanded,
                onToolbarDefaultExpandedChange,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            CategoryRow("自定义角色（陪伴模式）", "自己写 prompt 的角色，用于陪伴模式") { onOpenCustomCharacters() }
        }
        SettingsSection.Byok -> SectionScaffold("账户 / BYOK", { section = null }, modifier) {
            ByokSection(geminiApiKey, onGeminiApiKeyChange)
        }
        SettingsSection.About -> SectionScaffold("关于", { section = null }, modifier) {
            Text("雨中逸事 · Rain Tales", style = MaterialTheme.typography.bodyLarge)
            Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "角色由 AI 生成，不是真人，不能替代专业的医疗、心理或法律帮助。使用 AI 功能需你已年满 18 岁或所在地区法定年龄。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "请从官方渠道（GitHub）获取本应用；来自其它渠道的修改版构建与本项目无关、不获支持。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                    }
                }) { Text("开源地址") }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$REPO_URL/blob/main/docs/privacy-policy.md")))
                    }
                }) { Text("隐私政策") }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            CategoryRow("重看开场", "再看一遍与米娜的初遇（VN 开场教程）") { onReplayTutorial() }
        }
    }
}

@Composable
private fun SettingsHome(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar("设置", onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            CategoryRow("AI 与评论", "AI 评论、知识链接") { onOpenSection(SettingsSection.Ai) }
            CategoryRow("语音", "朗读评论") { onOpenSection(SettingsSection.Voice) }
            CategoryRow("隐私", "位置、时段、隐私概览") { onOpenSection(SettingsSection.Privacy) }
            CategoryRow("拍摄", "画面比例 4:3 / 16:9 / 全屏") { onOpenSection(SettingsSection.Shoot) }
            CategoryRow("实时点评", "短时记忆、抓帧间隔、静止处理") { onOpenSection(SettingsSection.Live) }
            CategoryRow("人像拍照", "高帧率") { onOpenSection(SettingsSection.Portrait) }
            CategoryRow("角色", "角色条显示") { onOpenSection(SettingsSection.Character) }
            CategoryRow("账户 / BYOK", "填你自己的 Gemini key") { onOpenSection(SettingsSection.Byok) }
            CategoryRow("关于", "版本、开源、隐私政策") { onOpenSection(SettingsSection.About) }
            CategoryRow("开发者", "日志、用量", onOpenDebug)

            // 底部：询问米娜（不知道某设定怎么调时，直接问她）。
            Button(
                onClick = onOpenAssistant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text("☂️  询问米娜")
            }
        }
    }
}

/** 抓帧间隔预设（秒）：3 秒 ~ 10 分钟。 */
private val INTERVAL_PRESETS = listOf(3, 5, 12, 30, 60, 300, 600)

/** 秒 → 好读的时长（如 12 秒 / 5 分 / 1 分 30 秒）。 */
private fun fmtInterval(sec: Int): String = when {
    sec < 60 -> "$sec 秒"
    sec % 60 == 0 -> "${sec / 60} 分"
    else -> "${sec / 60} 分 ${sec % 60} 秒"
}

/** BYOK 子页：填/存/清自己的 Gemini key + 状态 + 获取链接。 */
@Composable
private fun ByokSection(geminiApiKey: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable(geminiApiKey) { mutableStateOf(geminiApiKey) }

    Text(
        text = if (geminiApiKey.isBlank()) "状态：未配置（将回退到内置 key，或无法用 AI）" else "状态：已配置 ✅",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Gemini API Key") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Button(onClick = {
            onChange(text.trim())
            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
        }) { Text("保存") }
        OutlinedButton(onClick = {
            text = ""
            onChange("")
            Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
        }) { Text("清除") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                )
            }
        }) { Text("获取 key") }
    }
    Text(
        text = "key 只存在本机（app 私有存储），用于直接调用你自己的 Google Gemini。本应用不收集、不上传你的 key。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = "⚠️ 若 key 属于免费层，上传的内容（照片/留言）可能被 Google 用于训练模型；付费层默认不会。介意请使用付费层，或关闭 AI 评论。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Gemini 模型名（空=用内置默认）。Google 退役模型时，用户填新名即可续用、无需升级 app。 */
@Composable
private fun ModelField(geminiModel: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable(geminiModel) { mutableStateOf(geminiModel) }

    Text("Gemini 模型", style = MaterialTheme.typography.bodyLarge)
    Text(
        text = if (geminiModel.isBlank()) "当前：默认（${GeminiClient.DEFAULT_MODEL}）" else "当前：$geminiModel",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("模型名") },
        placeholder = { Text(GeminiClient.DEFAULT_MODEL) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Button(onClick = {
            onChange(text.trim())
            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
        }) { Text("保存") }
        OutlinedButton(onClick = {
            text = ""
            onChange("")
            Toast.makeText(context, "已恢复默认", Toast.LENGTH_SHORT).show()
        }) { Text("恢复默认") }
    }
    Text(
        text = "一般不用改。若某天默认模型被 Google 停用（AI 评论报错/404），来这里填一个可用的新模型名即可继续用，不必等 app 更新。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/**
 * 回忆备份：把影集批注 + 照片元数据导出/导入为 JSON。
 * 系统云备份已关（隐私），故用显式导出让批注跨重装/换机存活（release-checklist #12）。
 * 用 Storage Access Framework 让用户自选文件位置——无需任何存储权限。
 */
@Composable
private fun MemoryBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { GalleryRepository(context) }

    // 导出：系统"新建文档"选位置 → 写入 JSON。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = repo.exportMemoriesJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess {
                Toast.makeText(context, "回忆已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 导入：系统"打开文档"选文件 → 读 JSON 恢复。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("无法读取文件")
                repo.importMemoriesJson(json)
            }.onSuccess { r ->
                Toast.makeText(
                    context,
                    "已导入：批注 ${r.annotationsImported} 条（跳过重复 ${r.annotationsSkipped}）",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Text("回忆备份", style = MaterialTheme.typography.bodyLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Button(onClick = {
            exportLauncher.launch("raintale-memories-${System.currentTimeMillis()}.json")
        }) { Text("导出") }
        OutlinedButton(onClick = {
            importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        }) { Text("导入") }
    }
    Text(
        text = "把影集里角色说的话 + 你的批注导出成一个文件，换机/重装后导入即可恢复（照片本身在系统相册、需另行保留）。系统云备份已关闭，这是保存回忆的方式。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = "导出文件含批注内容与拍摄时间/大致地点（不含 API key）；文件不会被上传，保存到哪、给谁由你自行保管。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * 功能首次开启告知：拦截"关→开"的确认弹窗（知识链接/位置共用）。
 * 「开启」= 记已看（版本化）+ 真正打开开关；「取消」= 保持关闭、下次开启再弹。
 */
@Composable
private fun FirstEnableNoticeDialog(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    onEnable: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.notice_enable)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.notice_cancel)) } },
    )
}

/** 隐私程度概览：按当前设置算出"什么会离开你的设备"。也是零收集告知（PrivacyNoticeDialog）的常驻可查版。 */
@Composable
private fun PrivacyOverview(
    aiEnabled: Boolean,
    locationEnabled: Boolean,
    timeGreetingEnabled: Boolean,
    knowledgeLinkEnabled: Boolean,
) {
    val level: String
    val lines: List<String>
    if (!aiEnabled) {
        level = "最高（不联网）"
        lines = listOf("AI 已关闭：拍照完全不联网，照片只存在本机。")
    } else {
        val sent = buildList {
            add("📷 你拍的合照")
            if (locationEnabled) add("📍 大致位置（城市级）")
            if (timeGreetingEnabled) add("🕐 当前时段")
            add("（用「留言拍照」时还会发送你输入的留言）")
        }
        level = if (locationEnabled || timeGreetingEnabled) "中（含位置/时段）" else "中"
        lines = sent
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 零收集声明（常驻版；首启弹窗版见 PrivacyNoticeDialog）。
            Text(
                "开发者没有服务器，不收集、不经手任何数据。下列数据只在你的设备与你启用的第三方服务之间直接传输。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text("隐私程度：$level", style = MaterialTheme.typography.titleSmall)
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
            if (aiEnabled) {
                Text(
                    "以上经你自己的 API key 发给 Google Gemini。ML Kit 人像分割在本机运行，不上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (knowledgeLinkEnabled) {
                    Text(
                        "🔎 知识链接开启：识别出的地标/品牌名还会发给维基百科查简介（Gemini 之外的第二个接收方）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentLocationLine(locationEnabled: Boolean) {
    val context = LocalContext.current
    val area by LocationProvider.area.collectAsStateWithLifecycle()
    LaunchedEffect(locationEnabled) {
        if (locationEnabled) runCatching { LocationProvider.refresh(context) }
    }
    Text(
        text = "当前位置（粗略）：" + when {
            !locationEnabled -> "—（已关闭）"
            area != null -> area!!
            else -> "暂无（需授予位置权限或移动一下）"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title, onBack)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        ) { content() }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun CategoryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
