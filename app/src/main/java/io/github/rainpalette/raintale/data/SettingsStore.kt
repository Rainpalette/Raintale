package io.github.rainpalette.raintale.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.rainpalette.raintale.camera.OrientationLock
import io.github.rainpalette.raintale.camera.PreviewAspect
import io.github.rainpalette.raintale.live.StaticMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 各「一次性告知」的**当前版本号**。同意/看过记的是版本号而非布尔——
 * 文案有实质更新时把对应版本 +1，所有用户（含老用户）会自动重新看到一次，
 * 保证"用户同意的是哪一版"始终有据可查（知情同意要新鲜）。
 */
object NoticeVersions {
    /** AI 上传须知。v1=初版（布尔时代）；v2=加免费层训练警告；v3=加"AI 输出可能不准确"（2026-07-07）。 */
    const val AI_DISCLAIMER = 3
    /** 零收集数据告知（首启弹一次，常驻于 设置→隐私 概览）。 */
    const val PRIVACY = 1
    /** 实时点评持续上传告知（首次开始点评前弹一次）。 */
    const val LIVE = 1
    /** 知识链接首次开启告知（维基是 Gemini 之外的第二个接收方）。 */
    const val KNOWLEDGE = 1
    /** 位置信息首次开启告知（坐标→地理编码服务换地名，Gemini 只收大致地名）。 */
    const val LOCATION = 1
}

/** 所有用户设置的快照（默认值即 app 默认行为）。 */
data class AppSettings(
    val aiEnabled: Boolean = true,
    val ttsEnabled: Boolean = true,
    val characterBarEnabled: Boolean = true,
    val staticMode: StaticMode = StaticMode.SKIP,
    val locationEnabled: Boolean = false,
    val timeGreetingEnabled: Boolean = false,
    val knowledgeLinkEnabled: Boolean = true,
    val highFpsEnabled: Boolean = false,
    /** BYOK：用户自己的 Gemini API key（空=用内置 key 或无法用 AI）。存于 app 私有 DataStore。 */
    val geminiApiKey: String = "",
    /** Gemini 模型名覆盖（空=用 GeminiClient.DEFAULT_MODEL）。模型被 Google 退役时用户可自填新名续用。 */
    val geminiModel: String = "",
    /** 底部"角色/动作"工具条是否展开（记住用户偏好；默认收起，取景更干净）。 */
    val toolbarExpanded: Boolean = false,
    /** 取景/拍照比例（普通/留言/人像页；默认 4:3。实时点评固定全屏，不读此项）。 */
    val previewAspect: PreviewAspect = PreviewAspect.RATIO_4_3,
    /** 屏幕方向：自动跟随倾斜 / 锁定直屏 / 锁定横屏（默认自动）。 */
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    /** 语音来源：内建 TTS / 端上神经语音（默认内建）。 */
    /** 实时点评时是否同步屏幕录制。**录影暂下架**：设置不再暴露此开关、LiveScreen 已强制不录；
     *  字段与 setter 保留休眠，便于日后重新上架恢复（见 ModeStrip 的 inStrip 注释）。 */
    val liveRecordingEnabled: Boolean = true,
    /** 短时记忆（实时点评）：记最近 AI 评论（10 条/1 小时）当额外上下文；默认关。 */
    val shortTermMemoryEnabled: Boolean = false,
    /** 实时点评抓帧间隔（秒）：多久"看一眼"画面。默认 12 秒（范围见设置页预设）。 */
    val liveIntervalSec: Int = 12,
    /** 已同意的"AI 上传照片"须知**版本号**（0=未同意；低于 [NoticeVersions.AI_DISCLAIMER] 时弹拦截弹窗）。
     *  关 AI 会清零，再开需重新同意；须知文案实质更新（版本 +1）也会触发重新同意。 */
    val aiDisclaimerAcceptedVersion: Int = 0,
    /** 已看过的零收集数据告知版本号（0=未看过；低于 [NoticeVersions.PRIVACY] 时首启弹一次）。 */
    val privacyNoticeSeenVersion: Int = 0,
    /** 已看过的实时点评持续上传告知版本号（0=未看过；低于 [NoticeVersions.LIVE] 时首次开始点评前弹）。 */
    val liveNoticeSeenVersion: Int = 0,
    /** 已看过的知识链接开启告知版本号（低于 [NoticeVersions.KNOWLEDGE] 时开启开关前先弹）。 */
    val knowledgeNoticeSeenVersion: Int = 0,
    /** 已看过的位置信息开启告知版本号（低于 [NoticeVersions.LOCATION] 时开启开关前先弹）。 */
    val locationNoticeSeenVersion: Int = 0,
    /** 是否看过 VN 开场教程（首次启动播放，看过即不再自动弹；设置里可重看）。 */
    val tutorialSeen: Boolean = false,
)

