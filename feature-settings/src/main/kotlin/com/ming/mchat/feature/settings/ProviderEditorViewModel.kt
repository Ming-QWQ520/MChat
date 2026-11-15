package com.ming.mchat.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ming.mchat.core.data.datastore.ModelBehaviorSettings
import com.ming.mchat.core.data.datastore.SettingsRepository
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.repo.ProviderRepository
import com.ming.mchat.core.llm.ChatService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val chatService: ChatService,
) : ViewModel() {

    val providerId: String = savedStateHandle.get<String>("providerId").orEmpty()

    val provider: StateFlow<ProviderConfigEntity?> =
        providerRepository.observeProviders()
            .map { list -> list.firstOrNull { it.id == providerId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val models: StateFlow<List<ModelConfigEntity>> =
        providerRepository.observeModels(providerId)
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

    init {
        refreshKeyStatus()
    }

    fun refreshKeyStatus() {
        viewModelScope.launch { _keyConfigured.value = providerRepository.hasApiKey(providerId) }
    }

    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            providerRepository.saveApiKey(providerId, key)
            refreshKeyStatus()
            _testResult.value = "API Key 已加密保存"
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { providerRepository.setEnabled(providerId, enabled) }
    }

    fun setModelEnabled(modelKey: String, enabled: Boolean) {
        viewModelScope.launch { providerRepository.setModelEnabled(modelKey, enabled) }
    }

    fun updateBaseUrl(baseUrl: String) {
        viewModelScope.launch {
            providerRepository.getProvider(providerId)?.let {
                providerRepository.updateProvider(it.copy(baseUrl = baseUrl.trim().trimEnd('/')))
            }
            _testResult.value = "Base URL 已更新（末尾斜杠已规范化）"
        }
    }

    fun setDefault(modelId: String) {
        viewModelScope.launch {
            settingsRepository.setModelBehavior(
                behavior.value.copy(defaultProviderId = providerId, defaultModelId = modelId)
            )
            _testResult.value = "默认模型已设为 $modelId"
        }
    }

    fun deleteProvider() {
        viewModelScope.launch {
            providerRepository.deleteProvider(providerId)
            _deleted.value = true
        }
    }

    fun testConnection() {
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
}
