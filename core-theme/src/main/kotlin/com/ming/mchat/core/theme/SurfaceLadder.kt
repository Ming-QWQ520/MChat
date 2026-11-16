package com.ming.mchat.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * kelivo 风格表面阶梯：从 MaterialTheme.colorScheme 派生一组页面级语义色。
 * - page：页面底色（= colorScheme.surface）；
 * - card：卡片色（比 page 高一级）；
 * - surfaceFill：输入框 / 胶囊填充（onSurface 低透明叠色）；
 * - hairline / hairlineStrong：发丝分隔线（细边框用）；
 * 页面统一从 LocalAppSurfaces 取色，禁止散落硬编码。
 */
data class AppSurfaces(
    val page: Color,
    val card: Color,
    val surfaceFill: Color,
    val hairline: Color,
    val hairlineStrong: Color,
)

/** 半透明色 [overlay] 以 [alpha] 叠在 [base] 上（等价 Flutter Color.alphaBlend）。 */
private fun over(base: Color, overlay: Color, alpha: Float): Color =
    if (alpha >= 1f) overlay else lerp(base, overlay, alpha)

/** 依据 colorScheme 派生表面阶梯（kelivo SurfaceLadder 非分层模式）。 */
fun deriveAppSurfaces(scheme: androidx.compose.material3.ColorScheme): AppSurfaces {
    val dark = scheme.surface.luminance() < 0.5f
    val page = scheme.surface
    val card = if (dark) over(page, Color.White, 0.10f) else over(page, Color.White, 0.96f)
    val surfaceFill = over(page, scheme.onSurface, if (dark) 0.16f else 0.05f)
    return AppSurfaces(
        page = page,
        card = card,
        surfaceFill = surfaceFill,
        hairline = scheme.outlineVariant.copy(alpha = if (dark) 0.08f else 0.06f).compositeOver(page),
        hairlineStrong = scheme.outlineVariant.copy(alpha = if (dark) 0.26f else 0.38f).compositeOver(page),
    )
}

/** 全局语义表面 CompositionLocal（由 MChatTheme 提供）。 */
val LocalAppSurfaces = staticCompositionLocalOf {
    AppSurfaces(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

/** 便捷访问：与 MaterialTheme.colorScheme 同级的语义表面。 */
val MaterialTheme.appSurfaces: AppSurfaces
    @Composable get() = LocalAppSurfaces.current

/** 记忆化派生（非 Composable 场景直接用 deriveAppSurfaces）。 */
@Composable
fun rememberAppSurfaces(): AppSurfaces {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { deriveAppSurfaces(scheme) }
}
