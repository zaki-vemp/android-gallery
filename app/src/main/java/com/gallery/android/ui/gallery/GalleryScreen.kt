package com.gallery.android.ui.gallery

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.utils.DateUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    bucketId: Long? = null,
    customAlbumId: Long? = null,
    onMediaClick: (Long) -> Unit,
    onFavoritesClick: () -> Unit,
    onTrashClick: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterDialog by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val mediaList by if (customAlbumId != null) {
        viewModel.getMediaByCustomAlbum(customAlbumId).collectAsStateWithLifecycle(emptyList())
    } else if (bucketId != null) {
        viewModel.getMediaByBucket(bucketId).collectAsStateWithLifecycle(emptyList())
    } else {
        viewModel.allMedia.collectAsStateWithLifecycle()
    }

    val filteredMedia = remember(mediaList, uiState.activeFilter) {
        mediaList.filter { uiState.activeFilter.matches(it) }
    }

    val groupedEntries = remember(filteredMedia) {
        filteredMedia.groupBy { DateUtils.formatGroupDate(it.dateAdded) }.entries.toList()
    }

    val timelineSections = remember(groupedEntries) {
        var runningIndex = 0
        groupedEntries.map { (dateLabel, items) ->
            TimelineSection(label = dateLabel, itemIndex = runningIndex).also {
                runningIndex += 1 + items.size
            }
        }
    }

    val isSelecting = uiState.selectedIds.isNotEmpty()
    val activeTimelineIndex by remember(timelineSections, gridState) {
        derivedStateOf {
            timelineSections.indexOfLast { it.itemIndex <= gridState.firstVisibleItemIndex }
                .coerceAtLeast(0)
        }
    }

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
                    title = {
                        Text(
                            if (bucketId != null || customAlbumId != null) "Album" else "Photos",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Sort and filter")
                        }
                        GridSizeMenu(
                            currentColumns = uiState.gridColumns,
                            onColumnChange = viewModel::setGridColumns,
                        )
                        if (bucketId == null) {
                            IconButton(onClick = onFavoritesClick) {
                                Icon(Icons.Default.Favorite, contentDescription = "Favorites")
                            }
                            IconButton(onClick = onTrashClick) {
                                Icon(Icons.Default.Delete, contentDescription = "Trash")
                            }
                        }
                    },
                )
            }
        }
    ) { padding ->
        if (filteredMedia.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                title = if (uiState.activeFilter == GalleryFilter.ALL) "No photos or videos" else "No matching items",
                subtitle = if (uiState.activeFilter == GalleryFilter.ALL) null else uiState.activeFilter.label,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(uiState.gridColumns),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uiState.gridColumns, isSelecting) {
                            if (!isSelecting) {
                                detectGridPinch(
                                    currentColumns = uiState.gridColumns,
                                    onColumnChange = viewModel::setGridColumns,
                                )
                            }
                        },
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groupedEntries.forEach { (dateLabel, items) ->
                        item(key = dateLabel, span = { GridItemSpan(maxLineSpan) }) {
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

                if (timelineSections.size > 1) {
                    DateTimelineScrubber(
                        sections = timelineSections,
                        activeIndex = activeTimelineIndex,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onScrubTo = { sectionIndex ->
                            coroutineScope.launch {
                                gridState.scrollToItem(timelineSections[sectionIndex].itemIndex)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showFilterDialog) {
        GalleryFilterDialog(
            selectedFilter = uiState.activeFilter,
            onSelect = {
                viewModel.setFilter(it)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false },
        )
    }
}

private data class TimelineSection(
    val label: String,
    val itemIndex: Int,
)

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

@Composable
private fun DateTimelineScrubber(
    sections: List<TimelineSection>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    onScrubTo: (Int) -> Unit,
) {
    var railHeightPx by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(activeIndex) }
    val shownIndex = if (isDragging) previewIndex else activeIndex
    val fraction = if (sections.size == 1) 0f else shownIndex.toFloat() / (sections.lastIndex).toFloat()

    fun updateSection(offsetY: Float) {
        if (railHeightPx <= 0) return
        val index = ((offsetY / railHeightPx) * sections.lastIndex)
            .roundToInt()
            .coerceIn(0, sections.lastIndex)
        if (previewIndex != index) {
            previewIndex = index
            onScrubTo(index)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(76.dp)
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .onSizeChanged { railHeightPx = it.height }
            .pointerInput(sections) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        updateSection(it.y)
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        updateSection(change.position.y)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                )
            }
            .pointerInput(sections) {
                detectTapGestures { offset ->
                    isDragging = true
                    updateSection(offset.y)
                    isDragging = false
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(4.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((railHeightPx - 24.dp.roundToPx()) * fraction).roundToInt(),
                    )
                }
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )

        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((railHeightPx - 48.dp.roundToPx()) * fraction).roundToInt(),
                    )
                },
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = sections[shownIndex].label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private suspend fun PointerInputScope.detectGridPinch(
    currentColumns: Int,
    onColumnChange: (Int) -> Unit,
    minColumns: Int = 2,
    maxColumns: Int = 6,
) {
    var changedColumns = false
    detectTransformGestures { _, _, zoom, _ ->
        if (!changedColumns) {
            when {
                zoom > 1.08f && currentColumns > minColumns -> {
                    onColumnChange(currentColumns - 1)
                    changedColumns = true
                }

                zoom < 0.92f && currentColumns < maxColumns -> {
                    onColumnChange(currentColumns + 1)
                    changedColumns = true
                }
            }
        }
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
                .size(Size(256, 256))
                .memoryCachePolicy(CachePolicy.ENABLED)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryFilterDialog(
    selectedFilter: GalleryFilter,
    onSelect: (GalleryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort and filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GalleryFilter.entries.forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { onSelect(filter) }, onLongClick = {}),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = filter == selectedFilter,
                            onClick = { onSelect(filter) },
                        )
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
