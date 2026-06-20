package com.example.image.data.model

import android.graphics.Bitmap
import java.io.File

sealed interface ImageLoadState {
    data object Idle : ImageLoadState
    data object Loading : ImageLoadState
    data class Success(
        val bitmap: Bitmap,
        val file: File,
        val analysis: ImageAnalysis
    ) : ImageLoadState
    data class Error(val message: String) : ImageLoadState
}
