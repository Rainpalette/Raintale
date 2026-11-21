package io.github.rainpalette.raintale.ui.camera

import android.view.OrientationEventListener
import android.view.Surface
import io.github.rainpalette.raintale.camera.OrientationLock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 设备物理朝向（`Surface.ROTATION_*`），用**重力感应**侦测——**即使 Activity 锁定竖屏也能拿到**
 * （锁定后 `display.rotation` 不再变化，故不能再用 DisplayManager）。
 *
 * 拍摄页用它做两件事：
 * 1. 设 CameraX `targetRotation` → 拍出与手持方向一致的照片（横拿出横图）。
 * 2. 把图标/控件「转正」（锁屏下横拿时，整屏视觉是转过的，靠 [uprightDegrees] 抵消）。
 */
@Composable
fun rememberDeviceRotation(): State<Int> {
    val context = LocalContext.current
    val rotation = remember { mutableIntStateOf(Surface.ROTATION_0) }
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // CameraX 官方推荐的"角度→targetRotation"映射。
                rotation.intValue = when {
                    orientation in 45 until 135 -> Surface.ROTATION_270
                    orientation in 135 until 225 -> Surface.ROTATION_180
                    orientation in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}

/**
 * 锁定竖屏下把图标/控件「转正」所需的旋转角（度）：抵消横拿时锁屏内容的视觉旋转。
 * 真机若发现方向转反了，翻一下 ±90 的正负号即可。
 */
fun uprightDegrees(rotation: Int): Float = when (rotation) {
    Surface.ROTATION_90 -> 90f
    Surface.ROTATION_270 -> -90f
    Surface.ROTATION_180 -> 180f
    else -> 0f
}

/** 当前是否横拿（左右横屏）。合成器据此转置裁切比例 / 处理角色（Phase 2）。 */
fun isLandscapeRotation(rotation: Int): Boolean =
    rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

/**
 * 结合「屏幕方向」设置算出**实际使用的朝向**：
 * AUTO 用传感器朝向；锁定则固定（直屏=ROTATION_0 / 横屏=ROTATION_90），忽略传感器。
 */
fun effectiveRotation(lock: OrientationLock, sensorRotation: Int): Int = when (lock) {
    OrientationLock.AUTO -> sensorRotation
    OrientationLock.PORTRAIT -> Surface.ROTATION_0
    OrientationLock.LANDSCAPE -> Surface.ROTATION_90
}
