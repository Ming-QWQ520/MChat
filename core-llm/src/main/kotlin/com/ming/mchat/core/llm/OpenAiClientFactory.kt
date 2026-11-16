package com.ming.mchat.core.llm

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.Effort
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.ming.mchat.core.common.OutputTokenMode
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.api.core.Role
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * openai-kotlin 客户端工厂：
 * - baseUrl / token 按 Provider 配置动态构建；
 * - 按 providerId 缓存实例，配置变更时失效重建；
 * - 日志级别 NONE，避免任何敏感信息落日志。
 */
@Singleton
class OpenAiClientFactory @Inject constructor() {

    private data class CacheKey(val baseUrl: String, val token: String)

    private val cache = ConcurrentHashMap<String, Pair<CacheKey, OpenAI>>()

    fun client(providerId: String, baseUrl: String, token: String): OpenAI {
        val normalized = baseUrl.trimEnd('/')
        val key = CacheKey(normalized, token)
        val cached = cache[providerId]
        if (cached != null && cached.first == key) return cached.second
        cached?.second?.let { runCatching { it.close() } }
        val client = OpenAI(
            token = token,
            host = OpenAIHost(baseUrl = "$normalized/"),
            logging = LoggingConfig(logLevel = LogLevel.None),
        )
        cache[providerId] = key to client
        return client
    }

    fun invalidate(providerId: String) {
        cache.remove(providerId)?.second?.let { runCatching { it.close() } }
    }

    companion object {
        /** 统一的请求构建逻辑，供流式 / 非流式复用。 */
        fun buildRequest(params: StreamChatParams): ChatCompletionRequest {
            val messages = buildList {
                if (params.systemPrompt.isNotBlank()) {
                    add(
                        ChatMessage(
                            role = if (params.model.systemRole.equals("developer", ignoreCase = true)) Role.Developer else Role.System,
                            content = params.systemPrompt,
                        )
                    )
                }
                params.messages.forEach { m ->
                    val role = if (m.role.equals("assistant", true)) Role.Assistant else Role.User
                    if (m.imageDataUrls.isEmpty()) {
                        add(ChatMessage(role = role, content = m.text))
                    } else {
                        val parts = buildList {
                            if (m.text.isNotBlank()) add(TextPart(m.text))
                            m.imageDataUrls.forEach { url ->
                                add(ImagePart(url = url, detail = m.imageDetail))
                            }
                        }
                        add(ChatMessage(role = role, content = parts))
                    }
                }
            }

            // 输出 Token 上限：默认不发送 max_tokens；手动模式指定值；自动模式取上下文窗口 50%。
            val maxTokens: Int? = when (params.outputTokenMode) {
                OutputTokenMode.DEFAULT -> null
                OutputTokenMode.MANUAL -> params.outputTokenManualValue.coerceAtLeast(64)
                OutputTokenMode.AUTO -> (params.model.contextTokens / 2)
                    .coerceAtMost(params.model.maxOutputTokens)
            }

            val temperature = if (params.model.supportsTemperature) params.temperature else null

            // 思考强度：仅推理模型；MAX 档不传参数（使用模型默认最高）。
            val effort: Effort? = if (
                params.model.supportsReasoning && params.reasoningEffort != ReasoningEffortLevel.MAX
            ) {
                Effort(params.reasoningEffort.name.lowercase())
            } else {
                null
            }

            return ChatCompletionRequest(
                model = ModelId(params.model.modelId),
                messages = messages,
                temperature = temperature,
                maxTokens = if (params.model.useMaxCompletionTokens) null else maxTokens,
                maxCompletionTokens = if (params.model.useMaxCompletionTokens) maxTokens else null,
                reasoningEffort = effort,
                streamOptions = if (params.model.supportsStreamUsage) StreamOptions(includeUsage = true) else null,
            )
        }
    }
}
