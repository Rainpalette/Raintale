package io.github.rainpalette.raintale.data.gallery

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 图库/影集批注的 Room 库（照片元数据 + 批注）。单例。 */
@Database(entities = [PhotoMeta::class, PhotoAnnotation::class], version = 3, exportSchema = false)
abstract class GalleryDatabase : RoomDatabase() {

    abstract fun galleryDao(): GalleryDao

    companion object {
        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        // v1→v2：photo_meta 加 characterId（评旧图找回评论者）。保留已有测试数据。
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_meta ADD COLUMN characterId TEXT")
            }
        }

        // v2→v3：键从 MediaStore uri 改为文件名（重装不变，release-checklist #12）。
        // 旧行按 uri 存、无法在纯 SQL 里换算成文件名，故**重建表**（清掉 pre-release 测试数据）——
        // 这是发布前引入的键结构，不存在需要保留的已发布用户数据。
        // 下面的 DDL 精确匹配 Room 为新实体生成的 schema（列/类型/NOT NULL/主键/索引名一致），避免开库时的一致性校验失败。
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `photo_meta`")
                db.execSQL("DROP TABLE IF EXISTS `annotation`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photo_meta` (`photoKey` TEXT NOT NULL, " +
                        "`favorite` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL, " +
                        "`area` TEXT, `characterId` TEXT, PRIMARY KEY(`photoKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `annotation` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`photoKey` TEXT NOT NULL, `source` TEXT NOT NULL, `authorName` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `edited` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotation_photoKey` ON `annotation` (`photoKey`)")
            }
        }

        fun get(context: Context): GalleryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "gallery.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
