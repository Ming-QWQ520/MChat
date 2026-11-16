package com.ming.mchat.core.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片预处理：
 * - 读 bounds 防 OOM（inSampleSize 预降采样）；
 * - EXIF 方向校正（重编码后即剥离 EXIF）；
 * - 透明 PNG 铺白底，统一转 JPEG；
 * - 长边压缩到上限，循环降质量/降尺寸保证单图不超过大小上限。
 */
@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Result(val file: File, val width: Int, val height: Int, val sizeBytes: Long)

    suspend fun process(
        uri: Uri,
        targetDir: File,
        maxLongEdge: Int,
        jpegQuality: Int,
        maxBytes: Long,
    ): Result = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取图片")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("图片解码失败（可能为不支持的 HEIC/损坏文件）")
        }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxLongEdge / 2 &&
            bounds.outHeight / (sample * 2) >= maxLongEdge / 2
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IOException("图片解码失败")

        bitmap = applyExifRotation(bytes, bitmap)

        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / longEdge
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }

        if (bitmap.hasAlpha()) {
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(flattened).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            bitmap = flattened
        }

        targetDir.mkdirs()
        val outFile = File(targetDir, "${UUID.randomUUID()}.jpg")
        var quality = jpegQuality.coerceIn(40, 100)
        var output = compress(bitmap, quality)
        while (output.size > maxBytes && quality > 40) {
            quality -= 10
            output = compress(bitmap, quality)
        }
        if (output.size > maxBytes) {
            val scale = 0.8f
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            output = compress(bitmap, quality)
        }
        outFile.writeBytes(output)

        Result(
            file = outFile,
            width = bitmap.width,
            height = bitmap.height,
            sizeBytes = outFile.length(),
        )
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }

    private fun applyExifRotation(sourceBytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(sourceBytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
