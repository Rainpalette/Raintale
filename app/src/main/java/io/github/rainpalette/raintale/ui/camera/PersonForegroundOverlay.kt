package io.github.rainpalette.raintale.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * 人像前景层：把 [PersonSegmenter][io.github.rainpalette.raintale.vision.PersonSegmenter] 抠出的人
 * 画在角色之上，实现"人挡住角色"。
 *
 * 对齐策略：用 `ContentScale.Crop` + `Alignment.Center` 填满，与 `PreviewView.FILL_CENTER`
 * （同样居中裁切填满、同样相机宽高比）自动重合——前景图已在分割层旋正/镜像到与预览同方向。
 *
 * [cutout] 为 null（无人/分割失败/功能关闭）时什么都不画，兜底回"角色盖最上"。
 */
@Composable
fun PersonForegroundOverlay(
    cutout: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val bitmap = cutout ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    )
}
