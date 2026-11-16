package com.ming.mchat.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ming.mchat.core.data.datastore.ModelBehaviorSettings
import com.ming.mchat.core.data.datastore.SettingsRepository
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.repo.ProviderRepository
import com.ming.mchat.core.data.seed.DatabaseSeeder
import com.ming.mchat.core.llm.ChatService
import com.ming.mchat.core.llm.readableMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Provider 编辑 / 新建 ViewModel：
 * - 新建模式（providerId == "new"）：填 displayName / Base URL / API Key 创建；
 * - 编辑模式：Base URL / Key / 启用 / 删除；
 * - 模型管理：手动添加、编辑、删除、从 API 拉取（GET /models）、恢复内置模型（DeepSeek）；
 * - 测试连接、设为默认。
 */
@HiltViewModel
class ProviderEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val chatService: ChatService,
    private val databaseSeeder: DatabaseSeeder,
) : ViewModel() {

    val isNew: Boolean = savedStateHandle.get<String>("providerId") == NEW_PROVIDER_ROUTE
    val providerId: String = if (isNew) "" else savedStateHandle.get<String>("providerId").orEmpty()

    val provider: StateFlow<ProviderConfigEntity?> =
        providerRepository.observeProviders()
            .map { list -> list.firstOrNull { it.id == providerId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val models: StateFlow<List<ModelConfigEntity>> =
        providerRepository.observeModels(if (isNew) "\u0000none" else providerId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val behavior: StateFlow<ModelBehaviorSettings> =
        settingsRepository.modelBehavior
            .stateIn(viewModelScope, SharingStarted.Eagerly, ModelBehaviorSettings())

    private val _keyConfigured = MutableStateFlow(false)
    val keyConfigured: StateFlow<Boolean> = _keyConfigured.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _fetchingModels = MutableStateFlow(false)
    val fetchingModels: StateFlow<Boolean> = _fetchingModels.asStateFlow()

    /** 新建成功后要跳转到的 providerId；UI 观察后导航到编辑页。 */
    private val _createdProviderId = MutableStateFlow<String?>(null)
    val createdProviderId: StateFlow<String?> = _createdProviderId.asStateFlow()

    init {
        if (!isNew) refreshKeyStatus()
    }

    fun consumeCreatedEvent() {
        _createdProviderId.value = null
    }

    fun refreshKeyStatus() {
        if (isNew) return
        viewModelScope.launch { _keyConfigured.value = providerRepository.hasApiKey(providerId) }
    }

    // ---------------- 新建供应商 ----------------

    /** 创建自定义供应商（Base URL / API Key / 展示名），成功后通知 UI 跳转编辑页。 */
    fun createProvider(displayName: String, baseUrl: String, apiKey: String) {
        if (displayName.isBlank() || baseUrl.isBlank()) {
            _testResult.value = "请填写供应商名称与 Base URL"
            return
        }
        viewModelScope.launch {
            val entity = providerRepository.createProvider(displayName, baseUrl, apiKey.takeIf { it.isNotBlank() })
            _createdProviderId.value = entity.id
        }
    }

    // ---------------- Provider 编辑 ----------------

    fun saveApiKey(key: String) {
        if (isNew || key.isBlank()) return
        viewModelScope.launch {
            providerRepository.saveApiKey(providerId, key)
            refreshKeyStatus()
            _testResult.value = "API Key 已加密保存"
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (isNew) return
        viewModelScope.launch { providerRepository.setEnabled(providerId, enabled) }
    }

    fun setModelEnabled(modelKey: String, enabled: Boolean) {
        viewModelScope.launch { providerRepository.setModelEnabled(modelKey, enabled) }
    }

    fun updateBaseUrl(baseUrl: String) {
        if (isNew) return
        viewModelScope.launch {
            providerRepository.getProvider(providerId)?.let {
                providerRepository.updateProvider(it.copy(baseUrl = baseUrl.trim().trimEnd('/')))
            }
            _testResult.value = "Base URL 已更新（末尾斜杠已规范化）"
        }
    }

    fun setDefault(modelId: String) {
        if (isNew) return
        viewModelScope.launch {
            settingsRepository.setModelBehavior(
                behavior.value.copy(defaultProviderId = providerId, defaultModelId = modelId)
            )
            _testResult.value = "默认模型已设为 $modelId"
        }
    }

    fun deleteProvider() {
        if (isNew) return
        viewModelScope.launch {
            providerRepository.deleteProvider(providerId)
            _deleted.value = true
        }
    }

    fun testConnection() {
        if (isNew) {
            _testResult.value = "请先保存供应商"
            return
        }
        viewModelScope.launch {
            _testing.value = true
            val provider = providerRepository.getProvider(providerId)
            val key = providerRepository.readApiKey(providerId)
            if (key.isNullOrBlank()) {
                _testResult.value = "尚未配置 API Key"
            } else if (provider == null) {
                _testResult.value = "Provider 不存在"
            } else {
                val model = providerRepository.getModels(providerId).firstOrNull { it.enabled }
                    ?: providerRepository.getModels(providerId).firstOrNull()
                if (model == null) {
                    _testResult.value = "没有可用模型"
                } else {
                    _testResult.value = when (
                        val result = chatService.testConnection(providerId, provider.baseUrl, key, model)
                    ) {
                        is ChatService.TestConnectionResult.Success -> "连接成功（${result.latencyMs}ms）"
                        is ChatService.TestConnectionResult.Failure -> "连接失败：${result.message}"
                    }
                }
            }
            _testing.value = false
        }
    }

    // ---------------- 模型管理（增 / 删 / 改 / 拉取） ----------------

    /** 手动添加 / 编辑模型（modelId 为主键，同 id 覆盖）。 */
    fun saveModel(model: ModelConfigEntity) {
        viewModelScope.launch {
            if (isNew) {
                _testResult.value = "请先保存供应商再添加模型"
                return@launch
            }
            if (model.modelId.isBlank() || model.displayName.isBlank()) {
                _testResult.value = "模型 ID 与名称不能为空"
                return@launch
            }
            providerRepository.upsertModel(model)
            _testResult.value = "模型 ${model.displayName} 已保存"
        }
    }

    /** 删除模型（引用它的会话自动 remap）。 */
    fun deleteModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            providerRepository.deleteModel(providerId, modelId)
            _testResult.value = "模型 $modelId 已删除"
        }
    }

    /** 从供应商 API 拉取模型列表（GET /models）；只新增本地缺失的模型，不覆盖已有配置。 */
    fun fetchModelsFromApi() {
        if (isNew) {
            _testResult.value = "请先保存供应商再拉取模型"
            return
        }
        viewModelScope.launch {
            _fetchingModels.value = true
            val provider = providerRepository.getProvider(providerId)
            val key = providerRepository.readApiKey(providerId)
            when {
                provider == null -> _testResult.value = "Provider 不存在"
                key.isNullOrBlank() -> _testResult.value = "尚未配置 API Key，无法拉取模型列表"
                else -> {
                    try {
                        val isAnthropic = provider.baseUrl.contains("anthropic", ignoreCase = true)
                        val remote = chatService.fetchModels(provider.baseUrl, key, isAnthropic)
                        if (remote.isEmpty()) {
                            _testResult.value = "服务端未返回任何模型"
                        } else {
                            val existing = providerRepository.getModels(providerId).map { it.modelId }.toSet()
                            val toInsert = remote.filter { it.first !in existing }
                            toInsert.forEach { (id, display) ->
                                providerRepository.upsertModel(defaultModelEntity(providerId, id, display))
                            }
                            _testResult.value = if (toInsert.isEmpty()) {
                                "已是最新（服务端共 ${remote.size} 个模型）"
                            } else {
                                "已新增 ${toInsert.size} 个模型（服务端共 ${remote.size} 个）"
                            }
                        }
                    } catch (t: Throwable) {
                        _testResult.value = "拉取失败：${t.readableMessage()}"
                    }
                }
            }
            _fetchingModels.value = false
        }
    }

    /** 恢复 DeepSeek 内置模型目录（deepseek-flash / deepseek-v4-pro）。 */
    fun restoreBuiltinModels() {
        viewModelScope.launch {
            databaseSeeder.restoreBuiltinModels(providerId)
            _testResult.value = "内置模型已恢复"
        }
    }

    private fun defaultModelEntity(providerId: String, modelId: String, displayName: String) =
        ModelConfigEntity(
            id = "$providerId:$modelId",
            providerId = providerId,
            modelId = modelId,
            displayName = displayName,
            contextTokens = 32768,
            maxOutputTokens = 8192,
        )

    companion object {
        const val NEW_PROVIDER_ROUTE = "new"
    }
}