// 单进程一个 DataStore（preferencesDataStore 委托保证同一 Context 唯一实例）。
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置的持久化存储（DataStore Preferences）。替代原先 `rememberSaveable`——冷启动也不丢。
 * 用 applicationContext 创建，避免泄漏 Activity。
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    /** 当前设置流；读盘异步，首个值前用默认。 */
    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            aiEnabled = p[Keys.AI] ?: true,
            ttsEnabled = p[Keys.TTS] ?: true,
            characterBarEnabled = p[Keys.CHARACTER_BAR] ?: true,
            staticMode = p[Keys.STATIC_MODE]?.let { runCatching { StaticMode.valueOf(it) }.getOrNull() }
                ?: StaticMode.SKIP,
            locationEnabled = p[Keys.LOCATION] ?: false,
            timeGreetingEnabled = p[Keys.TIME_GREETING] ?: false,
            knowledgeLinkEnabled = p[Keys.KNOWLEDGE_LINK] ?: true,
            highFpsEnabled = p[Keys.HIGH_FPS] ?: false,
            geminiApiKey = p[Keys.GEMINI_KEY] ?: "",
            geminiModel = p[Keys.GEMINI_MODEL] ?: "",
            toolbarExpanded = p[Keys.TOOLBAR_EXPANDED] ?: false,
            previewAspect = p[Keys.PREVIEW_ASPECT]?.let { runCatching { PreviewAspect.valueOf(it) }.getOrNull() }
                ?: PreviewAspect.RATIO_4_3,
            orientationLock = p[Keys.ORIENTATION_LOCK]?.let { runCatching { OrientationLock.valueOf(it) }.getOrNull() }
                ?: OrientationLock.AUTO,
            liveRecordingEnabled = p[Keys.LIVE_RECORDING] ?: true,
            shortTermMemoryEnabled = p[Keys.SHORT_TERM_MEMORY] ?: false,
            liveIntervalSec = p[Keys.LIVE_INTERVAL_SEC] ?: 12,
            aiDisclaimerAcceptedVersion = p[Keys.AI_DISCLAIMER_VERSION] ?: 0,
            privacyNoticeSeenVersion = p[Keys.PRIVACY_NOTICE_VERSION] ?: 0,
            liveNoticeSeenVersion = p[Keys.LIVE_NOTICE_VERSION] ?: 0,
            knowledgeNoticeSeenVersion = p[Keys.KNOWLEDGE_NOTICE_VERSION] ?: 0,
            locationNoticeSeenVersion = p[Keys.LOCATION_NOTICE_VERSION] ?: 0,
            tutorialSeen = p[Keys.TUTORIAL_SEEN] ?: false,
        )
    }

    /** 开关 AI 评论。**关 AI 会一并清掉"已同意须知"（版本清零）** → 再次开启时会重新弹须知（重新同意）。 */
    suspend fun setAiEnabled(value: Boolean) {
        dataStore.edit {
            it[Keys.AI] = value
            if (!value) it[Keys.AI_DISCLAIMER_VERSION] = 0
        }
    }
    suspend fun setAiDisclaimerAcceptedVersion(value: Int) = put(Keys.AI_DISCLAIMER_VERSION, value)
    suspend fun setPrivacyNoticeSeenVersion(value: Int) = put(Keys.PRIVACY_NOTICE_VERSION, value)
    suspend fun setLiveNoticeSeenVersion(value: Int) = put(Keys.LIVE_NOTICE_VERSION, value)
    suspend fun setKnowledgeNoticeSeenVersion(value: Int) = put(Keys.KNOWLEDGE_NOTICE_VERSION, value)
    suspend fun setLocationNoticeSeenVersion(value: Int) = put(Keys.LOCATION_NOTICE_VERSION, value)
    suspend fun setTtsEnabled(value: Boolean) = put(Keys.TTS, value)
    suspend fun setCharacterBarEnabled(value: Boolean) = put(Keys.CHARACTER_BAR, value)
    suspend fun setStaticMode(value: StaticMode) = put(Keys.STATIC_MODE, value.name)
    suspend fun setLocationEnabled(value: Boolean) = put(Keys.LOCATION, value)
    suspend fun setTimeGreetingEnabled(value: Boolean) = put(Keys.TIME_GREETING, value)
    suspend fun setKnowledgeLinkEnabled(value: Boolean) = put(Keys.KNOWLEDGE_LINK, value)
    suspend fun setHighFpsEnabled(value: Boolean) = put(Keys.HIGH_FPS, value)
    suspend fun setGeminiApiKey(value: String) = put(Keys.GEMINI_KEY, value)
    suspend fun setGeminiModel(value: String) = put(Keys.GEMINI_MODEL, value)
    suspend fun setToolbarExpanded(value: Boolean) = put(Keys.TOOLBAR_EXPANDED, value)
    suspend fun setPreviewAspect(value: PreviewAspect) = put(Keys.PREVIEW_ASPECT, value.name)
    suspend fun setOrientationLock(value: OrientationLock) = put(Keys.ORIENTATION_LOCK, value.name)
    suspend fun setLiveRecordingEnabled(value: Boolean) = put(Keys.LIVE_RECORDING, value)
    suspend fun setShortTermMemoryEnabled(value: Boolean) = put(Keys.SHORT_TERM_MEMORY, value)
    suspend fun setLiveIntervalSec(value: Int) = put(Keys.LIVE_INTERVAL_SEC, value)
    suspend fun setTutorialSeen(value: Boolean) = put(Keys.TUTORIAL_SEEN, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val AI = booleanPreferencesKey("ai_enabled")
        val TTS = booleanPreferencesKey("tts_enabled")
        val CHARACTER_BAR = booleanPreferencesKey("character_bar")
        val STATIC_MODE = stringPreferencesKey("static_mode")
        val LOCATION = booleanPreferencesKey("location")
        val TIME_GREETING = booleanPreferencesKey("time_greeting")
        val KNOWLEDGE_LINK = booleanPreferencesKey("knowledge_link")
        val HIGH_FPS = booleanPreferencesKey("high_fps")
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val TOOLBAR_EXPANDED = booleanPreferencesKey("toolbar_expanded")
        val PREVIEW_ASPECT = stringPreferencesKey("preview_aspect")
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
        val LIVE_RECORDING = booleanPreferencesKey("live_recording")
        val SHORT_TERM_MEMORY = booleanPreferencesKey("short_term_memory")
        val LIVE_INTERVAL_SEC = intPreferencesKey("live_interval_sec")
        // 旧布尔键 "ai_disclaimer_accepted" 已弃用（须知 v2 加入训练警告，所有人需重新同意一次，故直接换键）。
        val AI_DISCLAIMER_VERSION = intPreferencesKey("ai_disclaimer_version")
        val PRIVACY_NOTICE_VERSION = intPreferencesKey("privacy_notice_version")
        val LIVE_NOTICE_VERSION = intPreferencesKey("live_notice_version")
        val KNOWLEDGE_NOTICE_VERSION = intPreferencesKey("knowledge_notice_version")
        val LOCATION_NOTICE_VERSION = intPreferencesKey("location_notice_version")
        val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
    }
}
