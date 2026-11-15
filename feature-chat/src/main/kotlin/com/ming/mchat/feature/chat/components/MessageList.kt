package com.ming.mchat.feature.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.entity.UsageSource
import com.ming.mchat.feature.chat.ChatUiState

/** 消息列表（含流式中的气泡）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageList(
    state: ChatUiState,
    listState: LazyListState,
    onCopy: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.messages.size, key = { state.messages[it].id }) { index ->
            val message = state.messages[index]
            MessageBubble(
                message = message,
                isLastAssistant = message.id == state.messages.lastOrNull { it.role == Role.ASSISTANT }?.id,
                onCopy = onCopy,
                onDelete = onDelete,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isLastAssistant: Boolean,
    onCopy: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: () -> Unit,
) {
    val isUser = message.role == Role.USER
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = 330.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                )
                .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
                .padding(12.dp),
        ) {
            if (isUser) {
                SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyMedium) }
            } else {
                MarkdownText(text = message.content)
            }
            // Token 明细（估算标记 ≈）
            message.totalTokens?.let { total ->
                Spacer(Modifier.height(6.dp))
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
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    onClick = { menuExpanded = false; onCopy(message.content) },
                )
                if (!isUser && isLastAssistant) {
                    DropdownMenuItem(
                        text = { Text("重新生成") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = { menuExpanded = false; onRegenerate() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDelete(message.id) },
                )
            }
        }
    }
}

/** 流式中的助手气泡：思考过程可折叠 + 打字光标 + ≈ token。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingBubble(state: ChatUiState) {
    var reasoningExpanded by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .widthIn(max = 330.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            )
            .padding(12.dp),
    ) {
        if (state.streamingReasoning.isNotBlank()) {
            Surface(
                onClick = { reasoningExpanded = !reasoningExpanded },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "思考过程",
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
            }
            if (reasoningExpanded) {
                Text(
                    state.streamingReasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.streamingContent.isNotBlank()) {
            MarkdownText(text = state.streamingContent + " ▍")
        } else {
            Text("正在输入…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "≈ ${state.streamingEstimatedTokens} tokens · 生成中",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
