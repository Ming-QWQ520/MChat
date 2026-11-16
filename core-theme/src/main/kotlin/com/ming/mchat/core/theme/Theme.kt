package com.ming.mchat.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.ming.mchat.core.common.ThemeMode

/**
 * MChat 品牌配色（借鉴 DeepSeek 手机端的三色体系：背景 / 内容 / 品牌蓝）。
 * 品牌主色 #4D6BFE，浅色背景 #F7F7F8，深色背景 #0F1014。
 * 全 app 只允许通过 MaterialTheme.colorScheme 语义 token 取色，禁止硬编码。
 */

// ---- 品牌蓝（DeepSeek Blue，hue ≈ 228°）----
private val BrandBlue = Color(0xFF4D6BFE)
private val BrandBlueDim = Color(0xFF1E3080) // 深色模式 onPrimary / 浅色 primaryContainer 文字
private val BrandContainerLight = Color(0xFFE3E9FF) // 浅色 primaryContainer（用户气泡）
private val BrandContainerDark = Color(0xFF2B3668) // 深色 primaryContainer（用户气泡）

/** 浅色主题：白灰背景 + 品牌蓝强调，整体克制无杂色。 */
private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BrandContainerLight,
    onPrimaryContainer = Color(0xFF10275F),
    inversePrimary = BrandContainerDark,
    secondary = Color(0xFF595E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF161B2C),
    tertiary = Color(0xFF4C5FA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBE2FF),
    onTertiaryContainer = Color(0xFF00174B),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceTint = BrandBlue,
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDF9F8),
    surfaceDim = Color(0xFFDED8DB),
    surfaceContainer = Color(0xFFF0F1F5),
    surfaceContainerHigh = Color(0xFFEAEBF0),
    surfaceContainerHighest = Color(0xFFE4E5EB),
    surfaceContainerLow = Color(0xFFFFFFFF), // 输入框容器：纯白卡片
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

/** 深色主题：近黑背景（DeepSeek 深色端 #0D~#1E 区间）+ 提亮品牌蓝。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = BrandBlueDim,
    primaryContainer = BrandContainerDark,
    onPrimaryContainer = Color(0xFFDEE3FF),
    inversePrimary = BrandBlue,
    secondary = Color(0xFFC3C5D4),
    onSecondary = Color(0xFF2D3040),
    secondaryContainer = Color(0xFF434656),
    onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFFB5C4FF),
    onTertiary = Color(0xFF1C2E7A),
    tertiaryContainer = Color(0xFF334591),
    onTertiaryContainer = Color(0xFFDBE2FF),
    background = Color(0xFF0F1014), // DeepSeek 深色端近黑背景
    onBackground = Color(0xFFE2E2E8),
    surface = Color(0xFF0F1014),
    onSurface = Color(0xFFE2E2E8),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB8C4FF),
    inverseSurface = Color(0xFFE2E2E8),
    inverseOnSurface = Color(0xFF2E3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF35363C),
    surfaceDim = Color(0xFF0F1014),
    surfaceContainer = Color(0xFF191A1F),
    surfaceContainerHigh = Color(0xFF23242A),
    surfaceContainerHighest = Color(0xFF2E2F35),
    surfaceContainerLow = Color(0xFF1D1E24), // DeepSeek 输入容器 #1E1E20 区间
    surfaceContainerLowest = Color(0xFF0A0B0E),
)

/**
 * MChat 主题：
 * - 跟随系统（默认）：Android 12+ 动态取色（壁纸色），以下回退 MChat 品牌配色；
 * - 浅色 / 深色：固定品牌配色；
 * - 动态取色：仅 Android 12+ 可选，强制动态色（明暗仍随系统）；
 * - 字体大小设置通过 LocalDensity fontScale 生效；
 * - 所有页面统一读取语义色 token，不允许硬编码颜色。
 */
@Composable
fun MChatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    liquidGlass: LiquidGlassParams = LiquidGlassParams(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM, ThemeMode.DYNAMIC -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = when (themeMode) {
        ThemeMode.SYSTEM, ThemeMode.DYNAMIC -> dynamicAvailable
        else -> false
    }
    val context = LocalContext.current
    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * fontScale.coerceIn(0.85f, 1.3f))

    CompositionLocalProvider(
        LocalLiquidGlass provides liquidGlass,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MChatTypography,
            content = content,
        )
    }
}

/**
 * 排版（DeepSeek 风格）：
 * - 聊天正文 bodyMedium 16sp / 行高 1.65（VLM 建议 1.7 行高，长文阅读更舒适）；
 * - 标题字重收敛为 Medium/SemiBold，避免过粗。
 */
private val MChatTypography = Typography(
    headlineSmall = TextStyle(fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.4.sp, letterSpacing = 0.25.sp), // 1.65 行高
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)
