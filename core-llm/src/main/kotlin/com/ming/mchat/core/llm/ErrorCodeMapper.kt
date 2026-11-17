package com.ming.mchat.core.llm

/**
 * HTTP / 业务错误码统一映射：把状态码 + 服务端 message 翻译成可读中文。
 * DeepSeek / OpenAI 兼容端点通用。
 */
object ErrorCodeMapper {

    /** HTTP 状态码 → 用户可读描述。 */
    fun describe(statusCode: Int?, serverMessage: String? = null): String {
        val base = when (statusCode) {
            400 -> "请求格式错误（400）：请检查消息内容或参数"
            401 -> "API Key 无效或未授权（401）：请检查 Key 是否正确、是否属于该服务商"
            402 -> "账户余额不足（402）：请前往服务商控制台充值"
            403 -> "访问被拒绝（403）：Key 无该模型权限或触发风控"
            404 -> "接口路径不存在（404）：请检查 Base URL 与模型名称"
            408 -> "请求超时（408）：网络不稳定，请重试"
            413 -> "请求体过大（413）：请缩短消息或减少附件"
            422 -> "请求参数无法处理（422）：消息结构可能不合法"
            429 -> "请求过于频繁（429）：触发限流，请稍后再试"
            500 -> "服务端内部错误（500）：请稍后重试"
            502, 504 -> "网关错误（$statusCode）：服务商网络异常，请稍后重试"
            503 -> "服务暂不可用（503）：模型过载或维护中，请稍后重试"
            null -> "请求失败"
            else -> "请求失败（HTTP $statusCode）"
        }
        return if (!serverMessage.isNullOrBlank()) "$base\n服务端：$serverMessage" else base
    }

    /** finish_reason → 可读提示（null 表示无需提示）。 */
    fun describeFinishReason(reason: String?): String? = when (reason) {
        "stop" -> null
        "length" -> "输出达到长度上限而被截断"
        "content_filter" -> "内容触发安全过滤被拦截"
        "tool_calls" -> null
        "insufficient_system_resource" -> "服务端推理资源不足，生成被中断"
        "aborted" -> "生成被中断"
        "cancelled" -> "已手动停止"
        else -> null
    }

    /** 网络异常 → 可读描述。 */
    fun describeNetworkError(message: String?): String {
        val m = message.orEmpty()
        return when {
            m.contains("Unable to resolve", ignoreCase = true) -> "网络错误：无法解析主机，请检查网络或 Base URL"
            m.contains("timeout", true) || m.contains("timed out", true) -> "网络错误：连接超时"
            m.contains("Connection refused", true) -> "网络错误：连接被拒绝，请检查 Base URL 端口"
            m.contains("SSL", true) -> "网络错误：SSL 握手失败，请检查 Base URL 证书"
            m.contains("Unexpected end of stream", true) -> "网络错误：连接被服务端提前关闭"
            m.isNotBlank() -> "网络错误：$m"
            else -> "网络错误：连接失败"
        }
    }
}
