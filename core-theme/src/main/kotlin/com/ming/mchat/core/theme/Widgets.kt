package com.ming.mchat.core.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 长按锚点菜单（iOS / kelivo 风格）：
 * - 以长按点为水平中心展开；
 * - 自适应展开方向：长按点偏上（含中间区域）→ 向下展开；偏下 → 向上展开；
 *   若展开后溢出屏幕则切换方向，仍放不下时贴边；
 * - 展示动画：150ms EaseOutCubic 淡入 + 以按压侧为原点的轻微缩放（尊重“减少动画”）；
 * - 内容容器自带 animateContentSize，子区域（如会话列表）展开 / 收起有过渡动画。
 *
 * [pressRootOffset] 为长按点在根布局坐标系中的位置（根 Box 原点 = 窗口原点）。
 */
@Composable
fun AnchoredContextMenu(
    pressRootOffset: Offset,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    menuMaxWidth: Dp = 232.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val screenWidth = view.width
    val screenHeight = view.height
    val marginPx = with(density) { 12.dp.toPx() }
    val menuMaxWidthPx = with(density) { menuMaxWidth.toPx() }

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val menuHeightEstimatePx = with(density) { 4 * 44.dp.toPx() } // 估算高度（首次测量前使用）

    val measuredH = if (menuSize != IntSize.Zero) menuSize.height.toFloat() else menuHeightEstimatePx
    val measuredW = if (menuSize != IntSize.Zero) menuSize.width.toFloat() else menuMaxWidthPx

    // 方向决策：偏下（>55% 屏高）向上展开；偏上与中间默认向下；溢出时自动切换。
    val rawUp = pressRootOffset.y > screenHeight * 0.55f
    val fitsUp = pressRootOffset.y - measuredH > marginPx
    val fitsDown = pressRootOffset.y + measuredH < screenHeight - marginPx
    val expandUp = when {
        rawUp && fitsUp -> true
        rawUp && !fitsUp && fitsDown -> false
        !rawUp && !fitsDown && fitsUp -> true
        else -> false
    }

    // Popup 的 offset 相对父布局原点：先测量父布局在窗口中的原点（0 尺寸锚点，TopStart 对齐）
    var parentOrigin by remember { mutableStateOf(Offset.Zero) }
    var anchorReady by remember { mutableStateOf(false) }
    // 首帧 parentOrigin 未测量时 offset 可能为 0；入场动画从 0 渐入，校正发生在首帧后，无可见跳动
    val localX = (pressRootOffset.x - measuredW / 2f - parentOrigin.x)
        .coerceIn(marginPx - parentOrigin.x, (screenWidth - measuredW - marginPx - parentOrigin.x).coerceAtLeast(marginPx - parentOrigin.x))
    val localY = if (expandUp) pressRootOffset.y - measuredH - parentOrigin.y else pressRootOffset.y - parentOrigin.y
    val x = localX
    val y = localY

    // 入场动画：淡入 + 从按压侧轻微放大
    val reduced = rememberReducedMotion()
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduced) tween(1) else tween(MotionDurations.POPUP, easing = MotionCurves.EaseOutCubic),
        label = "anchoredMenu",
    )

    // 0 尺寸锚点：与 Popup 同一父布局，用于换算父布局原点（窗口坐标）
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(0.dp)
            .onGloballyPositioned {
                parentOrigin = it.positionInWindow()
                anchorReady = true
            },
    )
    Popup(
        onDismissRequest = onDismiss,
        offset = IntOffset(x.toInt(), y.toInt()),
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true),
    ) {
        Box(
            modifier.graphicsLayer {
                // 锚点未就绪（首帧）时保持完全透明，避免位置校正前闪现
                alpha = if (!anchorReady) 0f else if (reduced) 1f else (0.35f + 0.65f * progress)
                val scale = if (reduced) 1f else (0.92f + 0.08f * progress)
                scaleX = scale
                scaleY = scale
                transformOrigin = if (expandUp) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)
            },
        ) {
            MenuGlassCard(
                modifier = Modifier.onGloballyPositioned { menuSize = it.size },
            ) {
                Column(
                    Modifier
                        .widthIn(max = menuMaxWidth)
                        .animateContentSize(
                            animationSpec = tween(MotionDurations.CONTENT, easing = MotionCurves.EaseOutCubic),
                        ),
                    content = content,
                )
            }
        }
    }
}

/**
 * 菜单玻璃卡：真实 backdrop 采样在 Popup 独立窗口中不可用，
 * 使用半透明 + 发丝描边 + 软阴影的降级玻璃（与全屏玻璃观感一致）。
 */
@Composable
fun MenuGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    Box(
        modifier
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .background(
                Brush.verticalGradient(
                    if (dark) {
                        listOf(Color(0xF026262A), Color(0xEE1C1C1F))
                    } else {
                        listOf(Color(0xF2FFFFFF), Color(0xEEF3F3F5))
                    }
                ),
                shape,
            )
            .border(
                BorderStroke(0.5.dp, scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.10f)),
                shape,
            ),
    ) {
        content()
    }
}

/**
 * 菜单项（kelivo _MenuItem 风格）：44dp 高，18dp 图标 + 14.5sp 文本，danger 红色，
 * iOS 式按压（无涟漪，按压轻缩放）。onClick 为尾随 lambda。
 */
@Composable
fun AnchoredMenuItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (danger) scheme.error else scheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animatePressScale(pressed = pressed, targetScale = 0.98f)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 14.5.sp,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** iOS 式触感行（无涟漪）：按压轻缩放 + 返回按压状态。 */
@Composable
fun Modifier.tactileClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animatePressScale(pressed = pressed, targetScale = 0.97f)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * 长按手势锚点：回调返回“根坐标按压点”（配合 [AnchoredContextMenu]）。
 * 用法：Modifier.longPressAnchor { rootOffset -> ... }
 */
fun Modifier.longPressAnchor(onLongPress: (Offset) -> Unit): Modifier = composed {
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { rootPos = it.positionInRoot() }
        .pointerInput(onLongPress) {
            detectTapGestures(
                onLongPress = { localOffset -> onLongPress(rootPos + localOffset) },
            )
        }
}
