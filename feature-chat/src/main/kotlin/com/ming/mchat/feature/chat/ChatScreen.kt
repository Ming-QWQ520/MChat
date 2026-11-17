package com.ming.mchat.feature.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.theme.AnchoredContextMenu
import com.ming.mchat.core.theme.AnchoredMenuItem
import com.ming.mchat.core.theme.LocalLiquidGlass
import com.ming.mchat.core.theme.liquidGlass
import com.ming.mchat.feature.chat.components.ChatBottomArea
import com.ming.mchat.feature.chat.components.ContextRingDetailPopup
import com.ming.mchat.feature.chat.components.MessageList
import com.ming.mchat.feature.chat.components.ScrollNavButtons
import com.ming.mchat.feature.history.HistoryDrawerContent
import com.ming.mchat.feature.history.ModelPickerSheet
import com.ming.mchat.feature.history.RenameConversationDialog
import java.io.File
import kotlinx.coroutines.launch

/**
 * 聊天页（kelivo 风格）：
 * - 顶栏：菜单 / 用户头像 + 会话标题 + 模型副标题（点击切换模型）/ 临时对话 + 新建；
 * - 历史抽屉（75% 宽）+ 消息列表 + 悬浮输入区（快捷短语）；
 * - 流式输出时仅在用户停留于底部才自动跟随滚动，上滑阅读历史不受打断；
 * - 滚动导航按钮：回到底部 / 上一条提问（不在底部时出现）；
 * - 聊天背景支持纯色 / 主题渐变 / 自定义图片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val glass = LocalLiquidGlass.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showModelPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showRingDetail by remember { mutableStateOf(false) }

    // 错误透传（含 HTTP 400/413 等）
    val error = state.error
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // 余额刷新：进入页面 + 回到前台（ON_RESUME）；重组 / 发送中不触发
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.providerId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBalance()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.providerId) { viewModel.refreshBalance() }

    // 用户是否停留在列表底部：上滑阅读历史时暂停自动跟随，回到底部自动恢复
    val displayMessages = state.displayMessages
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            layoutInfo.totalItemsCount == 0 || lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    // 切换会话后直接定位到底部（无动画）
    LaunchedEffect(state.conversationId, state.isTemporary) {
        if (displayMessages.isNotEmpty()) {
            listState.scrollToItem(displayMessages.size - 1)
        }
    }

    // 用户发送新消息：始终滚动到底部
    LaunchedEffect(displayMessages.size) {
        val msgs = displayMessages
        if (msgs.isNotEmpty() && msgs.last().role == Role.USER) {
            listState.animateScrollToItem(msgs.size - 1)
        }
    }

    // 跟随输出滚动：流式输出且用户位于底部时，平滑跟随新内容
    LaunchedEffect(state.streamingContent.length, state.streamingReasoning.length) {
        if (state.isStreaming && isAtBottom) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) runCatching { listState.scrollToItem(total - 1) }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = ChatViewModel.MAX_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImageAttachments(uris)
    }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addDocumentAttachments(uris)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            // 历史抽屉：始终使用不透明背景，聊天背景图不会透入抽屉
            HistoryDrawerContent(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(0.dp),
                currentProviderId = state.providerId,
                currentProviderName = state.providers.firstOrNull { it.id == state.providerId }?.displayName ?: state.providerId,
                currentModelName = state.modelDisplayName,
                conversations = state.conversations.map {
                    com.ming.mchat.feature.history.DrawerConversationItem(
                        id = it.id,
                        title = it.title,
                        updatedAt = it.updatedAt,
                        isCurrent = it.id == state.conversationId,
                        isPinned = it.isPinned,
                    )
                },
                userName = state.userName.ifBlank { "我" },
                userAvatarEmoji = state.userAvatarEmoji,
                isTemporary = state.isTemporary,
                onNewChat = {
                    scope.launch { keyboard?.hide(); drawerState.close(); viewModel.newConversation() }
                },
                onSelectConversation = { id ->
                    scope.launch { keyboard?.hide(); drawerState.close(); viewModel.selectConversation(id) }
                },
                onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
                onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                onDuplicateConversation = { id -> viewModel.duplicateConversation(id) },
                onBatchDelete = { ids -> viewModel.deleteConversations(ids) },
                onBatchPin = { ids, pinned -> viewModel.setPinnedMany(ids, pinned) },
                onPinConversation = { id, pinned -> viewModel.setPinned(id, pinned) },
                onToggleTemporary = {
                    scope.launch { keyboard?.hide(); drawerState.close(); viewModel.toggleTemporary() }
                },
                onOpenModelPicker = { showModelPicker = true },
                onOpenSettings = {
                    scope.launch { keyboard?.hide(); drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        // 聊天背景（纯色/渐变/图片 + 调整项）由根层级 AppBackground 全局渲染，本层保持透明
        Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            modifier = if (glass.enabled) Modifier.liquidGlass(RectangleShape) else Modifier,
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            ),
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { keyboard?.hide(); drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "打开历史抽屉")
                                }
                            },
                            title = {
                                Column(
                                    Modifier
                                        .padding(start = 4.dp)
                                        .clickable(enabled = state.conversationId != 0L && !state.isTemporary) {
                                            if (state.conversationId != 0L) showRename = true
                                        },
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 用户头像（点击打开抽屉，长按打开资料设置）
                                        Text(
                                            state.userAvatarEmoji,
                                            fontSize = androidx.compose.ui.unit.TextUnit(
                                                18f,
                                                androidx.compose.ui.unit.TextUnitType.Sp,
                                            ),
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            if (state.isTemporary) "临时对话" else state.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        state.modelDisplayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            actions = {
                                // 临时对话开关（kelivo MessageCircleDashed）
                                IconButton(onClick = { viewModel.toggleTemporary() }) {
                                    Icon(
                                        if (state.isTemporary) Icons.Filled.ChatBubbleOutline else Icons.Filled.ChatBubbleOutline,
                                        contentDescription = if (state.isTemporary) "退出临时对话" else "进入临时对话",
                                        tint = if (state.isTemporary) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // 新建对话
                                IconButton(onClick = {
                                    scope.launch { keyboard?.hide(); viewModel.newConversation() }
                                }) {
                                    Icon(Icons.Filled.ModeComment, contentDescription = "新建对话")
                                }
                            },
                        )
                    },
                    bottomBar = {
                        ChatBottomArea(
                            state = state,
                            onInputChange = viewModel::onInputChange,
                            onSend = viewModel::send,
                            onStop = viewModel::stop,
                            onPickImages = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onPickDocuments = { documentPicker.launch(arrayOf("text/*", "application/pdf")) },
                            onRemoveAttachment = viewModel::removeAttachment,
                            onContextRingClick = { showRingDetail = true },
                            onModelChipClick = { showModelPicker = true },
                            onReasoningEffortChange = viewModel::setReasoningEffort,
                            onDismissLowBalance = viewModel::dismissLowBalance,
                            onQuickPhrase = viewModel::applyQuickPhrase,
                            glassEnabled = glass.enabled,
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        if (displayMessages.isEmpty() && !state.isStreaming) {
                            EmptyChatWelcome(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 40.dp),
                                userName = state.userName,
                                avatarEmoji = state.userAvatarEmoji,
                            )
                        } else {
                            MessageList(
                                state = state,
                                listState = listState,
                                onCopy = { clipboard.setText(AnnotatedString(it)) },
                                onDelete = viewModel::deleteMessage,
                                onRegenerate = viewModel::regenerate,
                                onEditResend = viewModel::editAndResend,
                                onOpenFile = { attachment ->
                                    runCatching {
                                        val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            File(attachment.localPath),
                                        )
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                        )
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "暂无应用可打开此文件", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                        // 滚动导航按钮（不在底部时显示）
                        ScrollNavButtons(
                            listState = listState,
                            atBottom = isAtBottom,
                            hasMessages = displayMessages.isNotEmpty(),
                            enabled = state.showNavButtons,
                            isUserIndex = { idx -> idx in displayMessages.indices && displayMessages[idx].role == Role.USER },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                        if (showRingDetail) {
                            Popup(
                                alignment = Alignment.BottomStart,
                                onDismissRequest = { showRingDetail = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                ContextRingDetailPopup(
                                    usedTokens = state.usedPromptTokens,
                                    contextWindow = state.contextWindow,
                                    estimated = state.contextEstimated,
                                    balance = state.balance,
                                    showBalance = state.isDeepSeek,
                                    onDismiss = { showRingDetail = false },
                                )
                            }
                        }
                    }
                }
            }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            providers = state.providers,
            modelsByProvider = state.modelsByProvider,
            currentProviderId = state.providerId,
            currentModelId = state.modelId,
            onSelect = viewModel::switchModel,
            onDismiss = { showModelPicker = false },
        )
    }

    if (showRename && state.conversationId != 0L) {
        RenameConversationDialog(
            initial = state.title,
            onDismiss = { showRename = false },
            onConfirm = { title ->
                viewModel.renameConversation(state.conversationId, title)
                showRename = false
            },
        )
    }

    // 低余额弹窗（可选）：设置中开启后，低余额首次检测到时弹出；取消后本次会话不再提示
    var balancePopupDismissed by remember { mutableStateOf(false) }
    if (state.isDeepSeek && state.lowBalance && state.lowBalancePopupEnabled && !balancePopupDismissed) {
        AlertDialog(
            onDismissRequest = { balancePopupDismissed = true },
            title = { Text("余额较低") },
            text = {
                Text(
                    "DeepSeek 当前余额 ${state.balance?.totalBalance ?: "?"} ${state.balance?.currency ?: "CNY"}，" +
                        "低于预警阈值，建议关注用量或前往平台充值。",
                )
            },
            confirmButton = {
                TextButton(onClick = { balancePopupDismissed = true }) { Text("知道了") }
            },
            dismissButton = {
                TextButton(onClick = { balancePopupDismissed = true }) { Text("取消") }
            },
        )
    }

    if (state.confirmInjection) {
        AlertDialog(
            onDismissRequest = viewModel::dismissInjectionWarning,
            title = { Text("检测到疑似提示词注入") },
            text = {
                Column {
                    state.injectionWarnings.forEach { warning ->
                        Text("• $warning", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "\n内容仅被标记，不会被修改。是否仍要发送？",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmInjectionSend) { Text("仍然发送") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissInjectionWarning) { Text("取消") }
            },
        )
    }
}

/**
 * 空会话欢迎页（kelivo 风格）：居中头像 + 问候语 + 副标题。
 */
@Composable
private fun EmptyChatWelcome(
    modifier: Modifier = Modifier,
    userName: String,
    avatarEmoji: String,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    avatarEmoji,
                    fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (userName.isBlank()) "你好，我能帮什么忙吗？" else "你好，$userName！我能帮什么忙吗？",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "MChat · 本地优先的 AI 助手",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
