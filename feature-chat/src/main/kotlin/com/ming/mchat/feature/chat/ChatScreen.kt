package com.ming.mchat.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ming.mchat.core.common.ChatBackground
import com.ming.mchat.core.common.MarkdownStripper
import com.ming.mchat.core.theme.LocalLiquidGlass
import com.ming.mchat.core.theme.glassBackdrop
import com.ming.mchat.core.theme.liquidGlass
import com.ming.mchat.feature.chat.components.ChatBottomArea
import com.ming.mchat.feature.chat.components.ContextRingDetailPopup
import com.ming.mchat.feature.chat.components.MessageList
import com.ming.mchat.feature.history.HistoryDrawerContent
import com.ming.mchat.feature.history.ModelPickerSheet
import com.ming.mchat.feature.history.RenameConversationDialog
import kotlinx.coroutines.launch

/**
 * 聊天页：历史抽屉 + 消息列表 + 底部输入区。
 * - 抽屉左边缘右滑打开（gesturesEnabled），TopAppBar 菜单按钮冗余入口；
 * - 打开抽屉前隐藏键盘；
 * - 液态玻璃开启时：TopAppBar / 底栏 / 抽屉走玻璃层，抽屉打开时对背后内容做真实背景模糊。
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
    val context = LocalContext.current
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

    // 自动滚动：新消息动画滚动；流式增量直接定位
    LaunchedEffect(state.messages.size, state.isStreaming) {
        val lastIndex = state.messages.size - 1 + if (state.isStreaming) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }
    LaunchedEffect(state.streamingContent.length) {
        if (state.isStreaming) {
            val target = state.messages.size
            if (target >= 0) listState.scrollToItem(target)
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
            HistoryDrawerContent(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(0.dp),
                currentProviderName = state.providers.firstOrNull { it.id == state.providerId }?.displayName ?: state.providerId,
                currentModelId = state.modelId,
                conversations = state.conversations.map {
                    com.ming.mchat.feature.history.DrawerConversationItem(
                        id = it.id,
                        title = it.title,
                        updatedAt = it.updatedAt,
                        isCurrent = it.id == state.conversationId,
                        isPinned = it.isPinned,
                    )
                },
                onNewChat = {
                    scope.launch { keyboard?.hide(); drawerState.close(); viewModel.newConversation() }
                },
                onSelectConversation = { id ->
                    scope.launch { keyboard?.hide(); drawerState.close(); viewModel.selectConversation(id) }
                },
                onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
                onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                onPinConversation = { id, pinned -> viewModel.setPinned(id, pinned) },
                onOpenModelPicker = { showModelPicker = true },
                onOpenSettings = {
                    scope.launch { keyboard?.hide(); drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        val drawerOpen = drawerState.targetValue == DrawerValue.Open
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (state.chatBackground == ChatBackground.GRADIENT) {
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    } else {
                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
                    }
                )
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdrop(active = drawerOpen && glass.enabled && !glass.reduceMotion),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        modifier = if (glass.enabled) Modifier.liquidGlass(RectangleShape) else Modifier,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (glass.enabled) Color.Transparent else MaterialTheme.colorScheme.surface,
                        ),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { keyboard?.hide(); drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "打开历史抽屉")
                            }
                        },
                        title = {
                            androidx.compose.foundation.layout.Column(
                                Modifier
                                    .padding(start = 4.dp)
                                    .clickable { showRename = true },
                            ) {
                                Text(
                                    state.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    state.modelId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                        glassEnabled = glass.enabled,
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    MessageList(
                        state = state,
                        listState = listState,
                        onCopy = { clipboard.setText(AnnotatedString(it)) },
                        onDelete = viewModel::deleteMessage,
                        onRegenerate = viewModel::regenerate,
                        onCopyPlain = { clipboard.setText(AnnotatedString(MarkdownStripper.toPlainText(it))) },
                        onShare = { text ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享消息"))
                        },
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

    if (state.confirmInjection) {
        AlertDialog(
            onDismissRequest = viewModel::dismissInjectionWarning,
            title = { Text("检测到疑似提示词注入") },
            text = {
                androidx.compose.foundation.layout.Column {
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

