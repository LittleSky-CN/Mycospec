package org.fungalsentinel.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class RawCaptureProcessor(private val context: Context) {
    companion object {
        private const val ALBUM_NAME = "FungalSentinel"
        private const val THUMBNAIL_SIZE_PX = 256
    }

    fun saveToGallery(dngFile: File, projectName: String, step: String): Uri? {
        if (!dngFile.exists()) return null
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, dngFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> FileInputStream(dngFile).use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) { resolver.delete(uri, null, null); null }
    }

    fun extractMetadata(dngFile: File): Map<String, Any> {
        if (!dngFile.exists()) return emptyMap()
        return try {
            val exif = android.media.ExifInterface(dngFile.absolutePath)
            mapOf(
                "exposure" to (exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull() ?: 0.0),
                "iso" to (exif.getAttribute(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS)?.toIntOrNull() ?: 0),
                "width" to (exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_WIDTH)?.toIntOrNull() ?: 0),
                "height" to (exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_LENGTH)?.toIntOrNull() ?: 0),
                "timestamp" to (exif.getAttribute(android.media.ExifInterface.TAG_DATETIME) ?: ""),
                "make" to (exif.getAttribute(android.media.ExifInterface.TAG_MAKE) ?: ""),
                "model" to (exif.getAttribute(android.media.ExifInterface.TAG_MODEL) ?: "")
            )
        } catch (e: Exception) { emptyMap() }
    }

    fun generateThumbnail(dngFile: File): File? {
        if (!dngFile.exists()) return null
        return try {
            val exif = android.media.ExifInterface(dngFile.absolutePath)
            val thumbBytes = exif.thumbnailBytes
            val bitmap = thumbBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?: BitmapFactory.decodeFile(dngFile.absolutePath)
                ?: return null
            writeScaledBitmap(bitmap, dngFile)
        } catch (e: Exception) { null }
    }

    private fun writeScaledBitmap(original: Bitmap, sourceFile: File): File {
        val scale = minOf(THUMBNAIL_SIZE_PX.toFloat() / original.width, THUMBNAIL_SIZE_PX.toFloat() / original.height).coerceAtMost(1f)
        val scaled = Bitmap.createScaledBitmap(
            original, (original.width * scale).toInt().coerceAtLeast(1),
            (original.height * scale).toInt().coerceAtLeast(1), true
        )
        val thumbFile = File(context.cacheDir, "thumb_${sourceFile.nameWithoutExtension}.jpg")
        FileOutputStream(thumbFile).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (scaled !== original) scaled.recycle()
        return thumbFile
    }

    fun deleteFromGallery(uri: Uri): Boolean = try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (e: Exception) { false }
}