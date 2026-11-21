package io.github.rainpalette.raintale.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rainpalette.raintale.ai.AiCommentRepository
import io.github.rainpalette.raintale.ai.CommentContext
import io.github.rainpalette.raintale.camera.CameraController
import io.github.rainpalette.raintale.camera.PhotoCompositor
import io.github.rainpalette.raintale.character.CharacterPose
import io.github.rainpalette.raintale.character.OfflineContext
import io.github.rainpalette.raintale.character.OfflineLinePool
import io.github.rainpalette.raintale.character.OfflineLines
import io.github.rainpalette.raintale.data.PhotoStore
import io.github.rainpalette.raintale.data.gallery.GalleryRepository
import io.github.rainpalette.raintale.debug.DebugLog
import io.github.rainpalette.raintale.location.LocationProvider
import io.github.rainpalette.raintale.util.NetworkUtil
import io.github.rainpalette.raintale.util.TimeUtil
import io.github.rainpalette.raintale.wiki.WikiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 相机主屏的"大脑"：持有 [CameraUiState]，编排拍照→合成→存盘→AI 评论。
 *
 * CameraX 的生命周期绑定对象由 UI 作用域的 [CameraController] 持有，不放进 ViewModel；
 * ViewModel 只负责状态与编排。
 */
class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val aiRepo = AiCommentRepository()

    /** 上一次说过的离线台词：下次选句时避开它，避免连拍复读。 */
    private var lastOfflineLine: String? = null

    /** 上一次拍照是否分割到了人（仅人像页会分割；null = 未知）——离线选句的场景线索之一。 */
    private var lastHasPerson: Boolean? = null

    /**
     * 按下快门：拍相机帧 + 按成片分辨率重绘角色 → 合成 → 存相册，随后异步取 AI 评论。
     *
     * @param character 快门瞬间的角色合成参数（基础矩形 + 变换 + 立绘渲染器）。
     * @param pose 当前角色动作，用于让 AI 评论更应景。
     */
    fun onShutter(
        controller: CameraController,
        character: CharacterCaptureSpec,
        pose: CharacterPose,
        persona: String,
        aiEnabled: Boolean,
        apiKey: String,
        locationEnabled: Boolean,
        timeGreetingEnabled: Boolean,
        context: Context,
        characterName: String = "",
        characterId: String = "",
        segmentPerson: (suspend (Bitmap) -> Bitmap?)? = null,
        /** 离线台词池：非 null＝AI 关/无 key/离线时说一句（普通/人像拍照传角色台词池）；
         *  null＝不兜底、保持静默（实时点评/录影的抓拍传 null）。 */
        offlineLines: OfflineLinePool? = null,
    ) {
        if (_uiState.value.isCapturing) return
        // 新一轮拍照清掉上一条评论与链接状态。
        _uiState.update { it.copy(isCapturing = true, message = null, aiComment = null, offlineReason = null, aiEntity = null, aiLinkworthy = false, linkReady = false, verifiedWiki = null) }

        viewModelScope.launch {
            val composed = captureComposeSave(controller, character, context, locationEnabled, characterId, segmentPerson)
            if (composed != null) {
                if (aiEnabled) {
                    generateComment(composed, pose, persona, apiKey, locationEnabled, timeGreetingEnabled, context, characterName = characterName, offlineLines = offlineLines)
                } else if (offlineLines != null) {
                    // AI 评论已关 = 演示模式：按场景说一句预写台词（不上传）。
                    showOfflineLine(offlineLines, OfflineLineReason.DEMO, offlineContext(pose), context, characterName)
                }
            }
        }
    }

    /**
     * 留言拍照：拍+合成+存盘，但**不自动评论**，而是把合成图交给留言面板，
     * 等用户写留言后再 [submitCaption]。
     */
    fun onCaptionShutter(
        controller: CameraController,
        character: CharacterCaptureSpec,
        context: Context,
        locationEnabled: Boolean = false,
        characterId: String = "",
    ) {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true, message = null, aiComment = null, aiEntity = null, aiLinkworthy = false, linkReady = false, verifiedWiki = null) }
        viewModelScope.launch {
            val composed = captureComposeSave(controller, character, context, locationEnabled, characterId)
            if (composed != null) {
                _uiState.update { it.copy(captionPhoto = composed) }
            }
        }
    }

    /** 提交留言：用留言面板里的照片 + 留言文字请求 AI。 */
    fun submitCaption(
        caption: String,
        pose: CharacterPose,
        persona: String,
        apiKey: String,
        aiEnabled: Boolean,
        locationEnabled: Boolean,
        timeGreetingEnabled: Boolean,
        context: Context,
        characterName: String = "",
        offlineLines: OfflineLinePool? = null,
    ) {
        val photo = _uiState.value.captionPhoto ?: return
        val uri = _uiState.value.lastPhotoUri
        _uiState.update { it.copy(captionPhoto = null) }
        viewModelScope.launch {
            // 留言存成"我"的批注（置顶：早于 AI 回复生成，按时间升序排在最上）。
            if (uri != null && caption.isNotBlank()) {
                runCatching { GalleryRepository(context).addUserNote(uri, caption) }
            }
            // AI 关时不上传（堵绕过）；有离线台词则说一句兜底。
            if (aiEnabled) {
                generateComment(photo, pose, persona, apiKey, locationEnabled, timeGreetingEnabled, context, caption = caption, characterName = characterName, offlineLines = offlineLines)
            } else if (offlineLines != null) {
                showOfflineLine(
                    offlineLines, OfflineLineReason.DEMO,
                    offlineContext(pose, hasCaption = caption.isNotBlank()), context, characterName,
                )
            }
        }
    }

    /** 取消留言（关面板，不评论）。 */
    fun cancelCaption() {
        _uiState.update { it.copy(captionPhoto = null) }
    }

    /**
     * 陪伴模式快门：拍一张**普通照片**（无角色合成）存相册，随后（若开 AI）用自定义角色的 persona
     * 生成一句纯文字评论（无 TTS）。角色不在画面里，走 companion prompt。
     */
    fun onCompanionShutter(
        controller: CameraController,
        persona: String,
        characterName: String,
        characterId: String,
        aiEnabled: Boolean,
        apiKey: String,
        locationEnabled: Boolean,
        timeGreetingEnabled: Boolean,
        context: Context,
    ) {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true, message = null, aiComment = null, aiEntity = null, aiLinkworthy = false, linkReady = false, verifiedWiki = null) }
        viewModelScope.launch {
            val photo = try {
                val bmp = controller.capture(context)
                val uri = withContext(Dispatchers.IO) { PhotoStore.saveBitmap(context, bmp) }
                val thumb = withContext(Dispatchers.Default) { bmp.toThumbnail() }
                val area = if (locationEnabled) LocationProvider.areaForPrompt(context) else null
                // 陪伴照片存自定义角色标记（"custom:{id}"）→ 图库里再评能找回同一个自定义角色。
                runCatching { GalleryRepository(context).setCaptured(uri, System.currentTimeMillis(), area, characterId.ifBlank { null }) }
                _uiState.update { it.copy(isCapturing = false, message = "已保存照片", lastPhotoUri = uri, lastPhotoThumbnail = thumb) }
                bmp
            } catch (e: Exception) {
                DebugLog.e(TAG, "陪伴模式拍照/存盘失败", e)
                _uiState.update { it.copy(isCapturing = false, message = "保存失败：${e.message}") }
                null
            }
            // 陪伴角色（自定义）无专属离线台词 → 用通用兜底池。
            if (photo != null) {
                if (aiEnabled) {
                    generateComment(
                        photo, CharacterPose.IDLE, persona, apiKey, locationEnabled, timeGreetingEnabled,
                        context, characterName = characterName, companion = true,
                        offlineLines = OfflineLines.generic,
                    )
                } else {
                    showOfflineLine(OfflineLines.generic, OfflineLineReason.DEMO, offlineContext(), context, characterName)
                }
            }
        }
    }

    /**
     * 拍一张 → 全分辨率合成角色 → 存相册，返回合成图（失败返回 null）。onShutter 与留言拍照共用。
     * @param segmentPerson 可选：对相机帧分割出人像前景，合成时画在角色之上（人像页"人在前"）。
     */
    private suspend fun captureComposeSave(
        controller: CameraController,
        character: CharacterCaptureSpec,
        context: Context,
        locationEnabled: Boolean = false,
        characterId: String = "",
        segmentPerson: (suspend (Bitmap) -> Bitmap?)? = null,
    ): Bitmap? = try {
        val cameraBitmap = controller.capture(context)
        // 对全分辨率成片单独分割（人像页才传），拿到锐利人像；普通拍照为 null。
        val personForeground = segmentPerson?.invoke(cameraBitmap)
        // 分割成功＝画面里有人；没传分割器（普通拍照）则保持"未知"。
        lastHasPerson = if (segmentPerson == null) null else personForeground != null
        val composed = withContext(Dispatchers.Default) {
            PhotoCompositor.composeFullRes(
                cameraBitmap,
                character.overlayWidth, character.overlayHeight,
                character.baseLeft, character.baseTop, character.baseRight, character.baseBottom,
                character.scale, character.offsetX, character.offsetY,
                character.render,
                personForeground,
                deviceRotation = character.deviceRotation,
            )
        }
        val uri = withContext(Dispatchers.IO) { PhotoStore.saveBitmap(context, composed) }
        val thumb = withContext(Dispatchers.Default) { composed.toThumbnail() }
        // 存照片元数据：拍摄时间 + 地点快照 + 角色 id（供影集卡片显示、AI 评旧图沿用地点/找回评论者）。
        val area = if (locationEnabled) LocationProvider.areaForPrompt(context) else null
        runCatching {
            GalleryRepository(context).setCaptured(
                uri, System.currentTimeMillis(), area, characterId.ifBlank { null },
            )
        }
        _uiState.update {
            it.copy(
                isCapturing = false,
                message = "已保存合影到相册",
                lastPhotoUri = uri,
                lastPhotoThumbnail = thumb,
            )
        }
        composed
    } catch (e: Exception) {
        DebugLog.e(TAG, "拍照/合成/存盘失败", e)
        _uiState.update { it.copy(isCapturing = false, message = "保存失败：${e.message}") }
        null
    }

    /** 异步生成 AI 评论；失败只影响气泡，不影响已存的照片。 */
    private suspend fun generateComment(
        photo: Bitmap,
        pose: CharacterPose,
        persona: String,
        apiKey: String,
        locationEnabled: Boolean,
        timeGreetingEnabled: Boolean,
        context: Context,
        caption: String? = null,
        characterName: String = "",
        companion: Boolean = false,
        offlineLines: OfflineLinePool? = null,
    ) {
        if (apiKey.isBlank()) {
            DebugLog.i(TAG, "未配置 API Key，跳过 AI 评论")
            if (offlineLines != null) {
                showOfflineLine(
                    offlineLines, OfflineLineReason.DEMO,
                    offlineContext(pose, caption != null), context, characterName,
                )
            } else {
                _uiState.update { it.copy(message = "未配置 API Key，去设置→账户填你的 key") }
            }
            return
        }
        if (!NetworkUtil.isOnline(context)) {
            DebugLog.e(TAG, "未联网，无法生成 AI 评论")
            if (offlineLines != null) {
                showOfflineLine(
                    offlineLines, OfflineLineReason.UNREACHABLE,
                    offlineContext(pose, caption != null), context, characterName,
                )
            } else {
                _uiState.update { it.copy(message = "未联网，无法生成 AI 评论") }
            }
            return
        }

        _uiState.update { it.copy(isCommenting = true) }
        try {
            val commentContext = CommentContext(
                timeOfDay = if (timeGreetingEnabled) TimeUtil.currentTimeOfDay() else null,
                // 不阻塞：用缓存地名 + 后台刷新（反向地理编码可能很慢）。
                area = if (locationEnabled) LocationProvider.areaForPrompt(context) else null,
                caption = caption,
            )
            val result = aiRepo.comment(apiKey, photo, pose, commentContext, persona = persona, companion = companion)
            DebugLog.i(TAG, "AI 评论：${result.text}（实体：${result.entity ?: "无"}，有把握：${result.entityConfident}，可链接：${result.linkworthy}）")
            // 拍照当下的 AI 评论自动存成角色批注（影集里能看到"当时说的话"）。
            val uri = _uiState.value.lastPhotoUri
            if (uri != null && characterName.isNotBlank()) {
                runCatching { GalleryRepository(context).addCharacterComment(uri, characterName, result.text) }
            }
            _uiState.update {
                it.copy(
                    isCommenting = false,
                    aiComment = result.text,
                    offlineReason = null,
                    aiEntity = result.entity,
                    aiLinkworthy = result.linkworthy,
                    linkReady = false,
                    verifiedWiki = null,
                )
            }
        } catch (e: Exception) {
            // todo 2-3：调用失败（429/5xx/超时…）不再只弹一条灰提示——
            // 陪伴 app 里"报错"等于"她没理我"，所以翻译成世界观内的一句话，
            // 真实错误照旧进 Debug 页（排查靠它，不靠弹窗）。
            DebugLog.e(TAG, "AI 评论失败", e)
            if (offlineLines != null) {
                showOfflineLine(
                    offlineLines, OfflineLineReason.UNREACHABLE,
                    offlineContext(pose, caption != null), context, characterName,
                )
            } else {
                _uiState.update { it.copy(isCommenting = false, message = "AI 评论失败：${e.message}") }
            }
        }
    }

    /** 组装离线选句用的场景线索（端上已知信息，零外发；时段不受「时段信息」开关约束，见 [OfflineContext]）。 */
    private fun offlineContext(
        pose: CharacterPose = CharacterPose.IDLE,
        hasCaption: Boolean = false,
    ) = OfflineContext(
        pose = pose,
        hasPerson = lastHasPerson,
        timeOfDay = TimeUtil.currentTimeOfDay(),
        hasCaption = hasCaption,
    )

    /**
     * 演示模式：按场景挑一句预写台词填进气泡。
     *
     * **不上传**；但会朗读、会像 AI 评论一样存进影集——演示模式是正式档位而非降级态，
     * 否则无 key 的用户「回忆」永远是空的。[OfflineLineReason] 只决定气泡上的来源小标。
     */
    private suspend fun showOfflineLine(
        pool: OfflineLinePool,
        reason: OfflineLineReason = OfflineLineReason.DEMO,
        ctx: OfflineContext = OfflineContext(),
        context: Context? = null,
        characterName: String = "",
    ) {
        val line = OfflineLines.pick(pool, ctx, avoid = lastOfflineLine)
        lastOfflineLine = line
        DebugLog.i(TAG, "预写台词（$reason）：$line（动作=${ctx.pose.label}，有人=${ctx.hasPerson}，时段=${ctx.timeOfDay}，留言=${ctx.hasCaption}）")
        val uri = _uiState.value.lastPhotoUri
        if (context != null && uri != null && characterName.isNotBlank()) {
            runCatching { GalleryRepository(context).addCharacterComment(uri, characterName, line) }
        }
        _uiState.update {
            it.copy(
                isCommenting = false,
                aiComment = line,
                offlineReason = reason,
                aiEntity = null,
                aiLinkworthy = false,
                linkReady = false,
                verifiedWiki = null,
            )
        }
    }

    /** 提示已展示，清空以免重复弹出。 */
    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** 关闭评论气泡。 */
    fun dismissComment() {
        _uiState.update { it.copy(aiComment = null, aiEntity = null, aiLinkworthy = false, linkReady = false, verifiedWiki = null) }
    }

    /**
     * 判据二·verify-then-link：后台查维基，**有合格词条才让链接显示**（linkReady），
     * 并缓存结果供点击时直接用。无词条则不显示链接（解决"书架"点开是无关内容）。
     */
    fun verifyEntityLink(entity: String) {
        viewModelScope.launch {
            val coords = LocationProvider.lastCoords()
            val result = WikiRepository.lookup(entity, coords?.first, coords?.second)
            if (result != null) {
                _uiState.update { it.copy(linkReady = true, verifiedWiki = result) }
            } else {
                DebugLog.i(TAG, "实体「$entity」维基无合格词条，不显示链接")
            }
        }
    }

    /** 点链接 → 直接用验证时缓存的维基结果弹窗（无需再查）。 */
    fun showEntityWiki() {
        val cached = _uiState.value.verifiedWiki ?: return
        _uiState.update {
            it.copy(
                wikiOpen = true, wikiLoading = false,
                wikiTitle = cached.title, wikiExtract = cached.extract,
                wikiCorrectionOpen = false,
            )
        }
    }

    /** 点"了解：实体"或纠错重查→ 查维基简介（带坐标做消歧义），弹窗显示。 */
    fun lookupEntity(entity: String) {
        _uiState.update {
            it.copy(
                wikiOpen = true, wikiLoading = true, wikiTitle = entity,
                wikiExtract = null, wikiCorrectionOpen = false,
            )
        }
        viewModelScope.launch {
            val coords = LocationProvider.lastCoords()
            val result = WikiRepository.lookup(entity, coords?.first, coords?.second)
            _uiState.update {
                it.copy(
                    wikiLoading = false,
                    wikiTitle = result?.title ?: entity,
                    wikiExtract = result?.extract ?: "未找到「$entity」的资料。",
                )
            }
        }
    }

    fun dismissWiki() {
        _uiState.update {
            it.copy(
                wikiOpen = false, wikiLoading = false, wikiTitle = null,
                wikiExtract = null, wikiCorrectionOpen = false,
            )
        }
    }

    /** 打开「不是这个地点」纠错输入。 */
    fun openWikiCorrection() {
        _uiState.update { it.copy(wikiCorrectionOpen = true) }
    }

    fun dismissWikiCorrection() {
        _uiState.update { it.copy(wikiCorrectionOpen = false) }
    }

    /** 用手填的正确地名重查。 */
    fun submitWikiCorrection(correctedName: String) {
        if (correctedName.isBlank()) return
        _uiState.update { it.copy(wikiCorrectionOpen = false) }
        lookupEntity(correctedName)
    }

    /** 生成小缩略图给"最近照片"入口（独立小 Bitmap，不动原图）。 */
    private fun Bitmap.toThumbnail(maxDim: Int = 160): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxDim) return this
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
