package org.fungalsentinel.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class RawCaptureProcessor(private val context: Context) {

    companion object {
        private const val ALBUM_NAME = "FungalSentinel"
        private const val THUMBNAIL_SIZE_PX = 256
    }

    fun saveToGallery(
        dngFile: File,
        projectName: String,
        step: String
    ): Uri? {
        if (!dngFile.exists()) return null

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, dngFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(dngFile).use { input ->
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    // ← 修复：使用 androidx.exifinterface 的 getAttribute() 方法
    fun extractMetadata(dngFile: File): Map<String, Any> {
        if (!dngFile.exists()) return emptyMap()

        return try {
            val exif = ExifInterface(FileInputStream(dngFile))
            mapOf(
                "exposure" to (exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toLongOrNull() ?: 0L),
                "iso" to (exif.getAttribute(ExifInterface.TAG_ISO_SPEED)?.toIntOrNull() ?: 0),
                "focus" to (exif.getAttribute("SubjectDistance")?.toFloatOrNull() ?: 0f),
                "width" to (exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.toIntOrNull() ?: 0),
                "height" to (exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.toIntOrNull() ?: 0),
                "timestamp" to (
                        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                            ?: ""
                        ),
                "make" to (exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""),
                "model" to (exif.getAttribute(ExifInterface.TAG_MODEL) ?: "")
            )
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun generateThumbnail(dngFile: File): File? {
        if (!dngFile.exists()) return null

        return try {
            val exif = ExifInterface(FileInputStream(dngFile))
            val thumbBytes = exif.thumbnailBytes
            if (thumbBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
                return writeScaledBitmap(bitmap, dngFile)
            }

            val bitmap = BitmapFactory.decodeFile(dngFile.absolutePath)
            if (bitmap != null) writeScaledBitmap(bitmap, dngFile) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeScaledBitmap(original: Bitmap, sourceFile: File): File {
        val scale = minOf(
            THUMBNAIL_SIZE_PX.toFloat() / original.width,
            THUMBNAIL_SIZE_PX.toFloat() / original.height
        ).coerceAtMost(1f)
        val targetW = (original.width * scale).toInt().coerceAtLeast(1)
        val targetH = (original.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)

        val thumbFile = File(
            context.cacheDir,
            "thumb_${sourceFile.nameWithoutExtension}.jpg"
        )
        FileOutputStream(thumbFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== original) scaled.recycle()
        return thumbFile
    }

    fun deleteFromGallery(uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}