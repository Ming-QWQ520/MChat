package com.ming.mchat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ming.mchat.core.theme.MotionCurves
import com.ming.mchat.feature.chat.ChatScreen
import com.ming.mchat.feature.settings.ProviderEditorScreen
import com.ming.mchat.feature.settings.SettingsScreen

/** 进入动画：从右侧滑入 + 淡入（EaseOutCubic 收尾，快出慢停）。 */
private fun enterAnim(): EnterTransition = slideInHorizontally(
    animationSpec = tween(320, easing = MotionCurves.EaseOutCubic),
) { it } + fadeIn(tween(220))

/** 退出动画：向右滑出 + 淡出（EaseInCubic 加速离开）。 */
private fun exitAnim(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(280, easing = MotionCurves.EaseInCubic),
) { it } + fadeOut(tween(200))

/** 底层页面淡出 / 淡入（不位移，避免抽屉式层级跳变）。 */
private fun underlyingFadeIn(): EnterTransition = fadeIn(tween(220))
private fun underlyingFadeOut(): ExitTransition = fadeOut(tween(180))

/** 全局导航：聊天（起点）→ 设置 → 供应商新建 / 编辑；设置层级统一左滑进 / 右滑出。 */
@Composable
fun MChatNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "chat",
    ) {
        composable(
            route = "chat",
            enterTransition = { underlyingFadeIn() },
            exitTransition = { underlyingFadeOut() },
            popEnterTransition = { underlyingFadeIn() },
            popExitTransition = { underlyingFadeOut() },
        ) {
            ChatScreen(
                onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } },
            )
        }
        composable(
            route = "settings",
            enterTransition = { enterAnim() },
            exitTransition = { underlyingFadeOut() },
            popEnterTransition = { underlyingFadeIn() },
            popExitTransition = { exitAnim() },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenProvider = { providerId -> navController.navigate("provider/$providerId") { launchSingleTop = true } },
            )
        }
        // 新建供应商：路由需注册在 "provider/{providerId}" 之前，精确匹配优先
        composable(
            route = "provider/new",
            enterTransition = { enterAnim() },
            exitTransition = { underlyingFadeOut() },
            popEnterTransition = { underlyingFadeIn() },
            popExitTransition = { exitAnim() },
        ) {
            ProviderEditorScreen(
                onBack = { navController.popBackStack() },
                onOpenProvider = { providerId ->
                    navController.navigate("provider/$providerId") {
                        popUpTo("provider/new") { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "provider/{providerId}",
            arguments = listOf(navArgument("providerId") { type = NavType.StringType }),
            enterTransition = { enterAnim() },
            exitTransition = { underlyingFadeOut() },
            popEnterTransition = { underlyingFadeIn() },
            popExitTransition = { exitAnim() },
        ) {
            ProviderEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
