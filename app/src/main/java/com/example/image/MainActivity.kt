package com.example.image

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import com.example.image.data.repository.ImageRepository
import com.example.image.ui.detail.DetailScreen
import com.example.image.ui.home.HomeScreen
import com.example.image.ui.theme.ImageTheme
import com.example.image.ui.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: GalleryViewModel by viewModels {
        GalleryViewModel.Factory(ImageRepository(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ImageTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GalleryApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun GalleryApp(viewModel: GalleryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedImage = uiState.selectedImage
    val context = LocalContext.current

    LaunchedEffect(uiState.albumMessage) {
        if (uiState.albumMessage.isNotBlank()) {
            Toast.makeText(context, uiState.albumMessage, Toast.LENGTH_SHORT).show()
        }
    }

    if (selectedImage != null) {
        DetailScreen(
            item = selectedImage,
            thumbState = uiState.imageStates[selectedImage.id],
            fullState = uiState.fullImageStates[selectedImage.id],
            isFavorite = uiState.favoriteIds.contains(selectedImage.id),
            albumMessage = uiState.albumMessage,
            onBack = { viewModel.selectImage(null) },
            onToggleFavorite = { viewModel.toggleFavorite(selectedImage) },
            onReload = { viewModel.reload(selectedImage) },
            onSaveToAlbum = { viewModel.saveToAlbum(selectedImage) }
        )
    } else {
        HomeScreen(
            uiState = uiState,
            onFilterChange = viewModel::setFilter,
            onSearchChange = viewModel::setSearchQuery,
            onImageClick = viewModel::selectImage,
            onToggleFavorite = viewModel::toggleFavorite,
            onNeedImages = viewModel::scheduleThumbLoads,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadNextPage
        )
    }
}
