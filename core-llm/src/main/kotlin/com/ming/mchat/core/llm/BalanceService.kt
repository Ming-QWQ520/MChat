package com.ming.mchat.core.llm

import com.ming.mchat.core.data.repo.ProviderRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DeepSeek 余额查询（GET /user/balance，不带 /v1）。
 * - 复用与 openai-kotlin 相同技术栈的自建 Ktor HttpClient（OkHttp 引擎）；
 * - Mutex + AtomicBoolean 防并发重复请求；
 * - 8 秒超时，失败静默保留上次缓存值；
 * - 余额查询不记入 aux_usage。
 */
@Singleton
class BalanceService @Inject constructor(
    private val providerRepository: ProviderRepository,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp)
    }

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
            val response = withTimeout(TIMEOUT_MILLIS) {
                httpClient.get("$BALANCE_BASE_URL/user/balance") {
                    header("Authorization", "Bearer $key")
                }
            }
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<DeepSeekBalance>(body)
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

@Serializable
internal data class DeepSeekBalance(
    @SerialName("is_available") val isAvailable: Boolean = false,
    @SerialName("balance_infos") val balanceInfos: List<BalanceInfo> = emptyList(),
)

@Serializable
internal data class BalanceInfo(
    val currency: String = "",
    @SerialName("total_balance") val totalBalance: String = "",
    @SerialName("granted_balance") val grantedBalance: String = "",
    @SerialName("topped_up_balance") val toppedUpBalance: String = "",
)
