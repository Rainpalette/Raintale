package io.github.rainpalette.raintale.ui.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// 暮色雨天底色（世界基调：低饱和蓝灰）。
private val DuskTop = Color(0xFF1B2733)
private val DuskBottom = Color(0xFF0E1519)
private val Ink = Color(0xFFF2F4F6)
private val InkDim = Color(0xCCF2F4F6)
private val Accent = Color(0xFFE9B872) // 站台灯的暖橙

private fun duskBrush() = Brush.verticalGradient(listOf(DuskTop, DuskBottom))

/**
 * VN 教程渲染器（引擎）：解释 [script]，从 [start] 开始，结束时调 [onFinish]（→ 进相机）。
 * 只认 [TutorialContent] 提供的脚本数据——改内容不动此文件。
 */
@Composable
fun TutorialPlayer(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    script: TutorialScript = TutorialContent.script,
    start: String = TutorialContent.START,
) {
    var label by rememberSaveable { mutableStateOf(start) }
    var index by rememberSaveable { mutableIntStateOf(0) }

    val beat = script[label]?.getOrNull(index)

    // 控制节点（Goto / End）与越界：用副作用推进，不渲染。
    LaunchedEffect(label, index) {
        val b = script[label]?.getOrNull(index)
        when {
            b is Beat.Goto -> { label = b.target; index = 0 }
            b == null || b is Beat.End -> onFinish()
        }
    }

    Box(modifier.fillMaxSize().background(duskBrush())) {
        when (beat) {
            is Beat.Photo -> PhotoBeat(beat) { advance(script, label, index, { index = it }, onFinish) }
            is Beat.Say -> SayBeat("$label:$index", beat) { advance(script, label, index, { index = it }, onFinish) }
            is Beat.Menu -> MenuBeat(beat) { target -> label = target; index = 0 }
            else -> {} // Goto / End / null：过渡帧，只显示底色
        }
        // 跳过（前奏/对白/菜单时可见）
        if (beat is Beat.Photo || beat is Beat.Say || beat is Beat.Menu) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp),
            ) { Text("跳过", color = InkDim) }
        }
    }
}

/** 前进一拍：未到段尾则 index+1，否则结束。 */
private fun advance(
    script: TutorialScript,
    label: String,
    index: Int,
    setIndex: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    val last = script[label].orEmpty().lastIndex
    if (index < last) setIndex(index + 1) else onFinish()
}

@Composable
private fun BoxScope.PhotoBeat(beat: Beat.Photo, onTap: () -> Unit) {
    Box(
        Modifier.fillMaxSize().noRippleClickable(onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (beat.imageRes != null) {
            Image(
                painter = painterResource(beat.imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                beat.placeholder ?: "（照片）",
                color = InkDim,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp),
            )
        }
        Text(
            "▼ 轻点继续",
            color = InkDim,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
        )
    }
}

@Composable
private fun BoxScope.SayBeat(key: String, beat: Beat.Say, onTap: () -> Unit) {
    var shown by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        shown = 0
        while (shown < beat.text.length) {
            delay(24)
            shown++
        }
    }
    val done = shown >= beat.text.length

    Box(Modifier.fillMaxSize().noRippleClickable { if (!done) shown = beat.text.length else onTap() }) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Surface(
                color = Color(0xE60E1519),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    if (!beat.speaker.isNullOrBlank()) {
                        Text(beat.speaker, color = Accent, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        beat.text.take(shown),
                        color = Ink,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = if (beat.speaker.isNullOrBlank()) 0.dp else 6.dp),
                    )
                    if (done) {
                        Text(
                            "▼ 轻点继续",
                            color = InkDim,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MenuBeat(beat: Beat.Menu, onChoose: (String) -> Unit) {
    Column(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!beat.prompt.isNullOrBlank()) {
            Text(
                beat.prompt,
                color = InkDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        beat.options.forEach { choice ->
            Surface(
                onClick = { onChoose(choice.target) },
                shape = RoundedCornerShape(12.dp),
                color = Color(0x33FFFFFF),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    choice.text,
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/** 无涟漪的全屏点击（VN 推进用）。 */
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
