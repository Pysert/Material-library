package com.example.image.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import com.example.image.data.cache.ImageCacheStore
import com.example.image.data.cache.MemoryImageCache
import com.example.image.data.model.CachedImage
import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageAnalysis
import com.example.image.data.model.ImageSize
import com.example.image.data.remote.ImageRemoteDataSource
import com.example.image.util.ImageAnalyzer
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ImageRepository(
    context: Context,
    private val remoteDataSource: ImageRemoteDataSource = ImageRemoteDataSource(),
    private val cacheStore: ImageCacheStore = ImageCacheStore(context),
    private val memoryCache: MemoryImageCache = MemoryImageCache()
) {
    private val appContext = context.applicationContext
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val analysisCache = ConcurrentHashMap<String, ImageAnalysis>()
    private val downloadSemaphore = Semaphore(4)
    private val prefs = appContext.getSharedPreferences("material_library", Context.MODE_PRIVATE)

    fun loadCatalogSeed(): Int {
        val saved = prefs.getInt(KEY_CATALOG_SEED, -1)
        if (saved != -1) return saved
        return createNewCatalogSeed()
    }

    fun createNewCatalogSeed(): Int {
        val seed = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        prefs.edit().putInt(KEY_CATALOG_SEED, seed).apply()
        return seed
    }

    fun loadFavoriteItems(): List<GalleryImage> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())
            .orEmpty()
            .mapNotNull { GalleryImage.fromFavoriteRecord(it) }
    }

    fun saveFavoriteItems(items: List<GalleryImage>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITES, items.map { it.toFavoriteRecord() }.toSet())
            .apply()
    }

    fun loadRecentItems(): List<GalleryImage> {
        return prefs.getString(KEY_RECENT, null)
            ?.split(RECORD_SEPARATOR)
            ?.mapNotNull { GalleryImage.fromFavoriteRecord(it) }
            .orEmpty()
    }

    fun saveRecentItems(items: List<GalleryImage>) {
        prefs.edit()
            .putString(KEY_RECENT, items.take(MAX_RECENT_COUNT).joinToString(RECORD_SEPARATOR) { it.toFavoriteRecord() })
            .apply()
    }

    suspend fun loadImage(item: GalleryImage, size: ImageSize): Result<CachedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val file = cacheStore.cacheFile(item, size)
            val cacheKey = cacheKey(item, size)
            val memoryBitmap = memoryCache.get(cacheKey)
            if (memoryBitmap != null) {
                return@runCatching cachedImage(cacheKey, memoryBitmap, file)
            }

            val lock = locks.computeIfAbsent("${size.dirName}_${item.id}") { Mutex() }
            lock.withLock {
                val lockedMemoryBitmap = memoryCache.get(cacheKey)
                if (lockedMemoryBitmap != null) {
                    return@withLock cachedImage(cacheKey, lockedMemoryBitmap, file)
                }

                val cachedBitmap = cacheStore.decodeBitmap(file, maxDecodeSize(size))
                if (cachedBitmap != null) {
                    file.setLastModified(System.currentTimeMillis())
                    memoryCache.put(cacheKey, cachedBitmap)
                    return@withLock cachedImage(cacheKey, cachedBitmap, file)
                }

                file.delete()
                downloadSemaphore.withPermit {
                    downloadToCache(item, size, file)
                }
                cacheStore.trimToSize(MAX_DISK_CACHE_BYTES)
                val downloadedBitmap = cacheStore.decodeBitmap(file, maxDecodeSize(size))
                    ?: throw IOException("鍥剧墖鏂囦欢鏃犳硶瑙ｆ瀽")
                memoryCache.put(cacheKey, downloadedBitmap)
                cachedImage(cacheKey, downloadedBitmap, file)
            }
        }
    }

    suspend fun clearImage(item: GalleryImage) {
        ImageSize.entries.forEach { size ->
            val key = cacheKey(item, size)
            memoryCache.remove(key)
            analysisCache.remove(key)
        }
        cacheStore.clearImage(item)
    }

    suspend fun saveToAlbum(item: GalleryImage, sourceFile: File): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val fileName = "material_${item.id}_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MaterialLibrary")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("鏃犳硶鍒涘缓鐩稿唽鏂囦欢")
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("鏃犳硶鍐欏叆鐩稿唽鏂囦欢")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }
    }

    private suspend fun downloadToCache(item: GalleryImage, size: ImageSize, file: File) {
        val tempFile = cacheStore.tempFileFor(file)
        tempFile.delete()
        try {
            remoteDataSource.downloadTo(item.urlFor(size), tempFile)
            if (!cacheStore.canDecodeImage(tempFile)) {
                throw IOException("涓嬭浇鍐呭涓嶆槸鏈夋晥鍥剧墖")
            }
            file.delete()
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (error: Exception) {
            tempFile.delete()
            file.delete()
            throw error
        }
    }

    private fun GalleryImage.urlFor(size: ImageSize): String {
        return if (size == ImageSize.Thumb) thumbUrl else fullUrl
    }

    private fun cacheKey(item: GalleryImage, size: ImageSize): String {
        return "${size.dirName}_${item.id}"
    }

    private fun cachedImage(cacheKey: String, bitmap: Bitmap, file: File): CachedImage {
        val analysis = analysisCache[cacheKey] ?: ImageAnalyzer.analyze(bitmap).also {
            analysisCache[cacheKey] = it
        }
        return CachedImage(
            bitmap = bitmap,
            file = file,
            analysis = analysis
        )
    }

    private fun maxDecodeSize(size: ImageSize): Int {
        return if (size == ImageSize.Thumb) 480 else 1600
    }

    private companion object {
        const val KEY_CATALOG_SEED = "catalog_seed"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT = "recent_items"
        const val RECORD_SEPARATOR = "\n"
        const val MAX_RECENT_COUNT = 50
        const val MAX_DISK_CACHE_BYTES = 100L * 1024L * 1024L
    }
}

