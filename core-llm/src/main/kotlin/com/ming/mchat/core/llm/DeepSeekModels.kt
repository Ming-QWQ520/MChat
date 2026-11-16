package com.ming.mchat.core.llm

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek / OpenAI 兼容 Chat Completions 协议数据模型（OkHttp + Gson 原生实现）。
 * 字段命名与官方文档一致；可空字段一律声明为可空，序列化时跳过 null。
 */

/** 消息内容块（文本 / 图片）。 */
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null,
)

data class ImageUrl(
    val url: String,
    val detail: String? = null,
)

/** 请求消息：content 为字符串或内容块数组，Gson 按声明类型序列化。 */
data class ChatMessageBody(
    val role: String,
    val content: Any?, // String 或 List<ContentPart>；system/user/assistant 通用
    @SerializedName("reasoning_content") val reasoningContent: String? = null,
)

/** 对话补全请求体。 */
data class ChatCompletionBody(
    val model: String,
    val messages: List<ChatMessageBody>,
    val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean? = null,
    @SerializedName("stream_options") val streamOptions: StreamOptions? = null,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null,
    val thinking: ThinkingControl? = null,
) {
    companion object {
        fun of(
            model: String,
            messages: List<ChatMessageBody>,
            temperature: Double?,
            maxTokens: Int?,
            stream: Boolean,
            includeUsage: Boolean,
            reasoningEffort: String?,
        ) = ChatCompletionBody(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = stream,
            streamOptions = if (stream && includeUsage) StreamOptions(includeUsage = true) else null,
            reasoningEffort = reasoningEffort,
        )
    }
}

/** 思考模式开关（OpenAI 格式）：enabled / disabled。 */
data class ThinkingControl(val type: String)

data class StreamOptions(@SerializedName("include_usage") val includeUsage: Boolean)

/** 非流式响应。 */
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null,
)

data class ChatChoice(
    val index: Int = 0,
    val message: ChatResponseMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null,
)

data class ChatResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerializedName("reasoning_content") val reasoningContent: String? = null,
)

/** 用量统计：DeepSeek 返回 prompt_cache_hit/miss 与 reasoning_tokens 明细。 */
data class ChatUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0,
    @SerializedName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int = 0,
    @SerializedName("prompt_cache_miss_tokens") val promptCacheMissTokens: Int = 0,
    @SerializedName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null,
) {
    val reasoningTokens: Int get() = completionTokensDetails?.reasoningTokens ?: 0
}

data class CompletionTokensDetails(
    @SerializedName("reasoning_tokens") val reasoningTokens: Int = 0,
)

/** 流式 chunk。usage 出现在最后一个 chunk（choices 仍非空，DeepSeek 附加在末帧）。 */
data class StreamChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    val usage: ChatUsage? = null,
)

data class StreamChoice(
    val index: Int = 0,
    val delta: StreamDelta? = null,
    @SerializedName("finish_reason") val finishReason: String? = null,
)

data class StreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerializedName("reasoning_content") val reasoningContent: String? = null,
)

/** GET /models 响应（OpenAI 兼容风格）。 */
data class ModelsListResponse(
    val `object`: String? = null,
    val data: List<ModelInfo> = emptyList(),
)

data class ModelInfo(
    val id: String = "",
    val `object`: String? = null,
    val owned_by: String? = null,
    val created: Long? = null,
)

/** 服务端错误响应（OpenAI 兼容：{"error":{"message":...,"type":...,"code":...}}）。 */
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
)

data class ApiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: Any? = null,
)
