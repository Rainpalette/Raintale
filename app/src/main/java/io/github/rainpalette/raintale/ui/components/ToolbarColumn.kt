package io.github.rainpalette.raintale.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rainpalette.raintale.character.Character
import io.github.rainpalette.raintale.character.CharacterPose
import io.github.rainpalette.raintale.character.Characters

/**
 * 「角色 / 动作」工具条（拍照页/人像页共用）。
 * - `collapsible = true`（默认）：可收起——收起只显示把手，展开则在把手上方显示动作条 +（可选）角色条，
 *   由把手切换、状态由上层持有。
 * - `collapsible = false`（设置「默认展开」开启）：**直接显示条、不出现把手按钮**（无法收起）。
 */
@Composable
fun ToolbarColumn(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    characterBarEnabled: Boolean,
    pose: CharacterPose,
    onSelectPose: (CharacterPose) -> Unit,
    character: Character,
    onSelectCharacter: (Character) -> Unit,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 不可收起时始终显示条；可收起时由 expanded 决定。
        if (expanded || !collapsible) {
            if (characterBarEnabled) {
                CharacterSelector(
                    characters = Characters.all,
                    selected = character,
                    onSelect = onSelectCharacter,
                )
            }
            PoseSelector(selected = pose, onSelect = onSelectPose)
        }
        // 仅可收起时才显示把手按钮。
        if (collapsible) {
            ToolbarHandle(expanded = expanded, onClick = { onExpandedChange(!expanded) })
        }
    }
}

@Composable
private fun ToolbarHandle(expanded: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.35f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text("角色 / 动作", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
