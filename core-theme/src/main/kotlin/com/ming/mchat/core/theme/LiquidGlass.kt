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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.ming.mchat.core.common.GlassIntensity

/** 液态玻璃参数：默认关闭；强度与减动画可在设置页配置。 */
data class LiquidGlassParams(
    val enabled: Boolean = false,
    val intensity: GlassIntensity = GlassIntensity.MEDIUM,
    val reduceMotion: Boolean = false,
)

val LocalLiquidGlass = staticCompositionLocalOf { LiquidGlassParams() }

/**
 * 玻璃采样源（Fluxhub AndroidLiquidGlass 用法）：
 * 根布局调用 [GlassBackdropSource] 包裹全部页面内容，内容即被录制为 LayerBackdrop；
 * 之后任意悬浮层通过 [Modifier.liquidGlass] 采样折射。关闭液态玻璃时不录制（零开销）。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun GlassBackdropSource(content: @Composable () -> Unit) {
    val params = LocalLiquidGlass.current
    if (!params.enabled) {
        CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
        return
    }
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.layerBackdrop(backdrop)) { content() }
    }
}

private data class GlassTuning(val blurPx: Float, val lensHeightPx: Float, val lensAmountPx: Float, val tintAlpha: Float)

private fun tuningFor(intensity: GlassIntensity, density: Float): GlassTuning {
    val d = density
    return when (intensity) {
        GlassIntensity.LOW -> GlassTuning(6f.dp2px(d), 8f.dp2px(d), 12f.dp2px(d), 0.10f)
        GlassIntensity.MEDIUM -> GlassTuning(10f.dp2px(d), 12f.dp2px(d), 20f.dp2px(d), 0.16f)
        GlassIntensity.HIGH -> GlassTuning(16f.dp2px(d), 16f.dp2px(d), 26f.dp2px(d), 0.22f)
    }
}

private fun Float.dp2px(density: Float): Float = this * density

/**
 * 液态玻璃修饰符（Fluxhub 风格：vibrancy + blur + lens 折射，采样 [LocalGlassBackdrop]）：
 * - Android 12 以下 backdrop 库自动跳过 RenderEffect，仅保留表面色调 + 发丝描边（不崩溃）；
 * - 玻璃开启时文本使用 onSurface 保证对比度，禁止纯白文字压浅色玻璃；
 * - 仅用于 TopAppBar、底部输入栏、抽屉、对话框、设置分组卡片等悬浮层。
 */
@Composable
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(24.dp)): Modifier {
    val params = LocalLiquidGlass.current
    if (!params.enabled) return this
    val backdrop = LocalGlassBackdrop.current ?: return this
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val tuning = tuningFor(params.intensity, density)
    val surfaceTint = if (dark) Color.Black.copy(alpha = tuning.tintAlpha) else Color.White.copy(alpha = tuning.tintAlpha)

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrancy()
                blur(tuning.blurPx)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    lens(tuning.lensHeightPx, tuning.lensAmountPx)
                }
            }
        },
        onDrawSurface = { drawRect(surfaceTint) },
    )
        .border(
            androidx.compose.foundation.BorderStroke(0.5.dp, scheme.onSurface.copy(alpha = 0.12f)),
            shape,
        )
}

/** 液态玻璃容器：关闭时退化为普通 Surface，两套视觉共用同一导航与功能。 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val glass = LocalLiquidGlass.current
    if (!glass.enabled || LocalGlassBackdrop.current == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = fallbackColor,
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

/** 旧接口兼容：无 backdrop 时退化为渐变高光模拟（对话框等无采样源场景）。 */
@Composable
fun Modifier.liquidGlassFallback(shape: Shape = RoundedCornerShape(24.dp)): Modifier {
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
        .border(androidx.compose.foundation.BorderStroke(0.5.dp, highlight), shape)
}

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
