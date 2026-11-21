package io.github.rainpalette.raintale.ui.camera

import android.content.Context
import android.content.Intent
import android.net.Uri

/** 打开系统看图程序查看合照。拍照页/人像页共用。 */
internal fun viewPhoto(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

/** 调起系统分享面板分享合照。拍照页/人像页共用。 */
internal fun sharePhoto(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享合照"))
}
