package com.ming.mchat.core.attachment

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractResult(val text: String, val truncated: Boolean)

/** 文本文件提取：编码检测（UTF-8 严格 → GBK → Latin-1 兜底）+ 超长截断。 */
@Singleton
class TextFileExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extract(file: File, maxChars: Int): ExtractResult = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        val text = decodeText(bytes)
        if (text.length <= maxChars) ExtractResult(text, false)
        else ExtractResult(text.take(maxChars) + "\n…[内容过长，已截断]", true)
    }

    private fun decodeText(bytes: ByteArray): String {
        runCatching {
            val decoder = Charset.forName("UTF-8").newDecoder()
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }
        runCatching {
            return String(bytes, charset("GBK"))
        }
        return String(bytes, Charset.forName("ISO-8859-1"))
    }
}

/** PDF 提取：PdfBox-Android 按页提取，总字数上限，扫描件提示。 */
@Singleton
class PdfTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extract(file: File, maxChars: Int): ExtractResult = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()
            val builder = StringBuilder()
            var truncated = false
            val pageCount = document.numberOfPages
            for (page in 1..pageCount) {
                stripper.setStartPage(page)
                stripper.setEndPage(page)
                val pageText = stripper.getText(document)
                if (builder.length + pageText.length > maxChars) {
                    builder.append(pageText, 0, maxChars - builder.length)
                    truncated = true
                    break
                }
                builder.append(pageText)
            }
            var text = builder.toString().trim()
            if (text.isEmpty()) {
                text = "（未提取到文本：可能是扫描件，暂不支持 OCR）"
            }
            ExtractResult(text, truncated)
        }
    }
}
