package io.github.rainpalette.raintale.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rainpalette.raintale.ui.tutorial.TutorialPlayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import io.github.rainpalette.raintale.BuildConfig
import io.github.rainpalette.raintale.ai.GeminiClient
import io.github.rainpalette.raintale.crash.CrashReporter
import io.github.rainpalette.raintale.data.AppSettings
import io.github.rainpalette.raintale.data.NoticeVersions
import io.github.rainpalette.raintale.data.SettingsStore
import io.github.rainpalette.raintale.ui.assistant.SettingsAssistantScreen
import io.github.rainpalette.raintale.ui.camera.CameraScreen
import io.github.rainpalette.raintale.ui.companion.CompanionScreen
import io.github.rainpalette.raintale.ui.debug.DebugScreen
import io.github.rainpalette.raintale.ui.gallery.GalleryScreen
import io.github.rainpalette.raintale.ui.live.LiveScreen
import io.github.rainpalette.raintale.ui.portrait.PortraitScreen
import io.github.rainpalette.raintale.ui.record.RecordScreen
import io.github.rainpalette.raintale.ui.settings.CustomCharacterScreen
import io.github.rainpalette.raintale.ui.settings.SettingsScreen
import io.github.rainpalette.raintale.ui.components.CaptureMode
import kotlinx.coroutines.launch

private enum class Overlay { None, Settings, Debug, Assistant, Gallery, CustomCharacters }

