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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ming.mchat.core.common.ThemeMode

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * MChat 主题：
 * - 跟随系统（默认）：Android 12+ 动态取色（壁纸色），以下回退默认 M3；
 * - 浅色 / 深色：固定配色；
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
            typography = Typography(),
            content = content,
        )
    }
}
