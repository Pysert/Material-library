package com.example.image.data.model

data class ImageLoadRequest(
    val image: GalleryImage,
    val priority: ImageLoadPriority,
    val distanceToViewport: Int,
    val canCancel: Boolean = true
)
