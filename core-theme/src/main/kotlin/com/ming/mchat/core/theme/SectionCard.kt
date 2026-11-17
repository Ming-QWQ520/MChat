package com.ming.mchat.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 风格设置分组卡片（kelivo SectionCard）：
 * - surfaceCard 填充 + 发丝描边（hairline）+ 12dp 圆角；
 * - 液态玻璃开启时使用 LiquidGlassSurface。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaces = MaterialTheme.appSurfaces
    LiquidGlassSurface(modifier = modifier.fillMaxWidth(), shape = shape, fallbackColor = surfaces.card) {
        Column(
            Modifier.padding(vertical = 4.dp),
            content = content,
        )
    }
}

/** iOS 分组小节标题（13sp semibold，中性色，缩进与卡片左缘对齐 + 4dp）。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = modifier.padding(start = 12.dp, end = 12.dp, top = if (first) 2.dp else 12.dp, bottom = 6.dp),
    )
}

/** iOS 分隔线：缩进与图标槽对齐（36 + 12 + 12）。 */
@Composable
fun IosDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier.padding(start = 54.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
    )
}

/**
 * iOS 导航行（kelivo _iosNavRow）：
 * - 36dp 图标槽 + 12dp 间距 + 15sp Medium 标签 + 13sp 次级文本 + 16dp chevron；
 * - 按压：无涟漪，颜色 lerp 至 surface（220ms EaseOutCubic，AnimatedPressColor 风格）。
 */
@Composable
fun IosNavRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    detailText: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interactive = onClick != null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fgBase = if (danger) scheme.error else scheme.onSurface.copy(alpha = 0.9f)
    val fg = if (pressed) androidx.compose.ui.graphics.lerp(fgBase, scheme.surface, 0.45f) else fgBase
    val bgColor = if (pressed) {
        scheme.onSurface.copy(alpha = if (scheme.surface.luminanceApprox()) 0.05f else 0.06f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(bgColor)
            .let {
                if (interactive) {
                    it.clickable(interactionSource = interaction, indication = null, onClick = onClick!!)
                } else {
                    it
                }
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = fg)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (detailText != null) {
            Text(
                detailText,
                fontSize = 13.sp,
                color = scheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
        }
        trailing?.invoke()
        if (interactive) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = fg,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminanceApprox(): Boolean =
    ((red * 299) + (green * 587) + (blue * 114)) / 1000f > 0.5f
