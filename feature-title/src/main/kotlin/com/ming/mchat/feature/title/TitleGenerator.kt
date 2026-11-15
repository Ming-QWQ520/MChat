package com.ming.mchat.feature.title

import com.ming.mchat.core.common.StringExt
import com.ming.mchat.core.common.TitleLanguage
import com.ming.mchat.core.data.datastore.SettingsRepository
import com.ming.mchat.core.data.datastore.TitleSettings
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.repo.ConversationRepository
import com.ming.mchat.core.data.repo.MessageRepository
import com.ming.mchat.core.data.repo.ProviderRepository
import com.ming.mchat.core.data.repo.UsageRepository
import com.ming.mchat.core.llm.ChatService
import com.ming.mchat.core.llm.TokenEstimator
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

/**
 * 会话标题生成器：
 * - 首轮（user==1 且 assistant==1）流式结束落库后异步触发；
 * - 成功写 AUTO_LLM；失败保持 AUTO_TRUNCATE，不重试不打扰；
 * - MANUAL / AUTO_LLM 之后任何自动流程不可覆盖（SQL 守卫）；
 * - 按 convId 用 Mutex 防并发；记入 aux_usage。
 */
@Singleton
class TitleGenerator @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val chatService: ChatService,
    private val tokenEstimator: TokenEstimator,
    private val usageRepository: UsageRepository,
) {

    private val locks = ConcurrentHashMap<Long, Mutex>()

    /** 首轮自动触发。 */
    suspend fun onRoundCompleted(conversationId: Long) {
        val settings = settingsRepository.title.first()
        if (!settings.titleGenEnabled) return
        val lock = locks.getOrPut(conversationId) { Mutex() }
        if (!lock.tryLock()) return
        try {
            val userCount = messageRepository.countByRole(conversationId, Role.USER)
            val assistantCount = messageRepository.countByRole(conversationId, Role.ASSISTANT)
            if (userCount != 1 || assistantCount != 1) return
            generate(conversationId, settings)
        } finally {
            lock.unlock()
        }
    }

    /** 手动重新生成（忽略首轮判定；MANUAL 需 UI 二次确认后调用）。 */
    suspend fun forceGenerate(conversationId: Long) {
        val settings = settingsRepository.title.first()
        val lock = locks.getOrPut(conversationId) { Mutex() }
        lock.lock()
        try {
            generate(conversationId, settings)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun generate(conversationId: Long, settings: TitleSettings) {
        val conversation = conversationRepository.getById(conversationId) ?: return
        val firstUser = messageRepository.firstUserMessage(conversationId) ?: return
        val picked = pickTitleModel(settings) ?: return
        val (provider, model) = picked

        val languageHint = when (settings.titleGenLanguage) {
            TitleLanguage.CHINESE -> "标题使用中文。"
            TitleLanguage.ENGLISH -> "The title must be written in English."
            TitleLanguage.AUTO -> "标题语言与用户消息的语言保持一致。"
        }
        val systemPrompt = "你是对话标题生成器。根据用户的第一条消息生成一个简短标题。$languageHint" +
            "标题不超过 12 个汉字或 6 个英文单词；不要输出引号、句号逗号或“标题：/Title:”等前缀；直接输出标题本身。"
        val userPrompt = firstUser.content.take(2000)

        val promptTokens = tokenEstimator.estimateTokens(systemPrompt + userPrompt)
        val rawTitle: String = try {
            withTimeout(TIMEOUT_MILLIS) {
                chatService.completeOnce(
                    providerId = provider.id,
                    baseUrl = provider.baseUrl,
                    token = providerRepository.readApiKey(provider.id).orEmpty(),
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    maxTokens = 40,
                    temperature = 0.2,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return // 失败保持 AUTO_TRUNCATE，不重试
        }

        val title = sanitizeTitle(rawTitle)
        if (title.isBlank()) return

        val updated = conversationRepository.setAutoLlmTitle(conversationId, title)
        if (updated > 0) {
            usageRepository.recordAux(
                kind = "TITLE",
                refId = conversationId,
                providerId = provider.id,
                modelId = model.modelId,
                promptTokens = promptTokens,
                completionTokens = tokenEstimator.estimateTokens(title),
                source = "ESTIMATED",
            )
        }
    }

    /** 标题模型：手动指定优先；否则自动选最便宜的非推理模型。 */
    private suspend fun pickTitleModel(settings: TitleSettings): Pair<ProviderConfigEntity, ModelConfigEntity>? {
        if (settings.titleGenProviderId.isNotBlank() && settings.titleGenModelId.isNotBlank()) {
            val provider = providerRepository.getProvider(settings.titleGenProviderId)
            val model = providerRepository.getModel(settings.titleGenProviderId, settings.titleGenModelId)
            if (provider?.enabled == true && model?.enabled == true) return provider to model
        }
        val enabledProviders = providerRepository.observeProviders().first().filter { it.enabled }
        if (enabledProviders.isEmpty()) return null
        val allModels = providerRepository.observeAllModels().first()
            .filter { it.enabled && enabledProviders.any { p -> p.id == it.providerId } }
        if (allModels.isEmpty()) return null
        val nonReasoning = allModels.filter { !it.supportsReasoning }
        val pool = nonReasoning.ifEmpty { allModels }
        val cheapOrder = listOf(
            "glm-4-flash", "gpt-4o-mini", "deepseek-chat", "grok-beta",
            "glm-4-plus", "gpt-4o", "grok-2-latest",
        )
        val chosen = pool.minByOrNull { m ->
            val index = cheapOrder.indexOf(m.modelId)
            if (index >= 0) index else Int.MAX_VALUE
        } ?: return null
        val provider = enabledProviders.first { it.id == chosen.providerId }
        return provider to chosen
    }

    companion object {
        private const val TIMEOUT_MILLIS = 15_000L

        /** 去引号 / “标题：” 前缀 / 句号逗号，压缩空白，按 CJK/拉丁截断。 */
        fun sanitizeTitle(raw: String): String {
            var t = raw.trim()
            t = t.trim('“', '”', '"', '\'', '‘', '’', '「', '」', '『', '』', '`', ' ')
            t = t.replace(Regex("(?i)^(标题|title)\\s*[:：]\\s*"), "")
            t = t.replace(Regex("[。，,.；;！!？?]"), "")
            t = t.replace(Regex("\\s+"), " ").trim()
            return StringExt.truncateTitle(t)
        }
    }
}
