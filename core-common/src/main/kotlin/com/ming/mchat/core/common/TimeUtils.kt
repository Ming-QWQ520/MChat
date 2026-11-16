package com.ming.mchat.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 时间工具：会话分组与显示。 */
object TimeUtils {

    fun formatTime(epochMillis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    fun formatDateTime(epochMillis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    /** 消息时间戳：今天显示 HH:mm，更早显示 MM-dd HH:mm，便于长会话中定位消息。 */
    fun formatMessageTime(epochMillis: Long, now: LocalDate = today()): String =
        if (bucketOf(epochMillis, now) == 0) formatTime(epochMillis) else formatDateTime(epochMillis)

    fun today(): LocalDate = LocalDate.now()

    /** 会话时间分组桶：0=今天 1=昨天 2=7 天内 3=30 天内 4=更早 */
    fun bucketOf(epochMillis: Long, now: LocalDate = today()): Int {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val diff = now.toEpochDay() - date.toEpochDay()
        return when {
            diff <= 0L -> 0
            diff == 1L -> 1
            diff <= 7L -> 2
            diff <= 30L -> 3
            else -> 4
        }
    }

    fun bucketLabel(bucket: Int): String = when (bucket) {
        0 -> "今天"
        1 -> "昨天"
        2 -> "7 天内"
        3 -> "30 天内"
        else -> "更早"
    }
}
