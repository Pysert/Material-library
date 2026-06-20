package com.example.image.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.image.data.model.GalleryFilter
import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageLoadPriority
import com.example.image.data.model.ImageLoadRequest
import com.example.image.data.model.ImageLoadState
import com.example.image.ui.viewmodel.GalleryUiState
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: GalleryUiState,
    onFilterChange: (GalleryFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onImageClick: (GalleryImage) -> Unit,
    onToggleFavorite: (GalleryImage) -> Unit,
    onNeedImages: (List<ImageLoadRequest>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyStaggeredGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
                false
            } else {
                val lastVisibleIndex = visibleItems.maxOf { it.index }
                val isNearLastItem = lastVisibleIndex >= layoutInfo.totalItemsCount - 3
                val isPhysicalEnd = !gridState.canScrollForward
                isNearLastItem || isPhysicalEnd
            }
        }
    }
    val baseImages = remember(
        uiState.discoverItems,
        uiState.favoriteItems,
        uiState.recentItems,
        uiState.selectedFilter
    ) {
        when (uiState.selectedFilter) {
            GalleryFilter.All -> uiState.discoverItems
            GalleryFilter.Favorites -> uiState.favoriteItems
            GalleryFilter.Recent -> uiState.recentItems
        }
    }
    val visibleImages = remember(
        baseImages,
        uiState.imageStates,
        uiState.favoriteIds,
        uiState.recentIds,
        uiState.selectedFilter,
        uiState.searchQuery
    ) {
        val query = uiState.searchQuery.trim()
        baseImages.filter { item ->
            item.matchesSearch(query, uiState.imageStates[item.id])
        }
    }
    val totalForFilter = baseImages.size
    val filterHint = when (uiState.selectedFilter) {
        GalleryFilter.All -> "下拉刷新换一批"
        GalleryFilter.Favorites -> "收藏"
        GalleryFilter.Recent -> "最近看过"
    }

    LaunchedEffect(visibleImages, uiState.selectedFilter) {
        onNeedImages(visibleImages.initialLoadRequests())
    }

    LaunchedEffect(gridState, visibleImages) {
        snapshotFlow {
            val visibleIndexes = gridState.layoutInfo.visibleItemsInfo.map { it.index }
            val first = visibleIndexes.minOrNull() ?: -1
            val last = visibleIndexes.maxOrNull() ?: -1
            ScrollWindow(
                first = first,
                last = last,
                isScrolling = gridState.isScrollInProgress
            )
        }.distinctUntilChanged().collect { window ->
            val first = window.first
            val last = window.last
            val isScrolling = window.isScrolling
            if (first < 0 || last < 0) return@collect
            onNeedImages(visibleImages.loadRequestsFor(window))
        }
    }

    LaunchedEffect(
        shouldLoadMore,
        visibleImages.size,
        uiState.selectedFilter,
        uiState.canLoadMore,
        uiState.isLoadingMore
    ) {
        if (
            shouldLoadMore &&
            uiState.selectedFilter == GalleryFilter.All &&
            uiState.canLoadMore &&
            !uiState.isLoadingMore
        ) {
            onLoadMore()
        }
    }

    LaunchedEffect(
        visibleImages.size,
        uiState.searchQuery,
        uiState.selectedFilter,
        uiState.canLoadMore,
        uiState.isLoadingMore
    ) {
        if (
            visibleImages.isEmpty() &&
            uiState.searchQuery.isNotBlank() &&
            uiState.selectedFilter == GalleryFilter.All &&
            uiState.canLoadMore &&
            !uiState.isLoadingMore
        ) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { if (uiState.selectedFilter == GalleryFilter.All) onRefresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "素材库",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = headerStatusText(
                            visibleCount = visibleImages.size,
                            totalForFilter = totalForFilter,
                            uiState = uiState,
                            filterHint = filterHint
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            FilterBar(
                selectedFilter = uiState.selectedFilter,
                onFilterChange = onFilterChange
            )

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                singleLine = true,
                placeholder = { Text(text = "搜索壁纸、PPT、封面、暖色、横图") }
            )

            if (visibleImages.isEmpty()) {
                EmptyState(
                    text = emptyText(uiState)
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        items(items = visibleImages, key = { it.id }) { item ->
                            ImageCard(
                                item = item,
                                state = uiState.imageStates[item.id] ?: ImageLoadState.Idle,
                                isFavorite = uiState.favoriteIds.contains(item.id),
                                onClick = { onImageClick(item) },
                                onToggleFavorite = { onToggleFavorite(item) },
                                onRetry = {
                                    onNeedImages(
                                        listOf(
                                            ImageLoadRequest(
                                                image = item,
                                                priority = ImageLoadPriority.Visible,
                                                distanceToViewport = 0,
                                                canCancel = false
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    }

                    if (uiState.isLoadingMore) {
                        Text(
                            text = "正在加载更多素材...",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun emptyText(uiState: GalleryUiState): String {
    if (uiState.searchQuery.isNotBlank()) {
        return "没有找到和“${uiState.searchQuery}”相关的素材"
    }
    return when (uiState.selectedFilter) {
        GalleryFilter.All -> "当前没有发现素材"
        GalleryFilter.Favorites -> "还没有收藏素材"
        GalleryFilter.Recent -> "还没有最近看过的素材"
    }
}

private fun headerStatusText(
    visibleCount: Int,
    totalForFilter: Int,
    uiState: GalleryUiState,
    filterHint: String
): String {
    val pageText = if (uiState.selectedFilter == GalleryFilter.Favorites) {
        "$visibleCount/$totalForFilter 张"
    } else if (uiState.selectedFilter == GalleryFilter.Recent) {
        "$visibleCount/$totalForFilter 张"
    } else {
        "已展示 ${uiState.loadedCatalogCount} 张"
    }
    val loadHint = when (uiState.selectedFilter) {
        GalleryFilter.All -> "$filterHint · 触底加载更多"
        GalleryFilter.Favorites -> filterHint
        GalleryFilter.Recent -> filterHint
    }
    return "$pageText · $loadHint"
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun GalleryImage.matchesSearch(query: String, state: ImageLoadState?): Boolean {
    if (query.isBlank()) return true
    val success = state as? ImageLoadState.Success
    val text = buildString {
        append(searchableText()).append(' ')
        if (success != null) {
            append(success.analysis.title).append(' ')
            append(success.analysis.description).append(' ')
            append(success.analysis.tags.joinToString(" "))
        }
    }
    return query
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .all { keyword -> text.contains(keyword, ignoreCase = true) }
}

private data class ScrollWindow(
    val first: Int,
    val last: Int,
    val isScrolling: Boolean
)

private fun List<GalleryImage>.initialLoadRequests(): List<ImageLoadRequest> {
    return take(12).mapIndexed { index, item ->
        ImageLoadRequest(
            image = item,
            priority = if (index < 6) ImageLoadPriority.Visible else ImageLoadPriority.Nearby,
            distanceToViewport = index,
            canCancel = index >= 6
        )
    }
}

private fun List<GalleryImage>.loadRequestsFor(window: ScrollWindow): List<ImageLoadRequest> {
    if (isEmpty() || window.first < 0 || window.last < 0) return emptyList()

    val visibleStart = window.first.coerceAtLeast(0)
    val visibleEnd = window.last.coerceAtMost(lastIndex)
    val nearbyBefore = if (window.isScrolling) 0 else 6
    val nearbyAfter = if (window.isScrolling) 6 else 12
    val prefetchAfter = if (window.isScrolling) 0 else 18
    val requestStart = (visibleStart - nearbyBefore).coerceAtLeast(0)
    val requestEnd = (visibleEnd + nearbyAfter + prefetchAfter).coerceAtMost(lastIndex)

    return (requestStart..requestEnd).map { index ->
        val priority = when {
            index in visibleStart..visibleEnd -> ImageLoadPriority.Visible
            index <= visibleEnd + nearbyAfter -> ImageLoadPriority.Nearby
            else -> ImageLoadPriority.Prefetch
        }
        ImageLoadRequest(
            image = this[index],
            priority = priority,
            distanceToViewport = distanceToViewport(index, visibleStart, visibleEnd),
            canCancel = priority != ImageLoadPriority.Visible
        )
    }
}

private fun distanceToViewport(index: Int, first: Int, last: Int): Int {
    return when {
        index < first -> first - index
        index > last -> index - last
        else -> 0
    }
}

@Composable
private fun FilterBar(
    selectedFilter: GalleryFilter,
    onFilterChange: (GalleryFilter) -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        GalleryFilter.entries.forEach { filter ->
            val selected = selectedFilter == filter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onFilterChange(filter) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
