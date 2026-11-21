package io.github.rainpalette.raintale.ui.camera

import android.graphics.Bitmap
import android.net.Uri

/**
 * 气泡里这句**预写台词**为什么出现——决定气泡上的来源小标。
 *
 * 分开两种是为了不说谎：没配 key / 关了 AI 是**用户选择的档位**，
 * 而没网 / 调用失败是**这次没接上**，两者对用户的含义完全不同。
 */
enum class OfflineLineReason(val badge: String) {
    /** AI 评论关闭，或还没配 key——演示模式。 */
    DEMO("演示模式 · 预写台词"),

    /** 有 key 也开了 AI，但没网或调用失败（真实错误记在 Debug 页）。 */
    UNREACHABLE("这次没接上 · 预写台词"),
}

/**
 * 相机主屏的 UI 状态。后续里程碑会往这里扩字段。
 */
data class CameraUiState(
    val isCapturing: Boolean = false,
    /** 一次性提示文案（如"已保存到相册"）；消费后由 ViewModel 清空。 */
    val message: String? = null,
    /** AI 评论是否生成中。 */
    val isCommenting: Boolean = false,
    /** AI 评论结果；null 表示当前无评论。 */
    val aiComment: String? = null,
    /** 当前 [aiComment] 若是**预写台词**，这里是原因（决定气泡小标）；null = 这句是 AI 生成的。 */
    val offlineReason: OfflineLineReason? = null,
    /** 评论里识别到的实体（地标/品牌/物体）。 */
    val aiEntity: String? = null,
    /** 判据一：Gemini 判定该实体是否专有/值得查（通用物体为 false）。 */
    val aiLinkworthy: Boolean = false,
    /** 判据二：维基确实有合格词条（验证通过才显示链接）。 */
    val linkReady: Boolean = false,
    /** 验证时查到的维基结果，点链接时直接复用，无需再查。 */
    val verifiedWiki: io.github.rainpalette.raintale.wiki.WikiRepository.Result? = null,
    // 知识链接维基弹窗状态。
    val wikiOpen: Boolean = false,
    val wikiLoading: Boolean = false,
    val wikiTitle: String? = null,
    val wikiExtract: String? = null,
    /** 「不是这个地点」纠错输入弹窗是否打开。 */
    val wikiCorrectionOpen: Boolean = false,
    /** 最近一次保存的合照 Uri，用于查看/分享。 */
    val lastPhotoUri: Uri? = null,
    /** 最近合照的小缩略图，给拍照页左上角"最近照片"入口显示。 */
    val lastPhotoThumbnail: Bitmap? = null,
    /** 留言拍照：待留言的合成图；非空时显示留言面板。 */
    val captionPhoto: Bitmap? = null,
)
