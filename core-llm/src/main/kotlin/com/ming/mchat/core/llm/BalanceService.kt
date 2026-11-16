package com.ming.mchat.core.llm

import com.google.gson.Gson
import com.ming.mchat.core.data.repo.ProviderRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request

/**
 * DeepSeek 余额查询（GET /user/balance，不带 /v1）。
 * - OkHttp + Gson 原生实现（与 ChatService 同技术栈）；
 * - Mutex 防并发重复请求；
 * - 8 秒超时，失败静默保留上次缓存值；
 * - 余额查询不记入 aux_usage。
 */
@Singleton
class BalanceService @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val httpClient: LlmHttpClient,
) {

    private val gson = Gson()

    private val mutex = Mutex()

    private val _state = MutableStateFlow<BalanceState?>(null)
    val state: StateFlow<BalanceState?> = _state.asStateFlow()

    /** 仅 DeepSeek Provider 启用。 */
    suspend fun refreshIfDeepSeek(providerId: String) {
        if (providerId != DEEPSEEK_ID) return
        refresh()
    }

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        try {
            val key = providerRepository.readApiKey(DEEPSEEK_ID)
            if (key.isNullOrBlank()) {
                _state.value = BalanceState(error = "未配置 DeepSeek API Key")
                return
            }
            val body = withContext(Dispatchers.IO) {
                withTimeout(TIMEOUT_MILLIS) {
                    val request = Request.Builder()
                        .url("$BALANCE_BASE_URL/user/balance")
                        .header("Authorization", "Bearer $key")
                        .get()
                        .build()
                    httpClient.default.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }
                }
            }
            val parsed = runCatching { gson.fromJson(body, DeepSeekBalance::class.java) }.getOrNull()
                ?: throw IOException("响应格式无法解析")
            val info = parsed.balanceInfos.firstOrNull()
            _state.update { current ->
                BalanceState(
                    isAvailable = parsed.isAvailable,
                    currency = info?.currency ?: "CNY",
                    totalBalance = info?.totalBalance?.toDoubleOrNull() ?: 0.0,
                    grantedBalance = info?.grantedBalance?.toDoubleOrNull() ?: 0.0,
                    toppedUpBalance = info?.toppedUpBalance?.toDoubleOrNull() ?: 0.0,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (t is TimeoutCancellationException && _state.value != null) {
                // 超时静默保留上次值
            } else if (_state.value == null) {
                _state.value = BalanceState(error = "余额查询失败")
            }
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        const val DEEPSEEK_ID = "deepseek"
        const val BALANCE_BASE_URL = "https://api.deepseek.com"
        const val TIMEOUT_MILLIS = 8_000L
    }
}

/** 余额状态：查询中由 UI 标识，失败时保留上次缓存或显示 “--”。 */
data class BalanceState(
    val isAvailable: Boolean = false,
    val currency: String = "CNY",
    val totalBalance: Double = 0.0,
    val grantedBalance: Double = 0.0,
    val toppedUpBalance: Double = 0.0,
    val updatedAt: Long? = null,
    val error: String? = null,
)

/** Gson 反序列化模型（@SerializedName 映射蛇形命名）。 */
internal data class DeepSeekBalance(
    @com.google.gson.annotations.SerializedName("is_available") val isAvailable: Boolean = false,
    @com.google.gson.annotations.SerializedName("balance_infos") val balanceInfos: List<BalanceInfo> = emptyList(),
)

internal data class BalanceInfo(
    val currency: String = "",
    @com.google.gson.annotations.SerializedName("total_balance") val totalBalance: String = "",
    @com.google.gson.annotations.SerializedName("granted_balance") val grantedBalance: String = "",
    @com.google.gson.annotations.SerializedName("topped_up_balance") val toppedUpBalance: String = "",
)
