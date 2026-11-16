package com.ming.mchat.core.llm

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.Effort
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.TextContent
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.exception.OpenAIAPIException
import com.aallam.openai.api.exception.OpenAIException
import com.aallam.openai.api.exception.OpenAIHttpException
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.ming.mchat.core.common.OutputTokenMode
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.ming.mchat.core.data.entity.ModelConfigEntity
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 统一聊天服务：基于 openai-kotlin（Ktor + OkHttp 引擎）。
 * - 流式：[OpenAI.chatCompletions] 返回 Flow<ChatCompletionChunk>，逐事件映射为 [LlmStreamEvent]，
 *   SSE 解析完全由库完成，无手写解析；
 * - 取消：收集协程取消时 Ktor 请求同步取消，CancellationException 不被吞掉；
 * - 思考模式：reasoning_effort 对齐 DeepSeek 取值（none/low/high/max，[Effort] 为值类可承载任意取值）；
 * - reasoning_content：openai-kotlin ChatDelta 原生字段；
 * - 错误：OpenAIAPIException 携带 HTTP 状态码与 Provider 错误详情，不吞 400/413。
 */
@Singleton
class ChatService @Inject constructor(
    private val clientFactory: OpenAiClientFactory,
    private val auxHttp: AuxHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun streamChat(params: StreamChatParams): Flow<LlmStreamEvent> {
        val openAI = clientFactory.client(params.providerId, params.baseUrl, params.token)
        val request = buildRequest(params, stream = true)

        return flow {
            var finishReason: String? = null
            try {
                openAI.chatCompletions(request).collect { chunk ->
                    // usage 出现在最后一个 chunk（stream_options.include_usage）
                    chunk.usage?.let { usage ->
                        emit(
                            LlmStreamEvent.Usage(
                                promptTokens = usage.promptTokens ?: 0,
                                completionTokens = usage.completionTokens ?: 0,
                                totalTokens = usage.totalTokens ?: 0,
                            )
                        )
                    }
                    chunk.choices.firstOrNull()?.let { choice ->
                        choice.delta?.content?.takeIf { it.isNotEmpty() }?.let { emit(LlmStreamEvent.Delta(it)) }
                        choice.delta?.reasoningContent?.takeIf { it.isNotEmpty() }?.let { emit(LlmStreamEvent.Reasoning(it)) }
                        choice.delta?.toolCalls?.takeIf { it.isNotEmpty() }?.let { emit(LlmStreamEvent.ToolCallDelta(it.size)) }
                        choice.finishReason?.let { finishReason = it.value }
                    }
                }
                emit(LlmStreamEvent.Done(finishReason))
            } catch (cause: Throwable) {
                // 取消正常传播；其余异常统一映射为 Error 事件（透传状态码与详情）
                if (cause is CancellationException) throw cause
                emit(cause.toLlmError())
            }
        }
    }

    /**
     * 非流式补全（标题生成 / 测试连接等辅助请求）。
     * 返回首个 choice 的文本；HTTP 错误抛出并保留状态码（见 [toLlmError]）。
     */
    suspend fun completeOnce(
        providerId: String,
        baseUrl: String,
        token: String,
        model: ModelConfigEntity,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double,
    ): String {
        val openAI = clientFactory.client(providerId, baseUrl, token)
        val request = chatCompletionRequest {
            this.model = ModelId(model.modelId)
            messages = buildList {
                if (systemPrompt.isNotBlank()) {
                    add(
                        if (model.systemRole.equals("developer", true)) {
                            ChatMessage.Developer(systemPrompt)
                        } else {
                            ChatMessage.System(systemPrompt)
                        }
                    )
                }
                add(ChatMessage.User(userPrompt))
            }
            if (model.supportsTemperature) this.temperature = temperature
            if (model.useMaxCompletionTokens) {
                maxCompletionTokens = maxTokens
            } else {
                this.maxTokens = maxTokens
            }
        }
        val completion = openAI.chatCompletion(request)
        return completion.choices.firstOrNull()?.message?.contentText().orEmpty().trim()
    }

    /** 设置页“测试连接”：maxTokens=1 的非流式短请求，12 秒超时。 */
    suspend fun testConnection(
        providerId: String,
        baseUrl: String,
        token: String,
        model: ModelConfigEntity,
    ): ChatService.TestConnectionResult {
        if (token.isBlank()) return TestConnectionResult.Failure(message = "尚未配置 API Key")
        return try {
            val start = System.currentTimeMillis()
            withTimeout(TEST_TIMEOUT_MILLIS) {
                completeOnce(
                    providerId = providerId,
                    baseUrl = baseUrl,
                    token = token,
                    model = model,
                    systemPrompt = "You are a connectivity test.",
                    userPrompt = "Reply with the single word: OK",
                    maxTokens = 1,
                    temperature = 0.0,
                )
            }
            TestConnectionResult.Success(latencyMs = System.currentTimeMillis() - start)
        } catch (t: Throwable) {
            if (t is TimeoutCancellationException) {
                TestConnectionResult.Failure(message = "连接超时（>${TEST_TIMEOUT_MILLIS / 1000}s），请检查网络或 Base URL")
            } else {
                TestConnectionResult.Failure(message = t.readableMessage())
            }
        }
    }

    /**
     * 拉取供应商模型列表（GET /models）。
     * - OpenAI 兼容端点：openai-kotlin models()，Bearer 认证；
     * - Anthropic 端点：Ktor 直连，x-api-key + anthropic-version；
     * 返回 (modelId, displayName) 对。
     */
    suspend fun fetchModels(
        baseUrl: String,
        token: String,
        isAnthropic: Boolean = false,
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (!isAnthropic) {
            val openAI = clientFactory.client("fetch:$baseUrl", baseUrl, token)
            return@withContext openAI.models().map { it.id.id to it.id.id }
        }
        // Anthropic 风格端点直连（Ktor）
        val url = OpenAiClientFactory.normalizeBaseUrl(baseUrl) + "/models"
        val response = auxHttp.client.get(url) {
            header("x-api-key", token)
            header("anthropic-version", "2023-06-01")
        }
        if (response.status != HttpStatusCode.OK) {
            val body = response.bodyAsText()
            throw HttpLlmException(response.status.value, ErrorCodeMapper.describe(response.status.value, parseServerMessage(body)))
        }
        val root = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
            ?: throw IOException("响应格式无法解析")
        root["data"]?.jsonArray?.mapNotNull { element ->
            runCatching { element.jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
        }?.map { it to it } ?: emptyList()
    }

    // ---------------- 内部工具 ----------------

    private fun buildRequest(params: StreamChatParams, stream: Boolean): com.aallam.openai.api.chat.ChatCompletionRequest {
        val messages = buildList {
            if (params.systemPrompt.isNotBlank()) {
                add(
                    if (params.model.systemRole.equals("developer", true)) {
                        ChatMessage.Developer(params.systemPrompt)
                    } else {
                        ChatMessage.System(params.systemPrompt)
                    }
                )
            }
            params.messages.forEach { m ->
                if (m.imageDataUrls.isEmpty()) {
                    add(if (m.role.equals("assistant", true)) ChatMessage.Assistant(m.text) else ChatMessage.User(m.text))
                } else {
                    val parts = buildList {
                        if (m.text.isNotBlank()) add(TextPart(m.text))
                        m.imageDataUrls.forEach { url -> add(ImagePart(url = url, detail = m.imageDetail)) }
                    }
                    add(ChatMessage.User(parts))
                }
            }
        }

        // 输出 Token 上限：默认不发送；手动模式指定值；自动模式取上下文窗口 50%。
        val maxTokens: Int? = when (params.outputTokenMode) {
            OutputTokenMode.DEFAULT -> null
            OutputTokenMode.MANUAL -> params.outputTokenManualValue.coerceAtLeast(64)
            OutputTokenMode.AUTO -> (params.model.contextTokens / 2).coerceAtMost(params.model.maxOutputTokens)
        }

        // 思考强度（对齐 DeepSeek reasoning_effort：none/low/high/max）：
        // - 仅声明支持推理的模型发送；
        // - DeepSeek 供应商：NONE 发送 "none" 显式关闭思考；
        // - 其他供应商：NONE 不发送参数（多数端点不识别 "none"）。
        val thinkingEnabled = params.reasoningEffort != ReasoningEffortLevel.NONE
        val effortParam: Effort? = if (params.model.supportsReasoning) {
            when (params.reasoningEffort) {
                ReasoningEffortLevel.NONE ->
                    if (params.providerId == BalanceService.DEEPSEEK_ID) Effort("none") else null
                ReasoningEffortLevel.LOW -> Effort("low")
                ReasoningEffortLevel.HIGH -> Effort("high")
                ReasoningEffortLevel.MAX -> Effort("max")
            }
        } else {
            null
        }

        // 思考开启时 temperature 不生效（DeepSeek 文档），直接不发送
        val temperature = if (params.model.supportsTemperature && !thinkingEnabled) params.temperature else null

        return chatCompletionRequest {
            this.model = ModelId(params.model.modelId)
            this.messages = messages
            this.temperature = temperature
            if (params.model.useMaxCompletionTokens) {
                maxCompletionTokens = maxTokens
            } else {
                this.maxTokens = maxTokens
            }
            if (stream && params.model.supportsStreamUsage) {
                streamOptions = StreamOptions(includeUsage = true)
            }
            reasoningEffort = effortParam
        }
    }

    private fun parseServerMessage(errorBody: String): String? {
        if (errorBody.isBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(errorBody).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** 提取 ChatMessage.content 文本（TextContent 或部件列表）。 */
    private fun com.aallam.openai.api.chat.ChatMessage.contentText(): String {
        return when (val content = messageContent) {
            is TextContent -> content.content
            is ListContent -> content.content.filterIsInstance<TextPart>().joinToString("") { it.text }
            else -> ""
        }
    }

    sealed interface TestConnectionResult {
        data class Success(val latencyMs: Long) : TestConnectionResult
        data class Failure(val message: String) : TestConnectionResult
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 12_000L
    }
}

/** 携带 HTTP 状态码的业务异常（保留给调用方兼容）。 */
class HttpLlmException(val statusCode: Int, override val message: String) : Exception(message)

/** 统一错误转译：保留 HTTP 状态码与 Provider 错误详情。 */
fun Throwable.toLlmError(): LlmStreamEvent.Error = when (this) {
    is OpenAIAPIException -> LlmStreamEvent.Error(
        statusCode = statusCode,
        message = ErrorCodeMapper.describe(statusCode, message ?: "服务端错误"),
    )
    is OpenAIHttpException, is OpenAIException -> LlmStreamEvent.Error(message = ErrorCodeMapper.describeNetworkError(message))
    is HttpLlmException -> LlmStreamEvent.Error(statusCode = statusCode, message = message)
    is IOException -> LlmStreamEvent.Error(message = ErrorCodeMapper.describeNetworkError(message))
    is TimeoutCancellationException -> LlmStreamEvent.Error(message = "请求超时")
    is ConnectTimeoutException -> LlmStreamEvent.Error(message = "连接超时，请检查网络或 Base URL")
    is SocketTimeoutException -> LlmStreamEvent.Error(message = "网络读取超时")
    else -> LlmStreamEvent.Error(message = message ?: javaClass.simpleName)
}

fun Throwable.readableMessage(): String = when (this) {
    is OpenAIAPIException -> ErrorCodeMapper.describe(statusCode, message ?: "服务端错误")
    is HttpLlmException -> message
    is IOException -> ErrorCodeMapper.describeNetworkError(message)
    else -> message ?: javaClass.simpleName
}
