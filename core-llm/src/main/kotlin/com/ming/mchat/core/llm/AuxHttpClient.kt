package com.ming.mchat.core.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 辅助直连 Ktor HttpClient（OkHttp 引擎）：
 * - DeepSeek 余额查询（GET /user/balance，openai-kotlin 不覆盖该私有端点）；
 * - Anthropic 风格 /models 拉取（x-api-key 认证）。
 * 与 openai-kotlin 底层引擎一致（均为 Ktor + OkHttp）。
 */
@Singleton
class AuxHttpClient @Inject constructor() {

    val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }
}
