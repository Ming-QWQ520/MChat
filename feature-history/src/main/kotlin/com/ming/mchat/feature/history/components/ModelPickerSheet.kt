package com.ming.mchat.feature.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity

private data class PickerEntry(
    val provider: ProviderConfigEntity,
    val model: ModelConfigEntity,
    val key: String,
    val isFavorite: Boolean,
)

/**
 * 模型选择底部弹层（kelivo ModelSelectSheet 风格）：
 * - 顶部搜索框（模型名 / 供应商名过滤）；
 * - 收藏区置顶（星标切换，收藏的模型跨供应商汇总）；
 * - 按供应商分组，每项：模型名 + 能力标签胶囊（推理 / 视觉 / 工具 / 上下文窗口）+ 选中高亮；
 * - 底部供应商 Tab 胶囊行，点击滚动定位到对应分组；
 * - 切换只覆盖当前会话，影响后续消息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfigEntity>,
    modelsByProvider: Map<String, List<ModelConfigEntity>>,
    currentProviderId: String,
    currentModelId: String,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelect: (providerId: String, modelId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val enabledProviders = remember(providers) { providers.filter { it.enabled } }

    val entries = remember(enabledProviders, modelsByProvider, favorites) {
        enabledProviders.flatMap { provider ->
            modelsByProvider[provider.id].orEmpty()
                .filter { it.enabled }
                .map { model ->
                    val key = "${provider.id}:${model.modelId}"
                    PickerEntry(provider, model, key, key in favorites)
                }
        }
    }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter {
            it.model.displayName.contains(query, true) ||
                it.model.modelId.contains(query, true) ||
                it.provider.displayName.contains(query, true)
        }
    }

    val favoriteEntries = filtered.filter { it.isFavorite }
    val grouped = remember(filtered) { filtered.groupBy { it.provider } }

    // 底部供应商 Tab → 分组滚动定位（索引 = 收藏区条目数 + 前序分组头/条目累加）
    var scrollToProvider by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollToProvider) {
        val pid = scrollToProvider ?: return@LaunchedEffect
        val base = if (favoriteEntries.isNotEmpty()) 1 + favoriteEntries.size else 0
        var index = base
        for ((p, list) in grouped) {
            if (p.id == pid) break
            index += 1 + list.size
        }
        runCatching { listState.animateScrollToItem(index) }
        scrollToProvider = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${entries.size} 个可用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索模型或供应商") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            if (favoriteEntries.isNotEmpty()) {
                item(key = "fav_header") { GroupHeader("收藏") }
                items(favoriteEntries, key = { "fav_${it.key}" }) { entry ->
                    ModelEntryRow(
                        entry = entry,
                        selected = false,
                        showProvider = true,
                        onSelect = onSelect,
                        onToggleFavorite = onToggleFavorite,
                        onDismiss = onDismiss,
                    )
                }
            }
            grouped.forEach { (provider, list) ->
                item(key = "header_${provider.id}") {
                    GroupHeader(
                        buildString {
                            append(provider.displayName)
                            append("  ·  ${list.size}")
                        },
                    )
                }
                items(list, key = { it.key }) { entry ->
                    ModelEntryRow(
                        entry = entry,
                        selected = entry.provider.id == currentProviderId && entry.model.modelId == currentModelId,
                        showProvider = false,
                        onSelect = onSelect,
                        onToggleFavorite = onToggleFavorite,
                        onDismiss = onDismiss,
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "暂无可用模型，请在设置中添加" else "没有匹配的模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // 底部供应商 Tab 胶囊行
        if (enabledProviders.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                items(enabledProviders, key = { it.id }) { provider ->
                    FilterChip(
                        selected = provider.id == currentProviderId,
                        onClick = { scrollToProvider = provider.id },
                        label = { Text(provider.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (provider.id == currentProviderId) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun ModelEntryRow(
    entry: PickerEntry,
    selected: Boolean,
    showProvider: Boolean,
    onSelect: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val model = entry.model
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                Color.Transparent
            },
        ),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (entry.provider.id == "deepseek") {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                com.ming.mchat.core.theme.R.drawable.deepseek_logo,
                            ),
                            contentDescription = "DeepSeek",
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            entry.provider.displayName.take(1).ifBlank { "?" },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(
                buildString {
                    append(model.displayName)
                    if (showProvider) append("  ·  ${entry.provider.displayName}")
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CapabilityTag(model.modelId, highlight = true)
                if (model.supportsReasoning) CapabilityTag("推理")
                if (model.supportsVision) CapabilityTag("视觉")
                CapabilityTag("${model.contextTokens / 1024}K")
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onToggleFavorite(entry.key) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (entry.isFavorite) "取消收藏" else "收藏",
                        tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "当前模型",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onSelect(entry.provider.id, model.modelId)
                onDismiss()
            },
    )
}

/** 能力标签胶囊。 */
@Composable
private fun CapabilityTag(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .widthIn(max = 150.dp),
        )
    }
}
