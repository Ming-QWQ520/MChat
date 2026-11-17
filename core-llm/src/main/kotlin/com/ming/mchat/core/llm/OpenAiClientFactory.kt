package com.ming.mchat.core.llm

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * openai-kotlin 客户端工厂：
 * - 按 providerId 缓存 [OpenAI] 实例，baseUrl / token 变更时失效重建；
 * - baseUrl 动态传入（ProviderConfigEntity.baseUrl），不硬编码；
 * - token 仅在构造时注入内存，日志级别 None（不落任何请求日志）；
 * - 流式长输出：socket 超时 300s（两个 chunk 之间的最长等待）。
 */
@Singleton
class OpenAiClientFactory @Inject constructor() {

    /** providerId -> (配置指纹, 客户端)。 */
    private val cache = ConcurrentHashMap<String, Pair<String, OpenAI>>()

    fun client(providerId: String, baseUrl: String, token: String): OpenAI {
        val normalized = normalizeBaseUrl(baseUrl)
        val key = "$normalized#$token"
        cache[providerId]?.let { (fingerprint, client) ->
            if (fingerprint == key) return client
        }
        val openAI = OpenAI(
            token = token,
            host = OpenAIHost(baseUrl = normalized),
            logging = LoggingConfig(logLevel = LogLevel.None),
            timeout = Timeout(connect = 60.seconds, socket = 300.seconds),
        )
        val previous = cache.put(providerId, key to openAI)
        previous?.second?.close()
        return openAI
    }

    /** 主动失效（API Key 更新后调用）。 */
    fun invalidate(providerId: String) {
        cache.remove(providerId)?.second?.close()
    }

    companion object {
        /** 去掉末尾斜杠；openai-kotlin 内部会按 `trimEnd('/') + "/" + path` 拼接。 */
        fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/').ifEmpty { baseUrl.trim() }
    }
}
