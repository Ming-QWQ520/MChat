package com.ming.mchat.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ming.mchat.core.common.TimeUtils
import com.ming.mchat.core.data.entity.AttachmentEntity
import com.ming.mchat.core.data.entity.AttachmentKind
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.entity.UsageSource
import com.ming.mchat.core.data.datastore.BubbleStyle
import com.ming.mchat.core.theme.AnchoredContextMenu
import com.ming.mchat.core.theme.AnchoredMenuItem
import com.ming.mchat.core.theme.MotionCurves
import com.ming.mchat.core.theme.MotionDurations
import com.ming.mchat.core.theme.appSurfaces
import com.ming.mchat.core.theme.longPressAnchor
import com.ming.mchat.feature.chat.ChatUiState
import java.io.File
import kotlinx.coroutines.launch

/**
 * 消息列表（kelivo 风格）：
 * - 用户消息：右侧气泡（最大 75% 屏宽），磨砂 / 实底两种风格，附件图片缩略图 / 文件 chip 带大小；
 * - 助手消息：左侧气泡 + 思考折叠卡（animateContentSize 展开 / 收起）；
 * - 长按消息：以长按点为中心展开的锚点菜单（自适应上下方向）；
 * - 用户消息支持“编辑后重发”；最后一条助手消息支持“重新生成”；
 * - 图片/文件点击预览（图片内建查看器，文件走系统查看器）；
 * - 图片背景时气泡透明度可调（设置项），排队插话消息带标记。
 */
@Composable
fun MessageList(
    state: ChatUiState,
    listState: LazyListState,
    onCopy: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: () -> Unit,
    onEditResend: (Long, String) -> Unit,
    onOpenFile: (AttachmentEntity) -> Unit,
) {
    // 长按操作目标（锚点菜单 / 原格式抽屉 / 选择文本 / 编辑对话框）
    var actionTarget by rememberSaveable(state.conversationId) { mutableStateOf<Long?>(null) }
    var actionPressOffset by remember { mutableStateOf(Offset.Zero) }
    var rawFormatTarget by rememberSaveable(state.conversationId) { mutableStateOf<Long?>(null) }
    var selectTarget by rememberSaveable(state.conversationId) { mutableStateOf<Long?>(null) }
    var editTarget by rememberSaveable(state.conversationId) { mutableStateOf<Long?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }

    val messages = state.displayMessages
    val actionMessage = messages.firstOrNull { it.id == actionTarget }
    val rawMessage = messages.firstOrNull { it.id == rawFormatTarget }
    val selectMessage = messages.firstOrNull { it.id == selectTarget }
    val editMessage = messages.firstOrNull { it.id == editTarget }
    val lastAssistantId = messages.lastOrNull { it.role == Role.ASSISTANT }?.id

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages.size, key = { messages[it].id }) { index ->
            val message = messages[index]
            MessageBubble(
                message = message,
                isLastAssistant = message.id == lastAssistantId,
                collapseReasoning = state.collapseReasoning,
                bubbleStyle = state.bubbleStyle,
                showTimestamp = state.showTimestamp,
                userName = state.userName,
                attachments = state.attachmentsByMessage[message.id].orEmpty(),
                isQueued = message.id in state.queuedMessageIds,
                imageBackgroundActive = state.imageBackgroundActive,
                bubbleAlpha = state.bubbleAlpha,
                onLongPress = { offset ->
                    actionTarget = message.id
                    actionPressOffset = offset
                },
                onOpenImage = { path -> viewingImage = path },
                onOpenFile = onOpenFile,
                onCopy = onCopy,
                onRegenerate = onRegenerate,
            )
        }
        if (state.isStreaming) {
            item(key = "streaming") {
                StreamingBubble(state)
            }
        }
        item(key = "bottom_spacer") { Spacer(Modifier.height(4.dp)) }
    }

    viewingImage?.let { path ->
        ImageViewerDialog(
            imagePath = path,
            onDismiss = { viewingImage = null },
        )
    }

    // 长按锚点菜单：以长按点为中心，自适应上下展开
    actionMessage?.let { message ->
        MessageActionsMenu(
            message = message,
            isLastAssistant = message.id == lastAssistantId,
            pressOffset = actionPressOffset,
            onCopy = { onCopy(message.content) },
            onSelectText = { selectTarget = message.id },
            onShowRaw = { rawFormatTarget = message.id },
            onEdit = { editTarget = message.id },
            onRegenerate = onRegenerate,
            onDelete = { onDelete(message.id) },
            onDismiss = { actionTarget = null },
        )
    }

    // 查看原格式：底部抽屉，原始 Markdown 源码
    rawMessage?.let { message ->
        RawMarkdownSheet(
            message = message,
            onCopy = { onCopy(message.content) },
            onDismiss = { rawFormatTarget = null },
        )
    }

    // 选择文本：格式化后文本的全选视图
    selectMessage?.let { message ->
        SelectTextSheet(
            message = message,
            onDismiss = { selectTarget = null },
        )
    }

    // 编辑后重发（仅用户消息）
    editMessage?.let { message ->
        if (message.role == Role.USER) {
            EditMessageDialog(
                initial = message.content,
                onDismiss = { editTarget = null },
                onConfirm = { newText ->
                    editTarget = null
                    onEditResend(message.id, newText)
                },
            )
        }
    }
}

