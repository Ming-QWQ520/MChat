package com.ming.mchat.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.ming.mchat.core.data.entity.AuxUsageEntity
import com.ming.mchat.core.data.entity.AttachmentEntity
import com.ming.mchat.core.data.entity.ConversationEntity
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.PromptPresetEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.entity.UsageDailyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert suspend fun insert(c: ConversationEntity): Long

    @Update suspend fun update(c: ConversationEntity)

    /** 置顶优先，其余按最近更新倒序。 */
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    /** 批量删除（多选模式）。 */
    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)

    /** 取消置顶全部（多选批量取消置顶用）。 */
    @Query("UPDATE conversations SET isPinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinnedMany(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET providerId = :providerId, modelId = :modelId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateModel(id: Long, providerId: String, modelId: String, updatedAt: Long)

    /** 模型下线迁移：同供应商内 remap 模型 id（不改 updatedAt 排序）。 */
    @Query("UPDATE conversations SET modelId = :newModelId WHERE providerId = :providerId AND modelId = :oldModelId")
    suspend fun remapModel(providerId: String, oldModelId: String, newModelId: String)

    /** 跨供应商迁移：同时更新 providerId 与 modelId（模型删除/供应商删除时兑底）。 */
    @Query(
        "UPDATE conversations SET providerId = :newProviderId, modelId = :newModelId " +
            "WHERE providerId = :oldProviderId AND modelId = :oldModelId"
    )
    suspend fun remapProviderModel(
        oldProviderId: String,
        oldModelId: String,
        newProviderId: String,
        newModelId: String,
    )

    /** 供应商删除时：其全部会话整体迁移到其他供应商。 */
    @Query("UPDATE conversations SET providerId = :newProviderId, modelId = :newModelId WHERE providerId = :oldProviderId")
    suspend fun remapAllModels(oldProviderId: String, newProviderId: String, newModelId: String)

    /** 自动兜底标题：MANUAL / AUTO_LLM 状态下不允许覆盖。 */
    @Query(
        "UPDATE conversations SET title = :title, titleSource = 'AUTO_TRUNCATE', updatedAt = :updatedAt " +
            "WHERE id = :id AND titleSource NOT IN ('MANUAL','AUTO_LLM')"
    )
    suspend fun updateAutoTruncateTitle(id: Long, title: String, updatedAt: Long): Int

    /** LLM 生成标题：同样受 MANUAL / AUTO_LLM 保护。 */
    @Query(
        "UPDATE conversations SET title = :title, titleSource = 'AUTO_LLM', updatedAt = :updatedAt " +
            "WHERE id = :id AND titleSource NOT IN ('MANUAL','AUTO_LLM')"
    )
    suspend fun updateAutoLlmTitle(id: Long, title: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET title = :title, titleSource = 'MANUAL', updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET promptPresetId = :presetId, variableValuesJson = :variablesJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePreset(id: Long, presetId: Long?, variablesJson: String?, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)
}

@Dao
interface MessageDao {
    @Insert suspend fun insert(m: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND role = :role")
    suspend fun countByRole(conversationId: Long, role: String): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'USER' ORDER BY id ASC LIMIT 1")
    suspend fun firstUserMessage(conversationId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id DESC LIMIT 1")
    suspend fun lastMessage(conversationId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    /** 编辑重发：删除某条消息之后（不含自身）的全部消息。 */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id > :messageId")
    suspend fun deleteAfter(conversationId: Long, messageId: Long)

    /** 编辑消息内容（仅用户消息文本）。 */
    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    /** 会话内全部消息（含附件行）供复制会话使用。 */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun listForCopy(conversationId: Long): List<MessageEntity>
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insert(a: AttachmentEntity): Long

    @Update suspend fun update(a: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getByMessage(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE messageId IN (SELECT id FROM messages WHERE conversationId = :conversationId)")
    suspend fun getByConversation(conversationId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProviderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(providers: List<ProviderConfigEntity>)

    @Insert suspend fun insert(p: ProviderConfigEntity)

    @Update suspend fun update(p: ProviderConfigEntity)

    @Query("SELECT * FROM provider_configs ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs ORDER BY sortOrder, id")
    suspend fun getAll(): List<ProviderConfigEntity>

    @Query("SELECT * FROM provider_configs WHERE id = :id")
    suspend fun getById(id: String): ProviderConfigEntity?

    @Query("UPDATE provider_configs SET apiKeyRef = :ref WHERE id = :id")
    suspend fun updateApiKeyRef(id: String, ref: String?)

    @Query("UPDATE provider_configs SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE provider_configs SET baseUrl = :baseUrl WHERE id = :id")
    suspend fun updateBaseUrl(id: String, baseUrl: String)

    @Query("DELETE FROM provider_configs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM provider_configs")
    suspend fun count(): Int
}

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(models: List<ModelConfigEntity>)

    @Update suspend fun update(m: ModelConfigEntity)

    @Query("SELECT * FROM model_configs WHERE providerId = :providerId ORDER BY id")
    fun observeByProvider(providerId: String): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs WHERE providerId = :providerId ORDER BY id")
    suspend fun getByProvider(providerId: String): List<ModelConfigEntity>

    @Query("SELECT * FROM model_configs ORDER BY id")
    fun observeAll(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs WHERE id = :id")
    suspend fun getById(id: String): ModelConfigEntity?

    @Query("UPDATE model_configs SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM model_configs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM model_configs WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)
}

@Dao
interface PresetDao {
    @Insert suspend fun insert(p: PromptPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(presets: List<PromptPresetEntity>)

    @Update suspend fun update(p: PromptPresetEntity)

    @Query("SELECT * FROM prompt_presets ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets ORDER BY sortOrder, id")
    suspend fun getAll(): List<PromptPresetEntity>

    @Query("SELECT * FROM prompt_presets WHERE id = :id")
    suspend fun getById(id: Long): PromptPresetEntity?

    @Query("DELETE FROM prompt_presets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM prompt_presets")
    suspend fun count(): Int
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_daily WHERE day >= :fromDay ORDER BY day DESC, model")
    fun observeSince(fromDay: String): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM usage_daily WHERE day = :day AND model = :model")
    suspend fun get(day: String, model: String): UsageDailyEntity?

    @Upsert
    suspend fun upsert(entity: UsageDailyEntity)

    @Query("DELETE FROM usage_daily")
    suspend fun clear()
}

@Dao
interface AuxUsageDao {
    @Insert suspend fun insert(a: AuxUsageEntity): Long

    @Query("SELECT * FROM aux_usage ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuxUsageEntity>>

    @Query("SELECT * FROM aux_usage ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AuxUsageEntity>

    @Query("DELETE FROM aux_usage")
    suspend fun clear()
}
