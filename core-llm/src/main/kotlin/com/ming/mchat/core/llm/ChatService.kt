package com.ming.mchat.core.llm

import com.google.gson.Gson
import com.ming.mchat.core.common.OutputTokenMode
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.ming.mchat.core.data.entity.ModelConfigEntity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * 统一聊天服务：OkHttp + okhttp-sse 原生实现（不依赖任何 OpenAI SDK）。
 * - 流式：EventSource 桥接 channelFlow；usage 从最后一个 chunk 捕获；
 * - 取消：收集协程取消时 awaitClose 关闭 EventSource；CancellationException 不被吞掉；
 * - 思考模式：reasoning_effort 对齐 DeepSeek 取值（none/low/high/max）。
 */
@Singleton
class ChatService @Inject constructor(
    private val httpClient: LlmHttpClient,
) {

    private val gson = Gson()

    fun streamChat(params: StreamChatParams): Flow<LlmStreamEvent> = channelFlow {
        val body = buildRequestBody(params, stream = true)
        val request = Request.Builder()
            .url(joinUrl(params.baseUrl, "/chat/completions"))
            .header("Authorization", "Bearer ${params.token}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        var lastUsage: ChatUsage? = null
        var finishReason: String? = null
        var emitted = false

        fun emitUsageOnce() {
            lastUsage?.let { u ->
                trySend(
                    LlmStreamEvent.Usage(
                        promptTokens = u.promptTokens,
                        completionTokens = u.completionTokens,
                        totalTokens = u.totalTokens,
                        cacheHitTokens = u.promptCacheHitTokens,
                        reasoningTokens = u.reasoningTokens,
                    )
                )
            }
        }

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    emitUsageOnce()
                    trySend(LlmStreamEvent.Done(finishReason))
                    emitted = true
                    eventSource.cancel()
                    close()
                    return
                }
                val chunk = runCatching { gson.fromJson(data, StreamChunk::class.java) }.getOrNull() ?: return
                chunk.usage?.let { lastUsage = it }
                chunk.choices.firstOrNull()?.let { choice ->
                    choice.delta?.content?.takeIf { it.isNotEmpty() }?.let { trySend(LlmStreamEvent.Delta(it)) }
                    choice.delta?.reasoningContent?.takeIf { it.isNotEmpty() }?.let { trySend(LlmStreamEvent.Reasoning(it)) }
                    choice.finishReason?.let { finishReason = it }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // 兼容未发送 [DONE] 的端点：正常关闭也补发 usage + Done（不重复发）
                if (!emitted) {
                    emitUsageOnce()
                    trySend(LlmStreamEvent.Done(finishReason))
                }
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (emitted) {
                    close()
                    return
                }
                if (response != null) {
                    val text = runCatching { response.body?.string() }.getOrNull()
                    trySend(
                        LlmStreamEvent.Error(
                            statusCode = response.code,
                            message = ErrorCodeMapper.describe(response.code, parseServerMessage(text)),
                        )
                    )
                } else {
                    trySend(LlmStreamEvent.Error(message = ErrorCodeMapper.describeNetworkError(t?.message)))
                }
                close()
            }
        }

        val source = EventSources.createFactory(httpClient.sse).newEventSource(request, listener)
        awaitClose { runCatching { source.cancel() } }
    }

    /**
     * 非流式补全（标题生成 / 测试连接等辅助请求）。
     * 返回 firstChoice 文本；HTTP 错误抛出并保留状态码。
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
    ): String = withContext(Dispatchers.IO) {
        val messages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(
                    ChatMessageBody(
                        role = if (model.systemRole.equals("developer", true)) "developer" else "system",
                        content = systemPrompt,
                    )
                )
            }
            add(ChatMessageBody(role = "user", content = userPrompt))
        }
        val body = ChatCompletionBody.of(
            model = model.modelId,
            messages = messages,
            temperature = if (model.supportsTemperature) temperature else null,
            maxTokens = if (model.useMaxCompletionTokens) null else maxTokens,
            stream = false,
            includeUsage = false,
            reasoningEffort = null, // 辅助请求不需要思考
        )
        val request = Request.Builder()
            .url(joinUrl(baseUrl, "/chat/completions"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON_MEDIA))
            .build()
        httpClient.default.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HttpLlmException(response.code, ErrorCodeMapper.describe(response.code, parseServerMessage(text)))
            }
            gson.fromJson(text, ChatCompletionResponse::class.java)
                .choices.firstOrNull()?.message?.content.orEmpty().trim()
        }
    }

    /** 设置页“测试连接”：maxTokens=1 的非流式短请求，12 秒超时。 */
    suspend fun testConnection(
        providerId: String,
        baseUrl: String,
        token: String,
        model: ModelConfigEntity,
    ): TestConnectionResult {
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
     * - OpenAI 兼容端点：Authorization: Bearer，解析 data[].id；
     * - Anthropic 端点：x-api-key + anthropic-version，解析 data[].id；
     * 返回 (modelId, displayName) 对；失败抛 HttpLlmException / IOException。
     */
    suspend fun fetchModels(
        baseUrl: String,
        token: String,
        isAnthropic: Boolean = false,
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(joinUrl(baseUrl, "/models"))
            .get()
        if (isAnthropic) {
            builder.header("x-api-key", token).header("anthropic-version", "2023-06-01")
        } else {
            builder.header("Authorization", "Bearer $token")
        }
        httpClient.default.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HttpLlmException(response.code, ErrorCodeMapper.describe(response.code, parseServerMessage(text)))
            }
            val root = runCatching { gson.fromJson(text, ModelsListResponse::class.java) }.getOrNull()
                ?: throw IOException("响应格式无法解析")
            root.data
                .mapNotNull { info -> info.id.takeIf { it.isNotBlank() } }
                .map { id -> id to id } // 展示名默认同 ID，可在添加时编辑
        }
    }

    // ---------------- 内部工具 ----------------

    private fun buildRequestBody(params: StreamChatParams, stream: Boolean): okhttp3.RequestBody {
        val messages = buildList {
            if (params.systemPrompt.isNotBlank()) {
                add(
                    ChatMessageBody(
                        role = if (params.model.systemRole.equals("developer", true)) "developer" else "system",
                        content = params.systemPrompt,
                    )
                )
            }
            params.messages.forEach { m ->
                if (m.imageDataUrls.isEmpty()) {
                    add(ChatMessageBody(role = m.role, content = m.text))
                } else {
                    val parts = buildList {
                        if (m.text.isNotBlank()) add(ContentPart(type = "text", text = m.text))
                        m.imageDataUrls.forEach { url ->
                            add(ContentPart(type = "image_url", imageUrl = ImageUrl(url = url, detail = m.imageDetail)))
                        }
                    }
                    add(ChatMessageBody(role = m.role, content = parts))
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
        val effortParam: String? = if (params.model.supportsReasoning) {
            when (params.reasoningEffort) {
                ReasoningEffortLevel.NONE -> if (params.providerId == BalanceService.DEEPSEEK_ID) "none" else null
                ReasoningEffortLevel.LOW -> "low"
                ReasoningEffortLevel.HIGH -> "high"
                ReasoningEffortLevel.MAX -> "max"
            }
        } else {
            null
        }

        // 思考开启时 temperature 不生效（DeepSeek 文档），直接不发送
        val temperature = if (params.model.supportsTemperature && !thinkingEnabled) params.temperature else null

        val body = ChatCompletionBody.of(
            model = params.model.modelId,
            messages = messages,
            temperature = temperature,
            maxTokens = if (params.model.useMaxCompletionTokens) null else maxTokens,
            stream = stream,
            includeUsage = stream && params.model.supportsStreamUsage,
            reasoningEffort = effortParam,
        )
        return gson.toJson(body).toRequestBody(JSON_MEDIA)
    }

    private fun parseServerMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        val parsed = runCatching { gson.fromJson(errorBody, ApiErrorBody::class.java) }.getOrNull()
        return parsed?.error?.message?.takeIf { it.isNotBlank() }
    }

    private fun joinUrl(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + path

    sealed interface TestConnectionResult {
        data class Success(val latencyMs: Long) : TestConnectionResult
        data class Failure(val message: String) : TestConnectionResult
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 12_000L
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** 携带 HTTP 状态码的业务异常。 */
class HttpLlmException(val statusCode: Int, override val message: String) : Exception(message)

/** 统一错误转译：保留 HTTP 状态码与 Provider 错误详情。 */
fun Throwable.toLlmError(): LlmStreamEvent.Error = when (this) {
    is HttpLlmException -> LlmStreamEvent.Error(statusCode = statusCode, message = message)
    is IOException -> LlmStreamEvent.Error(message = ErrorCodeMapper.describeNetworkError(message))
    is TimeoutCancellationException -> LlmStreamEvent.Error(message = "请求超时")
    else -> LlmStreamEvent.Error(message = message ?: javaClass.simpleName)
}

fun Throwable.readableMessage(): String = when (this) {
    is HttpLlmException -> message
    is IOException -> ErrorCodeMapper.describeNetworkError(message)
    else -> message ?: javaClass.simpleName
}
