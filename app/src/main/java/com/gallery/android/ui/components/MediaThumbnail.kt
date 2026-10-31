package com.gallery.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Size
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.utils.DateUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    media: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    cornerRadius: Int = 8,
    modifier: Modifier = Modifier,
) {
    val selectionScale = animateSelectionScale(isSelected)
    val context = LocalContext.current
    val thumbnailRequest = remember(media.uri, media.mediaType) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .apply {
                if (media.mediaType == MediaType.VIDEO) {
                    // Force Coil's video decoder to return a real poster frame. Without this,
                    // some MediaStore providers only expose the container and render blank.
                    videoFrameMillis(1_000)
                }
            }
            .crossfade(180)
            .size(Size(480, 480))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scaleOnPress(0.95f)
            .graphicsLayer {
                scaleX *= selectionScale
                scaleY *= selectionScale
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = thumbnailRequest,
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Subtle bottom scrim for video badge readability
        if (media.mediaType == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            startY = 120f,
                        ),
                    ),
            )
            VideoDurationBadge(
                duration = media.duration,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }

        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(GalleryMotion.Fast) + scaleIn(initialScale = 0.8f, animationSpec = GalleryMotion.SoftBounce),
            exit = fadeOut(GalleryMotion.Fast) + scaleOut(targetScale = 0.8f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp),
                )
            }
        }

        if (media.isFavorite && !isSelected) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(14.dp),
            )
        }
    }
}

@Composable
fun VideoDurationBadge(
    duration: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = DateUtils.formatDuration(duration),
            color = Color.White,
            fontSize = 10.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
