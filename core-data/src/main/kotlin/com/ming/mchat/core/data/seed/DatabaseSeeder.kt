package com.ming.mchat.core.data.seed

import com.ming.mchat.core.data.dao.ConversationDao
import com.ming.mchat.core.data.dao.ModelDao
import com.ming.mchat.core.data.dao.PresetDao
import com.ming.mchat.core.data.dao.ProviderDao
import com.ming.mchat.core.data.datastore.SettingsRepository
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.PromptPresetEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 首次启动种子数据：
 * - 内置供应商仅 DeepSeek（模型 deepseek-flash / deepseek-v4-pro，默认 deepseek-flash）；
 * - 其他 LLM 供应商由用户在设置中自行添加（Base URL / API Key / 模型）；
 * - 已有安装升级时同步内置模型目录（旧模型名 remap；用户清空过模型目录则不复活）；
 * - 不动用户已保存的 API Key。
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val presetDao: PresetDao,
    private val conversationDao: ConversationDao,
    private val settingsRepository: SettingsRepository,
) {

    fun seedAsync() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { seed() }
        }
    }

    suspend fun seed() {
        if (providerDao.count() == 0) {
            providerDao.insertAll(builtinProviders)
            modelDao.insertAll(deepseekCurrentModels)
        } else {
            upgradeBuiltinCatalog()
        }
        if (presetDao.count() == 0) {
            presetDao.insertAll(builtinPresets)
        }
        migrateLegacyDefaultModel()
    }

    /** 存量安装：DataStore 默认模型名指向旧模型时，同步 remap 到新名。 */
    private suspend fun migrateLegacyDefaultModel() {
        runCatching {
            val behavior = settingsRepository.modelBehavior.first()
            val mapped = deepseekLegacyRemap[behavior.defaultModelId]
            if (mapped != null && behavior.defaultProviderId == DEEPSEEK_ID) {
                settingsRepository.setModelBehavior(behavior.copy(defaultModelId = mapped))
            }
        }
    }

    /**
     * 内置目录升级（幂等）：
     * 1. DeepSeek 供应商缺则补插（baseUrl 以官方文档为准，不带 /v1）；
     * 2. 模型目录**不自动补插**（用户删除过的模型不复活）；
     *    全新安装时才插入内置模型，存量升级可在设置页手动“恢复内置模型”；
     * 3. 旧 DeepSeek 模型名 remap：deepseek-4.1-* / deepseek-chat / deepseek-reasoner → 新名；
     * 4. 旧版本内置的 openai / zhipu / grok 供应商：未配置 Key 的直接删除；
     *    已配置 Key 的保留（不打断存量用户），仅重置 isBuiltin=false 转为自定义供应商。
     */
    private suspend fun upgradeBuiltinCatalog() {
        // 1. DeepSeek 供应商兜底
        val deepseek = providerDao.getById(DEEPSEEK_ID)
        if (deepseek == null) {
            providerDao.insertAll(builtinProviders)
        }

        // 2. 旧模型名 remap（含禁用状态的旧模型）
        deepseekLegacyRemap.forEach { (legacyId, newId) ->
            val legacyKey = "$DEEPSEEK_ID:$legacyId"
            if (modelDao.getById(legacyKey) != null) {
                modelDao.setEnabled(legacyKey, false)
                conversationDao.remapModel(DEEPSEEK_ID, legacyId, newId)
            }
        }

        // 3. 旧内置供应商处理
        legacyBuiltinProviderIds.forEach { legacyId ->
            val provider = providerDao.getById(legacyId) ?: return@forEach
            val hasKey = !provider.apiKeyRef.isNullOrBlank()
            if (!hasKey) {
                // 未使用：删除供应商 + 模型目录，会话整体迁移到 DeepSeek
                val fallback = modelDao.getById("$DEEPSEEK_ID:deepseek-flash")
                if (fallback != null) {
                    conversationDao.remapAllModels(legacyId, DEEPSEEK_ID, fallback.modelId)
                }
                modelDao.deleteByProvider(legacyId)
                providerDao.delete(legacyId)
            } else if (provider.isBuiltin) {
                // 已在使用：保留数据，转为自定义供应商
                providerDao.update(provider.copy(isBuiltin = false))
            }
        }
    }

    /** 设置页“恢复内置模型”：DeepSeek 目录缺失的内置模型补插（IGNORE，不动同名自定义）。 */
    suspend fun restoreBuiltinModels(providerId: String) {
        if (providerId != DEEPSEEK_ID) return
        modelDao.insertAll(deepseekCurrentModels)
    }

    companion object {
        private const val DEEPSEEK_ID = "deepseek"

        /** 旧版本内置、现改由用户自建的供应商。 */
        private val legacyBuiltinProviderIds = listOf("openai", "zhipu", "grok")

        private fun model(
            providerId: String,
            modelId: String,
            displayName: String,
            contextTokens: Int = 32768,
            maxOutputTokens: Int = 8192,
            supportsVision: Boolean = false,
            supportsStreamUsage: Boolean = true,
            supportsReasoning: Boolean = false,
            supportsTemperature: Boolean = true,
            useMaxCompletionTokens: Boolean = false,
            systemRole: String = "system",
        ) = ModelConfigEntity(
            id = "$providerId:$modelId",
            providerId = providerId,
            modelId = modelId,
            displayName = displayName,
            contextTokens = contextTokens,
            maxOutputTokens = maxOutputTokens,
            supportsVision = supportsVision,
            supportsStreamUsage = supportsStreamUsage,
            supportsReasoning = supportsReasoning,
            supportsTemperature = supportsTemperature,
            useMaxCompletionTokens = useMaxCompletionTokens,
            systemRole = systemRole,
            isBuiltin = providerId == DEEPSEEK_ID,
        )

        /** 内置供应商：仅 DeepSeek（官方 Base URL，OpenAI 兼容路径直接可用）。 */
        val builtinProviders = listOf(
            ProviderConfigEntity(
                id = DEEPSEEK_ID,
                presetId = "deepseek",
                displayName = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                isBuiltin = true,
                sortOrder = 0,
            ),
        )

        /**
         * DeepSeek 当前在售模型（官方文档）：
         * - deepseek-flash：默认模型，支持思考（reasoning_effort）与图像理解；
         * - deepseek-v4-pro：思考模型，不支持图片输入。
         */
        val deepseekCurrentModels = listOf(
            model(
                DEEPSEEK_ID, "deepseek-flash", "DeepSeek-Flash",
                contextTokens = 131072, maxOutputTokens = 16384,
                supportsVision = true, supportsReasoning = true, supportsTemperature = true,
            ),
            model(
                DEEPSEEK_ID, "deepseek-v4-pro", "DeepSeek-V4-Pro",
                contextTokens = 131072, maxOutputTokens = 16384,
                supportsVision = false, supportsReasoning = true, supportsTemperature = true,
            ),
        )

        /** 已下线 / 更名的 DeepSeek 旧模型 → 现用模型。 */
        private val deepseekLegacyRemap = mapOf(
            "deepseek-4.1-flash" to "deepseek-flash",
            "deepseek-4.1-pro" to "deepseek-v4-pro",
            "deepseek-chat" to "deepseek-flash",
            "deepseek-reasoner" to "deepseek-v4-pro",
        )

        val builtinPresets = listOf(
            PromptPresetEntity(
                name = "翻译助手",
                description = "将用户内容翻译为指定语言，只输出译文",
                icon = "translate",
                systemPrompt = "你是一名专业翻译。请将用户内容翻译为{{target_lang}}，只输出译文，不要解释。译文风格：{{style}}。",
                variablesJson = """[{"key":"target_lang","label":"目标语言","type":"SELECT","defaultValue":"英文","options":["中文","英文","日文"],"required":true},{"key":"style","label":"译文风格","type":"SELECT","defaultValue":"自然口语","options":["自然口语","正式书面","学术"],"required":false}]""",
                isBuiltin = true,
                sortOrder = 0,
            ),
            PromptPresetEntity(
                name = "文档总结",
                description = "一句话概括 + 关键要点 + 风险提示",
                icon = "summarize",
                systemPrompt = "你是文档总结助手。请阅读用户提供的文档内容，输出：1) 一句话概括；2) 关键要点（不超过 {{points}} 条）；3) 待办与风险提示。输出语言：{{lang}}。",
                variablesJson = """[{"key":"points","label":"要点数量","type":"NUMBER","defaultValue":"5","required":false},{"key":"lang","label":"输出语言","type":"SELECT","defaultValue":"中文","options":["中文","英文"],"required":false}]""",
                isBuiltin = true,
                sortOrder = 1,
            ),
            PromptPresetEntity(
                name = "代码审查",
                description = "按问题/建议/风险输出审查结论",
                icon = "code",
                systemPrompt = "你是资深软件工程师，对用户提交的代码进行审查。关注：{{focus}}。请按【问题】【建议】【风险】分类输出 Markdown，引用具体代码位置。",
                variablesJson = """[{"key":"focus","label":"关注点","type":"MULTILINE","defaultValue":"正确性、可读性、性能","required":false}]""",
                isBuiltin = true,
                sortOrder = 2,
            ),
            PromptPresetEntity(
                name = "写作润色",
                description = "按目标风格改写文本",
                icon = "edit",
                systemPrompt = "你是中文写作润色助手。请改写用户文本，使其{{goal}}，保持原意不增删事实，只输出润色后的文本。",
                variablesJson = """[{"key":"goal","label":"润色目标","type":"SELECT","defaultValue":"更简洁","options":["更简洁","更正式","更生动"],"required":true}]""",
                isBuiltin = true,
                sortOrder = 3,
            ),
        )
    }
}
