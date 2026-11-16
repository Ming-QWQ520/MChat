package com.ming.mchat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ming.mchat.feature.chat.ChatScreen
import com.ming.mchat.feature.settings.ProviderEditorScreen
import com.ming.mchat.feature.settings.SettingsScreen

/** 全局导航：聊天（起点）→ 设置 → 供应商新建 / 编辑。 */
@Composable
fun MChatNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "chat",
    ) {
        composable("chat") {
            ChatScreen(
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenProvider = { providerId -> navController.navigate("provider/$providerId") },
            )
        }
        // 新建供应商：路由需注册在 "provider/{providerId}" 之前，精确匹配优先
        composable("provider/new") {
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
        ) {
            ProviderEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
