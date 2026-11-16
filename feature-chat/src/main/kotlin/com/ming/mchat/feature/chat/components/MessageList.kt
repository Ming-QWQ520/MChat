package com.ming.mchat.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ming.mchat.core.common.TimeUtils
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.entity.UsageSource
import com.ming.mchat.feature.chat.ChatUiState

/**
 * 消息列表（DeepSeek 手机端风格）：
 * - 用户消息：右侧主题色气泡；
 * - 助手消息：扁平排版 + DeepSeek 徽标；思考过程折叠卡片（可点击展开）；
 * - 长按消息弹出底部操作菜单：复制 / 选择文本 / 查看原格式 / 重新生成 / 删除。
 */
@Composable
fun MessageList(
    state: ChatUiState,
    listState: LazyListState,
    onCopy: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: () -> Unit,
) {
    // 长按操作目标（底部菜单 / 原格式抽屉 / 选择文本对话框）
    var actionTarget by rememberSaveable(state.conversationId) { mutableStateOf<Long?>(null) }
    var rawFormatTarget by rememberSaveable(state.conversationId) { mutableStateOf<Long?>(null) }
    var selectTarget by rememberSaveable(state.conversationId) { mutableStateOf<Long?>(null) }

    val messages = state.messages
    val actionMessage = messages.firstOrNull { it.id == actionTarget }
    val rawMessage = messages.firstOrNull { it.id == rawFormatTarget }
    val selectMessage = messages.firstOrNull { it.id == selectTarget }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(messages.size, key = { messages[it].id }) { index ->
            val message = messages[index]
            MessageBubble(
                message = message,
                isLastAssistant = message.id == messages.lastOrNull { it.role == Role.ASSISTANT }?.id,
                collapseReasoning = state.collapseReasoning,
                onLongPress = { actionTarget = message.id },
            )
        }
        if (state.isStreaming) {
            item(key = "streaming") {
                StreamingBubble(state)
            }
        }
        item(key = "bottom_spacer") { Spacer(Modifier.height(4.dp)) }
    }

    // 长按操作菜单（从上到下：复制 / 选择文本 / 查看原格式 / 重新生成）
    actionMessage?.let { message ->
        MessageActionsSheet(
            message = message,
            isLastAssistant = message.id == messages.lastOrNull { it.role == Role.ASSISTANT }?.id,
            onCopy = { onCopy(message.content) },
            onSelectText = { selectTarget = message.id },
            onShowRaw = { rawFormatTarget = message.id },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isLastAssistant: Boolean,
    collapseReasoning: Boolean,
    onLongPress: () -> Unit,
) {
    val isUser = message.role == Role.USER
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (isUser) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                    )
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                SelectionContainer {
                    Text(message.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                AssistantAvatar()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
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
                    // Token 明细（估算标记 ≈）
                    message.totalTokens?.let { total ->
                        Text(
                            text = buildString {
                                append("输入 ${message.promptTokens ?: 0} · 输出 ${message.completionTokens ?: 0} · 共 $total tokens")
                                if (message.usageSource == UsageSource.ESTIMATED) append("（≈ 估算）")
                                append(" · ${message.model.orEmpty()}")
                                message.latencyMs?.let { append(" · %.1fs".format(it / 1000.0)) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** DeepSeek 徽标：圆形品牌蓝底 + 鲸鱼 logo。 */
@Composable
private fun AssistantAvatar() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    com.ming.mchat.core.theme.R.drawable.deepseek_logo,
                ),
                contentDescription = "DeepSeek",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 思考过程卡片（DeepSeek 手机端风格）：
 * - 折叠态：标题行（正在思考… / 已深度思考）+ 灰色思考摘要（最多 2 行）；
 * - 展开态：完整思考内容；
 * - 点击标题行切换；defaultCollapsed=false（设置关闭折叠）时默认展开。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReasoningCard(
    reasoning: String,
    streaming: Boolean,
    defaultCollapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(reasoning.hashCode()) { mutableStateOf(!defaultCollapsed) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
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
                // 折叠时仍可见思考内容摘要（DeepSeek 端样式）
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
 * 长按消息底部操作菜单：从上到下依次为 复制 / 选择文本 / 查看原格式 / 重新生成 / 删除。
 * - 复制：整段 Markdown 原文；
 * - 选择文本：选择格式化（去 Markdown 符号）后的文本；
 * - 查看原格式：底部抽屉展示原始 Markdown 源码；
 * - 重新生成：仅最后一条助手消息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: MessageEntity,
    isLastAssistant: Boolean,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onShowRaw: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "${TimeUtils.formatMessageTime(message.createdAt)} · ${message.model ?: if (message.role == Role.USER) "你" else "MChat"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text("复制") },
            supportingContent = { Text("复制整段 Markdown 原文") },
            leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onDismiss(); onCopy() },
        )
        ListItem(
            headlineContent = { Text("选择文本") },
            supportingContent = { Text("选择格式化后的可读文本") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onDismiss(); onSelectText() },
        )
        ListItem(
            headlineContent = { Text("查看原格式") },
            supportingContent = { Text("查看原始 Markdown 输出") },
            leadingContent = { Icon(Icons.Filled.Visibility, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onDismiss(); onShowRaw() },
        )
        if (message.role != Role.USER && isLastAssistant) {
            ListItem(
                headlineContent = { Text("重新生成") },
                supportingContent = { Text("基于上一条提问重新回答") },
                leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onDismiss(); onRegenerate() },
            )
        }
        ListItem(
            headlineContent = { Text("删除", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("删除这条消息") },
            leadingContent = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onDismiss(); onDelete() },
        )
        Spacer(Modifier.height(18.dp))
    }
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

/** 流式中的助手消息：思考折叠卡 + 打字光标 + ≈ token。 */
@Composable
fun StreamingBubble(state: ChatUiState) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        AssistantAvatar()
        Spacer(Modifier.width(10.dp))
        Column {
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
    }
}
