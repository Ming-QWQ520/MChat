package com.ming.mchat.core.llm

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.exception.OpenAIAPIException
import com.ming.mchat.core.data.entity.ModelConfigEntity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withTimeout

/**
 * 统一聊天服务：基于 openai-kotlin chatCompletions Flow 的流式调用。
 * 取消通过收集协程的 Job.cancel() 传播；CancellationException 不被吞掉。
 */
@Singleton
class ChatService @Inject constructor(
    private val clientFactory: OpenAiClientFactory,
) {

    fun streamChat(params: StreamChatParams): Flow<LlmStreamEvent> = flow {
        val request = OpenAiClientFactory.buildRequest(params)
        val client = clientFactory.client(params.providerId, params.baseUrl, params.token)
        var finishReason: String? = null
        client.chatCompletions(request).collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                val delta = choice.delta
                val content = delta?.content
                if (!content.isNullOrEmpty()) emit(LlmStreamEvent.Delta(content))
                val reasoning = delta?.reasoningContent
                if (!reasoning.isNullOrEmpty()) emit(LlmStreamEvent.Reasoning(reasoning))
                val toolCalls = delta?.toolCalls
                if (!toolCalls.isNullOrEmpty()) emit(LlmStreamEvent.ToolCallDelta(toolCalls.size))
                choice.finishReason?.let { finishReason = it.value }
            }
            chunk.usage?.let { usage ->
                emit(
                    LlmStreamEvent.Usage(
                        promptTokens = usage.promptTokens ?: 0,
                        completionTokens = usage.completionTokens ?: 0,
                        totalTokens = usage.totalTokens ?: 0,
                    )
                )
            }
        }
        emit(LlmStreamEvent.Done(finishReason = finishReason))
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(throwable.toLlmError())
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
    ): String {
        val request = ChatCompletionRequest(
            model = com.aallam.openai.api.model.ModelId(model.modelId),
            messages = listOf(
                ChatMessage(role = Role.System, content = systemPrompt),
                ChatMessage(role = Role.User, content = userPrompt),
            ),
            temperature = if (model.supportsTemperature) temperature else null,
            maxTokens = if (model.useMaxCompletionTokens) null else maxTokens,
            maxCompletionTokens = if (model.useMaxCompletionTokens) maxTokens else null,
        )
        val client = clientFactory.client(providerId, baseUrl, token)
        val completion = client.chatCompletion(request)
        return completion.choices.firstOrNull()?.message?.content.orEmpty().trim()
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

    sealed interface TestConnectionResult {
        data class Success(val latencyMs: Long) : TestConnectionResult
        data class Failure(val message: String) : TestConnectionResult
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 12_000L
    }
}

/** 统一错误转译：保留 HTTP 状态码（400/413 等）与 Provider 错误详情。 */
fun Throwable.toLlmError(): LlmStreamEvent.Error = when (this) {
    is OpenAIAPIException -> LlmStreamEvent.Error(
        statusCode = statusCode,
        message = error?.detail?.message ?: message ?: "HTTP $statusCode",
    )
    is IOException -> LlmStreamEvent.Error(message = "网络错误：${message ?: "连接失败"}")
    is TimeoutCancellationException -> LlmStreamEvent.Error(message = "请求超时")
    else -> LlmStreamEvent.Error(message = message ?: javaClass.simpleName)
}

fun Throwable.readableMessage(): String = when (this) {
    is OpenAIAPIException -> "HTTP $statusCode：${error?.detail?.message ?: "请求被拒绝"}"
    is IOException -> "网络错误：${message ?: "连接失败"}"
    else -> message ?: javaClass.simpleName
}
