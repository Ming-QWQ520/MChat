package com.ming.mchat.core.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * models.dev 模型目录（https://models.dev/api.json）：
 * - 首次启动读内置精简快照（assets/models_dev.json，约 20 家主流供应商）；
 * - 设置页可在线刷新（全量 api.json），成功后缓存到私有目录，优先使用缓存；
 * - 供应商与模型的元数据（baseURL / 推理 / 工具 / 视觉 / 上下文窗口）统一来源。
 */
@Serializable
data class ModelsDevModel(
    val name: String = "",
    val reasoning: Boolean = false,
    val tool_call: Boolean = false,
    val attachment: Boolean = false,
    val temperature: Boolean = true,
    val context: Int? = null,
    val output: Int? = null,
)

@Serializable
data class ModelsDevProvider(
    val id: String,
    val name: String = "",
    val api: String = "",
    val doc: String = "",
    val models: Map<String, ModelsDevModel> = emptyMap(),
)

@Serializable
private data class ModelsDevRoot(
    val providers: Map<String, ModelsDevProvider> = emptyMap(),
)

/** 目录加载状态。 */
sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(
        val providers: List<ModelsDevProvider>,
        val fromCache: Boolean,
        val updatedAt: Long,
    ) : CatalogState
    data class Failed(val message: String) : CatalogState
}

@Singleton
class ModelsDevCatalog @Inject constructor(
    private val auxHttpClient: AuxHttpClient,
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val cacheFile: File get() = File(File(context.filesDir, "catalog"), "models_dev.json")

    private val loadMutex = Mutex()
    private var loadStarted = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 应用启动后异步加载：缓存 → 内置快照。 */
    fun ensureLoaded() {
        if (loadStarted) return
        loadStarted = true
        scope.launch { loadLocal() }
    }

    private suspend fun loadLocal() {
        loadMutex.withLock {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    if (cacheFile.exists()) cacheFile.readText() else null
                } ?: withContext(Dispatchers.IO) {
                    context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
                }
                val root = json.decodeFromString(ModelsDevRoot.serializer(), text)
                _state.value = CatalogState.Ready(
                    providers = root.providers.values.sortedBy { it.name.lowercase() },
                    fromCache = cacheFile.exists(),
                    updatedAt = cacheFile.takeIf { it.exists() }?.lastModified() ?: 0L,
                )
            }.onFailure { _state.value = CatalogState.Failed(it.message ?: "目录解析失败") }
        }
    }

    /** 在线刷新全量 api.json；成功后写缓存并更新状态，失败保留本地数据。 */
    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val body = auxHttpClient.client.get(REMOTE_URL) {
                headers { append("Accept", "application/json") }
            }.bodyAsText()
            val root = parseRemote(body)
            val providers = root.providers.values
                .filter { it.api.isNotBlank() && it.models.isNotEmpty() }
                .sortedBy { it.name.lowercase() }
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(
                json.encodeToString(
                    ModelsDevRoot.serializer(),
                    ModelsDevRoot(providers.associateBy { it.id }),
                ),
            )
            _state.value = CatalogState.Ready(providers, fromCache = true, updatedAt = System.currentTimeMillis())
            providers.size
        }
    }

    /** models.dev 原始 api.json → 精简结构（只保留直连供应商与核心字段）。 */
    private fun parseRemote(body: String): ModelsDevRoot {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject
        val providers = buildMap {
            for ((pid, pv) in obj) {
                val p = pv.jsonObject
                val api = p["api"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = p["name"]?.jsonPrimitive?.contentOrNull ?: pid
                val doc = p["doc"]?.jsonPrimitive?.contentOrNull ?: ""
                if (api.isBlank()) continue
                val models = buildMap {
                    (p["models"] as? JsonObject)?.forEach { (mid, mv) ->
                        val m = mv.jsonObject
                        val limit = (m["limit"] as? JsonObject)
                        put(
                            mid,
                            ModelsDevModel(
                                name = m["name"]?.jsonPrimitive?.contentOrNull ?: mid,
                                reasoning = m["reasoning"]?.jsonPrimitive?.booleanOrNull ?: false,
                                tool_call = m["tool_call"]?.jsonPrimitive?.booleanOrNull ?: false,
                                attachment = m["attachment"]?.jsonPrimitive?.booleanOrNull ?: false,
                                temperature = m["temperature"]?.jsonPrimitive?.booleanOrNull ?: true,
                                context = limit?.get("context")?.jsonPrimitive?.intOrNull,
                                output = limit?.get("output")?.jsonPrimitive?.intOrNull,
                            ),
                        )
                    }
                }
                put(pid, ModelsDevProvider(id = pid, name = name, api = api, doc = doc, models = models))
            }
        }
        return ModelsDevRoot(providers)
    }

    /** 依据 baseUrl / presetId / 名称查找目录中的供应商（用于模型导入匹配）。 */
    fun matchProvider(presetId: String?, baseUrl: String?, displayName: String?): ModelsDevProvider? {
        val ready = _state.value as? CatalogState.Ready ?: return null
        val base = baseUrl?.trim()?.trimEnd('/')?.removePrefix("https://")?.removePrefix("http://")
        return ready.providers.firstOrNull { it.id == presetId && presetId.isNotBlank() }
            ?: base?.let { b -> ready.providers.firstOrNull { c -> c.api.contains(b, true) || b.contains(c.api.removePrefix("https://").removePrefix("http://"), true) } }
            ?: ready.providers.firstOrNull { it.name.equals(displayName, true) && !displayName.isNullOrBlank() }
    }

    companion object {
        private const val ASSET_PATH = "models_dev.json"
        private const val REMOTE_URL = "https://models.dev/api.json?type=all"
    }
}
