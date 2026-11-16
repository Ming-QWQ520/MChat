package com.ming.mchat.core.attachment

import android.content.Context
import com.ming.mchat.core.data.entity.AttachmentKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 待发送附件（尚未绑定到消息）。 */
data class PendingAttachment(
    val localId: String = UUID.randomUUID().toString(),
    val kind: AttachmentKind,
    val mimeType: String,
    val displayName: String,
    val localPath: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val detail: String = "auto",
    val extractedText: String? = null,
    val truncated: Boolean = false,
    val estimatedTokens: Int = 0,
)

/** 附件初始化：PDFBox 资源加载（Application onCreate 调用一次）。 */
@Singleton
class AttachmentInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching { PDFBoxResourceLoader.init(context) }
            initialized = true
        }
    }
}
