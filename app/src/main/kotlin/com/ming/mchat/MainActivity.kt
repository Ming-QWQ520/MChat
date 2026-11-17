package com.ming.mchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.ming.mchat.core.theme.MChatTheme
import com.ming.mchat.navigation.MChatNavHost
import com.ming.mchat.ui.AppBackground
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
            MChatTheme(
                themeMode = appearance.themeMode,
                liquidGlass = com.ming.mchat.core.theme.LiquidGlassParams(
                    enabled = appearance.liquidGlassEnabled,
                    intensity = appearance.liquidGlassIntensity,
                    reduceMotion = appearance.reduceMotion,
                ),
                fontScale = appearance.fontScale,
            ) {
                // 全局背景在根层级渲染：聊天 / 设置 / Provider 页面全部可见
                Box(Modifier.fillMaxSize()) {
                    AppBackground(appearance)
                    MChatNavHost()
                }
            }
        }
    }
}
