package com.ming.mchat.core.llm

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 统一 OkHttp 客户端工厂（原生实现，替代 openai-kotlin）：
 * - 普通客户端：连接 60s / 读 120s / 写 60s（思考模式首包可能较慢）；
 * - SSE 客户端：readTimeout(0) 无限读超时，流式输出不被提前掐断；
 * - 全局单例复用连接池，避免每个请求新建连接的开销。
 */
@Singleton
class LlmHttpClient @Inject constructor() {

    /** 非流式请求（测试连接 / 标题生成 / 模型列表 / 余额）。 */
    val default: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** SSE 流式请求专用：无限读超时。 */
    val sse: OkHttpClient by lazy {
        default.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
