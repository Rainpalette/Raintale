package io.github.rainpalette.raintale.data.gallery

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {

    // ---- photo_meta ----
    @Query("SELECT * FROM photo_meta WHERE photoKey = :key")
    fun metaFlow(key: String): Flow<PhotoMeta?>

    @Query("SELECT * FROM photo_meta WHERE photoKey = :key")
    suspend fun meta(key: String): PhotoMeta?

    @Query("SELECT photoKey FROM photo_meta WHERE favorite = 1")
    fun favoriteKeys(): Flow<List<String>>

    @Upsert
    suspend fun upsertMeta(meta: PhotoMeta)

    // ---- annotation ----
    @Query("SELECT * FROM annotation WHERE photoKey = :key ORDER BY createdAt ASC")
    fun annotations(key: String): Flow<List<PhotoAnnotation>>

    @Query("SELECT COUNT(*) FROM annotation WHERE photoKey = :key")
    fun annotationCount(key: String): Flow<Int>

    @Insert
    suspend fun insertAnnotation(a: PhotoAnnotation): Long

    @Update
    suspend fun updateAnnotation(a: PhotoAnnotation)

    @Delete
    suspend fun deleteAnnotation(a: PhotoAnnotation)

    // ---- 导出/导入（回忆备份，release-checklist #12）----
    @Query("SELECT * FROM photo_meta")
    suspend fun allMeta(): List<PhotoMeta>

    @Query("SELECT * FROM annotation")
    suspend fun allAnnotations(): List<PhotoAnnotation>

    /** 导入用：按 (photoKey, createdAt, text) 判重，避免重复导入产生重复批注。 */
    @Query("SELECT COUNT(*) FROM annotation WHERE photoKey = :key AND createdAt = :createdAt AND text = :text")
    suspend fun annotationExists(key: String, createdAt: Long, text: String): Int

    // ---- 孤儿清理：照片在系统相册被删后，清掉对应元数据/批注 ----
    @Query("DELETE FROM photo_meta WHERE photoKey NOT IN (:existing)")
    suspend fun deleteMetaNotIn(existing: List<String>)

    @Query("DELETE FROM annotation WHERE photoKey NOT IN (:existing)")
    suspend fun deleteAnnotationsNotIn(existing: List<String>)
}
