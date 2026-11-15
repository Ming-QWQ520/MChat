package com.ming.mchat.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ming.mchat.core.attachment.AttachmentRepository
import com.ming.mchat.core.data.datastore.AppearanceSettings
import com.ming.mchat.core.data.datastore.AttachmentSettings
import com.ming.mchat.core.data.datastore.ModelBehaviorSettings
import com.ming.mchat.core.data.datastore.PromptSafetySettings
import com.ming.mchat.core.data.datastore.SettingsRepository
import com.ming.mchat.core.data.datastore.TitleSettings
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.PromptPresetEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.repo.ConversationRepository
import com.ming.mchat.core.data.repo.MessageRepository
import com.ming.mchat.core.data.repo.PresetRepository
import com.ming.mchat.core.data.repo.ProviderRepository
import com.ming.mchat.core.data.repo.UsageRepository
import com.ming.mchat.core.llm.BalanceService
import com.ming.mchat.core.llm.ChatService
import com.ming.mchat.core.llm.TokenEstimator
import com.ming.mchat.core.llm.readableMessage
import com.ming.mchat.feature.title.TitleGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExportMessage(
    val role: String,
    val content: String,
    val createdAt: Long,
    val model: String? = null,
    val totalTokens: Int? = null,
)

@Serializable
data class ExportConversation(
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ExportMessage> = emptyList(),
)

@Serializable
data class ExportRoot(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val conversations: List<ExportConversation>,
)

@Serializable
data class ExportPreset(
    val name: String,
    val description: String,
    val icon: String,
    val systemPrompt: String,
    val variablesJson: String,
    val modelHint: String? = null,
    val temperature: Double? = null,
)

