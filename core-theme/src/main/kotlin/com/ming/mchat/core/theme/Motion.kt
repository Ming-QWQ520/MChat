package com.ming.mchat.core.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * MChat 动效体系（对齐 kelivo，全部 Kotlin/Compose 原生实现）：
 * - 曲线：标准三次贝塞尔缓动，命名与 kelivo Curves.* 一一对应；
 * - 时长：110ms（按压）/ 150ms（弹层淡入）/ 200ms（内容过渡）/ 220ms（按压配色、滚动按钮）/
 *   240ms（AnimatedSwitcher）/ 300ms（页面切换）五档；
 * - 弹簧：列表与手势回弹使用中低刚度 + 无反弹阻尼；
 * - 减动画：所有 motion* 帮助函数尊重 rememberReducedMotion()，关闭时退化为 snap。
 */
object MotionCurves {
    /** CubicBezier(0.215, 0.61, 0.355, 1.0)：弹出/淡入的默认收尾曲线。 */
    val EaseOutCubic: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    /** CubicBezier(0.55, 0.055, 0.675, 0.19)：退出加速。 */
    val EaseInCubic: Easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)

    /** CubicBezier(0.645, 0.045, 0.355, 1.0)：位移 / 缩放往返。 */
    val EaseInOutCubic: Easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)

    /** CubicBezier(0.165, 0.84, 0.44, 1.0)：更长行程的收尾（抽屉、面板）。 */
    val EaseOutQuart: Easing = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1f)

    /** CubicBezier(0.23, 1, 0.32, 1)：超出后缓慢回落的 Expo 曲线（大位移）。 */
    val EaseOutExpo: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** 弹簧：指示器 / 分组回弹（无过冲）。 */
    val SpringBouncy: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 弹簧：按压形变即时跟随。 */
    val SpringPress: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/** 标准时长（毫秒）。 */
object MotionDurations {
    const val PRESS = 110
    const val POPUP = 150
    const val CONTENT = 200
    const val TACTILE = 220
    const val SWITCHER = 240
    const val TRANSITION = 300
}

/** animateContentSize / AnimatedContent 使用的默认曲线对。 */
val SwitchInCurve: Easing get() = MotionCurves.EaseOutCubic
val SwitchOutCurve: Easing get() = MotionCurves.EaseInCubic

/** 减动画感知的 tween：开启减动画时退化为瞬时。 */
@Composable
fun <T> motionTween(durationMillis: Int = MotionDurations.CONTENT): AnimationSpec<T> {
    val reduced = rememberReducedMotion()
    return if (reduced) tween(durationMillis = 1) else tween(durationMillis = durationMillis, easing = MotionCurves.EaseOutCubic)
}

/** 按压缩放（IosCardPress / IosIconButton 触感）：0.96 收缩 + 弹簧释放。 */
@Composable
fun animatePressScale(pressed: Boolean, targetScale: Float = 0.96f): State<Float> =
    animateFloatAsState(
        targetValue = if (pressed) targetScale else 1f,
        animationSpec = if (rememberReducedMotion()) {
            tween(durationMillis = 1)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "pressScale",
    )
