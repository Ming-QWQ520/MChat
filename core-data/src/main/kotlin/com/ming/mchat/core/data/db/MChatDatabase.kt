package com.ming.mchat.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ming.mchat.core.data.dao.AttachmentDao
import com.ming.mchat.core.data.dao.AuxUsageDao
import com.ming.mchat.core.data.dao.ConversationDao
import com.ming.mchat.core.data.dao.MessageDao
import com.ming.mchat.core.data.dao.ModelDao
import com.ming.mchat.core.data.dao.PresetDao
import com.ming.mchat.core.data.dao.ProviderDao
import com.ming.mchat.core.data.dao.UsageDao
import com.ming.mchat.core.data.entity.AttachmentEntity
import com.ming.mchat.core.data.entity.AuxUsageEntity
import com.ming.mchat.core.data.entity.ConversationEntity
import com.ming.mchat.core.data.entity.MessageEntity
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.PromptPresetEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity
import com.ming.mchat.core.data.entity.UsageDailyEntity

/** v1 → v2：会话表新增置顶列（isPinned，默认 false）。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ProviderConfigEntity::class,
        ModelConfigEntity::class,
        PromptPresetEntity::class,
        UsageDailyEntity::class,
        AuxUsageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun presetDao(): PresetDao
    abstract fun usageDao(): UsageDao
    abstract fun auxUsageDao(): AuxUsageDao
}
