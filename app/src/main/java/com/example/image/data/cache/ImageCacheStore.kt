package com.example.image.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageSize
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageCacheStore(context: Context) {
    private val appContext = context.applicationContext
    private val imageRoot: File
        get() = File(appContext.filesDir, "image_cache").apply { mkdirs() }

    fun cacheFile(item: GalleryImage, size: ImageSize): File {
        val dir = File(imageRoot, size.dirName).apply { mkdirs() }
        return File(dir, "${sha256(item.urlFor(size))}.jpg")
    }

    fun tempFileFor(file: File): File {
        return File(file.parentFile, "${file.name}.download")
    }

    fun decodeBitmap(file: File, maxSize: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize, maxSize)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun canDecodeImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    suspend fun clearImage(item: GalleryImage) = withContext(Dispatchers.IO) {
        ImageSize.entries.forEach { size -> cacheFile(item, size).delete() }
    }

    suspend fun trimToSize(maxBytes: Long) = withContext(Dispatchers.IO) {
        val files = imageRoot
            .walkTopDown()
            .filter { it.isFile && it.extension != "download" }
            .toList()

        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxBytes) return@withContext

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalSize <= maxBytes) return@forEach
            val length = file.length()
            if (file.delete()) {
                totalSize -= length
            }
        }
    }

    private fun GalleryImage.urlFor(size: ImageSize): String {
        return if (size == ImageSize.Thumb) thumbUrl else fullUrl
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
