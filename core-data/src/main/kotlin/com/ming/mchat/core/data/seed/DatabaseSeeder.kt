package com.ming.mchat.core.data.seed

import com.ming.mchat.core.data.dao.ModelDao
import com.ming.mchat.core.data.dao.PresetDao
import com.ming.mchat.core.data.dao.ProviderDao
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.PromptPresetEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 首次启动种子数据：四个内置 Provider（只插不覆盖用户 Key）+ 内置提示词预设。
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val presetDao: PresetDao,
) {

    fun seedAsync() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { seed() }
        }
    }

    suspend fun seed() {
        if (providerDao.count() == 0) {
            providerDao.insertAll(builtinProviders)
            modelDao.insertAll(builtinModels)
        }
        if (presetDao.count() == 0) {
            presetDao.insertAll(builtinPresets)
        }
    }

    companion object {
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
        )

        val builtinProviders = listOf(
            ProviderConfigEntity(
                id = "deepseek", presetId = "deepseek", displayName = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1", isBuiltin = true, sortOrder = 0,
            ),
            ProviderConfigEntity(
                id = "openai", presetId = "openai", displayName = "ChatGPT",
                baseUrl = "https://api.openai.com/v1", isBuiltin = true, sortOrder = 1,
            ),
            ProviderConfigEntity(
                id = "zhipu", presetId = "zhipu", displayName = "智谱 GLM",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4", isBuiltin = true, sortOrder = 2,
            ),
            ProviderConfigEntity(
                id = "grok", presetId = "grok", displayName = "Grok",
                baseUrl = "https://api.x.ai/v1", isBuiltin = true, sortOrder = 3,
            ),
        )

        val builtinModels = listOf(
            // DeepSeek
            model("deepseek", "deepseek-chat", "deepseek-chat", contextTokens = 131072, maxOutputTokens = 8192),
            model(
                "deepseek", "deepseek-reasoner", "deepseek-reasoner",
                contextTokens = 131072, maxOutputTokens = 8192,
                supportsReasoning = true, supportsTemperature = false,
            ),
            // ChatGPT
            model("openai", "gpt-4o", "gpt-4o", contextTokens = 128000, maxOutputTokens = 16384, supportsVision = true),
            model("openai", "gpt-4o-mini", "gpt-4o-mini", contextTokens = 128000, maxOutputTokens = 16384, supportsVision = true),
            model(
                "openai", "o3-mini", "o3-mini", contextTokens = 128000, maxOutputTokens = 65536,
                supportsReasoning = true, supportsTemperature = false,
                useMaxCompletionTokens = true, systemRole = "developer",
            ),
            // 智谱 GLM
            model("zhipu", "glm-4-plus", "glm-4-plus", contextTokens = 128000, maxOutputTokens = 8192),
            model("zhipu", "glm-4v-plus", "glm-4v-plus", contextTokens = 8192, maxOutputTokens = 8192, supportsVision = true),
            model("zhipu", "glm-4-flash", "glm-4-flash", contextTokens = 128000, maxOutputTokens = 8192),
            // Grok
            model("grok", "grok-2-latest", "grok-2-latest", contextTokens = 32768, maxOutputTokens = 8192),
            model("grok", "grok-2-vision-latest", "grok-2-vision-latest", contextTokens = 32768, maxOutputTokens = 8192, supportsVision = true),
            model("grok", "grok-beta", "grok-beta", contextTokens = 131072, maxOutputTokens = 8192, supportsStreamUsage = false),
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
