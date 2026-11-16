package com.ming.mchat.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
 * MChat 配色（对齐 kelivo 默认调色板）：
 * - 主色 #4D5C92（muted blue-violet），浅色页面 #F7F7F7，深色页面 #121213；
 * - 全 app 只允许通过 MaterialTheme.colorScheme / MaterialTheme.appSurfaces 语义 token 取色。
 */

/** kelivo default 调色板（浅色）。 */
private val KelivoLight = lightColorScheme(
    primary = Color(0xFF4D5C92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF03174B),
    secondary = Color(0xFF595D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF161B2C),
    tertiary = Color(0xFF75546F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F6),
    onTertiaryContainer = Color(0xFF2C122A),
    error = Color(0xFFBB0947),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDDADE),
    onErrorContainer = Color(0xFF400013),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF202020),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF202020),
    surfaceVariant = Color(0xFFE4E4E4),
    onSurfaceVariant = Color(0xFF646464),
    surfaceTint = Color(0xFF4D5C92),
    inverseSurface = Color(0xFF121213),
    inverseOnSurface = Color(0xFFF9F9F9),
    inversePrimary = Color(0xFF4D5C92),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFFC6C6C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDF9F8),
    surfaceDim = Color(0xFFDED8DB),
    surfaceContainer = Color(0xFFF0F0F1),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE4E4E6),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

/** kelivo default 调色板（深色）。 */
private val KelivoDark = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF1D2D61),
    primaryContainer = Color(0xFF354479),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFC2C5DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFFE3BADA),
    onTertiary = Color(0xFF432740),
    tertiaryContainer = Color(0xFF5B3D57),
    onTertiaryContainer = Color(0xFFFFD7F6),
    error = Color(0xFFFCB4BD),
    onError = Color(0xFF670023),
    errorContainer = Color(0xFF910034),
    onErrorContainer = Color(0xFFFCB4BD),
    background = Color(0xFF121213),
    onBackground = Color(0xFFF9F9F9),
    surface = Color(0xFF121213),
    onSurface = Color(0xFFF9F9F9),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFCECECE),
    surfaceTint = Color(0xFFB6C4FF),
    inverseSurface = Color(0xFFF7F7F7),
    inverseOnSurface = Color(0xFF202020),
    inversePrimary = Color(0xFFB6C4FF),
    outline = Color(0xFF8E8E8E),
    outlineVariant = Color(0xFF474747),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF35363C),
    surfaceDim = Color(0xFF121213),
    surfaceContainer = Color(0xFF1C1C1D),
    surfaceContainerHigh = Color(0xFF252527),
    surfaceContainerHighest = Color(0xFF2F2F31),
    surfaceContainerLow = Color(0xFF1D1E20),
    surfaceContainerLowest = Color(0xFF0A0B0E),
)

/**
 * MChat 主题：
 * - 跟随系统（默认）：Android 12+ 动态取色（壁纸色），以下回退 kelivo 默认调色板；
 * - 浅色 / 深色：固定 kelivo 调色板；
 * - 动态取色：仅 Android 12+ 可选，强制动态色（明暗仍随系统）；
 * - 字体大小设置通过 LocalDensity fontScale 生效；
 * - 所有页面统一读取语义色 token（colorScheme + appSurfaces），不允许硬编码颜色。
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
    val colorScheme: ColorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> KelivoDark
        else -> KelivoLight
    }
    val surfaces = rememberAppSurfaces()

    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * fontScale.coerceIn(0.85f, 1.3f))

    CompositionLocalProvider(
        LocalLiquidGlass provides liquidGlass,
        LocalAppSurfaces provides surfaces,
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
 * 排版（kelivo 风格）：
 * - 聊天正文 bodyMedium 16sp / 行高 1.65；
 * - 标题字重收敛为 Medium/SemiBold；Android 上 w500 渲染为 w400（kelivo normalize 策略），
 *   这里直接用 Medium/SemiBold 由系统 Fallback 处理。
 */
private val MChatTypography = Typography(
    headlineSmall = TextStyle(fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.4.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)