/**
 * 顶层导航：经典相机式的"模式"驱动——[mode]（普通/留言/人像/实时/录影）决定显示哪个拍摄页，
 * 普通↔留言为同一 CameraScreen（不重启相机），人像/实时/录影为各自页。设置/Debug 作为 [overlay] 盖在上面。
 * 轻量起见用状态切换，不引入 Navigation 库。
 *
 * 各项设置由 [SettingsStore]（DataStore）持久化——冷启动也保留。
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableStateOf(CaptureMode.Normal) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }

    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()

    // BYOK：优先用用户自己填的 key；为空时回退到编译期内置 key（开发便利）。
    val effectiveApiKey = settings.geminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
    // 模型名同步到 GeminiClient（全局单例配置，空值自动回落默认；见 GeminiClient.model 的 setter）。
    LaunchedEffect(settings.geminiModel) { GeminiClient.model = settings.geminiModel }
    val onSelectMode: (CaptureMode) -> Unit = { mode = it }

    // 返回键：逐层"往回退一步"（先关面板/退子页——由各页更深的 BackHandler 处理——再退 overlay/模式），
    // 退无可退（普通模式、无 overlay）时弹退出确认。此为最低优先级：更深的 BackHandler 先拦截。
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    val activity = context as? Activity
    // 自定义角色页从哪进来的（设置 / 陪伴页），返回时回到那里。
    var customCharsReturnTo by rememberSaveable { mutableStateOf(Overlay.None) }
    // 从设置「重看开场」触发重播 VN 教程（首次启动的自动播放走 settings.tutorialSeen）。
    var replayTutorial by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        when {
            overlay == Overlay.Debug -> overlay = Overlay.Settings
            overlay == Overlay.Assistant -> overlay = Overlay.Settings
            overlay == Overlay.Gallery -> overlay = Overlay.None
            overlay == Overlay.CustomCharacters -> overlay = customCharsReturnTo
            overlay == Overlay.Settings -> overlay = Overlay.None
            mode != CaptureMode.Normal -> mode = CaptureMode.Normal
            else -> showExitDialog = true
        }
    }

    when (overlay) {
        Overlay.Settings -> SettingsScreen(
            aiEnabled = settings.aiEnabled,
            onAiEnabledChange = { scope.launch { store.setAiEnabled(it) } },
            ttsEnabled = settings.ttsEnabled,
            onTtsEnabledChange = { scope.launch { store.setTtsEnabled(it) } },
            characterBarEnabled = settings.characterBarEnabled,
            onCharacterBarEnabledChange = { scope.launch { store.setCharacterBarEnabled(it) } },
            staticMode = settings.staticMode,
            onStaticModeChange = { scope.launch { store.setStaticMode(it) } },
            shortTermMemoryEnabled = settings.shortTermMemoryEnabled,
            onShortTermMemoryEnabledChange = { scope.launch { store.setShortTermMemoryEnabled(it) } },
            liveIntervalSec = settings.liveIntervalSec,
            onLiveIntervalSecChange = { scope.launch { store.setLiveIntervalSec(it) } },
            locationEnabled = settings.locationEnabled,
            onLocationEnabledChange = { scope.launch { store.setLocationEnabled(it) } },
            timeGreetingEnabled = settings.timeGreetingEnabled,
            onTimeGreetingEnabledChange = { scope.launch { store.setTimeGreetingEnabled(it) } },
            knowledgeLinkEnabled = settings.knowledgeLinkEnabled,
            onKnowledgeLinkEnabledChange = { scope.launch { store.setKnowledgeLinkEnabled(it) } },
            knowledgeNoticeSeen = settings.knowledgeNoticeSeenVersion >= NoticeVersions.KNOWLEDGE,
            onKnowledgeNoticeSeen = { scope.launch { store.setKnowledgeNoticeSeenVersion(NoticeVersions.KNOWLEDGE) } },
            locationNoticeSeen = settings.locationNoticeSeenVersion >= NoticeVersions.LOCATION,
            onLocationNoticeSeen = { scope.launch { store.setLocationNoticeSeenVersion(NoticeVersions.LOCATION) } },
            highFpsEnabled = settings.highFpsEnabled,
            onHighFpsEnabledChange = { scope.launch { store.setHighFpsEnabled(it) } },
            toolbarDefaultExpanded = settings.toolbarExpanded,
            onToolbarDefaultExpandedChange = { scope.launch { store.setToolbarExpanded(it) } },
            previewAspect = settings.previewAspect,
            onPreviewAspectChange = { scope.launch { store.setPreviewAspect(it) } },
            orientationLock = settings.orientationLock,
            onOrientationLockChange = { scope.launch { store.setOrientationLock(it) } },
            geminiApiKey = settings.geminiApiKey,
            onGeminiApiKeyChange = { scope.launch { store.setGeminiApiKey(it) } },
            geminiModel = settings.geminiModel,
            onGeminiModelChange = { scope.launch { store.setGeminiModel(it) } },
            onOpenDebug = { overlay = Overlay.Debug },
            onOpenAssistant = { overlay = Overlay.Assistant },
            onOpenCustomCharacters = { customCharsReturnTo = Overlay.Settings; overlay = Overlay.CustomCharacters },
            onReplayTutorial = { replayTutorial = true; overlay = Overlay.None },
            onBack = { overlay = Overlay.None },
            modifier = modifier,
        )
        Overlay.Debug -> DebugScreen(
            onBack = { overlay = Overlay.Settings },
            modifier = modifier,
        )
        Overlay.Assistant -> SettingsAssistantScreen(
            geminiApiKey = effectiveApiKey,
            onBack = { overlay = Overlay.Settings },
            modifier = modifier,
        )
        Overlay.Gallery -> GalleryScreen(
            geminiApiKey = effectiveApiKey,
            aiEnabled = settings.aiEnabled,
            timeGreetingEnabled = settings.timeGreetingEnabled,
            onBack = { overlay = Overlay.None },
            modifier = modifier,
        )
        Overlay.CustomCharacters -> CustomCharacterScreen(
            onBack = { overlay = customCharsReturnTo },
            modifier = modifier,
        )
        Overlay.None -> when (mode) {
            CaptureMode.Portrait -> PortraitScreen(
                selectedMode = mode,
                onSelectMode = onSelectMode,
                onOpenGallery = { overlay = Overlay.Gallery },
                highFpsEnabled = settings.highFpsEnabled,
                aiEnabled = settings.aiEnabled,
                ttsEnabled = settings.ttsEnabled,
                characterBarEnabled = settings.characterBarEnabled,
                toolbarDefaultExpanded = settings.toolbarExpanded,
                previewAspect = settings.previewAspect,
                locationEnabled = settings.locationEnabled,
                timeGreetingEnabled = settings.timeGreetingEnabled,
                knowledgeLinkEnabled = settings.knowledgeLinkEnabled,
                orientationLock = settings.orientationLock,
                geminiApiKey = effectiveApiKey,
                modifier = modifier,
            )
            CaptureMode.Companion -> CompanionScreen(
                selectedMode = mode,
                onSelectMode = onSelectMode,
                onOpenSettings = { overlay = Overlay.Settings },
                onOpenGallery = { overlay = Overlay.Gallery },
                onOpenCustomCharacters = { customCharsReturnTo = Overlay.None; overlay = Overlay.CustomCharacters },
                aiEnabled = settings.aiEnabled,
                locationEnabled = settings.locationEnabled,
                timeGreetingEnabled = settings.timeGreetingEnabled,
                orientationLock = settings.orientationLock,
                previewAspect = settings.previewAspect,
                geminiApiKey = effectiveApiKey,
                modifier = modifier,
            )
            CaptureMode.Record -> RecordScreen(
                selectedMode = mode,
                onSelectMode = onSelectMode,
                onOpenSettings = { overlay = Overlay.Settings },
                aiEnabled = settings.aiEnabled,
                ttsEnabled = settings.ttsEnabled,
                characterBarEnabled = settings.characterBarEnabled,
                toolbarDefaultExpanded = settings.toolbarExpanded,
                locationEnabled = settings.locationEnabled,
                timeGreetingEnabled = settings.timeGreetingEnabled,
                knowledgeLinkEnabled = settings.knowledgeLinkEnabled,
                orientationLock = settings.orientationLock,
                geminiApiKey = effectiveApiKey,
                modifier = modifier,
            )
            CaptureMode.Live -> LiveScreen(
                selectedMode = mode,
                onSelectMode = onSelectMode,
                aiEnabled = settings.aiEnabled,
                staticMode = settings.staticMode,
                locationEnabled = settings.locationEnabled,
                timeGreetingEnabled = settings.timeGreetingEnabled,
                orientationLock = settings.orientationLock,
                geminiApiKey = effectiveApiKey,
                liveRecordingEnabled = false, // 录影暂下架：实时点评不录屏（引擎代码保留，见 ModeStrip 注释）
                shortTermMemoryEnabled = settings.shortTermMemoryEnabled,
                liveIntervalSec = settings.liveIntervalSec,
                liveNoticeSeen = settings.liveNoticeSeenVersion >= NoticeVersions.LIVE,
                onLiveNoticeSeen = { scope.launch { store.setLiveNoticeSeenVersion(NoticeVersions.LIVE) } },
                modifier = modifier,
            )
            else -> CameraScreen( // Normal / Caption：同屏，按 captureMode 派发快门
                captureMode = mode,
                selectedMode = mode,
                onSelectMode = onSelectMode,
                onOpenSettings = { overlay = Overlay.Settings },
                onOpenGallery = { overlay = Overlay.Gallery },
                aiEnabled = settings.aiEnabled,
                ttsEnabled = settings.ttsEnabled,
                characterBarEnabled = settings.characterBarEnabled,
                toolbarDefaultExpanded = settings.toolbarExpanded,
                previewAspect = settings.previewAspect,
                locationEnabled = settings.locationEnabled,
                timeGreetingEnabled = settings.timeGreetingEnabled,
                knowledgeLinkEnabled = settings.knowledgeLinkEnabled,
                orientationLock = settings.orientationLock,
                geminiApiKey = effectiveApiKey,
                modifier = modifier,
            )
        }
    }

    // 上次崩溃报告（本地）：启动时读一次，有则弹提示（优先于须知，避免叠加）。
    var crashReport by remember { mutableStateOf(CrashReporter.pendingReport(context)) }
    crashReport?.let { report ->
        CrashReportDialog(
            report = report,
            onShare = {
                runCatching { context.startActivity(Intent.createChooser(CrashReporter.shareIntent(report), "分享崩溃报告")) }
                CrashReporter.clear(context)
                crashReport = null
            },
            onClose = {
                CrashReporter.clear(context)
                crashReport = null
            },
        )
    }

    // VN 开场教程：首次启动播放（看过存 tutorialSeen），或从设置「重看开场」触发。
    // 崩溃提示优先于教程；教程优先于须知（下方须知条件排除教程期）。全屏 Dialog 覆盖。
    // settingsLoaded：等 DataStore 首次真实值到达再判断，避免老用户开屏闪一下教程（首帧默认 tutorialSeen=false）。
    val settingsLoaded by produceState(false) { store.settings.first(); value = true }
    val showTutorial = settingsLoaded && crashReport == null && (replayTutorial || !settings.tutorialSeen)
    if (showTutorial) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            TutorialPlayer(
                onFinish = {
                    if (!settings.tutorialSeen) scope.launch { store.setTutorialSeen(true) }
                    replayTutorial = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // 零收集数据告知：首启弹一次（信息性、单按钮）。版本化：文案更新（NoticeVersions.PRIVACY +1）会重弹。
    // 顺序：崩溃提示 > 教程 > 本告知 > AI 须知（先安心"什么都不收集"，再谈 AI 上传的知情同意）。
    val showPrivacyNotice = settingsLoaded && crashReport == null && !showTutorial &&
        settings.privacyNoticeSeenVersion < NoticeVersions.PRIVACY
    if (showPrivacyNotice) {
        PrivacyNoticeDialog(
            onConfirm = { scope.launch { store.setPrivacyNoticeSeenVersion(NoticeVersions.PRIVACY) } },
        )
    }

    // "AI 上传照片"须知拦截弹窗：AI 开着且"同意版本低于当前版本"时弹——
    // 覆盖 首次启动 / AI 从关到开（关会清零）/ 须知文案更新（NoticeVersions.AI_DISCLAIMER +1）三种情形。
    // 崩溃提示 / 教程 / 零收集告知存在时先不弹，避免弹窗叠加；settingsLoaded 防止已同意用户开屏闪弹窗。
    if (settingsLoaded && crashReport == null && !showTutorial && !showPrivacyNotice &&
        settings.aiEnabled && settings.aiDisclaimerAcceptedVersion < NoticeVersions.AI_DISCLAIMER
    ) {
        AiDisclaimerDialog(
            onAgree = { scope.launch { store.setAiDisclaimerAcceptedVersion(NoticeVersions.AI_DISCLAIMER) } },
            onDisableAi = { scope.launch { store.setAiEnabled(false) } },
            onExit = { activity?.finish() },
        )
    }

    // 退无可退时的退出确认。AlertDialog 自身也拦截返回键（点返回=取消）。
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出雨中逸事？") },
            text = { Text("确定要退出应用吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    activity?.finish()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("取消") }
            },
        )
    }
}
