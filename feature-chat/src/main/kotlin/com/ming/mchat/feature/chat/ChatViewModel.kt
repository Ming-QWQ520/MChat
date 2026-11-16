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
    val modelId: String = "deepseek-4.1-flash",
    val modelDisplayName: String = "DeepSeek-4.1Flash",
    val modelSupportsVision: Boolean = false,
    val modelSupportsReasoning: Boolean = false,
    val reasoningEffort: ReasoningEffortLevel = ReasoningEffortLevel.MAX,
    val contextWindow: Int = 131072,
    val usedPromptTokens: Int = 0,
    val contextEstimated: Boolean = true,
    val presetName: String? = null,
    val balance: com.ming.mchat.core.llm.BalanceState? = null,
    val lowBalance: Boolean = false,
    val conversations: List<ConversationEntity> = emptyList(),
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val injectionWarnings: List<String> = emptyList(),
    val confirmInjection: Boolean = false,
    val error: String? = null,
    val chatBackground: ChatBackground = ChatBackground.SOLID,
    val backgroundImageUri: String = "",
    val providers: List<ProviderConfigEntity> = emptyList(),
    val modelsByProvider: Map<String, List<ModelConfigEntity>> = emptyMap(),
) {
    val isDeepSeek: Boolean get() = providerId == BalanceService.DEEPSEEK_ID
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
        // 外观：聊天背景（纯色/主题渐变/自定义图片）
        viewModelScope.launch {
            settingsRepository.appearance.collect { appearance ->
                _uiState.update {
                    it.copy(
                        chatBackground = appearance.chatBackground,
                        backgroundImageUri = appearance.backgroundImageUri,
                    )
                }
            }
        }
        // 余额 + 低余额阈值
        viewModelScope.launch {
            combine(balanceService.state, settingsRepository.modelBehavior) { balance, behavior ->
                balance to behavior.lowBalanceThreshold
            }.collect { (balance, threshold) ->
                _uiState.update {
                    it.copy(
                        balance = balance,
                        lowBalance = balance != null && balance.error == null && balance.totalBalance < threshold,
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

    fun send() {
        val state = _uiState.value
        if (state.isStreaming) return
        val text = state.input.trim()
        if (text.isEmpty() && state.pendingAttachments.isEmpty()) return
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
            performSend(text)
        }
    }

    /** 用户确认带注入嫌疑的消息继续发送。 */
    fun confirmInjectionSend() {
        _uiState.update { it.copy(confirmInjection = false, injectionWarnings = emptyList()) }
        val text = _uiState.value.input.trim()
        viewModelScope.launch { performSend(text) }
    }

    fun dismissInjectionWarning() {
        _uiState.update { it.copy(confirmInjection = false, injectionWarnings = emptyList()) }
    }

    private suspend fun performSend(text: String) {
        val snapshot = _uiState.value
        if (snapshot.isStreaming) return

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
        launchStream(conversationId, userId)
    }

    // ---------------- 流式请求 ----------------

    private fun launchStream(conversationId: Long, latestUserMessageId: Long) {
        streamJob = viewModelScope.launch {
            val state = _uiState.value
            val behavior = settingsRepository.modelBehavior.first()
            val provider = providerRepository.getProvider(state.providerId)
            val model = providerRepository.getModel(state.providerId, state.modelId)
            if (provider == null || !provider.enabled || model == null || !model.enabled) {
                _uiState.update { it.copy(error = "当前 Provider 或模型不可用，请在抽屉中重新选择模型") }
                return@launch
            }
            val token = providerRepository.readApiKey(provider.id)
            if (token.isNullOrBlank()) {
                _uiState.update { it.copy(error = "尚未配置 ${provider.displayName} 的 API Key，请到设置 → 模型与 Provider 中填写") }
                return@launch
            }

            // 历史消息（含当前轮）
            val history = messageRepository.getByConversation(conversationId)
            val currentAttachments = attachmentRepository.attachmentsForMessage(latestUserMessageId)
            val conversation = conversationRepository.getById(conversationId)
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
                // 用户主动停止：保存已生成的部分内容（NonCancellable 保证落库完成）
                uiRefresher.cancel()
                _uiState.update {
                    it.copy(
                        streamingContent = contentBuilder.toString(),
                        streamingReasoning = reasoningBuilder.toString(),
                    )
                }
                withContext(NonCancellable + Dispatchers.IO) {
                    persistAssistantMessage(
                        conversationId = conversationId,
                        model = model,
                        content = contentBuilder.toString(),
                        reasoningTokens = tokenEstimator.estimateTokens(reasoningBuilder.toString(), model.modelId),
                        usage = serverUsage,
                        estimatedPromptTokens = estimatedPromptTokens,
                        startedAt = startedAt,
                        finishReason = "cancelled",
                    )
                }
                _uiState.update { it.copy(isStreaming = false, streamingContent = "", streamingReasoning = "", streamingEstimatedTokens = 0) }
                throw c
            }

            uiRefresher.cancel()
            _uiState.update {
                it.copy(
                    streamingContent = contentBuilder.toString(),
                    streamingReasoning = reasoningBuilder.toString(),
                )
            }

            persistAssistantMessage(
                conversationId = conversationId,
                model = model,
                content = contentBuilder.toString(),
                reasoningTokens = tokenEstimator.estimateTokens(reasoningBuilder.toString(), model.modelId),
                usage = serverUsage,
                estimatedPromptTokens = estimatedPromptTokens,
                startedAt = startedAt,
                finishReason = finishReason,
            )
            _uiState.update { it.copy(isStreaming = false, streamingContent = "", streamingReasoning = "", streamingEstimatedTokens = 0) }

            // 首轮结束后异步生成标题；DeepSeek 刷新余额
            launch { runCatching { titleGenerator.onRoundCompleted(conversationId) } }
            if (provider.id == BalanceService.DEEPSEEK_ID) {
                launch { runCatching { balanceService.refresh() } }
            }
        }
    }

    private suspend fun persistAssistantMessage(
        conversationId: Long,
        model: ModelConfigEntity,
        content: String,
        reasoningTokens: Int,
        usage: LlmStreamEvent.Usage?,
        estimatedPromptTokens: Int,
        startedAt: Long,
        finishReason: String?,
    ) {
        if (content.isBlank()) return
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
                    providerId = behavior.defaultProviderId,
                    modelId = behavior.defaultModelId,
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
        if (state.isStreaming || state.conversationId == 0L) return
        val lastAssistant = state.messages.lastOrNull { it.role == Role.ASSISTANT } ?: return
        val lastUser = state.messages.lastOrNull { it.role == Role.USER } ?: return
        viewModelScope.launch {
            messageRepository.delete(lastAssistant.id)
            launchStream(state.conversationId, lastUser.id)
        }
    }

    fun deleteMessage(messageId: Long) {
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

        /** 流式 UI 刷新间隔（ms）：高频 delta 节流，保证滑动流畅。 */
        const val UI_REFRESH_INTERVAL_MS = 90L

        /** 本地估算时的系统提示词基线（模板 + 安全声明 + 聊天格式开销）。 */
        private const val SYSTEM_PROMPT_BASELINE = 160

        /** 图片消息本地估算基线（detail=auto 常见范围的中低值）。 */
        private const val IMAGE_TOKEN_BASELINE = 300
    }
}
