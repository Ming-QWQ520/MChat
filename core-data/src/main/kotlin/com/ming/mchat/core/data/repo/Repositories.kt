package com.ming.mchat.core.data.repo

import com.ming.mchat.core.data.dao.AttachmentDao
import com.ming.mchat.core.data.dao.ConversationDao
import com.ming.mchat.core.data.dao.MessageDao
import com.ming.mchat.core.data.dao.ProviderDao
import com.ming.mchat.core.data.dao.ModelDao
import com.ming.mchat.core.data.dao.PresetDao
import com.ming.mchat.core.data.dao.AuxUsageDao
import com.ming.mchat.core.data.dao.UsageDao
import com.ming.mchat.core.data.entity.ConversationEntity
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.PromptPresetEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.entity.TitleSource
import com.ming.mchat.core.data.keystore.CryptoManager
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
) {
    fun observeAll(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    suspend fun getById(id: Long): ConversationEntity? = conversationDao.getById(id)

    suspend fun create(providerId: String, modelId: String): ConversationEntity {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            title = "新对话",
            titleSource = TitleSource.DEFAULT,
            providerId = providerId,
            modelId = modelId,
            createdAt = now,
            updatedAt = now,
        )
        val id = conversationDao.insert(entity)
        return entity.copy(id = id)
    }

    /** 删除会话：事务级联清理消息/附件行，随后删除附件文件。 */
    suspend fun deleteCascade(conversationId: Long) = withContext(Dispatchers.IO) {
        val attachments = attachmentDao.getByConversation(conversationId)
        conversationDao.delete(conversationId)
        attachments.forEach { attachment ->
            runCatching { File(attachment.localPath).takeIf { it.exists() }?.delete() }
        }
    }

    /** 批量删除（多选模式）：逐个级联清理。 */
    suspend fun deleteManyCascade(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { id -> deleteCascade(id) }
    }

    /** 复制会话（kelivo 复制）：标题加“（副本）”后缀，消息与附件一并复制（附件文件复制为新文件）。 */
    suspend fun duplicate(conversationId: Long): Long? = withContext(Dispatchers.IO) {
        val source = conversationDao.getById(conversationId) ?: return@withContext null
        val now = System.currentTimeMillis()
        val newConvId = conversationDao.insert(
            source.copy(
                id = 0,
                title = source.title.removeSuffix("（副本）") + "（副本）",
                titleSource = TitleSource.MANUAL,
                isPinned = false,
                createdAt = now,
                updatedAt = now,
            )
        )
        val messages = messageDao.listForCopy(conversationId)
        messages.forEach { message ->
            val newMessageId = messageDao.insert(message.copy(id = 0, conversationId = newConvId))
            attachmentDao.getByMessage(message.id).forEach { attachment ->
                val copiedFile = runCatching {
                    val src = File(attachment.localPath)
                    if (src.exists()) {
                        val dst = File(src.parentFile, "copy_${now}_${src.name}")
                        src.copyTo(dst, overwrite = true)
                        dst.absolutePath
                    } else {
                        attachment.localPath
                    }
                }.getOrDefault(attachment.localPath)
                attachmentDao.insert(attachment.copy(id = 0, messageId = newMessageId, localPath = copiedFile, createdAt = now))
            }
        }
        newConvId
    }

    /** 批量置顶/取消置顶。 */
    suspend fun setPinnedMany(ids: List<Long>, pinned: Boolean) =
        conversationDao.setPinnedMany(ids, pinned)

    suspend fun rename(conversationId: Long, title: String) =
        conversationDao.rename(conversationId, title.trim(), System.currentTimeMillis())

    suspend fun setAutoTruncateTitle(conversationId: Long, title: String): Int =
        conversationDao.updateAutoTruncateTitle(conversationId, title, System.currentTimeMillis())

    suspend fun setAutoLlmTitle(conversationId: Long, title: String): Int =
        conversationDao.updateAutoLlmTitle(conversationId, title, System.currentTimeMillis())

    suspend fun updateModel(conversationId: Long, providerId: String, modelId: String) =
        conversationDao.updateModel(conversationId, providerId, modelId, System.currentTimeMillis())

    /** 会话置顶 / 取消置顶：不影响 updatedAt 排序基准。 */
    suspend fun setPinned(conversationId: Long, pinned: Boolean) =
        conversationDao.setPinned(conversationId, pinned)

    suspend fun updatePreset(conversationId: Long, presetId: Long?, variablesJson: String?) =
        conversationDao.updatePreset(conversationId, presetId, variablesJson, System.currentTimeMillis())

    suspend fun touch(conversationId: Long) =
        conversationDao.touch(conversationId, System.currentTimeMillis())
}

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: com.ming.mchat.core.data.dao.ConversationDao,
    private val attachmentDao: AttachmentDao,
) {
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeByConversation(conversationId)

    suspend fun insert(message: MessageEntity): Long {
        val id = messageDao.insert(message)
        conversationDao.touch(message.conversationId, System.currentTimeMillis())
        return id
    }

    suspend fun getByConversation(conversationId: Long): List<MessageEntity> =
        messageDao.getByConversation(conversationId)

    suspend fun delete(messageId: Long) = messageDao.delete(messageId)

    /** 编辑重发：更新消息内容并删除其后全部消息（含附件文件）。 */
    suspend fun editAndTruncateAfter(conversationId: Long, messageId: Long, newContent: String) =
        withContext(Dispatchers.IO) {
            val later = messageDao.listForCopy(conversationId).filter { it.id > messageId }
            messageDao.deleteAfter(conversationId, messageId)
            later.forEach { message ->
                attachmentDao.getByMessage(message.id).forEach { attachment ->
                    runCatching { File(attachment.localPath).takeIf { it.exists() }?.delete() }
                }
            }
            messageDao.updateContent(messageId, newContent)
        }

    suspend fun countByRole(conversationId: Long, role: Role): Int =
        messageDao.countByRole(conversationId, role.name)

    suspend fun firstUserMessage(conversationId: Long): MessageEntity? =
        messageDao.firstUserMessage(conversationId)

    suspend fun lastMessage(conversationId: Long): MessageEntity? =
        messageDao.lastMessage(conversationId)
}

