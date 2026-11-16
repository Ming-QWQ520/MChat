package com.ming.mchat.feature.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.common.TimeUtils
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.entity.UsageSource
import com.ming.mchat.feature.chat.ChatUiState

/**
 * 消息列表（DeepSeek 手机端风格）：
 * - 用户消息：右侧主题色气泡；
 * - 助手消息：扁平排版（无气泡背景）+ 圆形 MChat 徽标，token 明细为浅色脚注；
 * - 流式中的助手消息同样扁平排版。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageList(
    state: ChatUiState,
    listState: LazyListState,
    onCopy: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: () -> Unit,
    onCopyPlain: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(state.messages.size, key = { state.messages[it].id }) { index ->
            val message = state.messages[index]
            MessageBubble(
                message = message,
                isLastAssistant = message.id == state.messages.lastOrNull { it.role == Role.ASSISTANT }?.id,
                onCopy = onCopy,
                onDelete = onDelete,
                onRegenerate = onRegenerate,
                onCopyPlain = onCopyPlain,
                onShare = onShare,
            )
        }
        if (state.isStreaming) {
            item(key = "streaming") {
                StreamingBubble(state)
            }
        }
        item(key = "bottom_spacer") { Spacer(Modifier.height(4.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isLastAssistant: Boolean,
    onCopy: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: () -> Unit,
    onCopyPlain: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val isUser = message.role == Role.USER
    var menuExpanded by remember { mutableStateOf(false) }
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
                    .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
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
        MessageActionsMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            message = message,
            isLastAssistant = isLastAssistant,
            onCopy = onCopy,
            onCopyPlain = onCopyPlain,
            onShare = onShare,
            onDelete = onDelete,
            onRegenerate = onRegenerate,
        )
    }
}

/** 助手徽标：28dp 圆角方块 + M 字样（DeepSeek 鲸鱼 logo 的对应位置）。 */
@Composable
private fun AssistantAvatar() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "M",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 消息长按菜单（自定义）：按消息角色动态组装操作项。
 * - 头部信息行：时间 + 模型；
 * - 全部消息：复制 Markdown / 复制纯文本 / 分享 / 删除
 * - 仅最后一条助手消息：重新生成
 */
@Composable
fun MessageActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    message: MessageEntity,
    isLastAssistant: Boolean,
    onCopy: (String) -> Unit,
    onCopyPlain: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            "${TimeUtils.formatMessageTime(message.createdAt)} · ${message.model ?: if (message.role == Role.USER) "你" else "MChat"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        DropdownMenuItem(
            text = { Text("复制（Markdown）") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = { onDismiss(); onCopy(message.content) },
        )
        DropdownMenuItem(
            text = { Text("复制为纯文本") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
            onClick = { onDismiss(); onCopyPlain(message.content) },
        )
        DropdownMenuItem(
            text = { Text("分享") },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = { onDismiss(); onShare(message.content) },
        )
        if (message.role != Role.USER && isLastAssistant) {
            DropdownMenuItem(
                text = { Text("重新生成") },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                onClick = { onDismiss(); onRegenerate() },
            )
        }
        DropdownMenuItem(
            text = { Text("删除") },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = { onDismiss(); onDelete(message.id) },
        )
    }
}

/** 流式中的助手消息：扁平排版 + 思考过程可折叠 + 打字光标 + ≈ token。 */
@OptIn(ExperimentalFoundationApi::class)
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
                var reasoningExpanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(10.dp),
                        ),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { reasoningExpanded = !reasoningExpanded })
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已深度思考",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (reasoningExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reasoningExpanded) {
                        Text(
                            state.streamingReasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .padding(bottom = 8.dp),
                        )
                    }
                }
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
