package io.github.rainpalette.raintale.data.gallery

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一张照片的**每张一份**元数据：是否最爱 + 拍摄当时快照的时间/地点。
 * 主键 [photoKey] 是照片的**文件名**（MediaStore DISPLAY_NAME，如 `PhotoBuddy_20260706_153012.jpg`）。
 *
 * 为什么用文件名而非 content uri：uri 里的数字 id 在**卸载重装/换机后会变**，会让批注全部变孤儿；
 * 文件名按拍摄时间戳命名、稳定唯一、重装不变（release-checklist #12）。
 *
 * 为什么存 [area]：AI 评旧图要"沿用旧图地点"，但 MediaStore/EXIF 没有我们用的"城市级地名"
 * （只有 LocationProvider 给的粗略地名），所以只能在拍照存盘那刻快照进来。位置关/无 → null。
 */
@Entity(tableName = "photo_meta")
data class PhotoMeta(
    @PrimaryKey val photoKey: String,
    val favorite: Boolean = false,
    /** 拍摄时间（存盘那刻），毫秒。 */
    val capturedAt: Long = 0L,
    /** 拍摄地点的粗略地名快照（如"京都市"）；位置关/无则 null。 */
    val area: String? = null,
    /** 拍这张照片时的角色 id；图库评旧图按它找回评论者（旧图无记录则 null → 回退默认角色）。 */
    val characterId: String? = null,
)

/** 批注来源。 */
object AnnotationSource {
    const val CHARACTER = "character" // 角色（AI 评论）
    const val USER = "user" // 用户自己
}

/**
 * 影集批注：一张照片**多条**（角色的 AI 评论 + 用户的注释 + 留言 + 重新生成的多条，可并存）。
 * [authorName] 存**名字快照**（角色以后可删/改名，快照才不丢作者）。
 * [edited] 只是"改过"的布尔痕迹（防篡改后被拿去造谣），怎么用留待后续。
 */
@Entity(
    tableName = "annotation",
    indices = [Index("photoKey")],
)
data class PhotoAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联照片的文件名键（见 [PhotoMeta.photoKey]）。 */
    val photoKey: String,
    /** [AnnotationSource] 之一。 */
    val source: String,
    /** 作者名快照：角色名 / "我"。 */
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val edited: Boolean = false,
)
