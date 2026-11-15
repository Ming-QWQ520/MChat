package com.ming.mchat.core.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.common.GlassIntensity

/** 液态玻璃参数：默认关闭；强度与减动画可在设置页配置。 */
data class LiquidGlassParams(
    val enabled: Boolean = false,
    val intensity: GlassIntensity = GlassIntensity.MEDIUM,
    val reduceMotion: Boolean = false,
)

val LocalLiquidGlass = staticCompositionLocalOf { LiquidGlassParams() }

/**
 * 液态玻璃视觉层：在 MD3 语义色之上叠加半透明渐变背景 + 边缘高光描边。
 * 文本颜色使用 onSurface，保证深浅色对比度（禁止纯白文字压浅色玻璃）。
 * 仅用于 TopAppBar、底部输入栏、抽屉、对话框、设置分组卡片等悬浮层；
 * 列表项与长列表滚动区域不叠加实时模糊。
 */
@Composable
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(24.dp)): Modifier {
    val params = LocalLiquidGlass.current
    if (!params.enabled) return this
    val scheme = MaterialTheme.colorScheme
    val (bgAlpha, strokeAlpha) = when (params.intensity) {
        GlassIntensity.LOW -> 0.32f to 0.10f
        GlassIntensity.MEDIUM -> 0.45f to 0.16f
        GlassIntensity.HIGH -> 0.58f to 0.24f
    }
    val background = Brush.verticalGradient(
        listOf(
            scheme.surface.copy(alpha = (bgAlpha + 0.14f).coerceAtMost(0.85f)),
            scheme.surface.copy(alpha = bgAlpha),
        )
    )
    val highlight = Brush.verticalGradient(
        listOf(
            scheme.surface.copy(alpha = strokeAlpha + 0.18f),
            Color.Transparent,
            scheme.surface.copy(alpha = strokeAlpha),
        )
    )
    return this
        .background(background, shape)
        .border(androidx.compose.foundation.BorderStroke(1.dp, highlight), shape)
}

/** 液态玻璃容器：关闭时退化为普通 Surface，两套视觉共用同一导航与功能。 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    val glass = LocalLiquidGlass.current
    if (!glass.enabled) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content,
        )
    } else {
        Box(modifier = modifier.liquidGlass(shape).clip(shape)) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                content()
            }
        }
    }
}

/**
 * 真实背景模糊（Android 12+ RenderEffect）：对玻璃层背后的内容做整体模糊。
 * 低版本自动降级为半透明 + 渐变高光（不模糊），不崩溃。
 */
fun Modifier.glassBackdrop(active: Boolean, radius: Dp = 22.dp): Modifier =
    if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/** 减少动画：液态玻璃设置项 ∪ 系统“移除动画”无障碍设置。 */
@Composable
fun rememberReducedMotion(): Boolean {
    val params = LocalLiquidGlass.current
    val context = LocalContext.current
    val systemReduce = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    return params.reduceMotion || systemReduce
}
