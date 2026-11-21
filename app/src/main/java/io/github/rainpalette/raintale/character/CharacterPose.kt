package io.github.rainpalette.raintale.character

/**
 * 角色姿势（动作）。只表示"哪种动作"，具体显示哪张图由 [Character.drawableFor] 决定
 * ——因为同一个动作在不同角色下是不同的图。[label] 用于动作选择按钮。
 *
 * 增删动作：在这里加/删一项，底部动作条会自动多/少一个按钮；
 * 然后在各 [Character] 的 poseOverrides 里给出对应图（不给则回退到该角色的 idle 图）。
 */
enum class CharacterPose(val label: String) {
    IDLE("待机"),
    WAVE("挥手"),
    PEACE("比耶"),
}
