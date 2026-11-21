package io.github.rainpalette.raintale.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rainpalette.raintale.character.CharacterPose

/**
 * 动作选择条：每个 [CharacterPose] 一个 chip，高亮当前选中。
 */
@Composable
fun PoseSelector(
    selected: CharacterPose,
    onSelect: (CharacterPose) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CharacterPose.entries.forEach { pose ->
            FilterChip(
                selected = pose == selected,
                onClick = { onSelect(pose) },
                label = { Text(pose.label) },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
