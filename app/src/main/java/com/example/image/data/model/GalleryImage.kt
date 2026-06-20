package com.example.image.data.model

import java.net.URLDecoder
import java.net.URLEncoder

data class GalleryImage(
    val id: String,
    val photoDate: String,
    val thumbUrl: String,
    val fullUrl: String,
    val aspectRatio: Float,
    val contentTitle: String = "",
    val category: String = "灵感素材",
    val useCase: String = "素材收集",
    val keywords: List<String> = emptyList()
) {
    fun toFavoriteRecord(): String {
        return listOf(
            id,
            photoDate,
            thumbUrl,
            fullUrl,
            aspectRatio.toString(),
            contentTitle,
            category,
            useCase,
            keywords.joinToString(",")
        ).joinToString("|") { value ->
            URLEncoder.encode(value, "UTF-8")
        }
    }

    fun fallbackTitle(): String {
        return contentTitle.ifBlank { "$category ${id.substringAfterLast('_')}" }
    }

    fun searchableText(): String {
        return listOf(
            id,
            photoDate,
            contentTitle,
            category,
            useCase,
            keywords.joinToString(" ")
        ).joinToString(" ")
    }

    companion object {
        fun fromFavoriteRecord(record: String): GalleryImage? {
            val parts = record.split("|")
            if (parts.size < 5) return null
            return runCatching {
                val values = parts.map { URLDecoder.decode(it, "UTF-8") }
                GalleryImage(
                    id = values[0],
                    photoDate = values[1],
                    thumbUrl = values[2],
                    fullUrl = values[3],
                    aspectRatio = values[4].toFloat(),
                    contentTitle = values.getOrNull(5).orEmpty(),
                    category = values.getOrNull(6)?.ifBlank { "收藏素材" } ?: "收藏素材",
                    useCase = values.getOrNull(7)?.ifBlank { "素材复用" } ?: "素材复用",
                    keywords = values.getOrNull(8)
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                )
            }.getOrNull()
        }
    }
}
