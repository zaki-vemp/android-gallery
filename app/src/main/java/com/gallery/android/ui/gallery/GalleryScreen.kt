package com.gallery.android.ui.gallery

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.utils.DateUtils

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    bucketId: Long? = null,
    onMediaClick: (Long) -> Unit,
    onFavoritesClick: () -> Unit,
    onTrashClick: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mediaList by if (bucketId != null) {
        viewModel.getMediaByBucket(bucketId).collectAsStateWithLifecycle(emptyList())
    } else {
        viewModel.allMedia.collectAsStateWithLifecycle()
    }

    val grouped = remember(mediaList) {
        mediaList.groupBy { DateUtils.formatGroupDate(it.dateAdded) }
    }

    val isSelecting = uiState.selectedIds.isNotEmpty()

    Scaffold(
        topBar = {
            if (isSelecting) {
                SelectionTopBar(
                    count = uiState.selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onDelete = viewModel::moveSelectedToTrash,
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(if (bucketId != null) "Album" else "Photos", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (bucketId == null) {
                            IconButton(onClick = onFavoritesClick) {
                                Icon(Icons.Default.Favorite, contentDescription = "Favorites")
                            }
                            IconButton(onClick = onTrashClick) {
                                Icon(Icons.Default.Delete, contentDescription = "Trash")
                            }
                            GridSizeMenu(
                                currentColumns = uiState.gridColumns,
                                onColumnChange = viewModel::setGridColumns,
                            )
                        }
                    },
                )
            }
        }
    ) { padding ->
        if (mediaList.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(uiState.gridColumns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                grouped.forEach { (dateLabel, items) ->
                    stickyHeader(key = dateLabel) {
                        DateHeader(dateLabel)
                    }
                    items(items = items, key = { it.id }) { media ->
                        MediaThumbnail(
                            media = media,
                            isSelected = uiState.selectedIds.contains(media.id),
                            onClick = {
                                if (isSelecting) viewModel.toggleSelection(media.id)
                                else onMediaClick(media.id)
                            },
                            onLongClick = { viewModel.toggleSelection(media.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumbnail(
    media: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(media.uri)
                .crossfade(true)
                .build(),
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Video badge
        if (media.mediaType == MediaType.VIDEO) {
            VideoBadge(
                duration = media.duration,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        // Selection overlay
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp),
                )
            }
        }
        // Favorite star
        if (media.isFavorite) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun VideoBadge(duration: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
            Text(
                text = DateUtils.formatDuration(duration),
                color = Color.White,
                fontSize = 10.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        },
        actions = {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        },
    )
}

@Composable
private fun GridSizeMenu(currentColumns: Int, onColumnChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.GridView, contentDescription = "Grid size")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(2, 3, 4).forEach { cols ->
                DropdownMenuItem(
                    text = { Text("$cols columns${if (cols == currentColumns) " ✓" else ""}") },
                    onClick = { onColumnChange(cols); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text("No photos or videos", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
    }
}
