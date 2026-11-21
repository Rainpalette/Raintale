package io.github.rainpalette.raintale.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 负责"照片如何落到系统相册"的细节。
 *
 * M4：把合成后的 Bitmap 以 JPEG 写入 MediaStore 的 Pictures/PhotoBuddy。
 */
object PhotoStore {

    /** 相册里的子相册名（位于系统 Pictures 目录下）。 */
    private const val ALBUM_NAME = "PhotoBuddy"

    /**
     * 把 [bitmap] 存进系统相册，返回其 Uri。失败抛 [IOException]。
     * 调用方请在 IO 线程执行。minSdk 29 原生支持 RELATIVE_PATH。
     */
    fun saveBitmap(context: Context, bitmap: Bitmap): Uri {
        val name = "PhotoBuddy_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法在相册中创建条目")

        try {
            resolver.openOutputStream(uri).use { out ->
                if (out == null) throw IOException("无法打开输出流")
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw IOException("JPEG 压缩失败")
                }
            }
        } catch (e: Exception) {
            // 写失败时清掉半成品条目，避免相册里留下空文件。
            resolver.delete(uri, null, null)
            throw e
        }

        return uri
    }
}
