package io.github.rainpalette.raintale.ui.camera

import android.graphics.Bitmap

/**
 * 快门瞬间的角色合成参数快照：基础矩形（叠层坐标、不含变换）+ 用户变换 + 立绘渲染器。
 * 由拍照页/人像页从 [io.github.rainpalette.raintale.ui.character.CharacterCaptureInfo] + 当前 transform 组装，
 * 传给 [CameraViewModel] 走全分辨率合成。
 *
 * [render]：给定目标像素尺寸，返回该尺寸下光栅化好的角色立绘（见 `CharacterRenderer`）。
 */
class CharacterCaptureSpec(
    val overlayWidth: Int,
    val overlayHeight: Int,
    val baseLeft: Float,
    val baseTop: Float,
    val baseRight: Float,
    val baseBottom: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val render: (widthPx: Int, heightPx: Int) -> Bitmap,
    // 快门瞬间的设备朝向（Surface.ROTATION_*）。横拿时合成器按转置比例裁切（Phase 2a）、
    // 并据此把角色转正+重映射（Phase 2b）。
    val deviceRotation: Int,
)
