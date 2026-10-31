package com.gallery.android.ui.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.gallery.android.domain.model.TimelineAnchor
import com.gallery.android.ui.components.GalleryEmptyState
import com.gallery.android.ui.components.GalleryMotion
import com.gallery.android.ui.components.MediaThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    bucketId: Long? = null,
    customAlbumId: Long? = null,
    onMediaClick: (Long) -> Unit,
    onFavoritesClick: () -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val lazyItems = viewModel.gridItems.collectAsLazyPagingItems()
    val timelineAnchors by viewModel.timelineAnchors.collectAsStateWithLifecycle()

    LaunchedEffect(bucketId, customAlbumId) {
        viewModel.setSource(bucketId, customAlbumId)
    }

    val isSelecting = uiState.selectedIds.isNotEmpty()
    val activeTimelineIndex by remember {
        derivedStateOf {
            timelineAnchors.indexOfLast { it.itemIndex <= gridState.firstVisibleItemIndex }
                .coerceAtLeast(0)
        }
    }
    val isEmpty = lazyItems.itemCount == 0 &&
        lazyItems.loadState.refresh is LoadState.NotLoading

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSelecting,
                transitionSpec = {
                    (fadeIn(GalleryMotion.Fast) + slideInVertically { -it / 3 }) togetherWith
                        (fadeOut(GalleryMotion.Fast) + slideOutVertically { -it / 3 })
                },
                label = "topBar",
            ) { selecting ->
                if (selecting) {
                    SelectionTopBar(
                        count = uiState.selectedIds.size,
                        onClose = viewModel::clearSelection,
                        onDelete = viewModel::moveSelectedToTrash,
                    )
                } else {
                    Box {
                        com.gallery.android.ui.components.ApertureHeader(
                            title = if (bucketId != null || customAlbumId != null) "Album" else "Photos",
                            subtitle = "${String.format(Locale.getDefault(), "%,d", uiState.mediaCount)} items",
                            actions = {
                                com.gallery.android.ui.components.HairlineChip(
                                    onClick = {
                                        val nextCols = when (uiState.gridColumns) {
                                            2 -> 3
                                            3 -> 4
                                            else -> 2
                                        }
                                        viewModel.setGridColumns(nextCols)
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.GridView,
                                        contentDescription = "Change grid size",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${uiState.gridColumns}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                com.gallery.android.ui.components.HairlineIconButton(
                                    onClick = { showOverflowMenu = true },
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                )
                            },
                        )
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort and filter") },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showFilterDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Favourites") },
                                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onFavoritesClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Recently deleted") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onTrashClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onSettingsClick()
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (isEmpty) {
            GalleryEmptyState(
                icon = Icons.Default.Photo,
                title = if (uiState.activeFilter == GalleryFilter.ALL) {
                    "No photos or videos"
                } else {
                    "No matching items"
                },
                subtitle = if (uiState.activeFilter == GalleryFilter.ALL) {
                    "Photos you take will show up here"
                } else {
                    uiState.activeFilter.label
                },
                modifier = Modifier.padding(padding),
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
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        end = 18.dp,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(
                        count = lazyItems.itemCount,
                        key = { index ->
                            when (val item = lazyItems.peek(index)) {
                                is GalleryGridItem.Header -> "header-${item.date}-$index"
                                is GalleryGridItem.Media -> item.item.id
                                null -> "placeholder-$index"
                            }
                        },
                        span = { index ->
                            if (lazyItems.peek(index) is GalleryGridItem.Header) {
                                GridItemSpan(maxLineSpan)
                            } else {
                                GridItemSpan(1)
                            }
                        },
                        contentType = { index ->
                            when (lazyItems.peek(index)) {
                                is GalleryGridItem.Header -> "header"
                                is GalleryGridItem.Media -> "media"
                                else -> "placeholder"
                            }
                        },
                    ) { index ->
                        when (val item = lazyItems[index]) {
                            is GalleryGridItem.Header -> DateHeader(
                                date = item.date,
                                location = item.location,
                                count = 0,
                            )
                            is GalleryGridItem.Media -> {
                                val media = item.item
                                MediaThumbnail(
                                    media = media,
                                    isSelected = uiState.selectedIds.contains(media.id),
                                    onClick = {
                                        if (isSelecting) viewModel.toggleSelection(media.id)
                                        else onMediaClick(media.id)
                                    },
                                    onLongClick = { viewModel.toggleSelection(media.id) },
                                    cornerRadius = 8,
                                )
                            }
                            null -> Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                }

                if (timelineAnchors.size > 1) {
                    DateTimelineScrubber(
                        sections = timelineAnchors,
                        activeIndex = activeTimelineIndex,
                        isGridScrolling = gridState.isScrollInProgress,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onScrubTo = { sectionIndex, animated ->
                            val target = timelineAnchors.getOrNull(sectionIndex)?.itemIndex ?: return@DateTimelineScrubber
                            coroutineScope.launch {
                                if (animated) {
                                    gridState.animateScrollToItem(target)
                                } else {
                                    gridState.scrollToItem(target)
                                }
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


@Composable
private fun DateHeader(date: String, location: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = date.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.03.em,
            )
            if (location.isNotBlank()) {
                Text(
                    text = location.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.12.em,
                    maxLines = 1,
                )
            }
        }
        if (count > 0) {
            Text(
                text = "$count ITEMS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.12.em,
            )
        }
    }
}

@Composable
private fun DateTimelineScrubber(
    sections: List<TimelineAnchor>,
    activeIndex: Int,
    isGridScrolling: Boolean,
    modifier: Modifier = Modifier,
    onScrubTo: (sectionIndex: Int, animated: Boolean) -> Unit,
) {
    var railHeightPx by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(activeIndex) }
    var keepVisible by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex, isDragging) {
        if (!isDragging) previewIndex = activeIndex
    }

    LaunchedEffect(isDragging, isGridScrolling) {
        if (isDragging || isGridScrolling) {
            keepVisible = true
        } else {
            delay(900)
            keepVisible = false
        }
    }

    val shownIndex = previewIndex.coerceIn(0, sections.lastIndex)
    val targetFraction = if (sections.size == 1) 0f else shownIndex.toFloat() / sections.lastIndex.toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (isDragging) tween(0) else GalleryMotion.Fast,
        label = "scrubberFraction",
    )
    val fraction = if (isDragging) targetFraction else animatedFraction
    val emphasis by animateFloatAsState(
        targetValue = if (isDragging || keepVisible) 1f else 0.38f,
        animationSpec = GalleryMotion.Medium,
        label = "scrubberEmphasis",
    )

    val density = LocalDensity.current
    val thumbHeightPx = with(density) { 22.dp.toPx() }
    val trackColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    fun indexForY(offsetY: Float): Int {
        if (railHeightPx <= 0 || sections.size <= 1) return 0
        val travel = (railHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val y = (offsetY - thumbHeightPx / 2f).coerceIn(0f, travel)
        return ((y / travel) * sections.lastIndex).roundToInt().coerceIn(0, sections.lastIndex)
    }

    fun jumpTo(offsetY: Float, animated: Boolean) {
        val index = indexForY(offsetY)
        if (previewIndex != index || animated) {
            previewIndex = index
            onScrubTo(index, animated)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .padding(vertical = 20.dp)
            .onSizeChanged { railHeightPx = it.height }
            .pointerInput(sections) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        jumpTo(it.y, animated = false)
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        jumpTo(change.position.y, animated = false)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                )
            }
            .pointerInput(sections) {
                detectTapGestures { offset ->
                    jumpTo(offset.y, animated = true)
                }
            },
    ) {
        val tickStep = when {
            sections.size <= 12 -> 1
            sections.size <= 36 -> 2
            else -> (sections.size / 18).coerceAtLeast(3)
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .width(10.dp)
                .fillMaxHeight()
                .graphicsLayer { alpha = emphasis }
                .drawBehind {
                    val trackX = size.width / 2f
                    val trackWidth = 2.dp.toPx()
                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.16f),
                        topLeft = Offset(trackX - trackWidth / 2f, 0f),
                        size = Size(trackWidth, size.height),
                        cornerRadius = CornerRadius(trackWidth, trackWidth),
                    )
                    val travel = (size.height - thumbHeightPx).coerceAtLeast(1f)
                    sections.indices.forEach { i ->
                        if (i % tickStep != 0 && i != sections.lastIndex) return@forEach
                        val y = thumbHeightPx / 2f + travel * (i.toFloat() / sections.lastIndex.coerceAtLeast(1))
                        val tickW = if (i == shownIndex) 6.dp.toPx() else 3.dp.toPx()
                        drawRoundRect(
                            color = trackColor.copy(alpha = if (i == shownIndex) 0.45f else 0.18f),
                            topLeft = Offset(trackX - tickW / 2f, y - 0.75.dp.toPx()),
                            size = Size(tickW, 1.5.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                        )
                    }
                },
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 7.dp)
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((railHeightPx - thumbHeightPx) * fraction).roundToInt(),
                    )
                }
                .graphicsLayer { alpha = 0.35f + emphasis * 0.65f }
                .size(width = 16.dp, height = 22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.92f)),
        )

        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(GalleryMotion.Fast) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(GalleryMotion.Fast) + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(end = 22.dp)
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((railHeightPx - 36.dp.roundToPx()) * fraction).roundToInt(),
                    )
                },
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = sections[shownIndex].label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    maxLines = 1,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "$count selected",
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
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
                    text = { Text("$cols columns${if (cols == currentColumns) "  ✓" else ""}") },
                    onClick = {
                        onColumnChange(cols)
                        expanded = false
                    },
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GalleryFilter.entries.forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { onSelect(filter) }, onLongClick = {})
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = filter == selectedFilter,
                            onClick = { onSelect(filter) },
                        )
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
