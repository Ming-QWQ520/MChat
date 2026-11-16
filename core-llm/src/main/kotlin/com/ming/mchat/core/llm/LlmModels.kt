package com.ming.mchat.core.llm

import com.ming.mchat.core.common.OutputTokenMode
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.ming.mchat.core.data.entity.ModelConfigEntity

/** 统一流式事件：将 SSE chunk 映射为领域事件（与具体 HTTP 实现解耦）。 */
sealed interface LlmStreamEvent {
    /** 正文增量。 */
    data class Delta(val content: String) : LlmStreamEvent

    /** 思考过程增量（DeepSeek reasoning_content）。 */
    data class Reasoning(val content: String) : LlmStreamEvent

    /** 工具调用增量。 */
    data class ToolCallDelta(val count: Int) : LlmStreamEvent

    /** 服务端 usage（stream_options.include_usage，出现在最后一个 chunk）。 */
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val cacheHitTokens: Int = 0,
        val reasoningTokens: Int = 0,
    ) : LlmStreamEvent

    /** 流正常结束（携带 finishReason）。 */
    data class Done(val finishReason: String? = null) : LlmStreamEvent

    /** 错误：透传 HTTP 状态码与 Provider 错误信息，不吞 400/413。 */
    data class Error(val statusCode: Int? = null, val message: String) : LlmStreamEvent
}

/** 发送给 LLM 的内部消息模型（含当前轮图片）。 */
data class LlmMessage(
    val role: String, // "user" | "assistant"
    val text: String,
    val imageDataUrls: List<String> = emptyList(),
    val imageDetail: String = "auto",
)

/** 一次流式请求的完整参数。 */
data class StreamChatParams(
    val providerId: String,
    val baseUrl: String,
    val token: String,
    val model: ModelConfigEntity,
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val temperature: Double = 0.7,
    val outputTokenMode: OutputTokenMode = OutputTokenMode.DEFAULT,
    val outputTokenManualValue: Int = 2048,
    val reasoningEffort: ReasoningEffortLevel = ReasoningEffortLevel.HIGH,
)
