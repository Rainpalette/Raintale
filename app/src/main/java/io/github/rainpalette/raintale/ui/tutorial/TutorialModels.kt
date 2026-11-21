package io.github.rainpalette.raintale.ui.tutorial

import androidx.annotation.DrawableRes

/**
 * VN 教程的节点（Ren'Py 心智模型的极简版：say / menu / label-jump）。
 * 脚本＝ label → 节点序列；每段应以 [Beat.Menu] / [Beat.Goto] / [Beat.End] 收尾。
 * 改台词、加分支只动 [TutorialContent]，不碰引擎（[TutorialPlayer]）。
 */
sealed interface Beat {
    /** 全屏图片（前奏照片 / 背景）。[imageRes] 为空时显示 [placeholder] 占位。 */
    data class Photo(@DrawableRes val imageRes: Int? = null, val placeholder: String? = null) : Beat

    /** 一句对白。[speaker] 为空＝纯白描（不显示名字）。 */
    data class Say(val speaker: String?, val text: String) : Beat

    /** 选项菜单，每个选项跳到一个 label。[prompt] 可选（菜单上方一句提示）。 */
    data class Menu(val prompt: String? = null, val options: List<Choice>) : Beat

    /** 无条件跳到某 label（控制节点，不渲染）。[target] 是目标 label。 */
    data class Goto(val target: String) : Beat

    /** 结束教程 → 进入相机。 */
    data object End : Beat
}

/** 一个菜单选项：显示文字 [text] + 目标 label [target]。 */
data class Choice(val text: String, val target: String)

/** 脚本 = label → 该段的节点序列。 */
typealias TutorialScript = Map<String, List<Beat>>
