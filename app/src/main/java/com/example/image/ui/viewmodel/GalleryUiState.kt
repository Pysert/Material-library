package com.example.image.ui.viewmodel

import com.example.image.data.model.GalleryFilter
import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageLoadState

data class GalleryUiState(
    val activeItems: List<GalleryImage> = emptyList(),
    val discoverItems: List<GalleryImage> = emptyList(),
    val imageStates: Map<String, ImageLoadState> = emptyMap(),
    val fullImageStates: Map<String, ImageLoadState> = emptyMap(),
    val favoriteItems: List<GalleryImage> = emptyList(),
    val recentItems: List<GalleryImage> = emptyList(),
    val selectedFilter: GalleryFilter = GalleryFilter.All,
    val searchQuery: String = "",
    val selectedImage: GalleryImage? = null,
    val albumMessage: String = "",
    val isRefreshing: Boolean = false,
    val loadedCatalogCount: Int = 0,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false
) {
    val favoriteIds: Set<String> = favoriteItems.map { it.id }.toSet()
    val recentIds: Set<String> = recentItems.map { it.id }.toSet()
}