@Serializable
data class ExportPresetsRoot(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val presets: List<ExportPreset>,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providerRepository: ProviderRepository,
    private val presetRepository: PresetRepository,
    private val usageRepository: UsageRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val chatService: ChatService,
    private val balanceService: BalanceService,
    private val titleGenerator: TitleGenerator,
    private val tokenEstimator: TokenEstimator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> =
        settingsRepository.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())
    val modelBehavior: StateFlow<ModelBehaviorSettings> =
        settingsRepository.modelBehavior.stateIn(viewModelScope, SharingStarted.Eagerly, ModelBehaviorSettings())
    val promptSafety: StateFlow<PromptSafetySettings> =
        settingsRepository.promptSafety.stateIn(viewModelScope, SharingStarted.Eagerly, PromptSafetySettings())
    val title: StateFlow<TitleSettings> =
        settingsRepository.title.stateIn(viewModelScope, SharingStarted.Eagerly, TitleSettings())
    val attachment: StateFlow<AttachmentSettings> =
        settingsRepository.attachment.stateIn(viewModelScope, SharingStarted.Eagerly, AttachmentSettings())

    val providers: StateFlow<List<ProviderConfigEntity>> =
        providerRepository.observeProviders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val modelsByProvider: StateFlow<Map<String, List<com.ming.mchat.core.data.entity.ModelConfigEntity>>> =
        providerRepository.observeAllModels()
            .map { all -> all.groupBy { it.providerId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val presets: StateFlow<List<PromptPresetEntity>> =
        presetRepository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val balance: StateFlow<com.ming.mchat.core.llm.BalanceState?> = balanceService.state

    private val _keyStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val apiKeyStatus: StateFlow<Map<String, Boolean>> = _keyStatus.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val usageSince30Days: StateFlow<List<com.ming.mchat.core.data.entity.UsageDailyEntity>> =
        usageRepository.observeSince(java.time.LocalDate.now().minusDays(30).toString())
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val auxUsage: StateFlow<List<com.ming.mchat.core.data.entity.AuxUsageEntity>> =
        usageRepository.observeAuxRecent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refreshKeyStatus()
    }

    fun refreshKeyStatus() {
        viewModelScope.launch {
            val map = mutableMapOf<String, Boolean>()
            providers.value.forEach { map[it.id] = providerRepository.hasApiKey(it.id) }
            _keyStatus.value = map
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun notify(text: String) {
        _message.value = text
    }

    // ---------------- 设置写入 ----------------

    fun setAppearance(value: AppearanceSettings) = viewModelScope.launch { settingsRepository.setAppearance(value) }
    fun setModelBehavior(value: ModelBehaviorSettings) = viewModelScope.launch { settingsRepository.setModelBehavior(value) }
    fun setPromptSafety(value: PromptSafetySettings) = viewModelScope.launch { settingsRepository.setPromptSafety(value) }
    fun setTitle(value: TitleSettings) = viewModelScope.launch { settingsRepository.setTitle(value) }
    fun setAttachment(value: AttachmentSettings) = viewModelScope.launch { settingsRepository.setAttachment(value) }

    // ---------------- Provider ----------------

    fun saveApiKey(providerId: String, key: String) {
        viewModelScope.launch {
            providerRepository.saveApiKey(providerId, key)
            refreshKeyStatus()
            notify("API Key 已加密保存（Android Keystore）")
        }
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        viewModelScope.launch { providerRepository.setEnabled(providerId, enabled) }
    }

    fun setModelEnabled(modelKey: String, enabled: Boolean) {
        viewModelScope.launch { providerRepository.setModelEnabled(modelKey, enabled) }
    }

    fun updateBaseUrl(providerId: String, baseUrl: String) {
        viewModelScope.launch {
            providerRepository.getProvider(providerId)?.let {
                providerRepository.updateProvider(it.copy(baseUrl = baseUrl.trim().trimEnd('/')))
            }
        }
    }

    fun setDefaultModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            settingsRepository.setModelBehavior(modelBehavior.value.copy(defaultProviderId = providerId, defaultModelId = modelId))
            notify("默认模型已设为 $modelId")
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            providerRepository.deleteProvider(providerId)
            notify("已删除自定义 Provider")
        }
    }

    fun testConnection(providerId: String) {
        viewModelScope.launch {
            _testing.value = true
            val provider = providerRepository.getProvider(providerId)
            if (provider == null) {
                notify("Provider 不存在")
            } else {
                val key = providerRepository.readApiKey(providerId)
                val model = providerRepository.getModels(providerId).firstOrNull { it.enabled }
                    ?: providerRepository.getModels(providerId).firstOrNull()
                if (model == null) {
                    notify("该 Provider 下没有可用模型")
                } else {
                    when (val result = chatService.testConnection(providerId, provider.baseUrl, key.orEmpty(), model)) {
                        is ChatService.TestConnectionResult.Success -> {
                            notify("连接成功（${result.latencyMs}ms）")
                            usageRepository.recordAux(
                                "TEST_CONNECTION", null, providerId, model.modelId, 10, 1, "ESTIMATED",
                            )
                        }
                        is ChatService.TestConnectionResult.Failure -> notify("连接失败：${result.message}")
                    }
                }
            }
            if (providerId == BalanceService.DEEPSEEK_ID) balanceService.refresh()
            _testing.value = false
        }
    }

    fun refreshBalance() {
        viewModelScope.launch { balanceService.refresh() }
    }

    // ---------------- 预设 ----------------

    fun savePreset(preset: PromptPresetEntity) {
        viewModelScope.launch {
            if (preset.id == 0L) presetRepository.insert(preset) else presetRepository.update(preset)
            notify("预设已保存")
        }
    }

    fun deletePreset(presetId: Long) {
        viewModelScope.launch {
            presetRepository.delete(presetId)
            if (promptSafety.value.defaultPresetId == presetId) {
                settingsRepository.setPromptSafety(promptSafety.value.copy(defaultPresetId = -1L))
            }
            notify("预设已删除")
        }
    }

    fun setDefaultPreset(presetId: Long) {
        viewModelScope.launch {
            settingsRepository.setPromptSafety(promptSafety.value.copy(defaultPresetId = presetId))
        }
    }

    // ---------------- 标题 ----------------

    fun regenerateAllTitles() {
        viewModelScope.launch {
            var count = 0
            conversationRepository.observeAll().let { }
            val all = conversationRepository.observeAll().first()
            all.filter { it.titleSource == com.ming.mchat.core.data.entity.TitleSource.AUTO_TRUNCATE || it.titleSource == com.ming.mchat.core.data.entity.TitleSource.DEFAULT }
                .forEach { conversation ->
                    titleGenerator.forceGenerate(conversation.id)
                    count++
                }
            notify("已重新生成 $count 个会话标题")
        }
    }

    // ---------------- 数据管理 ----------------

    fun exportConversations(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val conversations = conversationRepository.observeAll().first()
                val payload = ExportRoot(
                    exportedAt = System.currentTimeMillis(),
                    conversations = conversations.map { conversation ->
                        val messages = messageRepository.getByConversation(conversation.id)
                        ExportConversation(
                            title = conversation.title,
                            providerId = conversation.providerId,
                            modelId = conversation.modelId,
                            createdAt = conversation.createdAt,
                            updatedAt = conversation.updatedAt,
                            messages = messages.map { it.toExportMessage() },
                        )
                    },
                )
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json.encodeToString(ExportRoot.serializer(), payload))
                } ?: error("无法写入文件")
            }.onSuccess { notify("会话已导出") }
                .onFailure { notify("导出失败：${it.message}") }
        }
    }

    fun importConversations(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                val root = json.decodeFromString(ExportRoot.serializer(), text)
                var count = 0
                root.conversations.forEach { export ->
                    val conversation = conversationRepository.create(export.providerId, export.modelId)
                    conversationRepository.rename(conversation.id, export.title)
                    export.messages.forEach { message ->
                        messageRepository.insert(
                            MessageEntity(
                                conversationId = conversation.id,
                                role = if (message.role == "ASSISTANT") Role.ASSISTANT else Role.USER,
                                content = message.content,
                                createdAt = message.createdAt,
                                model = message.model,
                                totalTokens = message.totalTokens,
                            )
                        )
                    }
                    count++
                }
                notify("已导入 $count 个会话")
            }.onFailure { notify("导入失败：${it.message}") }
        }
    }

    fun exportPresets(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val presets = presetRepository.getAll()
                val payload = ExportPresetsRoot(
                    exportedAt = System.currentTimeMillis(),
                    presets = presets.map {
                        ExportPreset(it.name, it.description, it.icon, it.systemPrompt, it.variablesJson, it.modelHint, it.temperature)
                    },
                )
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json.encodeToString(ExportPresetsRoot.serializer(), payload))
                } ?: error("无法写入文件")
            }.onSuccess { notify("预设已导出") }
                .onFailure { notify("导出失败：${it.message}") }
        }
    }

    fun importPresets(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                val root = json.decodeFromString(ExportPresetsRoot.serializer(), text)
                root.presets.forEach { preset ->
                    presetRepository.insert(
                        PromptPresetEntity(
                            name = preset.name,
                            description = preset.description,
                            icon = preset.icon,
                            systemPrompt = preset.systemPrompt,
                            variablesJson = preset.variablesJson,
                            modelHint = preset.modelHint,
                            temperature = preset.temperature,
                            isBuiltin = false,
                            sortOrder = 100,
                        )
                    )
                }
                notify("已导入 ${root.presets.size} 个预设")
            }.onFailure { notify("导入失败：${it.message}") }
        }
    }

    fun clearAttachmentCache() {
        viewModelScope.launch {
            val freed = attachmentRepository.clearCache()
            notify("已清理附件缓存（${freed / 1024 / 1024} MB）")
        }
    }

    fun clearUsageStats() {
        viewModelScope.launch {
            usageRepository.clearAll()
            notify("用量统计已清空")
        }
    }

    /** 一键清除本地数据（会话 + 附件 + 统计；保留设置与 Key）。 */
    fun clearAllLocalData() {
        viewModelScope.launch {
            val conversations = conversationRepository.observeAll().first()
            conversations.forEach { conversationRepository.deleteCascade(it.id) }
            attachmentRepository.clearCache()
            usageRepository.clearAll()
            notify("本地会话数据已清除")
        }
    }

    private fun MessageEntity.toExportMessage() = ExportMessage(
        role = role.name,
        content = content,
        createdAt = createdAt,
        model = model,
        totalTokens = totalTokens,
    )
}
