package com.ming.mchat.core.data.db

import androidx.room.TypeConverter
import com.ming.mchat.core.data.entity.AttachmentKind
import com.ming.mchat.core.data.entity.Role
import com.ming.mchat.core.data.entity.TitleSource
import com.ming.mchat.core.data.entity.UsageSource

/** Room 枚举类型转换（按 name 存储，与 DAO 中的 SQL 字面量保持一致）。 */
class Converters {
    @TypeConverter fun roleToString(value: Role?): String? = value?.name
    @TypeConverter fun stringToRole(value: String?): Role? = value?.let { runCatching { Role.valueOf(it) }.getOrNull() }

    @TypeConverter fun titleSourceToString(value: TitleSource?): String? = value?.name
    @TypeConverter fun stringToTitleSource(value: String?): TitleSource? = value?.let { runCatching { TitleSource.valueOf(it) }.getOrNull() }

    @TypeConverter fun usageSourceToString(value: UsageSource?): String? = value?.name
    @TypeConverter fun stringToUsageSource(value: String?): UsageSource? = value?.let { runCatching { UsageSource.valueOf(it) }.getOrNull() }

    @TypeConverter fun attachmentKindToString(value: AttachmentKind?): String? = value?.name
    @TypeConverter fun stringToAttachmentKind(value: String?): AttachmentKind? = value?.let { runCatching { AttachmentKind.valueOf(it) }.getOrNull() }
}
