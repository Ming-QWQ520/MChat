package com.ming.mchat.core.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.ming.mchat.core.data.dao.AttachmentDao
import com.ming.mchat.core.data.datastore.AttachmentSettings
import com.ming.mchat.core.data.entity.AttachmentEntity
import com.ming.mchat.core.data.entity.AttachmentKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 附件仓库：content:// 立即复制到应用私有目录；图片压缩重编码；
 * 文本/PDF 提取；绑定消息行；缓存清理。
 */
@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentDao: AttachmentDao,
    private val imageProcessor: ImageProcessor,
    private val textFileExtractor: TextFileExtractor,
    private val pdfTextExtractor: PdfTextExtractor,
) {

    fun attachmentsDir(): File = File(context.filesDir, "attachments").apply { mkdirs() }

    /** Photo Picker 图片导入（压缩 + 私有目录）。 */
    suspend fun importImages(uris: List<Uri>, settings: AttachmentSettings): List<PendingAttachment> =
        withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching {
                    val result = imageProcessor.process(
                        uri = uri,
                        targetDir = attachmentsDir(),
                        maxLongEdge = settings.imageMaxLongEdge,
                        jpegQuality = settings.jpegQuality,
                        maxBytes = settings.imageMaxBytes,
                    )
                    PendingAttachment(
                        kind = AttachmentKind.IMAGE,
                        mimeType = "image/jpeg",
                        displayName = queryDisplayName(uri) ?: "image.jpg",
                        localPath = result.file.absolutePath,
                        sizeBytes = result.sizeBytes,
                        width = result.width,
                        height = result.height,
                        detail = "auto",
                    )
                }.getOrNull()
            }
        }

    /** 文档导入（txt/md/csv/json + pdf）。 */
    suspend fun importDocuments(uris: List<Uri>, settings: AttachmentSettings): List<PendingAttachment> =
        withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    val name = queryDisplayName(uri) ?: "file"
                    val copied = copyToPrivate(uri, name)
                    val isPdf = mime.contains("pdf") || name.endsWith(".pdf", ignoreCase = true)
                    val extract = if (isPdf) {
                        pdfTextExtractor.extract(copied, settings.pdfMaxChars)
                    } else {
                        textFileExtractor.extract(copied, TEXT_MAX_CHARS)
                    }
                    PendingAttachment(
                        kind = if (isPdf) AttachmentKind.PDF else AttachmentKind.TEXT,
                        mimeType = mime.ifBlank { if (isPdf) "application/pdf" else "text/plain" },
                        displayName = name,
                        localPath = copied.absolutePath,
                        sizeBytes = copied.length(),
                        extractedText = extract.text,
                        truncated = extract.truncated,
                    )
                }.getOrNull()
            }
        }

    /** 绑定到消息（落库）。 */
    suspend fun bindToMessage(messageId: Long, pendings: List<PendingAttachment>) {
        withContext(Dispatchers.IO) {
            pendings.forEach { p ->
                attachmentDao.insert(
                    AttachmentEntity(
                        messageId = messageId,
                        kind = p.kind,
                        mimeType = p.mimeType,
                        displayName = p.displayName,
                        localPath = p.localPath,
                        sizeBytes = p.sizeBytes,
                        width = p.width,
                        height = p.height,
                        detail = p.detail,
                        extractedText = p.extractedText,
                        truncated = p.truncated,
                    )
                )
            }
        }
    }

    suspend fun attachmentsForMessage(messageId: Long): List<AttachmentEntity> =
        attachmentDao.getByMessage(messageId)

    /** 会话内全部附件（消息内图片/文件渲染用）。 */
    suspend fun attachmentsForConversation(conversationId: Long): List<AttachmentEntity> =
        attachmentDao.getByConversation(conversationId)

    /** 图片转 data URL（IO 线程编码）。 */
    suspend fun toDataUrl(localPath: String, mimeType: String): String = withContext(Dispatchers.IO) {
        val bytes = File(localPath).readBytes()
        "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    /** 清空附件缓存，返回释放的字节数。 */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val dir = attachmentsDir()
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            freed += file.length()
            runCatching { file.delete() }
        }
        freed
    }

    private fun copyToPrivate(uri: Uri, displayName: String): File {
        val target = File(attachmentsDir(), "${System.nanoTime()}_$displayName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取所选文件")
        return target
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        const val TEXT_MAX_CHARS = 100_000
    }
}
