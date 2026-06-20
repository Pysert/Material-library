package com.example.image.data.model

enum class ImageLoadPriority(val weight: Int) {
    Visible(0),
    Nearby(1),
    Prefetch(2)
}
