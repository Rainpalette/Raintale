package io.github.rainpalette.raintale.live

/**
 * 实时点评中，一帧被判为"无明显变化（静止）"时如何处理。
 *
 * - [SKIP]：跳过，不调用 AI（安静、省额度）。
 * - [LOOP]：进入静止循环——累计静止 N 次后出一句环境陪伴语音。
 * - [RESEND]：静止也直接再发一次（仍调用 AI）。
 */
enum class StaticMode(val label: String) {
    SKIP("跳过"),
    LOOP("静止循环"),
    RESEND("直接再发"),
}
