package com.example.image.util

import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageLoadState

fun ImageLoadState.summaryText(): String {
    return when (this) {
        ImageLoadState.Idle -> "等待加载图片..."
        ImageLoadState.Loading -> "正在加载图片..."
        is ImageLoadState.Error -> "图片加载失败，可以重试。"
        is ImageLoadState.Success -> {
            "${analysis.title} · ${bitmap.width} x ${bitmap.height} · ${formatFileSize(file.length())}"
        }
    }
}

fun ImageLoadState.titleText(item: GalleryImage): String {
    return when (this) {
        ImageLoadState.Idle -> item.fallbackTitle()
        ImageLoadState.Loading -> "正在分析图片"
        is ImageLoadState.Error -> item.fallbackTitle()
        is ImageLoadState.Success -> "${item.fallbackTitle()} · ${analysis.title}"
    }
}

fun GalleryImage.useCaseText(): String {
    return "$category · $useCase"
}

fun ImageLoadState.dimensionText(): String {
    return when (this) {
        ImageLoadState.Idle -> "未加载"
        ImageLoadState.Loading -> "读取中"
        is ImageLoadState.Error -> "未知"
        is ImageLoadState.Success -> "${bitmap.width} x ${bitmap.height}"
    }
}

fun ImageLoadState.fileSizeText(): String {
    return when (this) {
        ImageLoadState.Idle -> "未缓存"
        ImageLoadState.Loading -> "下载中"
        is ImageLoadState.Error -> "未知"
        is ImageLoadState.Success -> formatFileSize(file.length())
    }
}

fun ImageLoadState.dateText(fallback: String): String {
    return when (this) {
        ImageLoadState.Idle -> fallback
        ImageLoadState.Loading -> fallback
        is ImageLoadState.Error -> fallback
        is ImageLoadState.Success -> formatDate(file.lastModified(), "yyyy-MM-dd")
    }
}

fun ImageLoadState.fullLocation(): String {
    return when (this) {
        ImageLoadState.Idle -> "未保存"
        ImageLoadState.Loading -> "等待保存"
        is ImageLoadState.Error -> "未保存"
        is ImageLoadState.Success -> file.absolutePath
    }
}

fun ImageLoadState.downloadTimeText(): String {
    return when (this) {
        ImageLoadState.Idle -> "未下载"
        ImageLoadState.Loading -> "下载中"
        is ImageLoadState.Error -> "未知"
        is ImageLoadState.Success -> formatDate(file.lastModified(), "yyyy-MM-dd HH:mm")
    }
}