@Singleton
class ProviderRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val conversationDao: ConversationDao,
    private val cryptoManager: CryptoManager,
) {
    fun observeProviders(): Flow<List<ProviderConfigEntity>> = providerDao.observeAll()

    fun observeModels(providerId: String): Flow<List<ModelConfigEntity>> =
        modelDao.observeByProvider(providerId)

    fun observeAllModels(): Flow<List<ModelConfigEntity>> = modelDao.observeAll()

    suspend fun getProvider(id: String): ProviderConfigEntity? = providerDao.getById(id)

    suspend fun getModels(providerId: String): List<ModelConfigEntity> = modelDao.getByProvider(providerId)

    suspend fun getModel(providerId: String, modelId: String): ModelConfigEntity? =
        modelDao.getById("$providerId:$modelId")

    /** 保存 API Key：Keystore 加密，Room 只存别名。 */
    suspend fun saveApiKey(providerId: String, apiKey: String) {
        val alias = aliasOf(providerId)
        val encrypted = cryptoManager.encrypt(alias, apiKey.trim())
        providerDao.updateApiKeyRef(providerId, "$alias|$encrypted")
    }

    /** 读取 API Key；未配置或解密失败返回 null。 */
    suspend fun readApiKey(providerId: String): String? {
        val provider = providerDao.getById(providerId) ?: return null
        val ref = provider.apiKeyRef ?: return null
        val parts = ref.split("|", limit = 2)
        if (parts.size != 2) return null
        return cryptoManager.decrypt(parts[0], parts[1])
    }

    suspend fun hasApiKey(providerId: String): Boolean = readApiKey(providerId)?.isNotBlank() == true

    suspend fun updateProvider(provider: ProviderConfigEntity) = providerDao.update(provider)

    suspend fun setEnabled(providerId: String, enabled: Boolean) = providerDao.setEnabled(providerId, enabled)

    suspend fun setModelEnabled(modelId: String, enabled: Boolean) = modelDao.setEnabled(modelId, enabled)

    suspend fun deleteProvider(providerId: String) {
        val provider = providerDao.getById(providerId) ?: return
        if (provider.isBuiltin) return
        deleteProviderCascade(providerId)
    }

    /** 删除自定义供应商（含模型目录）；其会话 remap 到 DeepSeek 兜底模型。 */
    suspend fun deleteProviderCascade(providerId: String) {
        val provider = providerDao.getById(providerId) ?: return
        if (provider.isBuiltin) return
        val fallback = modelDao.getById("${BalanceServiceIds.DEEPSEEK}:deepseek-flash")
        if (fallback != null) {
            conversationDao.remapAllModels(providerId, BalanceServiceIds.DEEPSEEK, fallback.modelId)
        }
        modelDao.deleteByProvider(providerId)
        providerDao.delete(providerId)
    }

    /** 新建自定义供应商：id 冲突时自动追加序号；返回最终实体。 */
    suspend fun createProvider(displayName: String, baseUrl: String, apiKey: String?): ProviderConfigEntity {
        val sanitizedBase = baseUrl.trim().trimEnd('/')
        val baseId = displayName.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "provider" }
        var id = baseId
        var suffix = 1
        while (providerDao.getById(id) != null) {
            id = "$baseId-${++suffix}"
        }
        val sortOrder = (providerDao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        val entity = ProviderConfigEntity(
            id = id,
            presetId = "custom",
            displayName = displayName.trim().ifBlank { id },
            baseUrl = sanitizedBase,
            enabled = true,
            isBuiltin = false,
            sortOrder = sortOrder,
        )
        providerDao.insert(entity)
        apiKey?.takeIf { it.isNotBlank() }?.let { saveApiKey(id, it) }
        return entity
    }

    /** 新增 / 覆盖一个模型配置（providerId + modelId 主键）。 */
    suspend fun upsertModel(model: ModelConfigEntity) {
        val existing = modelDao.getById(model.id)
        if (existing == null) {
            modelDao.insertAll(listOf(model))
        } else {
            modelDao.update(model)
        }
    }

    /** 删除模型：优先 remap 到同供应商其他可用模型，无则 DeepSeek 兜底（含供应商切换）。 */
    suspend fun deleteModel(providerId: String, modelId: String) {
        val key = "$providerId:$modelId"
        if (modelDao.getById(key) == null) return
        val sameProvider = modelDao.getByProvider(providerId)
            .filter { it.id != key && it.enabled }
            .firstOrNull()
        if (sameProvider != null) {
            conversationDao.remapModel(providerId, modelId, sameProvider.modelId)
        } else {
            val fallback = modelDao.getById("${BalanceServiceIds.DEEPSEEK}:deepseek-flash")
            if (fallback != null) {
                conversationDao.remapProviderModel(providerId, modelId, BalanceServiceIds.DEEPSEEK, fallback.modelId)
            }
        }
        modelDao.delete(key)
    }

    private fun aliasOf(providerId: String): String = "mchat_key_$providerId"
}

