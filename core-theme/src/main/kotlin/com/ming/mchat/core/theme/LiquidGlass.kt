package com.ming.mchat.core.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

val LocalLiquidGlass = compositionLocalOf { LiquidGlassParams() }

/**
 * 自研液态玻璃视觉层（MChat Glass v2，纯 Compose 绘制，零第三方库依赖）：
 *
 * 分四层手工绘制（drawBehind + 前置 clip 裁剪到形状）——
 *  1. 基底：语义色 surface 的纵向渐变半透明面板（强度决定不透明度）；
 *  2. 顶部高光带：模拟光线自上而下的镜面反射（上部 35% 渐隐）；
 *  3. 底部辉光：边缘环境光回溢，避免面板“贴死”在内容上；
 *  4. 顶部边缘 1dp 发丝高光线 + 外层 1dp 语义色描边。
 *
 * 文本颜色使用 onSurface，保证深浅色对比度（禁止纯白文字压浅色玻璃）。
 * 仅用于 TopAppBar、底部输入栏、抽屉、对话框、设置分组卡片等悬浮层。
 *
 * @param alpha 附加不透明度系数（0~1），图片背景下输入区/气泡可在设置中微调。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float = 1f,
): Modifier {
    val params = LocalLiquidGlass.current
    if (!params.enabled) return this
    val scheme = MaterialTheme.colorScheme
    val (baseAlpha, edgeAlpha, glowAlpha) = when (params.intensity) {
        GlassIntensity.LOW -> Triple(0.30f, 0.10f, 0.05f)
        GlassIntensity.MEDIUM -> Triple(0.44f, 0.17f, 0.08f)
        GlassIntensity.HIGH -> Triple(0.58f, 0.26f, 0.12f)
    }
    val base = baseAlpha * alpha
    val edge = edgeAlpha * alpha
    val glow = glowAlpha * alpha
    val borderColor = scheme.surface.copy(alpha = (edge + 0.06f * alpha).coerceAtMost(1f))

    return this
        .clip(shape)
        .drawBehind {
            // 前置 clip(shape) 已将以下绘制裁剪到玻璃形状内
            // 1. 基底渐变面板
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.surface.copy(alpha = (base + 0.10f).coerceAtMost(1f)),
                        scheme.surface.copy(alpha = base * 0.92f),
                        scheme.surface.copy(alpha = base),
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            // 2. 顶部镜面高光带（上 35% 渐隐）
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.10f * alpha).coerceAtMost(0.14f)),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.35f,
                ),
            )
            // 3. 底部环境光辉光
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        scheme.surface.copy(alpha = glow),
                    ),
                    startY = size.height * 0.68f,
                    endY = size.height,
                ),
            )
            // 4. 顶部边缘高光线（左强右弱的斜向渐变，1dp 发丝）
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.22f * alpha).coerceAtMost(0.26f)),
                        Color.Transparent,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.6f, 0f),
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, 1.dp.toPx()),
            )
        }
        // 5. 发丝描边：外层 1dp 语义色轮廓
        .border(androidx.compose.foundation.BorderStroke(1.dp, borderColor), shape)
}

/** 液态玻璃容器：关闭时退化为普通 Surface，两套视觉共用同一导航与功能。 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    alpha: Float = 1f,
    content: @Composable () -> Unit,
) {
    val glass = LocalLiquidGlass.current
    if (!glass.enabled) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = fallbackColor.copy(alpha = alpha),
            content = content,
        )
    } else {
        Box(modifier = modifier.liquidGlass(shape, alpha)) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
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
