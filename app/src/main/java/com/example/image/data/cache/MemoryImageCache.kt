package com.example.image.data.cache

import android.graphics.Bitmap
import android.util.LruCache

class MemoryImageCache {
    private val cache = object : LruCache<String, Bitmap>(maxCacheSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount / 1024
        }
    }

    fun get(key: String): Bitmap? {
        return cache.get(key)
    }

    fun put(key: String, bitmap: Bitmap) {
        if (cache.get(key) == null) {
            cache.put(key, bitmap)
        }
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    private fun maxCacheSize(): Int {
        val runtime = Runtime.getRuntime()
        val maxMemoryKb = (runtime.maxMemory() / 1024).toInt()
        return maxMemoryKb / 8
    }
}
