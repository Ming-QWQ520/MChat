package com.ming.mchat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor

/**
 * 轻量 Markdown 渲染：段落 / 标题 / 列表 / 引用 / 围栏代码块（等宽 + 复制），
 * 行内支持 **加粗** 与 `代码`。
 * 表格、脚注、公式、Mermaid 等增强项在 backlog 中待自动迭代实现。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(text) { parseBlocks(text) }
    val textColor = if (color == Color.Unspecified) LocalContentColor.current else color
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Code -> CodeBlockView(block, key = "$index")
                is MdBlock.Heading -> Text(
                    text = block.text,
                    color = textColor,
                    style = style.copy(
                        fontSize = style.fontSize * (1.55f - 0.12f * block.level),
                        fontWeight = FontWeight.Bold,
                        lineHeight = style.lineHeight * (1.4f - 0.1f * block.level),
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                is MdBlock.Bullet -> Row {
                    Text("•  ", color = textColor, style = style)
                    InlineStyledText(block.text, textColor, style, Modifier.weight(1f))
                }
                is MdBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    InlineStyledText(
                        block.text,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        style,
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                is MdBlock.Paragraph -> InlineStyledText(block.text, textColor, style, Modifier.fillMaxWidth())
            }
        }
    }
}

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
}

private fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var index = 0
    val paragraph = StringBuilder()
    fun flushParagraph() {
        val trimmed = paragraph.toString().trim()
        if (trimmed.isNotEmpty()) blocks += MdBlock.Paragraph(trimmed)
        paragraph.clear()
    }
    while (index < lines.size) {
        val line = lines[index]
        val fence = Regex("^```(\\w*)\\s*$").find(line.trim())
        when {
            fence != null -> {
                flushParagraph()
                val language = fence.groupValues[1]
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.appendLine(lines[index])
                    index++
                }
                blocks += MdBlock.Code(language, code.toString().trimEnd())
            }
            line.trimStart().startsWith("#") -> {
                flushParagraph()
                val matched = Regex("^(#{1,4})\\s+(.*)$").find(line.trim())!!
                blocks += MdBlock.Heading(matched.groupValues[1].length, matched.groupValues[2])
            }
            Regex("^\\s*[-*+]\\s+.+").matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Bullet(line.trim().drop(1).trim())
            }
            line.trimStart().startsWith("> ") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.trim().drop(2).trim())
            }
            line.isBlank() -> flushParagraph()
            else -> {
                paragraph.appendLine(line)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

@Composable
private fun InlineStyledText(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val annotated = remember(text) { buildInlineAnnotated(text) }
    androidx.compose.material3.Text(
        text = annotated,
        color = color,
        style = style,
        modifier = modifier,
    )
}

private val inlinePattern = Regex("(?s)(\\*\\*(.+?)\\*\\*)|(`([^`]+)`)")

private fun buildInlineAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    inlinePattern.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        when {
            match.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[2])
            }
            match.groupValues[4].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            ) {
                append(match.groupValues[4])
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
private fun CodeBlockView(block: MdBlock.Code, key: String) {
    val clipboard = LocalClipboardManager.current
    val dark = isSystemInDarkTheme()
    Surface(
        color = if (dark) Color(0xFF14151A) else Color(0xFF1E2227),
        contentColor = Color(0xFFE6E6E6),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    block.language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CDCFE),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }, modifier = Modifier.padding(0.dp)) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                        tint = Color(0xFF9CDCFE),
                        modifier = Modifier.padding(0.dp),
                    )
                }
            }
            SelectionContainer {
                Text(
                    block.code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}
