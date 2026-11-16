package com.ming.mchat.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 消息角色（system 提示词每次发送动态生成，不落历史）。 */
enum class Role { USER, ASSISTANT, SYSTEM }

/** 会话标题来源状态机：手动改名后（MANUAL）任何自动流程不得覆盖。 */
enum class TitleSource { DEFAULT, AUTO_TRUNCATE, AUTO_LLM, MANUAL }

/** Token 用量来源：服务端优先，本地估算兜底。 */
enum class UsageSource { SERVER, ESTIMATED, MIXED }

/** 附件类型。 */
enum class AttachmentKind { IMAGE, TEXT, PDF }

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val titleSource: TitleSource = TitleSource.DEFAULT,
    val promptPresetId: Long? = null,
    val variableValuesJson: String? = null,
    val providerId: String,
    val modelId: String,
    /** 会话置顶：置顶后在抽屉顶部独立分组展示。 */
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: Role,
    val content: String,
    /** 思考模式思维链（reasoning_content），历史消息重新打开时可折叠展示。 */
    val reasoningContent: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val usageSource: UsageSource? = null,
    val model: String? = null,
    val latencyMs: Long? = null,
    val finishReason: String? = null,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val kind: AttachmentKind,
    val mimeType: String,
    val displayName: String,
    val localPath: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val detail: String? = null,
    val extractedText: String? = null,
    val truncated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val presetId: String,
    val displayName: String,
    val baseUrl: String,
    /** Android Keystore 别名，不存明文 Key。 */
    val apiKeyRef: String? = null,
    val enabled: Boolean = true,
    val isBuiltin: Boolean = false,
    val sortOrder: Int = 0,
    val extraJson: String? = null,
)

@Entity(tableName = "model_configs")
data class ModelConfigEntity(
    /** providerId + ":" + modelId */
    @PrimaryKey val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val contextTokens: Int = 32768,
    val maxOutputTokens: Int = 8192,
    val supportsVision: Boolean = false,
    val supportsStreamUsage: Boolean = true,
    val supportsReasoning: Boolean = false,
    val supportsTemperature: Boolean = true,
    val useMaxCompletionTokens: Boolean = false,
    val systemRole: String = "system",
    val enabled: Boolean = true,
    /** 内置目录模型：种子升级时“缺则补插”仅限未被用户清空的供应商。 */
    val isBuiltin: Boolean = false,
)

@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "chat_bubble",
    val systemPrompt: String,
    val variablesJson: String = "[]",
    val modelHint: String? = null,
    val temperature: Double? = null,
    val isBuiltin: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "usage_daily", primaryKeys = ["day", "model"])
data class UsageDailyEntity(
    val day: String,
    val model: String,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val requestCount: Long = 0,
)

@Entity(tableName = "aux_usage")
data class AuxUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** TITLE / TEST_CONNECTION */
    val kind: String,
    val refId: Long? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val source: String = "ESTIMATED",
    val createdAt: Long = System.currentTimeMillis(),
)
