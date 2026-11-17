package com.ming.mchat.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ming.mchat.core.attachment.AttachmentRepository
import com.ming.mchat.core.attachment.PendingAttachment
import com.ming.mchat.core.common.ChatBackground
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.ming.mchat.core.common.StringExt
import com.ming.mchat.core.data.datastore.SettingsRepository
import com.ming.mchat.core.data.entity.AttachmentKind
import com.ming.mchat.core.data.entity.ConversationEntity
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.entity.UsageSource
import com.ming.mchat.core.data.repo.ConversationRepository
import com.ming.mchat.core.data.repo.MessageRepository
import com.ming.mchat.core.data.repo.ProviderRepository
import com.ming.mchat.core.data.repo.UsageRepository
import com.ming.mchat.core.llm.BalanceService
import com.ming.mchat.core.llm.ChatService
import com.ming.mchat.core.llm.LlmMessage
import com.ming.mchat.core.llm.LlmStreamEvent
import com.ming.mchat.core.llm.StreamChatParams
import com.ming.mchat.core.llm.TokenEstimator
import com.ming.mchat.core.prompt.InjectionSanitizer
import com.ming.mchat.core.prompt.PromptResolver
import com.ming.mchat.feature.title.TitleGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 聊天页 UI 状态。 */
data class ChatUiState(
    val conversationId: Long = 0L,
    val title: String = "新对话",
    val messages: List<MessageEntity> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val streamingEstimatedTokens: Int = 0,
    val providerId: String = "deepseek",
    val modelId: String = "deepseek-flash",
    val modelDisplayName: String = "DeepSeek-Flash",
    val modelSupportsVision: Boolean = false,
    val modelSupportsReasoning: Boolean = false,
    val reasoningEffort: ReasoningEffortLevel = ReasoningEffortLevel.HIGH,
    val contextWindow: Int = 131072,
    val usedPromptTokens: Int = 0,
    val contextEstimated: Boolean = true,
    val presetName: String? = null,
    val balance: com.ming.mchat.core.llm.BalanceState? = null,
    val lowBalance: Boolean = false,
    /** 用户已手动关闭低余额提示；余额数据刷新后若仍低则重新显示。 */
    val lowBalanceDismissed: Boolean = false,
    val balanceUpdatedStamp: Long? = null,
    /** 低余额弹窗提醒（可选开关；默认仅颜色变红）。 */
    val lowBalancePopupEnabled: Boolean = false,
    /** 思考过程默认折叠（设置可关）。 */
    val collapseReasoning: Boolean = true,
    val conversations: List<ConversationEntity> = emptyList(),
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val injectionWarnings: List<String> = emptyList(),
    val confirmInjection: Boolean = false,
    val error: String? = null,
    val chatBackground: ChatBackground = ChatBackground.SOLID,
    val backgroundImageUri: String = "",
    /** 图片背景生效时为 true，气泡/输入区启用可调透明度。 */
    val imageBackgroundActive: Boolean = false,
    val bubbleAlpha: Float = 0.92f,
    val inputAlpha: Float = 0.92f,
    val providers: List<ProviderConfigEntity> = emptyList(),
    val modelsByProvider: Map<String, List<ModelConfigEntity>> = emptyMap(),
    /** 消息附件（messageId → 附件列表），消息内渲染图片/文件。 */
    val attachmentsByMessage: Map<Long, List<com.ming.mchat.core.data.entity.AttachmentEntity>> = emptyMap(),
    /** 生成中排队的用户消息 id（DSH-Go 式插话：本轮结束后自动发送）。 */
    val queuedMessageIds: Set<Long> = emptySet(),
    /** 临时对话（kelivo：不落库，退出即焚）。 */
    val isTemporary: Boolean = false,
    /** 临时对话的内存消息（id 为负数，避免与数据库 id 冲突）。 */
    val tempMessages: List<MessageEntity> = emptyList(),
    /** 进入临时模式前的会话 id（退出临时模式时恢复）。 */
    val preTemporaryConversationId: Long = 0L,
    /** 展示设置（kelivo：气泡风格 / 时间戳 / 滚动导航按钮）。 */
    val bubbleStyle: com.ming.mchat.core.data.datastore.BubbleStyle = com.ming.mchat.core.data.datastore.BubbleStyle.FROSTED,
    val showTimestamp: Boolean = true,
    val showNavButtons: Boolean = true,
    /** 用户资料（kelivo：用户名 / 头像 emoji）。 */
    val userName: String = "",
    val userAvatarEmoji: String = "🦉",
    /** 快捷短语（内置 + 自定义）。 */
    val quickPhrases: List<String> = emptyList(),
) {
    val isDeepSeek: Boolean get() = providerId == BalanceService.DEEPSEEK_ID
    /** 是否展示低余额提示条（未关闭且余额确实低）。 */
    val showLowBalanceNotice: Boolean get() = isDeepSeek && lowBalance && !lowBalanceDismissed
    /** 展示用消息列表：临时模式用内存消息，否则用数据库消息。 */
    val displayMessages: List<MessageEntity> get() = if (isTemporary) tempMessages else messages
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val providerRepository: ProviderRepository,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val chatService: ChatService,
    private val promptResolver: PromptResolver,
    private val injectionSanitizer: InjectionSanitizer,
    private val attachmentRepository: AttachmentRepository,
    private val titleGenerator: TitleGenerator,
    private val balanceService: BalanceService,
    private val tokenEstimator: TokenEstimator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var messagesJob: Job? = null

    init {
        // Provider / 模型 / 行为设置
        viewModelScope.launch {
            combine(
                settingsRepository.modelBehavior,
                providerRepository.observeProviders(),
                providerRepository.observeAllModels(),
            ) { behavior, providers, models -> Triple(behavior, providers, models) }
                .collect { (behavior, providers, models) ->
                    val modelsByProvider = models.filter { it.enabled }.groupBy { it.providerId }
                    _uiState.update { state ->
                        var next = state.copy(
                            providers = providers,
                            modelsByProvider = modelsByProvider,
                            reasoningEffort = behavior.reasoningEffort,
                            collapseReasoning = behavior.collapseReasoning,
                        )
                        val currentModel = modelsByProvider[state.providerId]?.firstOrNull { it.modelId == state.modelId }
                        next = next.copy(
                            modelDisplayName = currentModel?.displayName ?: state.modelId,
                            modelSupportsVision = currentModel?.supportsVision ?: false,
                            modelSupportsReasoning = currentModel?.supportsReasoning ?: false,
                            contextWindow = currentModel?.contextTokens ?: 32768,
                        )
                        // 未进入会话时，当前 Provider / 模型失效（含旧模型下线）则自动回退到可用模型
                        if (state.conversationId == 0L) {
                            val providerValid = providers.any { it.id == state.providerId && it.enabled }
                            if (!providerValid || currentModel == null) {
                                val picked = sequence {
                                    if (providerValid) yieldAll(modelsByProvider[state.providerId].orEmpty())
                                    yield(modelsByProvider[behavior.defaultProviderId]
                                        ?.firstOrNull { it.modelId == behavior.defaultModelId })
                                    yieldAll(modelsByProvider[behavior.defaultProviderId].orEmpty())
                                    providers.filter { it.enabled }.forEach { p ->
                                        yieldAll(modelsByProvider[p.id].orEmpty())
                                    }
                                }.filterNotNull().firstOrNull()
                                if (picked != null) {
                                    next = next.copy(
                                        providerId = picked.providerId,
                                        modelId = picked.modelId,
                                        modelDisplayName = picked.displayName,
                                        modelSupportsVision = picked.supportsVision,
                                        modelSupportsReasoning = picked.supportsReasoning,
                                        contextWindow = picked.contextTokens,
                                    )
                                }
                            }
                        }
                        next
                    }
                }
        }
        // 会话列表：同步当前会话标题（自动标题生成 / 重命名后无需重新选中）
        viewModelScope.launch {
            conversationRepository.observeAll().collect { list ->
                _uiState.update { state ->
                    val current = list.firstOrNull { it.id == state.conversationId }
                    state.copy(
                        conversations = list,
                        title = when {
                            state.conversationId == 0L -> "新对话"
                            current != null -> current.title
                            else -> state.title
                        },
                    )
                }
            }
        }
        // 外观：聊天背景 / 图片背景透明度 / 低余额弹窗开关
        viewModelScope.launch {
            combine(settingsRepository.appearance, settingsRepository.modelBehavior) { appearance, behavior ->
                appearance to behavior
            }.collect { (appearance, behavior) ->
                _uiState.update {
                    it.copy(
                        chatBackground = appearance.chatBackground,
                        backgroundImageUri = appearance.backgroundImageUri,
                        imageBackgroundActive = appearance.chatBackground == ChatBackground.IMAGE &&
                            appearance.backgroundImageUri.isNotBlank(),
                        bubbleAlpha = appearance.bubbleAlpha,
                        inputAlpha = appearance.inputAlpha,
                        lowBalancePopupEnabled = behavior.lowBalancePopupEnabled,
                    )
                }
            }
        }
        // 展示设置 + 用户资料 + 快捷短语（kelivo）
        viewModelScope.launch {
            combine(
                settingsRepository.chatDisplay,
                settingsRepository.userProfile,
                settingsRepository.quickPhrases,
            ) { display, profile, phrases -> Triple(display, profile, phrases) }.collect { (display, profile, phrases) ->
                _uiState.update {
                    it.copy(
                        bubbleStyle = display.bubbleStyle,
                        showTimestamp = display.showTimestamp,
                        showNavButtons = display.showMessageNavButtons,
                        userName = profile.userName,
                        userAvatarEmoji = profile.avatarEmoji,
                        quickPhrases = (BUILTIN_QUICK_PHRASES + phrases).distinct(),
                    )
                }
            }
        }
        // 余额 + 低余额阈值：余额数据刷新（updatedAt 变化）时重置“已关闭提示”标记
        viewModelScope.launch {
            combine(balanceService.state, settingsRepository.modelBehavior) { balance, behavior ->
                balance to behavior.lowBalanceThreshold
            }.collect { (balance, threshold) ->
                _uiState.update { state ->
                    val freshData = balance?.updatedAt != null && balance.updatedAt != state.balanceUpdatedStamp
                    state.copy(
                        balance = balance,
                        lowBalance = balance != null && balance.error == null && balance.totalBalance < threshold,
                        lowBalanceDismissed = if (freshData) false else state.lowBalanceDismissed,
                        balanceUpdatedStamp = balance?.updatedAt ?: state.balanceUpdatedStamp,
                    )
                }
            }
        }
    }

    // ---------------- 输入与发送 ----------------

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 关闭低余额提示条；余额数据刷新后若仍低会重新出现。 */
    fun dismissLowBalance() {
        _uiState.update { it.copy(lowBalanceDismissed = true) }
    }

    fun send() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isEmpty() && state.pendingAttachments.isEmpty()) return
        if (state.confirmInjection) return
        viewModelScope.launch {
            val injectionEnabled = settingsRepository.promptSafety.first().injectionScanEnabled
            if (injectionEnabled) {
                val scanText = buildString {
                    state.pendingAttachments.forEach { append(it.extractedText.orEmpty()); append('\n') }
                    append(text)
                }
                val warnings = injectionSanitizer.scan(scanText)
                if (warnings.isNotEmpty()) {
                    _uiState.update { it.copy(injectionWarnings = warnings, confirmInjection = true) }
                    return@launch
                }
            }
            if (state.isStreaming) {
                // DSH-Go 式插话：生成中入队，本轮回复完成后自动发送
                enqueueUserMessage(text)
            } else {
                performSend(text)
            }
        }
    }

    /** 用户确认带注入嫌疑的消息继续发送。 */
    fun confirmInjectionSend() {
        _uiState.update { it.copy(confirmInjection = false, injectionWarnings = emptyList()) }
        val text = _uiState.value.input.trim()
        viewModelScope.launch {
            if (_uiState.value.isStreaming) {
                enqueueUserMessage(text)
            } else {
                performSend(text)
            }
        }
    }

    fun dismissInjectionWarning() {
        _uiState.update { it.copy(confirmInjection = false, injectionWarnings = emptyList()) }
    }

    /** 生成中插话：先落库立即可见，标记排队，本轮结束后自动发送。 */
    private suspend fun enqueueUserMessage(text: String) {
        val snapshot = _uiState.value
        val conversationId = snapshot.conversationId.takeIf { it != 0L } ?: return
        val userId = messageRepository.insert(
            MessageEntity(
                conversationId = conversationId,
                role = Role.USER,
                content = text,
                createdAt = System.currentTimeMillis(),
            )
        )
        attachmentRepository.bindToMessage(userId, snapshot.pendingAttachments)

        // 首条用户消息：AUTO_TRUNCATE 兜底标题
        if (messageRepository.countByRole(conversationId, Role.USER) == 1) {
            conversationRepository.setAutoTruncateTitle(
                conversationId,
                StringExt.truncateTitle(text.ifBlank { snapshot.pendingAttachments.firstOrNull()?.displayName ?: "新对话" }),
            )
        }
        _uiState.update {
            it.copy(
                input = "",
                pendingAttachments = emptyList(),
                queuedMessageIds = it.queuedMessageIds + userId,
            )
        }
    }

    /** 排队消息出队：取最早排队的一条自动发送。 */
    private fun drainQueuedMessages() {
        val state = _uiState.value
        if (state.isStreaming || state.conversationId == 0L) return
        val next = state.queuedMessageIds.minOrNull() ?: return
        _uiState.update { it.copy(queuedMessageIds = it.queuedMessageIds - next) }
        launchStream(state.conversationId, next, temporary = false)
    }

    private suspend fun performSend(text: String) {
        val snapshot = _uiState.value
        if (snapshot.isStreaming) {
            enqueueUserMessage(text)
            return
        }

        // 临时对话：不落库，内存消息 + 直接流式
        if (snapshot.isTemporary) {
            val tempId = tempIdCounter.getAndDecrement()
            val userMessage = MessageEntity(
                id = tempId,
                conversationId = -1L,
                role = Role.USER,
                content = text,
                createdAt = System.currentTimeMillis(),
            )
            _uiState.update {
                it.copy(
                    tempMessages = it.tempMessages + userMessage,
                    input = "",
                    pendingAttachments = emptyList(),
                )
            }
            launchStream(-1L, tempId, temporary = true)
            return
        }

        val conversationId = if (snapshot.conversationId == 0L) {
            val behavior = settingsRepository.modelBehavior.first()
            val conversation = conversationRepository.create(
                providerId = snapshot.providerId.ifBlank { behavior.defaultProviderId },
                modelId = snapshot.modelId.ifBlank { behavior.defaultModelId },
            )
            observeMessages(conversation.id)
            // 关键：新会话 id 必须写回状态，否则下一轮发送会再次新建会话（历史断裂）
            _uiState.update { it.copy(conversationId = conversation.id) }
            conversation.id
        } else {
            snapshot.conversationId
        }

        val userId = messageRepository.insert(
            MessageEntity(
                conversationId = conversationId,
                role = Role.USER,
                content = text,
                createdAt = System.currentTimeMillis(),
            )
        )
        attachmentRepository.bindToMessage(userId, snapshot.pendingAttachments)

        // 首条用户消息：AUTO_TRUNCATE 兜底标题
        if (messageRepository.countByRole(conversationId, Role.USER) == 1) {
            conversationRepository.setAutoTruncateTitle(
                conversationId,
                StringExt.truncateTitle(text.ifBlank { snapshot.pendingAttachments.firstOrNull()?.displayName ?: "新对话" }),
            )
        }

        _uiState.update { it.copy(input = "", pendingAttachments = emptyList()) }
        launchStream(conversationId, userId, temporary = false)
    }

    /** 编辑后重发（kelivo / Chatbox）：更新该条用户消息内容，删除其后全部消息，重新流式。 */
    fun editAndResend(messageId: Long, newContent: String) {
        val state = _uiState.value
        if (state.isStreaming || state.conversationId == 0L || newContent.isBlank()) return
        viewModelScope.launch {
            messageRepository.editAndTruncateAfter(state.conversationId, messageId, newContent.trim())
            launchStream(state.conversationId, messageId, temporary = false)
        }
    }

    /** 快捷短语：直接填入输入框（已有内容则另起一行）。 */
    fun applyQuickPhrase(phrase: String) {
        _uiState.update {
            val base = it.input
            val next = if (base.isBlank()) phrase else base + "\n" + phrase
            it.copy(input = next)
        }
    }

    // ---------------- 流式请求 ----------------

    private fun launchStream(conversationId: Long, latestUserMessageId: Long, temporary: Boolean) {
        streamJob = viewModelScope.launch {
            val state = _uiState.value
            val behavior = settingsRepository.modelBehavior.first()
            val provider = providerRepository.getProvider(state.providerId)
            val model = providerRepository.getModel(state.providerId, state.modelId)
            if (provider == null || !provider.enabled || model == null || !model.enabled) {
                _uiState.update {
                    it.copy(
                        error = "当前 Provider 或模型不可用，请在抽屉中重新选择模型",
                        // 早退错误：清空排队标记，避免消息永远停留在“已排队”状态
                        queuedMessageIds = emptySet(),
                    )
                }
                return@launch
            }
            val token = providerRepository.readApiKey(provider.id)
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        error = "尚未配置 ${provider.displayName} 的 API Key，请到设置 → 模型与 Provider 中填写",
                        queuedMessageIds = emptySet(),
                    )
                }
                return@launch
            }

            // 历史消息（含当前轮）：临时模式用内存消息，正常模式读库
            val history = if (temporary) state.tempMessages else messageRepository.getByConversation(conversationId)
            val currentAttachments = if (temporary) {
                // 临时模式：发送时 pendingAttachments 已清空，从最近一条用户消息上下文取不到附件，直接忽略
                emptyList<com.ming.mchat.core.data.entity.AttachmentEntity>()
            } else {
                attachmentRepository.attachmentsForMessage(latestUserMessageId)
            }
            val conversation = if (temporary) null else conversationRepository.getById(conversationId)
            val resolved = promptResolver.resolve(
                conversation = conversation,
                modelId = model.modelId,
                attachmentNames = currentAttachments.map { it.displayName },
            )

            var estimatedImageTokens = 0
            val llmMessages = history.mapNotNull { message ->
                if (message.id == latestUserMessageId) {
                    val textAttachmentBody = currentAttachments
                        .filter { it.kind != AttachmentKind.IMAGE }
                        .joinToString("\n\n") {
                            injectionSanitizer.wrapUntrustedFile(it.displayName, it.extractedText.orEmpty())
                        }
                    val content = if (textAttachmentBody.isBlank()) message.content
                    else message.content + "\n\n" + textAttachmentBody
                    val images = currentAttachments.filter { it.kind == AttachmentKind.IMAGE }
                    if (images.isNotEmpty() && model.supportsVision) {
                        val dataUrls = images.map { attachmentRepository.toDataUrl(it.localPath, it.mimeType) }
                        images.forEach { estimatedImageTokens += tokenEstimator.estimateImageTokens(it.width ?: 1024, it.height ?: 1024) }
                        LlmMessage(role = "user", text = content, imageDataUrls = dataUrls, imageDetail = "auto")
                    } else {
                        LlmMessage(role = "user", text = content)
                    }
                } else if (message.role == Role.USER || message.role == Role.ASSISTANT) {
                    val atts = attachmentRepository.attachmentsForMessage(message.id)
                    val extra = atts.filter { it.kind != AttachmentKind.IMAGE }
                        .joinToString("\n\n") { injectionSanitizer.wrapUntrustedFile(it.displayName, it.extractedText.orEmpty()) }
                    val content = if (extra.isBlank()) message.content else message.content + "\n\n" + extra
                    LlmMessage(role = message.role.name.lowercase(), text = content)
                } else {
                    null
                }
            }

            val estimatedPromptTokens = tokenEstimator.estimateTokens(resolved.systemPrompt, model.modelId) +
                llmMessages.sumOf { tokenEstimator.estimateTokens(it.text, model.modelId) } +
                estimatedImageTokens

            val params = StreamChatParams(
                providerId = provider.id,
                baseUrl = provider.baseUrl,
                token = token,
                model = model,
                systemPrompt = resolved.systemPrompt,
                messages = llmMessages,
                temperature = behavior.globalTemperature,
                outputTokenMode = behavior.outputTokenMode,
                outputTokenManualValue = behavior.outputTokenManualValue,
                reasoningEffort = behavior.reasoningEffort,
            )

            _uiState.update {
                it.copy(
                    isStreaming = true,
                    streamingContent = "",
                    streamingReasoning = "",
                    streamingEstimatedTokens = 0,
                    error = null,
                    presetName = resolved.presetName,
                    usedPromptTokens = estimatedPromptTokens,
                    contextEstimated = true,
                )
            }

            val startedAt = System.currentTimeMillis()
            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var serverUsage: LlmStreamEvent.Usage? = null
            var finishReason: String? = null
            var streamError = false
            // 增量估算：只对每个 delta 做廉价字符近似，避免逐 token 全量重算（O(n²)）
            var streamingTokens = 0

            // UI 节流：网络 delta 高频到达，仅每 ~90ms 把构造器内容刷到界面，
            // 避免逐 token 重组 + 全量 Markdown 重解析导致的掉帧与滑动卡顿
            val uiRefresher = launch {
                while (isActive) {
                    delay(UI_REFRESH_INTERVAL_MS)
                    _uiState.update {
                        it.copy(
                            streamingContent = contentBuilder.toString(),
                            streamingReasoning = reasoningBuilder.toString(),
                            streamingEstimatedTokens = streamingTokens,
                        )
                    }
                }
            }

            try {
                chatService.streamChat(params).collect { event ->
                    when (event) {
                        is LlmStreamEvent.Delta -> {
                            contentBuilder.append(event.content)
                            streamingTokens += tokenEstimator.approximateByChars(event.content)
                        }
                        is LlmStreamEvent.Reasoning -> {
                            reasoningBuilder.append(event.content)
                        }
                        is LlmStreamEvent.ToolCallDelta -> Unit
                        is LlmStreamEvent.Usage -> serverUsage = event
                        is LlmStreamEvent.Done -> finishReason = event.finishReason
                        is LlmStreamEvent.Error -> {
                            streamError = true
                            _uiState.update { state ->
                                state.copy(
                                    error = buildString {
                                        append("请求失败")
                                        event.statusCode?.let { append("（HTTP $it）") }
                                        append("：")
                                        append(event.message)
                                    },
                                )
                            }
                        }
                    }
                }
            } catch (c: CancellationException) {
                // 用户主动停止：保存已生成的部分内容（NonCancellable 保证落库完成；临时模式仅存内存）
                uiRefresher.cancel()
                _uiState.update {
                    it.copy(
                        streamingContent = contentBuilder.toString(),
                        streamingReasoning = reasoningBuilder.toString(),
                    )
                }
                val partialContent = contentBuilder.toString()
                val partialReasoning = reasoningBuilder.toString()
                if (temporary) {
                    appendTempAssistant(partialContent, partialReasoning, model.modelId, startedAt, serverUsage, estimatedPromptTokens, "cancelled")
                } else {
                    withContext(NonCancellable + Dispatchers.IO) {
                        persistAssistantMessage(
                            conversationId = conversationId,
                            model = model,
                            content = partialContent,
                            reasoningContent = partialReasoning.ifBlank { null },
                            reasoningTokens = tokenEstimator.estimateTokens(partialReasoning, model.modelId),
                            usage = serverUsage,
                            estimatedPromptTokens = estimatedPromptTokens,
                            startedAt = startedAt,
                            finishReason = "cancelled",
                        )
                    }
                }
                _uiState.update { it.copy(isStreaming = false, streamingContent = "", streamingReasoning = "", streamingEstimatedTokens = 0) }
                // 停止后若还有排队消息，继续发送下一条
                if (_uiState.value.queuedMessageIds.isNotEmpty()) {
                    viewModelScope.launch { drainQueuedMessages() }
                }
                throw c
            }

            uiRefresher.cancel()
            _uiState.update {
                it.copy(
                    streamingContent = contentBuilder.toString(),
                    streamingReasoning = reasoningBuilder.toString(),
                )
            }

            if (temporary) {
                appendTempAssistant(contentBuilder.toString(), reasoningBuilder.toString(), model.modelId, startedAt, serverUsage, estimatedPromptTokens, finishReason)
            } else {
                persistAssistantMessage(
                    conversationId = conversationId,
                    model = model,
                    content = contentBuilder.toString(),
                    reasoningContent = reasoningBuilder.toString().ifBlank { null },
                    reasoningTokens = tokenEstimator.estimateTokens(reasoningBuilder.toString(), model.modelId),
                    usage = serverUsage,
                    estimatedPromptTokens = estimatedPromptTokens,
                    startedAt = startedAt,
                    finishReason = finishReason,
                )
            }
            _uiState.update { it.copy(isStreaming = false, streamingContent = "", streamingReasoning = "", streamingEstimatedTokens = 0) }

            // 请求出错：清空排队标记，等待用户手动重发（避免连续报错轰炸）
            if (streamError) {
                _uiState.update { it.copy(queuedMessageIds = emptySet()) }
                return@launch
            }

            // 排队的插话消息自动发送（DSH-Go 式队列）
            if (_uiState.value.queuedMessageIds.isNotEmpty()) {
                drainQueuedMessages()
                return@launch
            }

            // 首轮结束后异步生成标题；DeepSeek 刷新余额（临时模式不生成标题）
            if (!temporary) {
                launch { runCatching { titleGenerator.onRoundCompleted(conversationId) } }
            }
            if (provider.id == BalanceService.DEEPSEEK_ID) {
                launch { runCatching { balanceService.refresh() } }
            }
        }
    }

    /** 临时对话：把助手回复写入内存消息。 */
    private fun appendTempAssistant(
        content: String,
        reasoning: String,
        modelId: String,
        startedAt: Long,
        usage: LlmStreamEvent.Usage?,
        estimatedPromptTokens: Int,
        finishReason: String?,
    ) {
        if (content.isBlank() && reasoning.isBlank()) return
        val promptTokens = usage?.promptTokens ?: estimatedPromptTokens
        val completionTokens = usage?.completionTokens ?: tokenEstimator.estimateTokens(content, modelId)
        val entity = MessageEntity(
            id = tempIdCounter.getAndDecrement(),
            conversationId = -1L,
            role = Role.ASSISTANT,
            content = content,
            reasoningContent = reasoning.ifBlank { null },
            createdAt = System.currentTimeMillis(),
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = promptTokens + completionTokens,
            usageSource = if (usage != null && usage.totalTokens > 0) UsageSource.SERVER else UsageSource.ESTIMATED,
            model = modelId,
            latencyMs = System.currentTimeMillis() - startedAt,
            finishReason = finishReason,
        )
        _uiState.update { it.copy(tempMessages = it.tempMessages + entity) }
    }

    private suspend fun persistAssistantMessage(
        conversationId: Long,
        model: ModelConfigEntity,
        content: String,
        reasoningContent: String?,
        reasoningTokens: Int,
        usage: LlmStreamEvent.Usage?,
        estimatedPromptTokens: Int,
        startedAt: Long,
        finishReason: String?,
    ) {
        if (content.isBlank() && reasoningContent.isNullOrBlank()) return
        val latencyMs = System.currentTimeMillis() - startedAt
        val promptTokens: Int
        val completionTokens: Int
        val source: UsageSource
        if (usage != null && usage.totalTokens > 0) {
            promptTokens = usage.promptTokens
            completionTokens = usage.completionTokens
            source = UsageSource.SERVER
        } else {
            promptTokens = estimatedPromptTokens
            completionTokens = tokenEstimator.estimateTokens(content, model.modelId) + reasoningTokens
            source = UsageSource.ESTIMATED
        }
        messageRepository.insert(
            MessageEntity(
                conversationId = conversationId,
                role = Role.ASSISTANT,
                content = content,
                reasoningContent = reasoningContent,
                createdAt = System.currentTimeMillis(),
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = promptTokens + completionTokens,
                usageSource = source,
                model = model.modelId,
                latencyMs = latencyMs,
                finishReason = finishReason,
            )
        )
        usageRepository.recordUsage(model.modelId, promptTokens, completionTokens)
        // 上下文环语义：反映“下一轮请求”的上下文占用。
        // 服务端 usage 可用时：prompt + completion ≈ 下一轮 prompt；否则全历史本地估算。
        if (source == UsageSource.SERVER) {
            _uiState.update {
                it.copy(usedPromptTokens = promptTokens + completionTokens, contextEstimated = false)
            }
        } else {
            refreshContextUsage(conversationId)
        }
    }

    /** 停止按钮：取消流式 Job（部分内容已保存）。 */
    fun stop() {
        streamJob?.cancel()
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    // ---------------- 会话管理 ----------------

    private fun observeMessages(conversationId: Long) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            messageRepository.observeByConversation(conversationId).collect { list ->
                _uiState.update { it.copy(messages = list) }
                // 同步拉取消息附件（messageId → attachments），消息内渲染图片/文件
                val attachments = attachmentRepository.attachmentsForConversation(conversationId)
                _uiState.update {
                    it.copy(attachmentsByMessage = attachments.groupBy { att -> att.messageId })
                }
            }
        }
    }

    fun newConversation() {
        streamJob?.cancel()
        messagesJob?.cancel()
        viewModelScope.launch {
            val behavior = settingsRepository.modelBehavior.first()
            val models = providerRepository.getModels(behavior.defaultProviderId)
            val defaultModel = models.firstOrNull { it.modelId == behavior.defaultModelId }
                ?: models.firstOrNull()
            _uiState.update {
                it.copy(
                    conversationId = 0L,
                    title = "新对话",
                    messages = emptyList(),
                    input = "",
                    pendingAttachments = emptyList(),
                    usedPromptTokens = 0,
                    contextEstimated = true,
                    isStreaming = false,
                    streamingContent = "",
                    streamingReasoning = "",
                    streamingEstimatedTokens = 0,
                    attachmentsByMessage = emptyMap(),
                    queuedMessageIds = emptySet(),
                    providerId = behavior.defaultProviderId,
                    modelId = defaultModel?.modelId ?: behavior.defaultModelId,
                    modelDisplayName = defaultModel?.displayName ?: behavior.defaultModelId,
                    modelSupportsVision = defaultModel?.supportsVision ?: false,
                    modelSupportsReasoning = defaultModel?.supportsReasoning ?: false,
                    contextWindow = defaultModel?.contextTokens ?: 32768,
                )
            }
        }
    }

    fun selectConversation(id: Long) {
        viewModelScope.launch {
            val conversation = conversationRepository.getById(id) ?: return@launch
            val model = providerRepository.getModel(conversation.providerId, conversation.modelId)
            observeMessages(id)
            _uiState.update {
                it.copy(
                    conversationId = id,
                    title = conversation.title,
                    providerId = conversation.providerId,
                    modelId = conversation.modelId,
                    modelDisplayName = model?.displayName ?: conversation.modelId,
                    modelSupportsVision = model?.supportsVision ?: false,
                    modelSupportsReasoning = model?.supportsReasoning ?: false,
                    contextWindow = model?.contextTokens ?: 32768,
                    input = "",
                    pendingAttachments = emptyList(),
                    attachmentsByMessage = emptyMap(),
                    queuedMessageIds = emptySet(),
                )
            }
            refreshContextUsage(id)
        }
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch {
            conversationRepository.rename(id, title)
            if (id == _uiState.value.conversationId) {
                _uiState.update { it.copy(title = title) }
            }
        }
    }

    fun deleteConversation(id: Long) {
        // 先同步重置当前会话状态，避免删除后快速发消息时复用已删除的会话 id
        if (id == _uiState.value.conversationId) {
            streamJob?.cancel()
            messagesJob?.cancel()
            _uiState.update {
                it.copy(
                    conversationId = 0L,
                    title = "新对话",
                    messages = emptyList(),
                    isStreaming = false,
                    streamingContent = "",
                    streamingReasoning = "",
                    streamingEstimatedTokens = 0,
                    usedPromptTokens = 0,
                    contextEstimated = true,
                )
            }
        }
        viewModelScope.launch {
            conversationRepository.deleteCascade(id)
            if (id == _uiState.value.conversationId) {
                newConversation()
            }
        }
    }

    /** 会话置顶 / 取消置顶。 */
    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch { conversationRepository.setPinned(id, pinned) }
    }

    /** 复制会话（kelivo）：消息与附件一并复制，返回新会话 id。 */
    fun duplicateConversation(id: Long) {
        viewModelScope.launch {
            val newId = conversationRepository.duplicate(id)
            if (newId != null && newId > 0) {
                _uiState.update { it.copy(error = "已复制会话（副本）") }
            }
        }
    }

    /** 批量删除会话（多选模式）。 */
    fun deleteConversations(ids: Set<Long>) {
        val currentId = _uiState.value.conversationId
        if (ids.contains(currentId)) {
            // 删除当前会话：先重置状态避免竞态
            streamJob?.cancel()
            messagesJob?.cancel()
            _uiState.update {
                it.copy(
                    conversationId = 0L,
                    title = "新对话",
                    messages = emptyList(),
                    isStreaming = false,
                    streamingContent = "",
                    streamingReasoning = "",
                    streamingEstimatedTokens = 0,
                    usedPromptTokens = 0,
                    contextEstimated = true,
                )
            }
        }
        viewModelScope.launch {
            conversationRepository.deleteManyCascade(ids.toList())
            if (ids.contains(currentId)) newConversation()
        }
    }

    /** 批量置顶 / 取消置顶（多选模式）。 */
    fun setPinnedMany(ids: Set<Long>, pinned: Boolean) {
        viewModelScope.launch { conversationRepository.setPinnedMany(ids.toList(), pinned) }
    }

    /** 临时对话（kelivo temporary chat）：进入后消息不落库；退出恢复原会话。 */
    fun toggleTemporary() {
        val state = _uiState.value
        if (state.isTemporary) {
            // 退出临时模式：恢复之前的会话或回到新会话
            _uiState.update {
                it.copy(
                    isTemporary = false,
                    tempMessages = emptyList(),
                    isStreaming = false,
                    streamingContent = "",
                    streamingReasoning = "",
                    streamingEstimatedTokens = 0,
                    usedPromptTokens = 0,
                    contextEstimated = true,
                )
            }
            val restore = state.preTemporaryConversationId
            if (restore != 0L) selectConversation(restore) else newConversation()
        } else {
            streamJob?.cancel()
            messagesJob?.cancel()
            _uiState.update {
                it.copy(
                    isTemporary = true,
                    tempMessages = emptyList(),
                    title = "临时对话",
                    input = "",
                    pendingAttachments = emptyList(),
                    isStreaming = false,
                    streamingContent = "",
                    streamingReasoning = "",
                    streamingEstimatedTokens = 0,
                    usedPromptTokens = 0,
                    contextEstimated = true,
                    preTemporaryConversationId = it.conversationId,
                )
            }
        }
    }

    /** 切换模型：只覆盖当前会话。 */
    fun switchModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            if (_uiState.value.conversationId != 0L) {
                conversationRepository.updateModel(_uiState.value.conversationId, providerId, modelId)
            }
            _uiState.update { it.copy(providerId = providerId, modelId = modelId) }
        }
    }

    /** 输入栏“深度思考”三档强度（低/高/最高）：即时生效并持久化。 */
    fun setReasoningEffort(level: ReasoningEffortLevel) {
        if (_uiState.value.reasoningEffort == level) return
        _uiState.update { it.copy(reasoningEffort = level) }
        viewModelScope.launch {
            val behavior = settingsRepository.modelBehavior.first()
            settingsRepository.setModelBehavior(behavior.copy(reasoningEffort = level))
        }
    }

    fun regenerate() {
        val state = _uiState.value
        if (state.isStreaming) return
        if (state.isTemporary) {
            val lastUser = state.tempMessages.lastOrNull { it.role == Role.USER } ?: return
            _uiState.update {
                it.copy(tempMessages = it.tempMessages.dropLastWhile { m -> m.role != Role.USER })
            }
            launchStream(-1L, lastUser.id, temporary = true)
            return
        }
        if (state.conversationId == 0L) return
        val lastAssistant = state.messages.lastOrNull { it.role == Role.ASSISTANT } ?: return
        val lastUser = state.messages.lastOrNull { it.role == Role.USER } ?: return
        viewModelScope.launch {
            messageRepository.delete(lastAssistant.id)
            launchStream(state.conversationId, lastUser.id, temporary = false)
        }
    }

    fun deleteMessage(messageId: Long) {
        if (_uiState.value.isTemporary) {
            _uiState.update { it.copy(tempMessages = it.tempMessages.filterNot { m -> m.id == messageId }) }
            return
        }
        viewModelScope.launch { messageRepository.delete(messageId) }
    }

    /**
     * 上下文环用量：反映“下一轮请求”的上下文占用。
     * - 服务端 usage 可用（最后一条助手消息为 SERVER）：prompt + completion ≈ 下一轮 prompt；
     * - 否则全历史本地估算（含附件与系统提示词基线）。
     */
    private fun refreshContextUsage(conversationId: Long) {
        viewModelScope.launch {
            val messages = messageRepository.getByConversation(conversationId)
            val lastServer = messages.lastOrNull {
                it.role == Role.ASSISTANT && it.usageSource == UsageSource.SERVER && it.promptTokens != null
            }
            if (lastServer != null) {
                _uiState.update {
                    it.copy(
                        usedPromptTokens = (lastServer.promptTokens ?: 0) + (lastServer.completionTokens ?: 0),
                        contextEstimated = false,
                    )
                }
            } else {
                val estimated = SYSTEM_PROMPT_BASELINE + messages.sumOf { m ->
                    val attachmentTokens = attachmentRepository.attachmentsForMessage(m.id).sumOf { a ->
                        if (a.kind == AttachmentKind.IMAGE) IMAGE_TOKEN_BASELINE
                        else tokenEstimator.estimateTokens(a.extractedText.orEmpty())
                    }
                    tokenEstimator.estimateTokens(m.content) + attachmentTokens
                }
                _uiState.update { it.copy(usedPromptTokens = estimated, contextEstimated = true) }
            }
        }
    }

    // ---------------- 附件 ----------------

    fun addImageAttachments(uris: List<Uri>) {
        viewModelScope.launch {
            val settings = settingsRepository.attachment.first()
            val imported = attachmentRepository.importImages(uris, settings)
            if (imported.isEmpty()) {
                _uiState.update { it.copy(error = "图片导入失败（可能为不支持的格式）") }
                return@launch
            }
            _uiState.update { state ->
                val currentImages = state.pendingAttachments.count { it.kind == AttachmentKind.IMAGE }
                val room = (MAX_IMAGES - currentImages).coerceAtLeast(0)
                val images = imported.filter { it.kind == AttachmentKind.IMAGE }.take(room)
                val others = imported.filter { it.kind != AttachmentKind.IMAGE }
                state.copy(
                    pendingAttachments = state.pendingAttachments + images + others,
                    error = if (images.size < imported.count { it.kind == AttachmentKind.IMAGE }) "图片最多选择 $MAX_IMAGES 张" else null,
                )
            }
        }
    }

    fun addDocumentAttachments(uris: List<Uri>) {
        viewModelScope.launch {
            val settings = settingsRepository.attachment.first()
            val imported = attachmentRepository.importDocuments(uris, settings)
            if (imported.isEmpty()) {
                _uiState.update { it.copy(error = "文件导入失败") }
                return@launch
            }
            _uiState.update { state -> state.copy(pendingAttachments = state.pendingAttachments + imported) }
        }
    }

    fun removeAttachment(localId: String) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterNot { it.localId == localId })
        }
    }

    // ---------------- 余额 ----------------

    /** 进入聊天页 / ON_RESUME / 手动刷新触发；重组与发送中不触发。 */
    fun refreshBalance() {
        viewModelScope.launch { runCatching { balanceService.refreshIfDeepSeek(_uiState.value.providerId) } }
    }

    companion object {
        const val MAX_IMAGES = 9

        /** 临时消息自增负 id。 */
        private val tempIdCounter = java.util.concurrent.atomic.AtomicLong(-1)

        /** 内置快捷短语（kelivo quick phrase）。 */
        val BUILTIN_QUICK_PHRASES get() = com.ming.mchat.core.common.BuiltinQuickPhrases.ALL

        /** 流式 UI 刷新间隔（ms）：高频 delta 节流，保证滑动流畅。 */
        const val UI_REFRESH_INTERVAL_MS = 90L

        /** 本地估算时的系统提示词基线（模板 + 安全声明 + 聊天格式开销）。 */
        private const val SYSTEM_PROMPT_BASELINE = 160

        /** 图片消息本地估算基线（detail=auto 常见范围的中低值）。 */
        private const val IMAGE_TOKEN_BASELINE = 300
    }
}
