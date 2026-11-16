package com.ming.mchat.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.common.TimeUtils
import com.ming.mchat.core.theme.LiquidGlassSurface

/** 抽屉会话条目。 */
data class DrawerConversationItem(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val isCurrent: Boolean,
    val isPinned: Boolean = false,
)

/** 置顶分组使用的固定桶标识（时间桶为 0~4，负数避免冲突）。 */
private const val PINNED_BUCKET = -1

/**
 * 历史抽屉：三段固定布局。
 * 顶部：当前模型卡片 + 新建对话 + 会话搜索框；中部：置顶分组 + 按时间分组的历史会话（sticky header）；
 * 底部：设置入口。液态玻璃开启时抽屉与顶部卡片用 LiquidGlassSurface，列表项保持普通 Surface。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryDrawerContent(
    modifier: Modifier = Modifier,
    currentProviderName: String,
    currentModelId: String,
    conversations: List<DrawerConversationItem>,
    onNewChat: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onRenameConversation: (Long, String) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onPinConversation: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenModelPicker: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<DrawerConversationItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        // ---- 顶部：模型卡片 + 新建对话 ----
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 16.dp, 8.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("当前模型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(currentProviderName, style = MaterialTheme.typography.titleMedium)
                        Text(currentModelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onOpenModelPicker) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "切换模型")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onNewChat,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建对话", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        // ---- 会话搜索过滤 ----
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("搜索会话", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(18.dp)) },
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        // ---- 中部：置顶分组 + 时间分组的历史会话列表 ----
        val query = searchQuery.trim()
        val filtered = remember(conversations, query) {
            if (query.isEmpty()) conversations
            else conversations.filter { it.title.contains(query, ignoreCase = true) }
        }
        val grouped = remember(filtered) {
            val pinned = filtered.filter { it.isPinned }
            val byTime = filtered.filterNot { it.isPinned }
                .groupBy { TimeUtils.bucketOf(it.updatedAt) }
                .toSortedMap()
            buildList {
                if (pinned.isNotEmpty()) add(PINNED_BUCKET to pinned)
                byTime.forEach { (bucket, items) -> add(bucket to items) }
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            grouped.forEach { (bucket, items) ->
                stickyHeader(key = "header_$bucket") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                        ) {
                            if (bucket == PINNED_BUCKET) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                if (bucket == PINNED_BUCKET) "置顶" else TimeUtils.bucketLabel(bucket),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                items(items = items, key = { it.id }) { item ->
                    ConversationRow(
                        item = item,
                        onClick = { onSelectConversation(item.id) },
                        onRename = { renameTarget = item },
                        onDelete = { onDeleteConversation(item.id) },
                        onPin = { onPinConversation(item.id, !item.isPinned) },
                    )
                }
            }
            item {
                if (filtered.isEmpty()) {
                    Text(
                        if (query.isEmpty()) "暂无历史会话\n左上角“新建对话”开始" else "没有匹配“$query”的会话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }

        HorizontalDivider()
        // ---- 底部：设置入口 ----
        ListItem(
            headlineContent = { Text("设置") },
            leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings),
        )
    }

    renameTarget?.let { target ->
        RenameConversationDialog(
            initial = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                onRenameConversation(target.id, title)
                renameTarget = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: DrawerConversationItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除会话",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (item.isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
        ) {
            Box {
                ListItem(
                    headlineContent = {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(TimeUtils.formatDateTime(item.updatedAt), style = MaterialTheme.typography.labelSmall) },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        if (item.isPinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "已置顶",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (item.isPinned) "取消置顶" else "置顶") },
                        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                        onClick = { menuExpanded = false; onPin() },
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun RenameConversationDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("标题") },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
