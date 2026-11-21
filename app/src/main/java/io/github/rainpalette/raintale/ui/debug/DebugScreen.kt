package io.github.rainpalette.raintale.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import io.github.rainpalette.raintale.live.UsageMeter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainpalette.raintale.debug.DebugLog

/**
 * 开发者页（从 设置 → 开发者 进入）：应用内日志 + 用量计。
 *
 * 用户设置（AI/位置/角色条/静止处理/高帧率…）已迁到正式设置页（`ui/settings`），这里只留 debug 工具。
 */
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by DebugLog.entries.collectAsStateWithLifecycle()
    val usage by UsageMeter.usage.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) { Text("返回") }
            OutlinedButton(onClick = { DebugLog.clear() }) { Text("清空") }
            Text(
                text = "共 ${entries.size} 条",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // 实时点评·用量进度条（debug：每次 AI 调用 +10%）。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(text = "用量 ${(usage * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { UsageMeter.reset() },
                modifier = Modifier.padding(start = 12.dp),
            ) { Text("重置") }
        }
        LinearProgressIndicator(
            progress = { usage },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "暂无日志。出现错误时会记录到这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { entry ->
                    val color = when (entry.level) {
                        DebugLog.Level.ERROR -> MaterialTheme.colorScheme.error
                        DebugLog.Level.INFO -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = "[${entry.time}] ${entry.tag}: ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                    )
                }
            }
        }
    }
}
