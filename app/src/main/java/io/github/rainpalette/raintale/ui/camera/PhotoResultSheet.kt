package io.github.rainpalette.raintale.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp

/**
 * 拍后结果面板（仿留言面板）：显示刚拍的照片 + 「查看 / 修改 / 分享」。
 * 由拍照页/人像页点左上角"最近照片"缩略图触发——**不自动弹**，不干扰 AI 评论气泡/连拍。
 */
@Composable
fun PhotoResultSheet(
    photo: Bitmap,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onOpenGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("这张照片", style = MaterialTheme.typography.titleMedium)
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "刚拍的照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onView) { Text("查看") }
                    OutlinedButton(onClick = onEdit) { Text("修改") }
                    OutlinedButton(onClick = onOpenGallery) { Text("相册") }
                    Button(onClick = onShare) { Text("分享") }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}
