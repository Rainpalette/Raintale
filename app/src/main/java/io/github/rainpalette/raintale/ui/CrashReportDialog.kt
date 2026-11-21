package io.github.rainpalette.raintale.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 「上次异常退出」提示：显示本地崩溃报告（可滚动），让用户**分享**（自愿贴到 issue）或**关闭**。
 * 关闭即清除报告（不再重复提示）。零数据收集——报告只在本机，是否外发由用户决定。
 */
@Composable
fun CrashReportDialog(
    report: String,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("上次异常退出") },
        text = {
            Column {
                Text(
                    "应用上次运行时崩溃了。下面是本机记录的报告（未上传任何地方）。" +
                        "你可以「分享」把它贴到项目 issue 帮助改进，或直接关闭。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onShare) { Text("分享报告") } },
        dismissButton = { TextButton(onClick = onClose) { Text("关闭") } },
    )
}
