package com.example.image.data.model

import android.graphics.Bitmap
import java.io.File

data class CachedImage(
    val bitmap: Bitmap,
    val file: File,
    val analysis: ImageAnalysis
)
