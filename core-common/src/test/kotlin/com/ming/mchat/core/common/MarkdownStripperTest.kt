package com.ming.mchat.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStripperTest {

    @Test
    fun `标题与强调被剥离`() {
        val md = "### 标题\n这是**加粗**和*斜体*还有__下划线__的文本"
        val plain = MarkdownStripper.toPlainText(md)
        assertEquals("标题\n这是加粗和斜体还有下划线的文本", plain)
    }

    @Test
    fun `链接与图片只保留文字`() {
        val md = "看 [官网](https://example.com) 与 ![截图](img.png)"
        assertEquals("看 官网 与 截图", MarkdownStripper.toPlainText(md))
    }

    @Test
    fun `围栏代码保留内容去掉标记`() {
        val md = "示例：\n```kotlin\nval a = 1\n```\n结束"
        val plain = MarkdownStripper.toPlainText(md)
        assertTrue(plain.contains("val a = 1"))
        assertTrue(!plain.contains("```"))
    }

    @Test
    fun `列表与引用标记被剥离`() {
        val md = "- 项目一\n- 项目二\n\n> 引用内容"
        val plain = MarkdownStripper.toPlainText(md)
        assertTrue(plain.contains("项目一"))
        assertTrue(plain.contains("引用内容"))
        assertTrue(!plain.contains("- 项目"))
        assertTrue(!plain.contains("> "))
    }

    @Test
    fun `删除线与表格分隔行`() {
        val md = "~~过期~~内容\n\n| A | B |\n|---|---|\n| 1 | 2 |"
        val plain = MarkdownStripper.toPlainText(md)
        assertTrue(plain.contains("内容"))
        assertTrue(!plain.contains("~~"))
        assertTrue(!plain.contains("---"))
        assertTrue(plain.contains("1"))
    }

    @Test
    fun `空串与纯文本原样返回`() {
        assertEquals("", MarkdownStripper.toPlainText(""))
        assertEquals("普通文本", MarkdownStripper.toPlainText("普通文本"))
    }

    @Test
    fun `自动链接尖括号被去除`() {
        assertEquals("https://example.com", MarkdownStripper.toPlainText("<https://example.com>"))
    }
}
