package com.ming.mchat.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ming.mchat.core.attachment.PendingAttachment
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.ming.mchat.core.data.entity.AttachmentKind
import com.ming.mchat.core.theme.MotionCurves
import com.ming.mchat.core.theme.MotionDurations
import com.ming.mchat.core.theme.appSurfaces
import com.ming.mchat.feature.chat.ChatUiState
import java.io.File

/**
 * 底部输入区（kelivo 风格）：
 * 悬浮胶囊输入容器（28dp 圆角 + 屏幕边距 + 发丝描边），内含——
 * - 快捷短语行（闪电按钮展开，animateContentSize）；
 * - 文本输入行：[+] 附件 / 多行输入框 / 发送或停止按钮；
 * - 功能行：当前模型名称 chip（点击切换模型）、上下文环、深度思考强度 pill；
 * - 附件缩略图条与低余额提示也收纳在容器内。
 * 液态玻璃开启时容器使用 LiquidGlassSurface。
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
    onModelChipClick: () -> Unit,
    onReasoningEffortChange: (ReasoningEffortLevel) -> Unit,
    onDismissLowBalance: () -> Unit,
    onQuickPhrase: (String) -> Unit,
    glassEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerShape = RoundedCornerShape(28.dp)
    var showQuickPhrases by remember { mutableStateOf(false) }
    val containerModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
    val content: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(
                    animationSpec = tween(MotionDurations.CONTENT, easing = MotionCurves.EaseOutCubic),
                )
        ) {
            if (state.showLowBalanceNotice) {
                // 低余额提示：点击即可关闭（余额刷新后若仍低会重新出现）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = onDismissLowBalance)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "DeepSeek 余额较低（${state.balance?.totalBalance ?: "?"} ${state.balance?.currency ?: "CNY"}），点击关闭",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.pendingAttachments.isNotEmpty()) {
                AttachmentStrip(
                    attachments = state.pendingAttachments,
                    onRemove = onRemoveAttachment,
                )
            }
            // 快捷短语行（kelivo quick phrase）：闪电开关，点击填入输入框
            if (showQuickPhrases && state.quickPhrases.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    items(state.quickPhrases) { phrase ->
                        Surface(
                            onClick = { onQuickPhrase(phrase) },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                phrase,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            // 文本输入行：[+] / 输入框 / 发送或停止
            Row(verticalAlignment = Alignment.Bottom) {
                AttachButton(
                    onPickImages = onPickImages,
                    onPickDocuments = onPickDocuments,
                )
                QuickPhraseToggle(
                    expanded = showQuickPhrases,
                    enabled = state.quickPhrases.isNotEmpty(),
                    onToggle = { showQuickPhrases = !showQuickPhrases },
                )
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp),
                    placeholder = { Text("发消息", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    maxLines = 6,
                    minLines = 1,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                if (state.isStreaming) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .padding(start = 4.dp, bottom = 4.dp)
                            .size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "停止生成")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = state.input.isNotBlank() || state.pendingAttachments.isNotEmpty(),
                        modifier = Modifier
                            .padding(start = 4.dp, bottom = 4.dp)
                            .size(40.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                        )
                    }
                }
            }
            // 功能行：模型名称 chip / 预设 / 上下文环 / 深度思考
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                ModelChip(
                    displayName = state.modelDisplayName,
                    onClick = onModelChipClick,
                )
                state.presetName?.let { preset ->
                    Text(
                        "· 预设 $preset",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                ContextRing(
                    usedTokens = state.usedPromptTokens,
                    contextWindow = state.contextWindow,
                    estimated = state.contextEstimated,
                    balance = state.balance,
                    showBalance = state.isDeepSeek,
                    lowBalance = state.lowBalance,
                    onClick = onContextRingClick,
                    diameter = 30.dp,
                )
                Spacer(Modifier.width(10.dp))
                ThinkingEffortPill(
                    level = state.reasoningEffort,
                    enabled = state.modelSupportsReasoning,
                    onChange = onReasoningEffortChange,
                )
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        if (glassEnabled) {
            com.ming.mchat.core.theme.LiquidGlassSurface(
                modifier = containerModifier,
                shape = containerShape,
                content = content,
            )
        } else {
            Surface(
                modifier = containerModifier,
                shape = containerShape,
                color = MaterialTheme.appSurfaces.card,
                border = BorderStroke(0.5.dp, MaterialTheme.appSurfaces.hairline),
                content = content,
            )
        }
    }
}

/** 快捷短语开关（闪电图标）。 */
@Composable
private fun QuickPhraseToggle(
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle, enabled = enabled) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = "快捷短语",
            tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 附件按钮：+ 图标，弹出图片 / 文件选择菜单。 */
@Composable
private fun AttachButton(
    onPickImages: () -> Unit,
    onPickDocuments: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加附件",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("图片（最多 9 张）") },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                onClick = { expanded = false; onPickImages() },
            )
            DropdownMenuItem(
                text = { Text("文本 / PDF 文件") },
                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                onClick = { expanded = false; onPickDocuments() },
            )
        }
    }
}

/** 当前模型名称 chip：位于输入框内，点击打开模型选择。 */
@Composable
private fun ModelChip(
    displayName: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 深度思考强度 pill（对齐 DeepSeek reasoning_effort：关闭 / 低 / 高 / 最高）：
 * - 选中态：品牌蓝实底 + 白字（浅色）/ 品牌蓝字 + 亮底（深色），高对比不迷惑；
 * - 关闭态：中性容器色；模型不支持推理时禁用（半透明）；
 * - 点击弹出四档选择。
 */
@Composable
private fun ThinkingEffortPill(
    level: ReasoningEffortLevel,
    enabled: Boolean,
    onChange: (ReasoningEffortLevel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val thinkingOn = level != ReasoningEffortLevel.NONE
    val label = when (level) {
        ReasoningEffortLevel.NONE -> "深度思考·关"
        ReasoningEffortLevel.LOW -> "深度思考·低"
        ReasoningEffortLevel.HIGH -> "深度思考"
        ReasoningEffortLevel.MAX -> "深度思考·最高"
    }
    val containerColor = if (thinkingOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (thinkingOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        Surface(
            onClick = { if (enabled) expanded = true },
            shape = RoundedCornerShape(50),
            color = containerColor,
            contentColor = contentColor,
            border = if (thinkingOn) null
            else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                ReasoningEffortLevel.NONE to "关闭（不思考，直接回答）",
                ReasoningEffortLevel.LOW to "低（快速思考）",
                ReasoningEffortLevel.HIGH to "高（默认）",
                ReasoningEffortLevel.MAX to "最高（深入思考）",
            ).forEach { (option, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    leadingIcon = {
                        if (option == level) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    trailingIcon = {
                        Text(
                            when (option) {
                                ReasoningEffortLevel.NONE -> "none"
                                ReasoningEffortLevel.LOW -> "low"
                                ReasoningEffortLevel.HIGH -> "high"
                                ReasoningEffortLevel.MAX -> "max"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onChange(option)
                    },
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
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
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp)),
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
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp),
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
    Spacer(Modifier.height(2.dp))
}
