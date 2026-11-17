package com.ming.mchat.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.common.TimeUtils
import com.ming.mchat.core.theme.AnchoredContextMenu
import com.ming.mchat.core.theme.AnchoredMenuItem
import com.ming.mchat.core.theme.MotionCurves
import com.ming.mchat.core.theme.MotionDurations
import com.ming.mchat.core.theme.appSurfaces
import com.ming.mchat.core.theme.longPressAnchor

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
 * 历史抽屉（kelivo 风格）：
 * - 顶部：用户头像 + 用户名 / 当前模型卡片（点击切换）/ 新建对话 / 临时对话开关；
 * - 中部：搜索框 + 置顶/时间分组历史会话（当前会话高亮）；
 * - 长按会话：以长按点为中心的锚点菜单（多选 / 重命名 / 置顶 / 复制 / 删除）；
 * - 多选模式：计数栏 + 全选 + 批量置顶 / 批量删除（animateContentSize 切换）；
 * - 底部：设置入口。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryDrawerContent(
    modifier: Modifier = Modifier,
    currentProviderId: String,
    currentProviderName: String,
    currentModelName: String,
    conversations: List<DrawerConversationItem>,
    userName: String,
    userAvatarEmoji: String,
    isTemporary: Boolean,
    onNewChat: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onRenameConversation: (Long, String) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onDuplicateConversation: (Long) -> Unit,
    onBatchDelete: (Set<Long>) -> Unit,
    onBatchPin: (Set<Long>, Boolean) -> Unit,
    onPinConversation: (Long, Boolean) -> Unit,
    onToggleTemporary: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var renameTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    // 长按锚点菜单
    var menuTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuPressOffset by remember { mutableStateOf(Offset.Zero) }
    // 多选模式
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var confirmBatchDelete by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    // 分组：置顶 + 今天/昨天/7天内/30天内/更早
    val grouped = remember(filtered) { groupConversations(filtered) }
    val allSelectableIds = remember(filtered) { filtered.map { it.id }.toSet() }

    val menuConversation = conversations.firstOrNull { it.id == menuTarget }
    val renameConversation = conversations.firstOrNull { it.id == renameTarget }
    val deleteConversation = conversations.firstOrNull { it.id == deleteTarget }

    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            // 消费空白区域点击：抽屉关闭动画期间 scrim 已移除，
            // 未消费的点击会穿透到底层页面（误触输入区“+”附件按钮等）
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // ---- 顶部：搜索 + 模型卡片 + 新建/临时（kelivo：用户栏在底部） ----
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(14.dp))
            // 当前模型卡片：点击切换模型
            Surface(
                onClick = onOpenModelPicker,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.appSurfaces.surfaceFill,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            currentModelName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            currentProviderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 新建对话
                Surface(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建对话", style = MaterialTheme.typography.labelLarge)
                    }
                }
                // 临时对话（kelivo temporary chat）
                Surface(
                    onClick = onToggleTemporary,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTemporary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.appSurfaces.surfaceFill,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Filled.FlashOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isTemporary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isTemporary) "退出临时" else "临时对话",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isTemporary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- 搜索 / 多选计数栏（AnimatedVisibility + fade 切换）----
        Box(Modifier.padding(horizontal = 16.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn(tween(MotionDurations.SWITCHER, easing = MotionCurves.EaseOutCubic)),
                exit = fadeOut(tween(MotionDurations.SWITCHER, easing = MotionCurves.EaseInCubic)),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索对话") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.appSurfaces.hairlineStrong,
                        unfocusedBorderColor = MaterialTheme.appSurfaces.hairline,
                        focusedContainerColor = MaterialTheme.appSurfaces.surfaceFill,
                        unfocusedContainerColor = MaterialTheme.appSurfaces.surfaceFill,
                    ),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn(tween(MotionDurations.SWITCHER, easing = MotionCurves.EaseOutCubic)),
                exit = fadeOut(tween(MotionDurations.SWITCHER, easing = MotionCurves.EaseInCubic)),
            ) {
                SelectionCountBar(
                    selectedCount = selectedIds.size,
                    allSelected = allSelectableIds.isNotEmpty() && selectedIds.containsAll(allSelectableIds),
                    onCancel = {
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    onToggleSelectAll = {
                        selectedIds = if (selectedIds.containsAll(allSelectableIds)) emptySet() else allSelectableIds
                    },
                    onBatchPin = {
                        if (selectedIds.isNotEmpty()) onBatchPin(selectedIds, true)
                    },
                    onBatchUnpin = {
                        if (selectedIds.isNotEmpty()) onBatchPin(selectedIds, false)
                    },
                    onBatchDelete = {
                        if (selectedIds.isNotEmpty()) confirmBatchDelete = true
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- 会话列表 ----
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            grouped.forEach { (bucket, items) ->
                item(key = "header_$bucket") {
                    Text(
                        when (bucket) {
                            PINNED_BUCKET -> "置顶"
                            0 -> "今天"
                            1 -> "昨天"
                            2 -> "7 天内"
                            3 -> "30 天内"
                            else -> "更早"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                items(items.size, key = { items[it].id }) { index ->
                    val item = items[index]
                    ConversationRow(
                        item = item,
                        selectionMode = selectionMode,
                        selected = selectedIds.contains(item.id),
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
                            } else {
                                onSelectConversation(item.id)
                            }
                        },
                        onLongPress = { offset ->
                            if (!selectionMode) {
                                menuTarget = item.id
                                menuPressOffset = offset
                            }
                        },
                    )
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (searchQuery.isBlank()) "暂无历史对话" else "没有匹配的对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
        }

        // ---- 底部：用户栏（kelivo：头像 + 用户名 + 设置入口） ----
        Surface(color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clickable(onClick = onOpenSettings),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            userAvatarEmoji,
                            fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    userName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 长按锚点菜单（kelivo：多选 / 重命名 / 置顶 / 复制 / 删除）
    menuConversation?.let { item ->
        AnchoredContextMenu(
            pressRootOffset = menuPressOffset,
            onDismiss = { menuTarget = null },
        ) {
            AnchoredMenuItem(Icons.Filled.Checklist, "多选") {
                menuTarget = null
                selectionMode = true
                selectedIds = setOf(item.id)
            }
            AnchoredMenuItem(Icons.Filled.Edit, "重命名") {
                menuTarget = null
                renameTarget = item.id
            }
            AnchoredMenuItem(
                Icons.Filled.PushPin,
                if (item.isPinned) "取消置顶" else "置顶",
            ) {
                menuTarget = null
                onPinConversation(item.id, !item.isPinned)
            }
            AnchoredMenuItem(Icons.Filled.ContentCopy, "复制会话") {
                menuTarget = null
                onDuplicateConversation(item.id)
            }
            AnchoredMenuItem(Icons.Filled.Delete, "删除", danger = true) {
                menuTarget = null
                deleteTarget = item.id
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // 重命名对话框
    renameConversation?.let { item ->
        RenameConversationDialog(
            initial = item.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                onRenameConversation(item.id, title)
                renameTarget = null
            },
        )
    }

    // 删除二次确认
    deleteConversation?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除对话") },
            text = { Text("「${item.title}」将被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(item.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 批量删除二次确认
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除 ${selectedIds.size} 个对话") },
            text = { Text("所选对话及其消息将被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onBatchDelete(selectedIds)
                    confirmBatchDelete = false
                    selectionMode = false
                    selectedIds = emptySet()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") }
            },
        )
    }

    // 选中项被删除后同步清理
    LaunchedEffect(conversations) {
        val liveIds = conversations.map { it.id }.toSet()
        if (selectedIds.any { it !in liveIds }) {
            selectedIds = selectedIds.intersect(liveIds)
            if (selectedIds.isEmpty()) selectionMode = false
        }
    }
}

/** 会话行：选中态复选框 / 当前会话高亮；长按呼出锚点菜单。 */
@Composable
private fun ConversationRow(
    item: DrawerConversationItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else if (item.isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .longPressAnchor(onLongPress),
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Filled.Check else Icons.Filled.Check,
                contentDescription = if (selected) "已选中" else "未选中",
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                TimeUtils.formatMessageTime(item.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.isPinned) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "已置顶",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 多选计数栏：计数 + 全选 + 批量置顶/取消 + 批量删除 + 取消。 */
@Composable
private fun SelectionCountBar(
    selectedCount: Int,
    allSelected: Boolean,
    onCancel: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onBatchPin: () -> Unit,
    onBatchUnpin: () -> Unit,
    onBatchDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.appSurfaces.surfaceFill,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "退出多选")
            }
            Text(
                "已选 $selectedCount 项",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    Icons.Filled.Checklist,
                    contentDescription = if (allSelected) "取消全选" else "全选",
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onBatchPin) {
                Icon(Icons.Filled.PushPin, contentDescription = "批量置顶")
            }
            IconButton(onClick = onBatchDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "批量删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 会话分组：置顶优先，其余按 今天/昨天/7天内/30天内/更前 分桶。 */
private fun groupConversations(items: List<DrawerConversationItem>): List<Pair<Int, List<DrawerConversationItem>>> {
    val pinned = items.filter { it.isPinned }
    val others = items.filterNot { it.isPinned }
    val buckets = others.groupBy { TimeUtils.bucketOf(it.updatedAt) }
    val ordered = listOf(0, 1, 2, 3, 4).filter { buckets.containsKey(it) }
    return buildList {
        if (pinned.isNotEmpty()) add(PINNED_BUCKET to pinned)
        ordered.forEach { bucket -> add(bucket to buckets.getValue(bucket)) }
    }
}

/** 重命名会话对话框。 */
@Composable
fun RenameConversationDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
