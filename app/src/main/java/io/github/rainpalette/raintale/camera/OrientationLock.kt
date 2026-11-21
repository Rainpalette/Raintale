package io.github.rainpalette.raintale.camera

/**
 * 屏幕方向锁定（设置项）。
 * - [AUTO]：跟随手机倾斜（用 OrientationEventListener 的传感器朝向）。
 * - [PORTRAIT] / [LANDSCAPE]：忽略传感器、固定方向——照片/图标/角色都按该方向，不随倾斜改变。
 */
enum class OrientationLock(val label: String) {
    AUTO("自动"),
    PORTRAIT("锁定直屏"),
    LANDSCAPE("锁定横屏"),
}
