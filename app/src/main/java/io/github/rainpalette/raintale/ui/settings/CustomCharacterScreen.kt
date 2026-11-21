package io.github.rainpalette.raintale.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rainpalette.raintale.data.character.CustomCharacter
import io.github.rainpalette.raintale.data.character.CustomCharacterRepository
import kotlinx.coroutines.launch

/**
 * 自定义角色管理页（设置→角色→自定义角色）：列表 + 引导式编辑器。
 * 纯 prompt 角色（无立绘/语音），用于陪伴模式；上限 [CustomCharacterRepository.MAX] 个。
 */
@Composable
fun CustomCharacterScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { CustomCharacterRepository(context) }
    val characters by repo.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    // null=列表；非空=正在编辑（id=0 表示新建）。
    var editing by remember { mutableStateOf<CustomCharacter?>(null) }

    if (editing != null) {
        BackHandler { editing = null }
        CustomCharacterEditor(
            initial = editing!!,
            onCancel = { editing = null },
            onSave = { c ->
                scope.launch {
                    val ok = repo.save(c)
                    if (!ok) Toast.makeText(context, "最多 ${CustomCharacterRepository.MAX} 个角色", Toast.LENGTH_SHORT).show()
                    editing = null
                }
            },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text("自定义角色", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "自己写 prompt 的角色（无立绘、无语音），用于「陪伴模式」——拍完弹一句它说的话。最多 ${CustomCharacterRepository.MAX} 个。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            characters.forEach { c ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            c.persona(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editing = c }) { Text("编辑") }
                            OutlinedButton(onClick = { scope.launch { repo.delete(c) } }) { Text("删除") }
                        }
                    }
                }
            }

            Button(
                onClick = { editing = CustomCharacter(name = "") },
                enabled = characters.size < CustomCharacterRepository.MAX,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(if (characters.size < CustomCharacterRepository.MAX) "新增角色" else "已达上限")
            }
        }
    }
}

/** 引导式编辑器：分栏引导用户写出好 prompt。名字必填，其余选填。 */
@Composable
private fun CustomCharacterEditor(
    initial: CustomCharacter,
    onCancel: () -> Unit,
    onSave: (CustomCharacter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial.name) }
    var identity by remember { mutableStateOf(initial.identity) }
    var personality by remember { mutableStateOf(initial.personality) }
    var speech by remember { mutableStateOf(initial.speech) }
    var relationship by remember { mutableStateOf(initial.relationship) }
    var extra by remember { mutableStateOf(initial.extra) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text(if (initial.id == 0L) "新增角色" else "编辑角色", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Field("名字（必填）", name, "小狐", "角色名，也是拍照后那句话的署名") { name = it }
            Field("身份 / 外形（一句话）", identity, "一只机灵的橘色狐狸精", "它是谁、长什么样") { identity = it }
            Field("性格（几个关键词）", personality, "俏皮、自信、有点小调皮", "3~4 个词即可") { personality = it }
            Field("说话风格 / 口头禅（最影响效果）", speech, "轻快口语化，爱用「呀」「啦」", "语气、句子长短、口癖语气词——这栏最能决定它像不像") { speech = it }
            Field("和你的关系", relationship, "形影不离的好搭档", "把拿相机的人当成什么") { relationship = it }
            Field("额外补充（选填）", extra, "任何想补充的", "会随每次评论发给 AI，简短点") { extra = it }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(
                    onClick = {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                identity = identity.trim(),
                                personality = personality.trim(),
                                speech = speech.trim(),
                                relationship = relationship.trim(),
                                extra = extra.trim(),
                            )
                        )
                    },
                    enabled = name.isNotBlank(),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    hint: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = { Text(hint) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
