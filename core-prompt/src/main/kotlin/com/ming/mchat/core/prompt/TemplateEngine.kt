package com.ming.mchat.core.prompt

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模板引擎：Mustache 子集 {{var}}。
 * 未定义变量保留原样；内置变量 date/time/model/app_version/attachment_names/locale。
 */
@Singleton
class TemplateEngine @Inject constructor() {

    // 注意：Android 的 ICU 正则要求 "}" 必须转义（桌面 JVM 容忍未转义的 "}"）。
    // 此前 "\s*}}" 在真机上抛 PatternSyntaxException 导致启动即闪退，已修复为 "\s*\}\}"。
    private val variablePattern = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}")

    fun render(
        template: String,
        variables: Map<String, String>,
        builtins: Map<String, String> = emptyMap(),
    ): String = variablePattern.replace(template) { match ->
        val key = match.groupValues[1]
        variables[key] ?: builtins[key] ?: match.value
    }

    companion object {
        const val APP_VERSION = "0.1.0"

        /** 内置变量上下文。 */
        fun builtinContext(
            modelId: String,
            attachmentNames: List<String> = emptyList(),
            locale: String = java.util.Locale.getDefault().toLanguageTag(),
            now: LocalDateTime = LocalDateTime.now(),
        ): Map<String, String> = mapOf(
            "date" to now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            "time" to now.format(DateTimeFormatter.ofPattern("HH:mm")),
            "model" to modelId,
            "app_version" to APP_VERSION,
            "attachment_names" to attachmentNames.joinToString("、"),
            "locale" to locale,
        )
    }
}
