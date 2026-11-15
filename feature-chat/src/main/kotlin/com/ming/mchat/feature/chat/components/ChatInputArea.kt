package com.ming.mchat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ming.mchat.core.attachment.PendingAttachment
import com.ming.mchat.core.data.entity.AttachmentKind
import com.ming.mchat.feature.chat.ChatUiState
import java.io.File

/**
 * 底部输入区：低余额提示条 → 附件缩略图条 → 上下文环 + 输入栏。
 * 液态玻璃开启时使用 LiquidGlassSurface，关闭时为普通 Surface。
 */
@Composable
fun ChatBottomArea(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImages: () -> Unit,
    onPickDocuments: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onContextRingClick: () -> Unit,
    glassEnabled: Boolean,
) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (state.isDeepSeek && state.lowBalance) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    Text(
                        "DeepSeek 余额较低（${state.balance?.totalBalance ?: "?"} ${state.balance?.currency ?: "CNY"}），建议关注用量",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (state.pendingAttachments.isNotEmpty()) {
                AttachmentStrip(
                    attachments = state.pendingAttachments,
                    onRemove = onRemoveAttachment,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                ContextRing(
                    usedTokens = state.usedPromptTokens,
                    contextWindow = state.contextWindow,
                    estimated = state.contextEstimated,
                    balance = state.balance,
                    showBalance = state.isDeepSeek,
                    lowBalance = state.lowBalance,
                    onClick = onContextRingClick,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    state.presetName?.let {
                        Text(
                            "预设：$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (state.isStreaming) {
                        Text(
                            "生成中，点击右侧方块停止",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            InputBar(
                input = state.input,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                onPickImages = onPickImages,
                onPickDocuments = onPickDocuments,
                canSend = state.input.isNotBlank() || state.pendingAttachments.isNotEmpty(),
                isStreaming = state.isStreaming,
            )
        }
    }
    if (glassEnabled) {
        com.ming.mchat.core.theme.LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            content = content,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            content = content,
        )
    }
}

@Composable
private fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImages: () -> Unit,
    onPickDocuments: () -> Unit,
    canSend: Boolean,
    isStreaming: Boolean,
) {
    var attachMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Bottom) {
        Box {
            IconButton(onClick = { attachMenu = true }) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = "添加附件",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                DropdownMenuItem(
                    text = { Text("图片（最多 9 张）") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    onClick = { attachMenu = false; onPickImages() },
                )
                DropdownMenuItem(
                    text = { Text("文本 / PDF 文件") },
                    leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                    onClick = { attachMenu = false; onPickDocuments() },
                )
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            placeholder = { Text("输入消息…") },
            maxLines = 6,
            minLines = 1,
            shape = RoundedCornerShape(22.dp),
        )
        if (isStreaming) {
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "停止生成")
            }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

/** 附件缩略图条：可删除，显示估算 token。 */
@Composable
private fun AttachmentStrip(
    attachments: List<PendingAttachment>,
    onRemove: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        items(attachments, key = { it.localId }) { attachment ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        if (attachment.kind == AttachmentKind.IMAGE) {
                            AsyncImage(
                                model = File(attachment.localPath),
                                contentDescription = attachment.displayName,
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                attachment.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(88.dp),
                            )
                            Text(
                                "≈ ${attachment.estimatedTokens} tk",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRemove(attachment.localId) },
                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "移除附件",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
