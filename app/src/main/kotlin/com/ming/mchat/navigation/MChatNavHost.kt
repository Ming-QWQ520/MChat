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

/** 全局导航：聊天（起点）→ 设置 → Provider 详情。 */
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
        composable(
            route = "provider/{providerId}",
            arguments = listOf(navArgument("providerId") { type = NavType.StringType }),
        ) {
            ProviderEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
