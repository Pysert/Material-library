package com.example.image.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageLoadState
import com.example.image.util.dateText
import com.example.image.util.dimensionText
import com.example.image.util.downloadTimeText
import com.example.image.util.fileSizeText
import com.example.image.util.fullLocation
import com.example.image.util.summaryText
import com.example.image.util.titleText
import com.example.image.util.useCaseText

@Composable
fun DetailScreen(
    item: GalleryImage,
    thumbState: ImageLoadState?,
    fullState: ImageLoadState?,
    isFavorite: Boolean,
    albumMessage: String,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReload: () -> Unit,
    onSaveToAlbum: () -> Unit
) {
    val displayState = if (fullState is ImageLoadState.Success || fullState is ImageLoadState.Error) {
        fullState
    } else {
        thumbState ?: ImageLoadState.Loading
    }
    val isFullLoading = fullState is ImageLoadState.Loading
    val isShowingPreview = isFullLoading && thumbState is ImageLoadState.Success
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var isFullPreviewOpen by remember(item.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(text = "返回")
            }
            Text(
                text = displayState.titleText(item),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onToggleFavorite) {
                Text(text = if (isFavorite) "已收藏" else "收藏")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE6E3DC)),
            contentAlignment = Alignment.Center
        ) {
            when (displayState) {
                ImageLoadState.Idle, ImageLoadState.Loading -> DetailSkeleton()
                is ImageLoadState.Error -> Text(
                    text = displayState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                is ImageLoadState.Success -> {
                    Image(
                        bitmap = displayState.bitmap.asImageBitmap(),
                        contentDescription = displayState.titleText(item),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { isFullPreviewOpen = true }
                            .pointerInput(item.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = nextScale
                                    offset = if (nextScale == 1f) Offset.Zero else offset + pan
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit
                    )
                    if (isFullLoading) {
                        Text(
                            text = if (isShowingPreview) "正在加载高清图，当前为预览图" else "正在加载高清图...",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "点击图片查看大图",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp),
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReload,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                SingleLineButtonText(text = "重下")
            }
            OutlinedButton(
                onClick = { isFullPreviewOpen = true },
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                SingleLineButtonText(text = "大图")
            }
            Button(
                onClick = onSaveToAlbum,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                SingleLineButtonText(text = "保存")
            }
        }

        if (albumMessage.isNotBlank()) {
            Text(
                text = albumMessage,
                modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = displayState.summaryText(),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (displayState is ImageLoadState.Success) {
                DetailLine(label = "素材用途", value = item.useCaseText())
                DetailLine(label = "搜索关键词", value = item.keywords.joinToString(" / "))
                DetailLine(label = "自动描述", value = displayState.analysis.description)
                DetailLine(label = "色彩标签", value = displayState.analysis.tags.joinToString(" / "))
                DetailLine(label = "主色 RGB", value = displayState.analysis.rgbText)
            }
            DetailLine(label = "图片日期", value = displayState.dateText(item.photoDate))
            DetailLine(label = "图片尺寸", value = displayState.dimensionText())
            DetailLine(label = "文件大小", value = displayState.fileSizeText())
            DetailLine(label = "下载时间", value = displayState.downloadTimeText())
            DetailLine(label = "存储位置", value = displayState.fullLocation())
        }
    }

    if (isFullPreviewOpen) {
        FullscreenImagePreview(
            item = item,
            state = displayState,
            isFullLoading = isFullLoading,
            isShowingPreview = isShowingPreview,
            isFavorite = isFavorite,
            onClose = { isFullPreviewOpen = false },
            onToggleFavorite = onToggleFavorite,
            onSaveToAlbum = onSaveToAlbum
        )
    }
}

@Composable
private fun SingleLineButtonText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 13.sp
    )
}

@Composable
private fun DetailSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE9E6DF))
    )
}

@Composable
private fun FullscreenImagePreview(
    item: GalleryImage,
    state: ImageLoadState,
    isFullLoading: Boolean,
    isShowingPreview: Boolean,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveToAlbum: () -> Unit
) {
    var scale by remember(item.id, "fullscreen") { mutableFloatStateOf(1f) }
    var offset by remember(item.id, "fullscreen") { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        when (state) {
            ImageLoadState.Idle, ImageLoadState.Loading -> DetailSkeleton()
            is ImageLoadState.Error -> Text(
                text = state.message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            is ImageLoadState.Success -> {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = state.titleText(item),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(item.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 6f)
                                scale = nextScale
                                offset = if (nextScale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text(text = "返回", color = Color.White)
            }
            Text(
                text = state.titleText(item),
                modifier = Modifier.weight(1f),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onToggleFavorite) {
                Text(text = if (isFavorite) "已收藏" else "收藏", color = Color.White)
            }
            TextButton(onClick = onSaveToAlbum) {
                Text(text = "保存", color = Color.White)
            }
        }

        if (isFullLoading) {
            Text(
                text = if (isShowingPreview) "高清图加载中，当前为预览图" else "高清图加载中...",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp),
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
