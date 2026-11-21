package io.github.rainpalette.raintale.data.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 图库里的一张照片（来自 MediaStore 的 PhotoBuddy 相册）。 */
data class GalleryPhoto(
    val uri: Uri,
    val id: Long,
    val dateAddedSec: Long,
    /** 文件名（DISPLAY_NAME）：批注/元数据的**稳定键**（重装不变，见 [PhotoMeta.photoKey]）。 */
    val displayName: String,
)

/** 导入回忆的结果统计。 */
data class ImportResult(val metaImported: Int, val annotationsImported: Int, val annotationsSkipped: Int)

/**
 * 图库数据层：MediaStore 查 PhotoBuddy 照片 + Room 存元数据/批注。
 * 只读 `Pictures/PhotoBuddy` 相册（我方拍的合照）。
 *
 * **键设计**：Room 记录以照片**文件名**（DISPLAY_NAME）为键，重装/换机后仍能对上（content uri 的 id 会变）。
 * 对外仍以 [Uri] 为参数（调用方便利），内部用 [keyForUri] 解析成文件名。
 */
class GalleryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = GalleryDatabase.get(appContext).galleryDao()

    // ---- MediaStore：列出 PhotoBuddy 照片（新→旧）----
    suspend fun listPhotos(): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%Pictures/$ALBUM%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val out = mutableListOf<GalleryPhoto>()
        appContext.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += GalleryPhoto(
                    uri = ContentUris.withAppendedId(collection, id),
                    id = id,
                    dateAddedSec = c.getLong(dateCol),
                    displayName = c.getString(nameCol),
                )
            }
        }
        // 顺手清理孤儿元数据/批注（照片已被删的）——按文件名对账。
        reconcile(out.map { it.displayName })
        out
    }

    /** 把一个 content uri 解析成稳定键（文件名 DISPLAY_NAME）。查不到（照片已删等）返回 null。 */
    private suspend fun keyForUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        appContext.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    /** 清理已不存在照片的元数据/批注。空表时不清（避免误删全部）。 */
    private suspend fun reconcile(existingKeys: List<String>) {
        if (existingKeys.isEmpty()) return
        dao.deleteMetaNotIn(existingKeys)
        dao.deleteAnnotationsNotIn(existingKeys)
    }

    // ---- 元数据 / 最爱 ----
    // 先把 uri 解析成文件名键，再委托 Room 的 flow（解析在 IO 上做）。
    fun metaFlow(uri: Uri): Flow<PhotoMeta?> = flow {
        val key = keyForUri(uri) ?: run { emit(null); return@flow }
        emitAll(dao.metaFlow(key))
    }.flowOn(Dispatchers.IO)

    /** 最爱照片的文件名键集合（GalleryScreen 用它筛选，与 [GalleryPhoto.displayName] 比对）。 */
    fun favoriteKeys(): Flow<List<String>> = dao.favoriteKeys()

    suspend fun toggleFavorite(uri: Uri) {
        val key = keyForUri(uri) ?: return
        val cur = dao.meta(key)
        dao.upsertMeta((cur ?: PhotoMeta(photoKey = key)).copy(favorite = !(cur?.favorite ?: false)))
    }

    /** 拍照存盘时快照照片的时间/地点/角色（供 AI 评旧图沿用地点、找回评论者、卡片显示）。 */
    suspend fun setCaptured(uri: Uri, capturedAt: Long, area: String?, characterId: String?) {
        val key = keyForUri(uri) ?: return
        val cur = dao.meta(key)
        dao.upsertMeta(
            (cur ?: PhotoMeta(photoKey = key)).copy(capturedAt = capturedAt, area = area, characterId = characterId)
        )
    }

    // ---- 批注 ----
    fun annotations(uri: Uri): Flow<List<PhotoAnnotation>> = flow {
        val key = keyForUri(uri) ?: run { emit(emptyList()); return@flow }
        emitAll(dao.annotations(key))
    }.flowOn(Dispatchers.IO)

    fun annotationCount(uri: Uri): Flow<Int> = flow {
        val key = keyForUri(uri) ?: run { emit(0); return@flow }
        emitAll(dao.annotationCount(key))
    }.flowOn(Dispatchers.IO)

    /** 加一条角色（AI 评论）批注。 */
    suspend fun addCharacterComment(uri: Uri, characterName: String, text: String) {
        val key = keyForUri(uri) ?: return
        dao.insertAnnotation(
            PhotoAnnotation(
                photoKey = key,
                source = AnnotationSource.CHARACTER,
                authorName = characterName,
                text = text,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** 加一条用户批注（注释 / 留言）。 */
    suspend fun addUserNote(uri: Uri, text: String, authorName: String = "我") {
        val key = keyForUri(uri) ?: return
        dao.insertAnnotation(
            PhotoAnnotation(
                photoKey = key,
                source = AnnotationSource.USER,
                authorName = authorName,
                text = text,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteAnnotation(a: PhotoAnnotation) = dao.deleteAnnotation(a)
    suspend fun updateAnnotation(a: PhotoAnnotation) = dao.updateAnnotation(a)

    /** 取某张照片的地点快照（AI 评旧图沿用）。 */
    suspend fun areaOf(uri: Uri): String? = keyForUri(uri)?.let { dao.meta(it)?.area }

    /** 取拍这张照片的角色 id（AI 评旧图找回评论者；无记录返回 null）。 */
    suspend fun characterIdOf(uri: Uri): String? = keyForUri(uri)?.let { dao.meta(it)?.characterId }

    // ---- 导出 / 导入「回忆」（元数据 + 批注 → JSON）----
    // 系统云备份已关（隐私），故用显式导出/导入让批注跨重装/换机存活（release-checklist #12）。

    /** 把所有元数据 + 批注导出为 JSON 文本。 */
    suspend fun exportMemoriesJson(): String = withContext(Dispatchers.IO) {
        val metaArr = JSONArray()
        dao.allMeta().forEach { m ->
            metaArr.put(
                JSONObject()
                    .put("photoKey", m.photoKey)
                    .put("favorite", m.favorite)
                    .put("capturedAt", m.capturedAt)
                    .put("area", m.area ?: JSONObject.NULL)
                    .put("characterId", m.characterId ?: JSONObject.NULL)
            )
        }
        val annArr = JSONArray()
        dao.allAnnotations().forEach { a ->
            annArr.put(
                JSONObject()
                    .put("photoKey", a.photoKey)
                    .put("source", a.source)
                    .put("authorName", a.authorName)
                    .put("text", a.text)
                    .put("createdAt", a.createdAt)
                    .put("edited", a.edited)
            )
        }
        JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("version", EXPORT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("meta", metaArr)
            .put("annotations", annArr)
            .toString(2)
    }

    /** 从导出的 JSON 恢复：元数据 upsert；批注按 (键,时间,文本) 判重后插入。 */
    suspend fun importMemoriesJson(json: String): ImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        require(root.optString("format") == EXPORT_FORMAT) { "不是雨中逸事的回忆备份文件" }
        var metaCount = 0
        root.optJSONArray("meta")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsertMeta(
                    PhotoMeta(
                        photoKey = o.getString("photoKey"),
                        favorite = o.optBoolean("favorite", false),
                        capturedAt = o.optLong("capturedAt", 0L),
                        area = o.optStringOrNull("area"),
                        characterId = o.optStringOrNull("characterId"),
                    )
                )
                metaCount++
            }
        }
        var annImported = 0
        var annSkipped = 0
        root.optJSONArray("annotations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = o.getString("photoKey")
                val createdAt = o.optLong("createdAt", 0L)
                val text = o.getString("text")
                if (dao.annotationExists(key, createdAt, text) > 0) {
                    annSkipped++
                } else {
                    dao.insertAnnotation(
                        PhotoAnnotation(
                            photoKey = key,
                            source = o.optString("source", AnnotationSource.USER),
                            authorName = o.optString("authorName", "我"),
                            text = text,
                            createdAt = createdAt,
                            edited = o.optBoolean("edited", false),
                        )
                    )
                    annImported++
                }
            }
        }
        ImportResult(metaCount, annImported, annSkipped)
    }

    /** JSON 里 null 或缺失 → Kotlin null（org.json 的 optString 对 null 会返回 "null" 字面量，需单独处理）。 */
    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name) || !has(name)) null else optString(name).ifBlank { null }

    private companion object {
        const val ALBUM = "PhotoBuddy"
        const val EXPORT_FORMAT = "photobuddy-memories" // 格式 id 保留旧值：兼容已导出的备份文件，勿改
        const val EXPORT_VERSION = 1
    }
}
