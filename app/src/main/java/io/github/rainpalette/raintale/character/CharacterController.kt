package io.github.rainpalette.raintale.character

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 角色行为抽象，按蓝图归在 character 子系统。
 *
 * 做成接口是关键：当前用 [SpriteCharacterController]（静态图）实现；日后若要换
 * Live2D，只需新增一个实现类，UI（[io.github.rainpalette.raintale.ui.character.CharacterOverlay]）
 * 与合成层无需改动。
 *
 * 注：合成用的 `renderToBitmap()` 由叠层整层截图替代（见 PhotoCompositor），故未在此暴露。
 */
interface CharacterController {
    /** 当前角色。 */
    val character: StateFlow<Character>

    /** 当前动作，UI 据此（结合角色）决定画哪张图。 */
    val pose: StateFlow<CharacterPose>

    /** 切换角色。 */
    fun setCharacter(character: Character)

    /** 回到 Idle（待机）。 */
    fun playIdle()

    /** 切换到指定动作。 */
    fun setPose(pose: CharacterPose)
}

/**
 * 静态图/精灵实现：用当前角色 + 当前动作选一张立绘，Idle 的"呼吸"由 UI 层做动画。
 */
class SpriteCharacterController(
    initialCharacter: Character = Characters.default,
) : CharacterController {

    private val _character = MutableStateFlow(initialCharacter)
    override val character: StateFlow<Character> = _character.asStateFlow()

    private val _pose = MutableStateFlow(CharacterPose.IDLE)
    override val pose: StateFlow<CharacterPose> = _pose.asStateFlow()

    override fun setCharacter(character: Character) {
        _character.value = character
    }

    override fun playIdle() {
        _pose.value = CharacterPose.IDLE
    }

    override fun setPose(pose: CharacterPose) {
        _pose.value = pose
    }
}
