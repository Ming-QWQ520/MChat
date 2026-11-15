package com.ming.mchat.core.common

/** 通用字符串工具。 */
object StringExt {

    /** 会话兜底标题：压空白后按 CJK/拉丁分别截断。 */
    fun truncateTitle(raw: String, maxCjk: Int = 20, maxOther: Int = 40): String {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return cleaned
        val cjk = cleaned.count { it.code >= 0x2E80 }
        val limit = if (cjk * 2 >= cleaned.length) maxCjk else maxOther
        return if (cleaned.length <= limit) cleaned else cleaned.take(limit) + "…"
    }

    /** 余额 / 百分比简写：>999 显示为 1.2K，>9999 显示为 1.2万。 */
    fun shortenAmount(value: Double): String = when {
        value >= 9999 -> String.format(java.util.Locale.ROOT, "%.1f万", value / 10000)
        value >= 1000 -> String.format(java.util.Locale.ROOT, "%.1fK", value / 1000)
        else -> if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", value)
    }
}
