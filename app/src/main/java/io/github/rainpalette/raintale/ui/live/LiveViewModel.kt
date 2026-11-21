package io.github.rainpalette.raintale.ui.live

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rainpalette.raintale.ai.AiCommentRepository
import io.github.rainpalette.raintale.ai.CommentContext
import io.github.rainpalette.raintale.camera.CameraController
import io.github.rainpalette.raintale.character.CharacterController
import io.github.rainpalette.raintale.character.CharacterPose
import io.github.rainpalette.raintale.debug.DebugLog
import io.github.rainpalette.raintale.live.FrameChangeDetector
import io.github.rainpalette.raintale.live.ShortTermMemory
import io.github.rainpalette.raintale.live.StaticMode
import io.github.rainpalette.raintale.live.UsageMeter
import io.github.rainpalette.raintale.location.LocationProvider
import io.github.rainpalette.raintale.util.NetworkUtil
import io.github.rainpalette.raintale.util.TimeUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 实时点评的"大脑"：定时抓预览帧 → 本地变化检测 → 按 [StaticMode] 决定是否调 AI → 评论。
 *
 * debug 阶段：发给 AI 的是相机帧（不合成角色）；灵敏度固定"中"；不做录影、不做 TTS 重叠策略。
 */
class LiveViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private val aiRepo = AiCommentRepository()
    private val changeDetector = FrameChangeDetector()

    private var loopJob: Job? = null
    private var staticCount = 0

    @Volatile
    private var staticMode: StaticMode = StaticMode.SKIP

    @Volatile
    private var locationEnabled: Boolean = false

    @Volatile
    private var timeGreetingEnabled: Boolean = false

    @Volatile
    private var apiKey: String = ""

    @Volatile
    private var shortTermMemoryEnabled: Boolean = false

    @Volatile
    private var intervalMs: Long = DEFAULT_INTERVAL_MS

    fun setStaticMode(mode: StaticMode) {
        staticMode = mode
    }

    /** 设置抓帧间隔（多久看一眼画面）。下一拍生效。 */
    fun setIntervalMs(ms: Long) {
        intervalMs = ms
    }

    /** 开关短时记忆。关闭时清空已存记忆，避免残留旧上下文。 */
    fun setShortTermMemoryEnabled(enabled: Boolean) {
        shortTermMemoryEnabled = enabled
        if (!enabled) ShortTermMemory.clear()
    }

    fun setApiKey(key: String) {
        apiKey = key
    }

    fun setLocationEnabled(enabled: Boolean) {
        locationEnabled = enabled
    }

    fun setTimeGreetingEnabled(enabled: Boolean) {
        timeGreetingEnabled = enabled
    }

    /** 开始点评循环。 */
    fun start(controller: CameraController, characterController: CharacterController, context: Context) {
        if (loopJob?.isActive == true) return
        staticCount = 0
        changeDetector.reset()
        _uiState.update { it.copy(isRunning = true, staticCount = 0) }

        loopJob = viewModelScope.launch {
            // 预热：丢弃开头几帧，等相机自动曝光/对焦稳定，避免起始误判为"变化"而重复评论。
            warmUp(controller)
            while (isActive) {
                runCatching { tick(controller, characterController, context) }
                    .onFailure { DebugLog.e(TAG, "实时循环出错", it) }
                delay(intervalMs)
            }
        }
    }

    /** 启动预热：抓几帧丢弃，给相机时间稳定。不喂检测器，故第一次正式拍仍会出一句评论。 */
    private suspend fun warmUp(controller: CameraController) {
        repeat(WARMUP_FRAMES) {
            withTimeoutOrNull(GRAB_TIMEOUT_MS) { controller.grabFrame() }
            delay(WARMUP_INTERVAL_MS)
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    private suspend fun tick(
        controller: CameraController,
        characterController: CharacterController,
        context: Context,
    ) {
        // 抓一帧；2 秒内拿不到（相机未就绪）就跳过这拍。
        val frame = withTimeoutOrNull(GRAB_TIMEOUT_MS) { controller.grabFrame() } ?: return
        val pose = characterController.pose.value
        val persona = characterController.character.value.persona

        if (changeDetector.isChanged(frame)) {
            staticCount = 0
            _uiState.update { it.copy(staticCount = 0) }
            callAi(frame, pose, persona, ambient = false, context = context)
        } else {
            staticCount++
            _uiState.update { it.copy(staticCount = staticCount) }
            when (staticMode) {
                StaticMode.SKIP -> Unit
                StaticMode.RESEND -> callAi(frame, pose, persona, ambient = false, context = context)
                StaticMode.LOOP -> if (staticCount >= AMBIENT_AFTER) {
                    callAi(frame, pose, persona, ambient = true, context = context)
                    staticCount = 0
                    _uiState.update { it.copy(staticCount = 0) }
                }
            }
        }
    }

    private suspend fun callAi(frame: Bitmap, pose: CharacterPose, persona: String, ambient: Boolean, context: Context) {
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(message = "未配置 API Key（设置→账户）") }
            return
        }
        if (!NetworkUtil.isOnline(context)) {
            DebugLog.e(TAG, "未联网，跳过实时评论")
            _uiState.update { it.copy(message = "未联网") }
            return
        }
        _uiState.update { it.copy(isCommenting = true) }
        try {
            val commentContext = CommentContext(
                timeOfDay = if (timeGreetingEnabled) TimeUtil.currentTimeOfDay() else null,
                // 不阻塞：用缓存地名 + 后台刷新。
                area = if (locationEnabled) LocationProvider.areaForPrompt(context) else null,
                // 短时记忆：把最近说过的话作为额外上下文（开启时）。
                memory = if (shortTermMemoryEnabled) ShortTermMemory.recent() else null,
            )
            val result = aiRepo.comment(apiKey, frame, pose, commentContext, ambient, persona)
            UsageMeter.record()
            // 记下这条 AI 评论（开启短时记忆时），供后续调用参考。
            if (shortTermMemoryEnabled) ShortTermMemory.record(result.text)
            DebugLog.i(TAG, "实时评论${if (ambient) "(环境)" else ""}：${result.text}")
            // 实时点评本轮只用文本，不挂知识链接。
            _uiState.update { it.copy(isCommenting = false, comment = result.text) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "实时评论失败", e)
            _uiState.update { it.copy(isCommenting = false, message = "评论失败：${e.message}") }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun dismissComment() {
        _uiState.update { it.copy(comment = null) }
    }

    override fun onCleared() {
        loopJob?.cancel()
    }

    private companion object {
        const val TAG = "LiveViewModel"
        const val DEFAULT_INTERVAL_MS = 12_000L
        const val GRAB_TIMEOUT_MS = 2_000L
        const val AMBIENT_AFTER = 5 // 连续静止几次出一句环境语音
        const val WARMUP_FRAMES = 4 // 启动预热丢弃的帧数
        const val WARMUP_INTERVAL_MS = 500L
    }
}
