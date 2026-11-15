package com.ming.mchat.core.llm

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token 估算：JTokkit（OpenAI 系）优先，字符近似兜底。
 * 服务端 usage 可用时以服务端为准，本地估算仅用于流式中“≈”显示与未返回场景。
 */
@Singleton
class TokenEstimator @Inject constructor() {

    private val registry: EncodingRegistry by lazy { Encodings.newDefaultEncodingRegistry() }
    private val fallback: Encoding by lazy { registry.getEncoding(EncodingType.CL100K_BASE) }

    fun estimateTokens(text: String, modelId: String? = null): Int {
        if (text.isEmpty()) return 0
        return try {
            val encoding = modelId
                ?.let { registry.getEncodingForModel(it).orElse(null) }
                ?: fallback
            encoding.countTokens(text)
        } catch (t: Throwable) {
            approximateByChars(text)
        }
    }

    /** 字符近似兜底：CJK 约 1 字 1 token，其他约 4 字符 1 token。 */
    fun approximateByChars(text: String): Int {
        if (text.isEmpty()) return 0
        val cjk = text.count { it.code >= 0x2E80 }
        val rest = text.length - cjk
        return (cjk + rest / 4).coerceAtLeast(1)
    }

    /** 图片 token 估算（视觉模型 detail=auto 的常用近似）。 */
    fun estimateImageTokens(width: Int, height: Int): Int {
        val scaled = maxOf(width, height).coerceAtMost(2048) *
            minOf(width, height).coerceAtMost(2048)
        val tiles = Math.ceil(scaled / (512.0 * 512.0)).toInt().coerceAtLeast(1)
        return tiles * 170 + 85
    }
}
