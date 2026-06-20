package com.example.image.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.image.data.model.GalleryImage
import com.example.image.data.model.ImageLoadState
import com.example.image.util.dateText
import com.example.image.util.dimensionText
import com.example.image.util.fileSizeText
import com.example.image.util.titleText
import com.example.image.util.useCaseText

@Composable
fun ImageCard(
    item: GalleryImage,
    state: ImageLoadState,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(item.aspectRatio)
                .background(Color(0xFFE6E3DC)),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                ImageLoadState.Idle, ImageLoadState.Loading -> ImageSkeleton()
                is ImageLoadState.Error -> TextButton(onClick = onRetry) {
                    Text(text = "重试")
                }
                is ImageLoadState.Success -> {
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = state.titleText(item),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            TextButton(
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(text = if (isFavorite) "已收藏" else "收藏")
            }
        }

        Column(modifier = Modifier.padding(9.dp)) {
            if (state is ImageLoadState.Idle || state is ImageLoadState.Loading) {
                TextSkeleton(widthFraction = 0.78f)
                Spacer(modifier = Modifier.height(6.dp))
                TextSkeleton(widthFraction = 0.48f)
                Spacer(modifier = Modifier.height(6.dp))
                TextSkeleton(widthFraction = 0.62f)
            } else {
                Text(
                    text = state.titleText(item),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                AnalysisTags(state = state)
                Spacer(modifier = Modifier.height(3.dp))
                DescriptionLine(text = "日期: ${state.dateText(item.photoDate)}")
                DescriptionLine(text = item.useCaseText())
                DescriptionLine(text = "尺寸: ${state.dimensionText()}")
                DescriptionLine(text = "大小: ${state.fileSizeText()}")
            }
        }
    }
}

@Composable
private fun ImageSkeleton() {
    val brush = rememberShimmerBrush()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush)
    )
}

@Composable
private fun TextSkeleton(widthFraction: Float) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(brush)
    )
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate = transition.animateFloat(
        initialValue = -350f,
        targetValue = 350f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFE4E1DA),
            Color(0xFFF8F4EC),
            Color(0xFFE4E1DA)
        ),
        start = androidx.compose.ui.geometry.Offset(translate.value, translate.value),
        end = androidx.compose.ui.geometry.Offset(translate.value + 280f, translate.value + 280f)
    )
}

@Composable
private fun AnalysisTags(state: ImageLoadState) {
    if (state !is ImageLoadState.Success) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(state.analysis.mainColor)
        )
        state.analysis.tags.take(2).forEach { tag ->
            Text(
                text = tag,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DescriptionLine(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
