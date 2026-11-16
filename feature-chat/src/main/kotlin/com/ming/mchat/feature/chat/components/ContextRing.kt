package com.ming.mchat.feature.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ming.mchat.core.common.StringExt
import com.ming.mchat.core.llm.BalanceState
import com.ming.mchat.core.theme.LiquidGlassSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 上下文环（输入框内，默认直径 30dp，可配置）：
 * - 环身 = 下一轮请求上下文占用 / contextWindow，随上下文增加而填充（圆形进度条）；
 * - 填充比例带平滑动画（400ms）；<80% 主题色，80~95% 橙色，>95% 红色；
 * - 环心：非 DeepSeek 显示占用百分比；DeepSeek 显示简短余额（>999 显示 1.2K）；
 * - 点击展开浮层：上下文拆分 + DeepSeek 余额明细。
 */
@Composable
fun ContextRing(
    usedTokens: Int,
    contextWindow: Int,
    estimated: Boolean,
    balance: BalanceState?,
    showBalance: Boolean,
    lowBalance: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 30.dp,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rawRatio = if (contextWindow > 0) {
        (usedTokens.toFloat() / contextWindow).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 平滑动画：上下文增长时环形进度渐进填充
    val ratio by animateFloatAsState(
        targetValue = rawRatio,
        animationSpec = tween(durationMillis = 400),
        label = "contextRingProgress",
    )
    val scheme = MaterialTheme.colorScheme
    val ringColor = when {
        ratio > 0.95f -> scheme.error
        ratio > 0.80f -> Color(0xFFFFA726)
        else -> scheme.primary
    }
    val trackColor = scheme.surfaceContainerHighest
    val warning = showBalance && lowBalance
    val strokeWidth = with(density) { (diameter * 0.11f).toPx() }
    val centerFontSize = with(density) { (diameter * 0.26f).toSp() }

    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            if (ratio > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    style = stroke,
                )
            }
            if (warning) {
                drawCircle(
                    color = scheme.error.copy(alpha = 0.35f),
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        val centerText = if (showBalance) {
            when {
                balance == null -> "--"
                balance.error != null && balance.updatedAt == null -> "--"
                else -> StringExt.shortenAmount(balance.totalBalance)
            }
        } else {
            (if (estimated) "≈" else "") + "${(rawRatio * 100).toInt()}%"
        }
        Text(
            text = centerText,
            fontSize = centerFontSize,
            lineHeight = centerFontSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (warning) scheme.error else scheme.onSurface,
        )
    }
}

/** 上下文环详情浮层：上下文 token 拆分 + 余额明细（仅 DeepSeek）。 */
@Composable
fun ContextRingDetailPopup(
    usedTokens: Int,
    contextWindow: Int,
    estimated: Boolean,
    balance: BalanceState?,
    showBalance: Boolean,
    onDismiss: () -> Unit,
) {
    LiquidGlassSurface(
        modifier = Modifier.padding(16.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("上下文用量", style = MaterialTheme.typography.titleSmall)
            Text(
                "${if (estimated) "≈ " else ""}$usedTokens / $contextWindow tokens（${(usedTokens * 100 / contextWindow.coerceAtLeast(1))}%）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (estimated) "服务端 usage 未返回，当前为本地估算" else "来自服务端 usage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (usedTokens > contextWindow * 95 / 100) {
                Text(
                    "上下文接近上限，建议新建会话或清理历史",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (showBalance) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("DeepSeek 余额", style = MaterialTheme.typography.titleSmall)
                if (balance?.error != null) {
                    Text(
                        balance.error ?: "查询失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (balance != null) {
                    if (!balance.isAvailable) {
                        Text(
                            "账户当前不可用",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("总额：${balance.totalBalance} ${balance.currency}", style = MaterialTheme.typography.bodySmall)
                    Text("赠送：${balance.grantedBalance} ${balance.currency}", style = MaterialTheme.typography.bodySmall)
                    Text("充值：${balance.toppedUpBalance} ${balance.currency}", style = MaterialTheme.typography.bodySmall)
                    balance.updatedAt?.let {
                        Text(
                            "更新于 " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text("--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