/** 气泡容器配色：kelivo 默认（用户主题色浅罩 / 助手无气泡）/ 磨砂 / 实底；图片背景时用可调不透明度保证可读。 */
@Composable
private fun bubbleContainer(
    bubbleStyle: BubbleStyle,
    isUser: Boolean,
    imageBackgroundActive: Boolean = false,
    bubbleAlpha: Float = 0.92f,
): androidx.compose.ui.graphics.Color {
    val scheme = MaterialTheme.colorScheme
    if (imageBackgroundActive) {
        // 图片背景：用户/助手均用高不透明度衬底（设置可调），避免裸字压图不可读
        return if (isUser) scheme.primaryContainer.copy(alpha = bubbleAlpha)
        else scheme.surface.copy(alpha = (bubbleAlpha * 0.95f).coerceIn(0.3f, 1f))
    }
    return when (bubbleStyle) {
        BubbleStyle.NONE -> {
            // kelivo 默认：用户 = primary 浅罩（浅色 8% / 深色 15%），助手无气泡
            if (isUser) scheme.primary.copy(alpha = if (scheme.surface.luminance() < 0.5f) 0.15f else 0.08f)
            else androidx.compose.ui.graphics.Color.Transparent
        }
        BubbleStyle.FROSTED -> {
            val dark = scheme.surface.luminance() < 0.5f
            if (isUser) scheme.primaryContainer.copy(alpha = 0.78f)
            else scheme.surface.copy(alpha = if (dark) 0.72f else 0.86f)
        }
        BubbleStyle.SOLID -> if (isUser) scheme.primaryContainer else MaterialTheme.appSurfaces.card
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isLastAssistant: Boolean,
    collapseReasoning: Boolean,
    bubbleStyle: BubbleStyle,
    showTimestamp: Boolean,
    userName: String,
    attachments: List<AttachmentEntity>,
    isQueued: Boolean,
    imageBackgroundActive: Boolean,
    bubbleAlpha: Float,
    onLongPress: (Offset) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenFile: (AttachmentEntity) -> Unit,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
) {
    val isUser = message.role == Role.USER
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.75f).dp
    val surfaces = MaterialTheme.appSurfaces
    // kelivo 默认：助手消息无气泡直接铺满（图片背景时仍用衬底保证可读）
    val assistantBare = bubbleStyle == BubbleStyle.NONE && !imageBackgroundActive

    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (isUser) {
                // 用户头部：用户名 / 时间戳（气泡外）
                if (showTimestamp) {
                    Text(
                        buildString {
                            if (userName.isNotBlank()) append(userName); append("  ")
                            append(TimeUtils.formatMessageTime(message.createdAt))
                        }.trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else if (showTimestamp) {
                // 助手头部（kelivo）：模型图标 + 模型名 + 时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        modifier = Modifier.size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        buildString {
                            append(message.model?.substringAfterLast('/')?.take(18).orEmpty().ifBlank { "AI" })
                            append("  ·  ")
                            append(TimeUtils.formatMessageTime(message.createdAt))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }

            if (isUser || !assistantBare) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bubbleContainer(bubbleStyle, isUser, imageBackgroundActive, bubbleAlpha),
                    border = if (bubbleStyle == BubbleStyle.FROSTED || imageBackgroundActive) {
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    } else {
                        null
                    },
                    modifier = Modifier
                        .widthIn(max = if (isUser) maxBubbleWidth else androidx.compose.ui.unit.Dp.Unspecified)
                        .longPressAnchor(onLongPress),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        MessageBody(
                            message = message,
                            isUser = isUser,
                            attachments = attachments,
                            isQueued = isQueued,
                            collapseReasoning = collapseReasoning,
                            onOpenImage = onOpenImage,
                            onOpenFile = onOpenFile,
                        )
                    }
                }
            } else {
                // 助手无气泡：直接铺满，与两侧留 8dp 呼吸空间
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .longPressAnchor(onLongPress),
                ) {
                    MessageBody(
                        message = message,
                        isUser = false,
                        attachments = attachments,
                        isQueued = isQueued,
                        collapseReasoning = collapseReasoning,
                        onOpenImage = onOpenImage,
                        onOpenFile = onOpenFile,
                    )
                }
            }
            if (!isUser) {
                // 助手尾部：完成后操作栏（复制 / 重新生成，仅最后一条）+ Token 明细（kelivo OverflowBar）
                if (isLastAssistant && message.content.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                    ) {
                        androidx.compose.material3.IconButton(
                            onClick = { onCopy(message.content) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "复制",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "重新生成",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                message.totalTokens?.let { total ->
                    Text(
                        text = buildString {
                            append("输入 ${message.promptTokens ?: 0} · 输出 ${message.completionTokens ?: 0} · 共 $total tokens")
                            if (message.usageSource == UsageSource.ESTIMATED) append("（≈ 估算）")
                            message.latencyMs?.let { append(" · %.1fs".format(it / 1000.0)) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

/** 消息正文（气泡内 / 无气泡铺满共用）：附件 / 排队标记 / 思考卡片 / Markdown。 */
@Composable
private fun MessageBody(
    message: MessageEntity,
    isUser: Boolean,
    attachments: List<AttachmentEntity>,
    isQueued: Boolean,
    collapseReasoning: Boolean,
    onOpenImage: (String) -> Unit,
    onOpenFile: (AttachmentEntity) -> Unit,
) {
    if (isUser) {
        // 附件：图片缩略图（可预览）/ 文件 chip（带大小，系统查看器打开）
        MessageAttachments(
            attachments = attachments,
            onOpenImage = onOpenImage,
            onOpenFile = onOpenFile,
        )
        if (message.content.isNotBlank()) {
            SelectionContainer {
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (isQueued) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    Icons.Filled.HourglassTop,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "已排队 · 本轮回复后自动发送",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        // 思考过程（历史消息）：折叠卡片
        message.reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
            ReasoningCard(
                reasoning = reasoning,
                streaming = false,
                defaultCollapsed = collapseReasoning,
            )
            Spacer(Modifier.height(8.dp))
        }
        MarkdownText(text = message.content)
    }
}

/** 文件大小格式化：B / KB / MB / GB。 */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * 消息内附件渲染：
 * - 图片：一行多张圆角缩略图（最大 96dp），点击全屏预览；
 * - 文件：图标 + 名称 + 大小 chip，点击系统查看器打开。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageAttachments(
    attachments: List<AttachmentEntity>,
    onOpenImage: (String) -> Unit,
    onOpenFile: (AttachmentEntity) -> Unit,
) {
    if (attachments.isEmpty()) return
    val images = attachments.filter { it.kind == AttachmentKind.IMAGE }
    val files = attachments.filter { it.kind != AttachmentKind.IMAGE }

    if (images.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (files.isEmpty()) 6.dp else 4.dp),
        ) {
            items(images, key = { it.id }) { image ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(image.localPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = image.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { onOpenImage(image.localPath) }),
                )
            }
        }
    }
    if (files.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        ) {
            files.forEach { file ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                    onClick = { onOpenFile(file) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.widthIn(max = 190.dp)) {
                            Text(
                                file.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatFileSize(file.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 全屏图片查看器：双指缩放 / 拖动 / 双击放大 / 单击关闭。
 */
@Composable
fun ImageViewerDialog(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(imagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
            Text(
                "双指缩放 · 单击关闭",
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )
        }
    }
}

/**
 * 思考过程折叠卡片（animateContentSize 展开 / 收起动画，MotionCurves）：
 * - 折叠态：标题行（正在思考… / 已深度思考）+ 灰色思考摘要（最多 2 行）；
 * - 展开态：完整思考内容；
 * - 点击标题行切换；defaultCollapsed=false（设置关闭折叠）时默认展开。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReasoningCard(
    reasoning: String,
    streaming: Boolean,
    defaultCollapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    // 不以内容为 key：流式期间增量更新不会重置用户的展开/收起操作
    var expanded by rememberSaveable { mutableStateOf(!defaultCollapsed) }
    Surface(
        color = MaterialTheme.appSurfaces.surfaceFill,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(MotionDurations.CONTENT, easing = MotionCurves.EaseOutCubic),
            ),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { expanded = !expanded })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (streaming) "正在思考…" else "已深度思考",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起思考" else "展开思考",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                SelectionContainer {
                    Text(
                        reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 10.dp),
                    )
                }
            } else if (reasoning.isNotBlank()) {
                // 折叠时仍可见思考内容摘要
                Text(
                    reasoning.replace(Regex("\\s+"), " ").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                )
            }
        }
    }
}

/**
 * 长按消息锚点菜单：以长按点为中心展开，自适应上下方向。
 * 菜单项：复制 / 选择文本 / 查看原格式 / 编辑重发（用户消息）/ 重新生成（最后一条助手）/ 删除。
 */
@Composable
fun MessageActionsMenu(
    message: MessageEntity,
    isLastAssistant: Boolean,
    pressOffset: Offset,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onShowRaw: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnchoredContextMenu(
        pressRootOffset = pressOffset,
        onDismiss = onDismiss,
    ) {
        Text(
            "${TimeUtils.formatMessageTime(message.createdAt)} · ${message.model ?: if (message.role == Role.USER) "你" else "MChat"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        )
        AnchoredMenuItem(Icons.Filled.ContentCopy, "复制") { onDismiss(); onCopy() }
        AnchoredMenuItem(Icons.AutoMirrored.Filled.Notes, "选择文本") { onDismiss(); onSelectText() }
        AnchoredMenuItem(Icons.Filled.Visibility, "查看原格式") { onDismiss(); onShowRaw() }
        if (message.role == Role.USER) {
            AnchoredMenuItem(Icons.Filled.Edit, "编辑后重发") { onDismiss(); onEdit() }
        }
        if (message.role != Role.USER && isLastAssistant) {
            AnchoredMenuItem(Icons.Filled.Refresh, "重新生成") { onDismiss(); onRegenerate() }
        }
        AnchoredMenuItem(Icons.Filled.Delete, "删除", danger = true) { onDismiss(); onDelete() }
        Spacer(Modifier.height(6.dp))
    }
}

/** 编辑后重发对话框：修改用户消息内容并重新提问。 */
@Composable
fun EditMessageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑后重发") },
        text = {
            Column {
                Text(
                    "保存后会删除这条消息之后的所有回复，并基于新内容重新提问。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("重发")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 查看原格式：底部抽屉显示原始 Markdown 源码（等宽 + 水平滚动 + 复制）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawMarkdownSheet(
    message: MessageEntity,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "原始 Markdown",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onCopy,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SelectionContainer {
            Text(
                message.content,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

/** 选择文本：底部弹层内直接选择格式化后的全文。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectTextSheet(
    message: MessageEntity,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "选择文本",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "长按并拖动选柄以选择文本",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SelectionContainer {
            Text(
                com.ming.mchat.core.common.MarkdownStripper.toPlainText(message.content),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

/** 流式中的助手消息：思考折叠卡 + 打字光标 + ≈ token（kelivo 默认风格下无气泡铺满）。 */
@Composable
fun StreamingBubble(state: ChatUiState) {
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.75f).dp
    val bare = state.bubbleStyle == BubbleStyle.NONE && !state.imageBackgroundActive

    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        if (state.streamingReasoning.isNotBlank()) {
            ReasoningCard(
                reasoning = state.streamingReasoning,
                streaming = state.streamingContent.isBlank(),
                defaultCollapsed = state.collapseReasoning,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (state.streamingContent.isNotBlank()) {
            MarkdownText(text = state.streamingContent + " ▍")
        } else if (state.streamingReasoning.isBlank()) {
            Text(
                "正在输入…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "≈ ${state.streamingEstimatedTokens} tokens · 生成中",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (bare) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            content = body,
        )
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleContainer(state.bubbleStyle, isUser = false, state.imageBackgroundActive, state.bubbleAlpha),
            border = if (state.bubbleStyle == BubbleStyle.FROSTED) {
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            } else {
                null
            },
            modifier = Modifier.widthIn(max = maxBubbleWidth),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), content = body)
        }
    }
}

/**
 * 滚动导航按钮（kelivo ScrollNavigationButtons）：
 * - 回到底部（不在底部时显示）；
 * - 跳转到上一条提问；
 * 玻璃圆钮，220ms EaseOutCubic 缩放 + 淡入淡出。
 */
@Composable
fun ScrollNavButtons(
    listState: LazyListState,
    atBottom: Boolean,
    hasMessages: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isUserIndex: (Int) -> Boolean = { false },
) {
    if (!enabled || !hasMessages) return
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f

    // 上一条提问的位置：从当前首个可见项向上找最近一条 USER 消息
    val listState0 = listState
    fun jumpToPreviousQuestion() {
        val info = listState0.layoutInfo
        val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: return
        val target = (firstVisible - 1).downTo(0).firstOrNull { idx -> isUserIndex(idx) } ?: return
        scope.launch { listState0.animateScrollToItem(target) }
    }

    Column(
        modifier.padding(end = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassCircleButton(
            visible = !atBottom,
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = "上一条提问",
            dark = dark,
        ) { jumpToPreviousQuestion() }
        GlassCircleButton(
            visible = !atBottom,
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "回到底部",
            dark = dark,
        ) {
            scope.launch {
                val total = listState0.layoutInfo.totalItemsCount
                if (total > 0) listState0.animateScrollToItem(total - 1)
            }
        }
    }
}

/** 玻璃圆钮（kelivo _ScrollButton）：BackdropFilter 不可用于列表项，使用半透明 + 描边近似。 */
@Composable
private fun GlassCircleButton(
    visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = tween(MotionDurations.TACTILE, easing = MotionCurves.EaseOutCubic),
        label = "navScale",
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(MotionDurations.TACTILE, easing = MotionCurves.EaseOutCubic),
        label = "navAlpha",
    )
    Box(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
    ) {
        Surface(
            onClick = onClick,
            enabled = visible,
            shape = CircleShape,
            color = if (dark) scheme.surface.copy(alpha = 0.55f) else scheme.surface.copy(alpha = 0.65f),
            border = BorderStroke(
                0.5.dp,
                if (dark) scheme.onSurface.copy(alpha = 0.12f) else scheme.outline.copy(alpha = 0.2f),
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.onSurface.copy(alpha = 0.87f),
                )
            }
        }
    }
}
