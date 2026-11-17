package com.ming.mchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ming.mchat.core.common.ChatBackground
import com.ming.mchat.core.data.datastore.AppearanceSettings
import java.io.File

/**
 * 全局聊天背景（根层级渲染，所有页面可见）：
 * - 纯色：主题 surface；
 * - 渐变：surface → primaryContainer 淡渐变；
 * - 图片：自定义图片（裁剪铺满 / 适应宽度二选一，应对 16:9 横图）
 *   + 可调高斯模糊 + 可调压暗遮罩 + 顶/底部渐变 scrim（保证状态栏与输入区可读）。
 *
 * 页面级 Scaffold 一律 containerColor = Transparent，由本层统一决定底色。
 */
@Composable
fun AppBackground(
    appearance: AppearanceSettings,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    when (appearance.chatBackground) {
        ChatBackground.SOLID -> {
            Box(modifier.fillMaxSize().background(scheme.surface))
        }

        ChatBackground.GRADIENT -> {
            Box(
                modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.surface,
                                scheme.primaryContainer.copy(alpha = 0.35f),
                                scheme.surface,
                            )
                        )
                    ),
            )
        }

        ChatBackground.IMAGE -> {
            val imageFile = File(appearance.backgroundImageUri)
            if (appearance.backgroundImageUri.isBlank() || !imageFile.exists()) {
                // 图片缺失时回退纯色，避免透明漏底
                Box(modifier.fillMaxSize().background(scheme.surface))
            } else {
                val scrim = if (isSystemInDarkTheme()) Color.Black else Color.White
                Box(
                    modifier
                        .fillMaxSize()
                        .background(scheme.surface),
                ) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = null,
                        contentScale = if (appearance.bgFillWidth) {
                            ContentScale.FillWidth
                        } else {
                            ContentScale.Crop
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (appearance.bgBlurDp > 0) {
                                    Modifier.blur(appearance.bgBlurDp.dp)
                                } else {
                                    Modifier
                                }
                            ),
                    )
                    // 全局压暗遮罩（跟随主题：深色压黑、浅色压白）
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(scheme.surface.copy(alpha = appearance.bgDim))
                    )
                    // 顶部 / 底部渐变 scrim：状态栏与输入区可读性
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(
                                    Brush.verticalGradient(
                                        colors = listOf(scrim.copy(alpha = 0.26f), Color.Transparent),
                                        startY = 0f,
                                        endY = size.height * 0.10f,
                                    ),
                                )
                                drawRect(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, scrim.copy(alpha = 0.32f)),
                                        startY = size.height * 0.80f,
                                        endY = size.height,
                                    ),
                                )
                            },
                    )
                }
            }
        }
    }
}
