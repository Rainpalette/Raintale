package io.github.rainpalette.raintale.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rainpalette.raintale.R

/**
 * 零收集数据告知：首次启动弹一次（版本化：文案实质更新时 NoticeVersions.PRIVACY +1 会对所有用户重弹）。
 * 与 AI 须知不同，这是**信息性告知**（没有需要拒绝的内容）：单按钮确认、无倒计时；
 * 但仍不可点外/返回关闭——确认过才算"看过"。常驻版在 设置→隐私 的隐私概览。
 *
 * 弹窗顺序（AppRoot）：崩溃提示 > 教程 > 本告知 > AI 须知。
 */
@Composable
fun PrivacyNoticeDialog(onConfirm: () -> Unit) {
    Dialog(
        onDismissRequest = { /* 只能按按钮关闭 */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.privacy_notice_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.privacy_notice_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.privacy_notice_confirm))
                }
            }
        }
    }
}
