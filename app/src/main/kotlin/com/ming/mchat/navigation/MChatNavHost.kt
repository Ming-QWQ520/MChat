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

/** 设置子页返回主页：主页从左侧 1/3 位移滑回 + 淡入（iOS 后退现层）。 */
private fun settingsPopEnter(): EnterTransition = slideInHorizontally(
    animationSpec = tween(320, easing = MotionCurves.EaseOutCubic),
) { -it / 3 } + fadeIn(tween(220))

/** 底层页面淡出 / 淡入（不位移，避免抽屉式层级跳变）。 */
private fun underlyingFadeIn(): EnterTransition = fadeIn(tween(220))
private fun underlyingFadeOut(): ExitTransition = fadeOut(tween(180))

/**
 * 全局导航：聊天（起点）→ 设置（主页 / 子页统一路由）→ 供应商新建 / 编辑。
 * 设置子页路由化（对齐 kelivo Navigator 体系）：每个子页独立 NavBackStackEntry，
 * 状态完全隔离 —— 彻底杜绝“进入设置误跳子页”的状态残留 / 点击穿透问题；
 * 返回键天然逐层回退（子页 → 设置主页 → 聊天）。
 */
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
        // 设置主页与全部子页：settings?subpage=xxx；无参即主页
        composable(
            route = "settings?subpage={subpage}",
            arguments = listOf(
                navArgument("subpage") {
                    type = NavType.StringType
                    defaultValue = "home"
                },
            ),
            enterTransition = { enterAnim() },
            exitTransition = { underlyingFadeOut() },
            popEnterTransition = { settingsPopEnter() },
            popExitTransition = { exitAnim() },
        ) { backStackEntry ->
            val subpage = backStackEntry.arguments?.getString("subpage") ?: "home"
            SettingsScreen(
                subpage = subpage,
                onBack = { navController.popBackStack() },
                onOpenSubPage = { page ->
                    navController.navigate("settings?subpage=$page")
                },
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
