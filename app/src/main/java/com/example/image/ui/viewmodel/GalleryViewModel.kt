package com.example.image.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.image.data.model.CachedImage
import com.example.image.data.model.GalleryFilter
import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageLoadPriority
import com.example.image.data.model.ImageLoadRequest
import com.example.image.data.model.ImageLoadState
import com.example.image.data.model.ImageSize
import com.example.image.data.repository.GalleryFactory
import com.example.image.data.repository.ImageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: ImageRepository
) : ViewModel() {
    private var catalogSeed = repository.loadCatalogSeed()
    private var catalogImages = GalleryFactory.create(catalogSeed)
    private var loadedCatalogCount = PAGE_SIZE.coerceAtMost(catalogImages.size)
    private val initialFavoriteItems = repository.loadFavoriteItems()
    private val initialRecentItems = repository.loadRecentItems()
    private var generation = 0
    private var isPageLoading = false
    private val thumbJobs = mutableMapOf<String, ThumbJob>()
    private val pendingThumbRequests = mutableMapOf<String, ImageLoadRequest>()
    private val _uiState = MutableStateFlow(
        GalleryUiState(
            activeItems = mergeImages(loadedCatalogImages(), initialFavoriteItems, initialRecentItems),
            discoverItems = loadedCatalogImages(),
            favoriteItems = initialFavoriteItems,
            recentItems = initialRecentItems,
            loadedCatalogCount = loadedCatalogCount,
            canLoadMore = canLoadMoreCatalog()
        )
    )
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun scheduleThumbLoads(requests: List<ImageLoadRequest>) {
        val requestGeneration = generation
        val normalizedRequests = requests
            .distinctBy { it.image.id }
            .sortedWith(
                compareBy<ImageLoadRequest> { it.priority.weight }
                    .thenBy { it.distanceToViewport }
            )
        trimThumbJobs(normalizedRequests, requestGeneration)

        normalizedRequests.forEach { request ->
            val item = request.image
            val running = thumbJobs[item.id]
            if (running != null) {
                running.priority = minPriority(running.priority, request.priority)
                running.distanceToViewport = minOf(running.distanceToViewport, request.distanceToViewport)
                return@forEach
            }
            val state = _uiState.value.imageStates[item.id]
            if (state is ImageLoadState.Success) return@forEach

            val pending = pendingThumbRequests[item.id]
            pendingThumbRequests[item.id] = if (pending == null) {
                request
            } else {
                mergeRequest(pending, request)
            }
        }
        freeSlotForVisibleRequests(normalizedRequests, requestGeneration)
        startNextThumbJobs(requestGeneration)
    }

    private fun startNextThumbJobs(requestGeneration: Int) {
        while (thumbJobs.size < MAX_ACTIVE_THUMB_JOBS && pendingThumbRequests.isNotEmpty()) {
            val request = pendingThumbRequests.values
                .sortedWith(
                    compareBy<ImageLoadRequest> { it.priority.weight }
                        .thenBy { it.distanceToViewport }
                )
                .first()
            val item = request.image
            pendingThumbRequests.remove(item.id)
            val state = _uiState.value.imageStates[item.id]
            if (state is ImageLoadState.Success || thumbJobs.containsKey(item.id)) continue

            setThumbState(item.id, ImageLoadState.Loading)
            val job = viewModelScope.launch {
                try {
                    val stateAfterLoad = repository.loadImage(item, ImageSize.Thumb)
                        .toStateWithNotice("图片加载失败，请检查网络后重试")
                    if (isStillCurrent(item.id, requestGeneration)) {
                        setThumbState(item.id, stateAfterLoad)
                    }
                } finally {
                    thumbJobs.remove(item.id)
                    startNextThumbJobs(requestGeneration)
                }
            }
            thumbJobs[item.id] = ThumbJob(
                image = item,
                job = job,
                priority = request.priority,
                distanceToViewport = request.distanceToViewport,
                generation = requestGeneration
            )
        }
    }

    fun loadFull(item: GalleryImage) {
        val requestGeneration = generation
        val state = _uiState.value.fullImageStates[item.id]
        if (state is ImageLoadState.Success || state is ImageLoadState.Loading) return

        setFullState(item.id, ImageLoadState.Loading)
        viewModelScope.launch {
            val stateAfterLoad = repository.loadImage(item, ImageSize.Full)
                .toStateWithNotice("高清图加载失败，请稍后重试")
            if (isStillCurrent(item.id, requestGeneration)) {
                setFullState(item.id, stateAfterLoad)
            }
        }
    }

    fun selectImage(item: GalleryImage?) {
        _uiState.update { it.copy(selectedImage = item, albumMessage = "") }
        if (item != null) {
            recordRecent(item)
            loadFull(item)
        }
    }

    fun setFilter(filter: GalleryFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleFavorite(item: GalleryImage) {
        val current = _uiState.value.favoriteItems
        val next = if (current.any { it.id == item.id }) {
            current.filterNot { it.id == item.id }
        } else {
            listOf(item) + current
        }
        repository.saveFavoriteItems(next)
        _uiState.update {
            it.copy(
                favoriteItems = next,
                activeItems = mergeImages(loadedCatalogImages(), next, it.recentItems),
                discoverItems = loadedCatalogImages()
            )
        }
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (isPageLoading || current.isLoadingMore || !current.canLoadMore) return

        val requestGeneration = generation
        isPageLoading = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            delay(120)
            if (requestGeneration != generation) {
                isPageLoading = false
                _uiState.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            loadedCatalogCount = (loadedCatalogCount + PAGE_SIZE).coerceAtMost(catalogImages.size)
            _uiState.update {
                it.copy(
                    activeItems = mergeImages(loadedCatalogImages(), it.favoriteItems, it.recentItems),
                    discoverItems = loadedCatalogImages(),
                    loadedCatalogCount = loadedCatalogCount,
                    canLoadMore = canLoadMoreCatalog(),
                    isLoadingMore = false
                )
            }
            isPageLoading = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            generation += 1
            isPageLoading = false
            val previous = _uiState.value
            val keepBeforeRefreshIds = (previous.favoriteItems + previous.recentItems).map { it.id }.toSet()
            thumbJobs.keys
                .filter { it !in keepBeforeRefreshIds }
                .forEach { id -> thumbJobs.remove(id)?.job?.cancel() }
            pendingThumbRequests.keys
                .filter { it !in keepBeforeRefreshIds }
                .forEach { pendingThumbRequests.remove(it) }
            catalogSeed = repository.createNewCatalogSeed()
            catalogImages = GalleryFactory.create(catalogSeed)
            loadedCatalogCount = PAGE_SIZE.coerceAtMost(catalogImages.size)
            val favorites = repository.loadFavoriteItems()
            val recent = repository.loadRecentItems()
            val keepIds = (favorites + recent).map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    activeItems = mergeImages(loadedCatalogImages(), favorites, recent),
                    discoverItems = loadedCatalogImages(),
                    imageStates = it.imageStates.filterKeys { id -> id in keepIds },
                    fullImageStates = it.fullImageStates.filterKeys { id -> id in keepIds },
                    favoriteItems = favorites,
                    recentItems = recent,
                    selectedFilter = GalleryFilter.All,
                    loadedCatalogCount = loadedCatalogCount,
                    canLoadMore = canLoadMoreCatalog(),
                    isLoadingMore = false,
                    isRefreshing = true
                )
            }
            delay(350)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun reload(item: GalleryImage) {
        val requestGeneration = generation
        viewModelScope.launch {
            repository.clearImage(item)
            setThumbState(item.id, ImageLoadState.Loading)
            setFullState(item.id, ImageLoadState.Loading)
            val thumb = repository.loadImage(item, ImageSize.Thumb)
                .toStateWithNotice("图片加载失败，请检查网络后重试")
            if (!isStillCurrent(item.id, requestGeneration)) return@launch
            setThumbState(item.id, thumb)
            val full = repository.loadImage(item, ImageSize.Full)
                .toStateWithNotice("高清图加载失败，请稍后重试")
            if (isStillCurrent(item.id, requestGeneration)) {
                setFullState(item.id, full)
            }
        }
    }

    fun saveToAlbum(item: GalleryImage) {
        val requestGeneration = generation
        viewModelScope.launch {
            _uiState.update { it.copy(albumMessage = "正在保存到系统相册...") }
            setFullState(item.id, ImageLoadState.Loading)
            val loaded = repository.loadImage(item, ImageSize.Full)
            val stateAfterLoad = loaded.toStateWithNotice("高清图加载失败，请稍后重试")
            if (!isStillCurrent(item.id, requestGeneration)) return@launch
            setFullState(item.id, stateAfterLoad)
            loaded.onSuccess { cached ->
                val saved = repository.saveToAlbum(item, cached.file)
                _uiState.update {
                    it.copy(albumMessage = if (saved.isSuccess) "已保存到系统相册" else "保存失败，请稍后重试")
                }
            }.onFailure {
                _uiState.update { state -> state.copy(albumMessage = "高清图加载失败，暂时不能保存") }
            }
        }
    }

    private fun setThumbState(id: String, state: ImageLoadState) {
        _uiState.update { current ->
            current.copy(imageStates = current.imageStates + (id to state))
        }
    }

    private fun setFullState(id: String, state: ImageLoadState) {
        _uiState.update { current ->
            current.copy(fullImageStates = current.fullImageStates + (id to state))
        }
    }

    private fun trimThumbJobs(requests: List<ImageLoadRequest>, requestGeneration: Int) {
        val requestById = requests.associateBy { it.image.id }
        val requestedIds = requestById.keys
        pendingThumbRequests.keys
            .filter { it !in requestedIds }
            .forEach { pendingThumbRequests.remove(it) }
        thumbJobs.toList().forEach { (id, running) ->
            val request = requestById[id]
            if (running.generation != requestGeneration) {
                cancelThumbJob(id)
                return@forEach
            }
            if (request != null) {
                running.priority = minPriority(running.priority, request.priority)
                running.distanceToViewport = minOf(running.distanceToViewport, request.distanceToViewport)
                return@forEach
            }
        }
    }

    private fun freeSlotForVisibleRequests(
        requests: List<ImageLoadRequest>,
        requestGeneration: Int
    ) {
        val hasPendingVisible = requests.any { request ->
            request.priority == ImageLoadPriority.Visible &&
                pendingThumbRequests.containsKey(request.image.id)
        }
        if (!hasPendingVisible || thumbJobs.size < MAX_ACTIVE_THUMB_JOBS) return

        val requestedIds = requests.map { it.image.id }.toSet()
        val candidate = thumbJobs.values
            .filter { job ->
                job.generation == requestGeneration &&
                    job.image.id !in requestedIds &&
                    job.priority != ImageLoadPriority.Visible
            }
            .maxWithOrNull(
                compareBy<ThumbJob> { it.priority.weight }
                    .thenBy { it.distanceToViewport }
            )
        if (candidate != null) {
            cancelThumbJob(candidate.image.id)
        }
    }

    private fun cancelThumbJob(id: String) {
        thumbJobs.remove(id)?.job?.cancel()
        pendingThumbRequests.remove(id)
        if (_uiState.value.imageStates[id] is ImageLoadState.Loading) {
            setThumbState(id, ImageLoadState.Idle)
        }
    }

    private fun minPriority(
        current: ImageLoadPriority,
        incoming: ImageLoadPriority
    ): ImageLoadPriority {
        return if (incoming.weight < current.weight) incoming else current
    }

    private fun mergeRequest(
        current: ImageLoadRequest,
        incoming: ImageLoadRequest
    ): ImageLoadRequest {
        return current.copy(
            priority = minPriority(current.priority, incoming.priority),
            distanceToViewport = minOf(current.distanceToViewport, incoming.distanceToViewport),
            canCancel = current.canCancel && incoming.canCancel
        )
    }

    private fun isStillCurrent(id: String, requestGeneration: Int): Boolean {
        val current = _uiState.value
        if (requestGeneration != generation) {
            return current.favoriteIds.contains(id) ||
                current.recentIds.contains(id) ||
                current.selectedImage?.id == id
        }
        return current.activeItems.any { it.id == id } || current.selectedImage?.id == id
    }

    private fun CachedImage.toSuccessState(): ImageLoadState.Success {
        return ImageLoadState.Success(
            bitmap = bitmap,
            file = file,
            analysis = analysis
        )
    }

    private fun Result<CachedImage>.toStateWithNotice(fallbackMessage: String): ImageLoadState {
        return fold(
            onSuccess = { cached ->
                cached.toSuccessState()
            },
            onFailure = { error ->
                ImageLoadState.Error(error.message ?: fallbackMessage)
            }
        )
    }

    private fun recordRecent(item: GalleryImage) {
        val next = (listOf(item) + _uiState.value.recentItems)
            .distinctBy { it.id }
            .take(MAX_RECENT_COUNT)
        repository.saveRecentItems(next)
        _uiState.update {
            it.copy(
                recentItems = next,
                activeItems = mergeImages(loadedCatalogImages(), it.favoriteItems, next),
                discoverItems = loadedCatalogImages()
            )
        }
    }

    private fun loadedCatalogImages(): List<GalleryImage> {
        return catalogImages.take(loadedCatalogCount)
    }

    private fun canLoadMoreCatalog(): Boolean {
        return loadedCatalogCount < catalogImages.size
    }

    private fun mergeImages(
        catalog: List<GalleryImage>,
        favorites: List<GalleryImage>,
        recent: List<GalleryImage>
    ): List<GalleryImage> {
        return (catalog + favorites + recent).distinctBy { it.id }
    }

    class Factory(
        private val repository: ImageRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GalleryViewModel(repository) as T
        }
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val MAX_RECENT_COUNT = 50
        const val MAX_ACTIVE_THUMB_JOBS = 4
    }

    private data class ThumbJob(
        val image: GalleryImage,
        val job: Job,
        var priority: ImageLoadPriority,
        var distanceToViewport: Int,
        val generation: Int
    )
}
