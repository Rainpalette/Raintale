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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rainpalette.raintale.R
import kotlinx.coroutines.delay

/** 首次须知的倒计时（debug 用 3 秒；正式版再延长）。 */
private const val COUNTDOWN_SEC = 3

/**
 * "AI 上传照片"须知的**拦截弹窗**：不可点外/返回关闭，同意按钮倒计时后才可点。
 * 同意是**版本化**的（SettingsStore.NoticeVersions.AI_DISCLAIMER）：首次启动、AI 从关到开（关会清零）、
 * 以及须知文案实质更新（版本 +1）三种情形都会（重新）弹。改 disclaimer_body 文案时记得同步版本号。
 *
 * 三个出口：同意继续 / 关闭 AI 评论后继续（不再上传）/ 不同意退出。
 */
@Composable
fun AiDisclaimerDialog(
    onAgree: () -> Unit,
    onDisableAi: () -> Unit,
    onExit: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(COUNTDOWN_SEC) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    Dialog(
        onDismissRequest = { /* 不可关 */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.disclaimer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Button(
                    onClick = onAgree,
                    enabled = remaining == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 带格式参数的样板：倒计时用 stringResource(id, arg) 传入剩余秒数。
                    Text(
                        if (remaining == 0) stringResource(R.string.disclaimer_agree)
                        else stringResource(R.string.disclaimer_agree_countdown, remaining),
                    )
                }
                OutlinedButton(onClick = onDisableAi, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.disclaimer_disable_ai))
                }
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.disclaimer_exit))
                }
            }
        }
    }
}
