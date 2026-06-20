package com.example.image.data.model

import androidx.compose.ui.graphics.Color

data class ImageAnalysis(
    val title: String,
    val description: String,
    val tags: List<String>,
    val mainColor: Color,
    val rgbText: String,
    val brightness: Float,
    val saturation: Float,
    val warmth: Float,
    val orientation: ImageOrientation
)

enum class ImageOrientation(val label: String) {
    Portrait("竖图"),
    Landscape("横图"),
    Square("方图")
}