/** 供应商 id 常量（供 remap 兜底引用，避免 core-data 依赖 core-llm）。 */
private object BalanceServiceIds {
    const val DEEPSEEK = "deepseek"
}

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao,
) {
    fun observeAll(): Flow<List<PromptPresetEntity>> = presetDao.observeAll()

    suspend fun getAll(): List<PromptPresetEntity> = presetDao.getAll()

    suspend fun getById(id: Long): PromptPresetEntity? = presetDao.getById(id)

    suspend fun insert(preset: PromptPresetEntity): Long = presetDao.insert(preset)

    suspend fun update(preset: PromptPresetEntity) = presetDao.update(preset)

    suspend fun delete(presetId: Long) = presetDao.delete(presetId)
}

@Singleton
class UsageRepository @Inject constructor(
    private val usageDao: UsageDao,
    private val auxUsageDao: AuxUsageDao,
) {
    fun observeSince(fromDay: String): Flow<List<com.ming.mchat.core.data.entity.UsageDailyEntity>> =
        usageDao.observeSince(fromDay)

    fun observeAuxRecent(limit: Int = 200): Flow<List<com.ming.mchat.core.data.entity.AuxUsageEntity>> =
        auxUsageDao.observeRecent(limit)

    suspend fun recordUsage(model: String, promptTokens: Int, completionTokens: Int) {
        val day = LocalDate.now().toString()
        val existing = usageDao.get(day, model)
        usageDao.upsert(
            com.ming.mchat.core.data.entity.UsageDailyEntity(
                day = day,
                model = model,
                promptTokens = (existing?.promptTokens ?: 0) + promptTokens,
                completionTokens = (existing?.completionTokens ?: 0) + completionTokens,
                requestCount = (existing?.requestCount ?: 0) + 1,
            )
        )
    }

    suspend fun recordAux(
        kind: String,
        refId: Long?,
        providerId: String?,
        modelId: String?,
        promptTokens: Int,
        completionTokens: Int,
        source: String,
    ) {
        auxUsageDao.insert(
            com.ming.mchat.core.data.entity.AuxUsageEntity(
                kind = kind,
                refId = refId,
                providerId = providerId,
                modelId = modelId,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = promptTokens + completionTokens,
                source = source,
            )
        )
    }

    suspend fun clearAll() {
        usageDao.clear()
        auxUsageDao.clear()
    }
}
