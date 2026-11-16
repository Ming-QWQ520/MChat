package com.ming.mchat.core.prompt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 注入扫描：只标记常见注入模式，不删除任何内容；发送前由 UI 弹确认。
 */
@Singleton
class InjectionSanitizer @Inject constructor() {

    private data class Rule(val label: String, val regex: Regex)

    private val rules = listOf(
        Rule(
            "疑似“忽略此前指令”式注入",
            Regex("(?i)(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above)\\s+(instructions?|prompts?|context|rules?)"),
        ),
        Rule(
            "疑似“新系统指令”覆盖",
            Regex("(?i)(new|updated?|override)\\s+(system|developer)\\s+(instructions?|prompt)"),
        ),
        Rule(
            "疑似角色劫持",
            Regex("(?i)you\\s+are\\s+now\\s+(a|an)\\s+"),
        ),
        Rule(
            "疑似消息角色伪装",
            Regex("(?i)\\n\\s*(system|developer|assistant)\\s*[:：]"),
        ),
        Rule(
            "疑似套取系统提示词",
            Regex("(?i)(reveal|show|print|repeat)\\s+(your\\s+)?(system\\s+)?(prompt|instructions)"),
        ),
        Rule(
            "疑似中文语境注入指令",
            Regex("(忽略|无视|覆盖)(之前|以上|先前)的(指令|提示|规则)|进入开发者模式|系统提示词泄露"),
        ),
    )

    /** 返回命中规则的友好提示列表；空列表表示未发现可疑模式。 */
    fun scan(text: String): List<String> = rules
        .filter { it.regex.containsMatchIn(text) }
        .map { it.label }

    /** 附件注入包裹：强分隔符 + 安全声明由 PromptResolver 统一处理。 */
    fun wrapUntrustedFile(name: String, content: String): String =
        "<untrusted_file name=\"$name\">\n$content\n</untrusted_file>"
}
