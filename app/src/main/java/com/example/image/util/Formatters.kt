package com.example.image.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.CHINA, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.CHINA, "%.2f MB", mb)
}

fun formatDate(timestamp: Long, pattern: String): String {
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(timestamp))
}
