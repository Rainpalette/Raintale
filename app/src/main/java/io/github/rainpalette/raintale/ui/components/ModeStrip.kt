package io.github.rainpalette.raintale.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 拍摄模式（经典相机式的"模式条"）。普通/留言同屏切换；人像/实时切到各自页。
 * 名称简短以适配窄屏一条排开；改名只改这里。
 *
 * [inStrip]＝是否在模式条里显示。**录影暂下架**（功能未完善），设 false 从模式条隐藏，
 * 但保留枚举值与路由（RecordScreen 等代码不动）——日后重新上架把 Record 的 inStrip 翻回 true 即可。
 */
enum class CaptureMode(val label: String, val inStrip: Boolean = true) {
    Normal("普通"),
    Caption("留言"),
    Portrait("人像"),
    Live("实时"),
    Record("录影", inStrip = false),
    Companion("陪伴"),
}

/**
 * 模式条：快门上方一条横排，点切模式。叠在相机预览（深色）上，故用白/半透明白。
 */
@Composable
fun ModeStrip(
    selected: CaptureMode,
    onSelect: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptureMode.entries.filter { it.inStrip }.forEach { mode ->
            val isSel = mode == selected
            Text(
                text = mode.label,
                color = if (isSel) Color.White else Color.White.copy(alpha = 0.55f),
                fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                fontSize = if (isSel) 15.sp else 14.sp,
                modifier = Modifier
                    .clickable { onSelect(mode) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
    }
}
