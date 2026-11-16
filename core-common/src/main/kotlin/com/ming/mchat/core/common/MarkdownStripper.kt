package com.ming.mchat.core.common

/**
 * Markdown → 纯文本转换：用于消息“复制为纯文本”与分享场景。
 * 剥离常见 Markdown 语法（围栏/行内代码标记、标题、强调、删除线、链接、图片、
 * 引用、无序列表标记、水平线、表格分隔行），保留可读正文内容。
 * 未识别的语法保持原样，不做破坏性删除。
 */
object MarkdownStripper {

    private val FENCE = Regex("(?m)^```[^\\n]*$")
    private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val AUTOLINK = Regex("<((?:https?|mailto)://[^>]+)>")
    private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
    private val BLOCKQUOTE = Regex("(?m)^\\s{0,3}>\\s?")
    private val BOLD_ITALIC = Regex("(\\*{1,3}|_{1,3})(?=\\S)(.+?)(?<=\\S)\\1")
    private val STRIKETHROUGH = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val BULLET = Regex("(?m)^(\\s*)[-*+]\\s+")
    private val TABLE_SEPARATOR = Regex("(?m)^\\s*\\|?[\\s:|-]*-[-\\s:|]*\\|?\\s*$")
    private val TABLE_PIPE = Regex("(?m)^\\s*\\|(.*)\\|\\s*$")
    private val HR = Regex("(?m)^\\s{0,3}(?:[-*_]\\s*){3,}$")
    private val MULTI_BLANK = Regex("\\n{3,}")

    /** 将 Markdown 转为纯文本；空串原样返回。 */
    fun toPlainText(markdown: String): String {
        if (markdown.isBlank()) return markdown
        var text = markdown
            .replace(FENCE, "")
            .replace(IMAGE, "$1")
            .replace(LINK, "$1")
            .replace(AUTOLINK, "$1")
            .replace(HEADING, "")
            .replace(BLOCKQUOTE, "")
            .replace(STRIKETHROUGH, "$1")
            .replace(BOLD_ITALIC, "$2")
            .replace(BULLET, "$1")
            .replace(TABLE_SEPARATOR, "")
            .replace(TABLE_PIPE, "$1")
            .replace(HR, "")
        // 表格单元格分隔：| → 制表符，避免文字粘连
        text = text.replace(" | ", "\t").replace("|", "\t")
        return text.replace(MULTI_BLANK, "\n\n").trim()
    }
}
