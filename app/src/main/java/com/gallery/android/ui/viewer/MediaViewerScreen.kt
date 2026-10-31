package com.gallery.android.ui.viewer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Rational
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.ui.components.CornerBrackets
import com.gallery.android.utils.DateUtils
import com.gallery.android.utils.ExifUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: Long,
    onBack: () -> Unit,
    onEditImage: ((Uri) -> Unit)? = null,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customAlbums by viewModel.customAlbums.collectAsStateWithLifecycle()
    val showOcrPanel = uiState.showOcrPanel
    var showControls by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val isDarkViewer = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val viewerBackground = if (isDarkViewer) Color(0xFF0A0B0C) else Color(0xFFFBFBFB)
    val viewerText = if (isDarkViewer) Color(0xFFF3F4F5) else Color(0xFF1D1F20)
    val viewerDim = if (isDarkViewer) Color(0xFF8D949C) else Color(0xFF6C7278)
    val viewerLine = if (isDarkViewer) Color.White.copy(alpha = 0.12f) else Color(0xFF1D1F20).copy(alpha = 0.14f)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var canDismissBySwipe by remember { mutableStateOf(true) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var albumAction by remember { mutableStateOf<AlbumMenuAction?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Single swipe-down-to-dismiss path
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val dismissThreshold = with(density) { 140.dp.toPx() }
    val dismissDistance = with(density) { configuration.screenHeightDp.dp.toPx() * 1.08f }
    var isDismissing by remember { mutableStateOf(false) }
    var isSnappingBack by remember { mutableStateOf(false) }
    var isVerticalDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isSnappingBack) {
        if (isSnappingBack) {
            Animatable(dragOffsetY).animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            ) {
                dragOffsetY = value
            }
            dragOffsetY = 0f
            isSnappingBack = false
        }
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            Animatable(dragOffsetY).animateTo(
                targetValue = dismissDistance,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            ) {
                dragOffsetY = value
            }
            onBack()
        }
    }

    LaunchedEffect(initialMediaId) {
        viewModel.open(initialMediaId)
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { uiState.mediaList.size },
    )
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
    )
    val previewListState = rememberLazyListState()
    remember(initialMediaId, uiState.mediaList.size) {
        if (uiState.mediaList.isNotEmpty()) {
            pagerState.requestScrollToPage(
                uiState.currentIndex.coerceIn(0, uiState.mediaList.lastIndex),
            )
        }
    }

    // Sync pager position back to ViewModel
    LaunchedEffect(pagerState.settledPage, uiState.mediaList) {
        if (uiState.mediaList.isNotEmpty()) {
            viewModel.setCurrentIndex(pagerState.settledPage)
            canDismissBySwipe = true
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (uiState.mediaList.isNotEmpty()) {
            previewListState.scrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val dismissProgress = (dragOffsetY / dismissThreshold).coerceIn(0f, 1f)
                    alpha = 1f - dismissProgress * 0.55f
                }
                .background(viewerBackground),
        )
        val currentMedia = viewModel.currentMedia() ?: uiState.preview

        if (uiState.mediaList.isEmpty()) {
            val preview = uiState.preview
            if (preview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val dismissProgress = (dragOffsetY / dismissThreshold).coerceIn(0f, 1f)
                            translationY = dragOffsetY
                            val dismissScale = 1f - dismissProgress * 0.08f
                            scaleX = dismissScale
                            scaleY = dismissScale
                        },
                ) {
                    MediaPage(
                        media = preview,
                        isCurrentPage = preview.mediaType != MediaType.VIDEO,
                        onTap = { showControls = !showControls },
                        onZoomStateChanged = { isZoomed -> canDismissBySwipe = !isZoomed },
                        bottomInset = 0.dp,
                        showControls = showControls,
                        onPrevious = {},
                        onNext = {},
                    )
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val dismissProgress = (dragOffsetY / dismissThreshold).coerceIn(0f, 1f)
                        translationY = dragOffsetY
                        val dismissScale = 1f - dismissProgress * 0.1f
                        scaleX = dismissScale
                        scaleY = dismissScale
                    }
                    .then(
                        if (canDismissBySwipe) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        if (!isSnappingBack && !isDismissing) {
                                            isVerticalDragging = true
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        if (!isVerticalDragging) return@detectVerticalDragGestures
                                        change.consume()
                                        val nextOffset = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                        dragOffsetY = if (nextOffset <= dismissThreshold) {
                                            nextOffset
                                        } else {
                                            dismissThreshold + (nextOffset - dismissThreshold) * 0.45f
                                        }
                                        if (dragOffsetY > 10f) {
                                            showControls = false
                                        }
                                    },
                                    onDragEnd = {
                                        isVerticalDragging = false
                                        if (dragOffsetY > dismissThreshold) {
                                            isDismissing = true
                                        } else if (dragOffsetY > 1f) {
                                            isSnappingBack = true
                                        }
                                    },
                                    onDragCancel = {
                                        isVerticalDragging = false
                                        if (dragOffsetY > 1f) {
                                            isSnappingBack = true
                                        }
                                    },
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                beyondViewportPageCount = 1,
                userScrollEnabled = !isSnappingBack && !isVerticalDragging && !isDismissing,
                flingBehavior = pagerFlingBehavior,
                key = { page -> uiState.mediaList[page].id },
            ) { page ->
                val media = uiState.mediaList[page]
                MediaPage(
                    media = media,
                    isCurrentPage = page == pagerState.settledPage,
                    onTap = { showControls = !showControls },
                    onZoomStateChanged = { isZoomed -> canDismissBySwipe = !isZoomed },
                    bottomInset = 0.dp,
                    showControls = showControls,
                    onPrevious = {
                        if (page > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page - 1,
                                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                                )
                            }
                        }
                    },
                    onNext = {
                        if (page < uiState.mediaList.lastIndex) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page + 1,
                                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                                )
                            }
                        }
                    },
                )
            }
        }

        // Top controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = viewerBackground.copy(alpha = 0.94f),
                shadowElevation = 0.dp,
            ) {
                TopAppBar(
                    windowInsets = WindowInsets.statusBars,
                    title = {
                        currentMedia?.let { media ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (media.mediaType == MediaType.VIDEO) media.name.uppercase(Locale.getDefault()) else "ALL PHOTOS",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = viewerText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (media.mediaType == MediaType.VIDEO) {
                                        buildString {
                                            if (media.width > 0 && media.height > 0) append("${media.width}×${media.height} · ")
                                            append(DateUtils.formatDuration(media.duration))
                                        }
                                    } else {
                                        "${DateUtils.formatViewerHeaderDate(media.dateAdded)} · ${DateUtils.formatViewerHeaderTime(media.dateAdded)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = viewerDim,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = viewerText)
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            val isFavorite = viewModel.currentMedia()?.isFavorite == true
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else viewerText,
                            )
                        }
                        if (viewModel.currentMedia()?.mediaType == MediaType.IMAGE) {
                            IconButton(onClick = viewModel::toggleOcrPanel) {
                                Icon(
                                    Icons.Default.TextFields,
                                    contentDescription = "Read text",
                                    tint = if (showOcrPanel) MaterialTheme.colorScheme.primary else viewerText,
                                )
                            }
                        }
                        IconButton(onClick = viewModel::toggleInfo) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = viewerText)
                        }
                        Box {
                            IconButton(onClick = { showActionsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions", tint = viewerText)
                            }
                            DropdownMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Move to album") },
                                    onClick = {
                                        showActionsMenu = false
                                        if (customAlbums.isEmpty()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Create a custom album first")
                                            }
                                        } else {
                                            albumAction = AlbumMenuAction.Move
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy to album") },
                                    onClick = {
                                        showActionsMenu = false
                                        if (customAlbums.isEmpty()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Create a custom album first")
                                            }
                                        } else {
                                            albumAction = AlbumMenuAction.Copy
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showActionsMenu = false
                                        showRenameDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Convert to PDF") },
                                    onClick = {
                                        showActionsMenu = false
                                        val media = currentMedia
                                        if (media?.mediaType != MediaType.IMAGE) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("PDF conversion is available for images only")
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                runCatching { convertImageToPdf(context, media) }
                                                    .onSuccess {
                                                        snackbarHostState.showSnackbar("Saved PDF to Documents")
                                                    }
                                                    .onFailure {
                                                        snackbarHostState.showSnackbar(it.message ?: "Unable to convert to PDF")
                                                    }
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Set as wallpaper") },
                                    onClick = {
                                        showActionsMenu = false
                                        currentMedia?.let { media ->
                                            setAsWallpaper(context, media)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Set as contact avatar") },
                                    onClick = {
                                        showActionsMenu = false
                                        val media = currentMedia
                                        if (media?.mediaType != MediaType.IMAGE) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Contact avatar is available for images only")
                                            }
                                        } else {
                                            setAsContactAvatar(context, media)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Set as private") },
                                    onClick = {
                                        showActionsMenu = false
                                        viewModel.moveToPrivateSafe(
                                            onDone = onBack,
                                            onResult = { message ->
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(message)
                                                }
                                            },
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Convert to compatible format") },
                                    onClick = {
                                        showActionsMenu = false
                                        val media = currentMedia
                                        if (media?.mediaType != MediaType.IMAGE) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Compatible conversion is available for images only")
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                runCatching { convertImageToCompatibleFormat(context, media) }
                                                    .onSuccess {
                                                        snackbarHostState.showSnackbar("Saved compatible copy to Pictures")
                                                    }
                                                    .onFailure {
                                                        snackbarHostState.showSnackbar(it.message ?: "Unable to convert format")
                                                    }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
        }

        // Bottom controls
        AnimatedVisibility(
            visible = showControls && currentMedia?.mediaType == MediaType.IMAGE,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(viewerBackground.copy(alpha = 0.97f))
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HorizontalDivider(color = viewerLine)
                if (uiState.mediaList.isNotEmpty()) {
                    val windowStart = (pagerState.settledPage - 20).coerceAtLeast(0)
                    val windowEnd = (pagerState.settledPage + 21).coerceAtMost(uiState.mediaList.size)
                    val previewWindow = uiState.mediaList.subList(windowStart, windowEnd)
                    LazyRow(
                        state = previewListState,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    ) {
                        itemsIndexed(
                            items = previewWindow,
                            key = { _, media -> media.id },
                        ) { windowIndex, media ->
                            val index = windowStart + windowIndex
                            val isSelected = index == pagerState.settledPage
                            val thumbnailWidth by animateDpAsState(
                                targetValue = if (isSelected) 52.dp else 38.dp,
                                animationSpec = tween(200),
                                label = "viewerThumbnailWidth",
                            )
                            val thumbnailRequest = remember(media.id, media.uri, media.mediaType) {
                                ImageRequest.Builder(context)
                                    .data(media.uri)
                                    .apply {
                                        if (media.mediaType == MediaType.VIDEO) {
                                            videoFrameMillis(1_000)
                                        }
                                    }
                                    .size(128, 128)
                                    .crossfade(false)
                                    .memoryCacheKey("viewer-thumb-${media.id}")
                                    .diskCacheKey("viewer-thumb-${media.id}")
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = thumbnailRequest,
                                contentDescription = media.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = thumbnailWidth, height = 46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else viewerLine,
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .graphicsLayer { alpha = if (isSelected) 1f else 0.56f }
                                    .background(viewerLine)
                                    .pointerInput(index) {
                                        detectTapGestures {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerAction(
                        icon = Icons.Default.Share,
                        label = "Share",
                        color = viewerText,
                        onClick = {
                            currentMedia?.let { m ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = m.mimeType
                                putExtra(Intent.EXTRA_STREAM, m.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                            }
                        }
                    )
                    ViewerAction(
                        icon = if (currentMedia?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = "Favourite",
                        color = if (currentMedia?.isFavorite == true) MaterialTheme.colorScheme.primary else viewerText,
                        onClick = viewModel::toggleFavorite,
                    )
                    ViewerAction(
                        icon = Icons.Default.Edit,
                        label = "Edit",
                        color = if (onEditImage != null) viewerText else viewerDim,
                        onClick = { currentMedia?.let { media -> onEditImage?.invoke(media.uri) } },
                    )
                    ViewerAction(
                        icon = Icons.Default.Wallpaper,
                        label = "Wallpaper",
                        color = viewerText,
                        onClick = {
                            currentMedia?.let { m ->
                            val wallpaperIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                setDataAndType(m.uri, m.mimeType)
                                putExtra("mimeType", m.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(wallpaperIntent, "Set as wallpaper"))
                            }
                        }
                    )
                    ViewerAction(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        color = viewerText,
                        onClick = { viewModel.moveToTrash(onDone = onBack) },
                    )
                }
            }
        }

        // Info sheet
        if (uiState.showInfo) {
            viewModel.currentMedia()?.let { media ->
                MediaInfoSheet(media = media, onDismiss = viewModel::toggleInfo)
            }
        }

        // OCR text panel
        if (uiState.showOcrPanel) {
            OcrTextPanel(
                ocrText = uiState.ocrText,
                isLoading = uiState.isOcrLoading,
                onDismiss = viewModel::toggleOcrPanel,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (albumAction != null) {
        AlbumPickerDialog(
            albums = customAlbums,
            title = if (albumAction == AlbumMenuAction.Move) "Move to album" else "Copy to album",
            onDismiss = { albumAction = null },
            onSelect = { album ->
                when (albumAction) {
                    AlbumMenuAction.Move -> viewModel.moveToAlbum(album.id) { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    AlbumMenuAction.Copy -> viewModel.copyToAlbum(album.id) { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    null -> Unit
                }
                albumAction = null
            },
        )
    }

    if (showRenameDialog) {
        RenameMediaDialog(
            initialName = viewModel.currentMedia()?.name.orEmpty(),
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameCurrentMedia(newName) { result ->
                    coroutineScope.launch {
                        result
                            .onSuccess {
                                snackbarHostState.showSnackbar("Renamed successfully")
                            }
                            .onFailure { error ->
                                snackbarHostState.showSnackbar(error.message ?: "Unable to rename media")
                            }
                    }
                }
                showRenameDialog = false
            },
        )
    }
}

@Composable
private fun RowScope.ViewerAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(19.dp))
        Text(
            text = label.uppercase(Locale.getDefault()),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em),
            maxLines = 1,
        )
    }
}

@Composable
private fun MediaPage(
    media: MediaItem,
    isCurrentPage: Boolean,
    onTap: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit,
    bottomInset: Dp,
    showControls: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (media.mediaType == MediaType.VIDEO) {
        LaunchedEffect(isCurrentPage) {
            if (isCurrentPage) {
                onZoomStateChanged(false)
            }
        }
        if (isCurrentPage) {
            VideoPlayer(
                uri = media.uri,
                onTap = onTap,
                bottomInset = bottomInset,
                showControls = showControls,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        } else {
            VideoPoster(media = media, onTap = onTap)
        }
    } else {
        ZoomableImage(
            uri = media.uri,
            mediaId = media.id,
            name = media.name,
            onTap = onTap,
            isCurrentPage = isCurrentPage,
            onZoomStateChanged = onZoomStateChanged,
        )
    }
}

@Composable
private fun VideoPoster(media: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    val request = remember(media.uri) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .videoFrameMillis(1_000)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(media.id) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = media.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ZoomableImage(
    uri: Uri,
    mediaId: Long,
    name: String,
    onTap: () -> Unit,
    isCurrentPage: Boolean,
    onZoomStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(
            maxZoomFactor = 6f,
            minZoomFactor = 1f,
            overzoomEffect = OverzoomEffect.RubberBanding,
        ),
    )
    val zoomableImageState = rememberZoomableImageState(zoomableState)
    val requestSize = remember(configuration.screenWidthDp, configuration.screenHeightDp, density) {
        val w = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        val h = with(density) { configuration.screenHeightDp.dp.roundToPx() }
        coil.size.Size(w, h)
    }
    val imageRequest = remember(uri, mediaId, requestSize) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(requestSize)
            .crossfade(false)
            .placeholderMemoryCacheKey("thumb-$mediaId")
            .memoryCacheKey("viewer-image-$mediaId")
            .diskCacheKey("viewer-image-$mediaId")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            zoomableState.resetZoom()
            onZoomStateChanged(false)
        }
    }

    LaunchedEffect(zoomableState, isCurrentPage) {
        if (!isCurrentPage) return@LaunchedEffect

        snapshotFlow { (zoomableState.zoomFraction ?: 0f) > 0.01f }
            .distinctUntilChanged()
            .collect { isZoomed ->
                onZoomStateChanged(isZoomed)
            }
    }

    ZoomableAsyncImage(
        model = imageRequest,
        contentDescription = name,
        modifier = Modifier.fillMaxSize(),
        state = zoomableImageState,
        contentScale = ContentScale.Fit,
        gestures = if (isCurrentPage) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
        onClick = { onTap() },
    )
}

private const val SEEK_INCREMENT_MS = 10_000L

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPlayer(
    uri: Uri,
    onTap: () -> Unit,
    bottomInset: Dp,
    showControls: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkPlayer = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val playerText = if (isDarkPlayer) Color(0xFFF3F4F5) else Color(0xFF1D1F20)
    val playerDim = if (isDarkPlayer) Color(0xFF8D949C) else Color(0xFF6C7278)
    val playerPanel = if (isDarkPlayer) Color(0xFF0D0E10) else Color(0xFFFBFBFB)
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(uri))
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var currentPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var isPlaying by remember(uri) { mutableStateOf(exoPlayer.playWhenReady) }
    var isSeeking by remember(uri) { mutableStateOf(false) }
    var seekPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var isBuffering by remember(uri) { mutableStateOf(true) }
    var playbackError by remember(uri) { mutableStateOf<String?>(null) }
    var playbackSpeed by remember(uri) { mutableFloatStateOf(1f) }
    var isMuted by remember(uri) { mutableStateOf(false) }
    val activePositionMs = if (isSeeking) seekPositionMs else currentPositionMs
    val previewRequestMs = if (isSeeking) (seekPositionMs / 500L) * 500L else -1L

    // Double-tap seek indicators
    var seekIndicator by remember { mutableStateOf<SeekDirection?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val latestIsSeeking by rememberUpdatedState(isSeeking)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Unable to play this video"
                isBuffering = false
            }
            override fun onEvents(player: Player, events: Player.Events) {
                durationMs = player.duration.coerceAtLeast(0L)
                if (!latestIsSeeking) currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            val isInPictureInPicture = (context as? Activity)?.isInPictureInPictureMode == true
            if (!isInPictureInPicture && (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP)) {
                exoPlayer.pause()
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        durationMs = exoPlayer.duration.coerceAtLeast(0L)
        currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                isPlaying = exoPlayer.isPlaying
            }
            delay(200)
        }
    }

    var previewBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, previewRequestMs) {
        previewBitmap = if (previewRequestMs < 0L) {
            null
        } else {
            loadVideoPreviewFrame(context, uri, previewRequestMs)
        }
    }
    val togglePlayback = {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
                currentPositionMs = 0L
            }
            exoPlayer.play()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { offset ->
                            val width = size.width
                            if (offset.x < width / 2) {
                                val newPos = (exoPlayer.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                currentPositionMs = newPos
                                seekIndicator = SeekDirection.BACKWARD
                            } else {
                                val requestedPosition = exoPlayer.currentPosition + SEEK_INCREMENT_MS
                                val knownDuration = exoPlayer.duration.coerceAtLeast(0L)
                                val newPos = if (knownDuration > 0L) {
                                    requestedPosition.coerceAtMost(knownDuration)
                                } else {
                                    requestedPosition
                                }
                                exoPlayer.seekTo(newPos)
                                currentPositionMs = newPos
                                seekIndicator = SeekDirection.FORWARD
                            }
                            coroutineScope.launch {
                                delay(800)
                                seekIndicator = null
                            }
                        },
                    )
                },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
        }

        playbackError?.let { message ->
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = {
                            playbackError = null
                            isBuffering = true
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                    ) {
                        Text("RETRY")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = seekIndicator == SeekDirection.BACKWARD,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp),
        ) {
            SeekIndicator(direction = SeekDirection.BACKWARD)
        }

        AnimatedVisibility(
            visible = seekIndicator == SeekDirection.FORWARD,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp),
        ) {
            SeekIndicator(direction = SeekDirection.FORWARD)
        }

        AnimatedVisibility(
            visible = showControls && !isPlaying,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            FilledIconButton(
                onClick = togglePlayback,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                ),
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = playerPanel.copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isSeeking && previewBitmap != null) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 140.dp, height = 80.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = DateUtils.formatDuration(activePositionMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "−${DateUtils.formatDuration((durationMs - activePositionMs).coerceAtLeast(0L))}",
                            style = MaterialTheme.typography.labelLarge,
                            color = playerDim,
                        )
                    }

                    if (durationMs > 0L && !isBuffering) {
                        VideoFilmstripScrubber(
                            uri = uri,
                            durationMs = durationMs,
                            positionMs = activePositionMs,
                            onPositionChange = { newPosition ->
                                isSeeking = true
                                seekPositionMs = newPosition
                            },
                            onPositionChangeFinished = {
                                exoPlayer.seekTo(seekPositionMs)
                                currentPositionMs = seekPositionMs
                                isSeeking = false
                            },
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            val newPos = (exoPlayer.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = playerText)
                        }
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = playerText)
                        }
                        FilledIconButton(
                            onClick = togglePlayback,
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = if (isDarkPlayer) Color(0xFF0D0E10) else Color.White,
                            ),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = playerText)
                        }
                        IconButton(onClick = {
                            val requestedPosition = exoPlayer.currentPosition + SEEK_INCREMENT_MS
                            val knownDuration = exoPlayer.duration.coerceAtLeast(0L)
                            val newPos = if (knownDuration > 0L) {
                                requestedPosition.coerceAtMost(knownDuration)
                            } else {
                                requestedPosition
                            }
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = playerText)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerChip(
                            selected = true,
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    0.5f -> 1f
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    else -> 0.5f
                                }
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                            },
                        ) {
                            Text("${playbackSpeed}×", style = MaterialTheme.typography.labelLarge)
                        }
                        PlayerChip(
                            selected = isMuted,
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                        ) {
                            Icon(
                                if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        PlayerChip(
                            selected = false,
                            onClick = {
                                (context as? Activity)?.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build(),
                                )
                            },
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in picture",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) accent else outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun VideoBadge(label: String) {
    Text(
        text = label,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.5.sp,
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.14f, androidx.compose.ui.unit.TextUnitType.Em),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.48f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun VideoFilmstripScrubber(
    uri: Uri,
    durationMs: Long,
    positionMs: Long,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: () -> Unit,
) {
    val context = LocalContext.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    val frameCount = 5

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(frameCount) { index ->
                val frameRequest = remember(uri, safeDuration, index) {
                    ImageRequest.Builder(context)
                        .data(uri)
                        .videoFrameMillis((safeDuration * index) / (frameCount - 1L))
                        .size(160, 96)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = frameRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Slider(
            value = positionMs.coerceIn(0L, safeDuration).toFloat(),
            onValueChange = { onPositionChange(it.toLong().coerceIn(0L, safeDuration)) },
            onValueChangeFinished = onPositionChangeFinished,
            valueRange = 0f..safeDuration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private enum class SeekDirection { FORWARD, BACKWARD }

@Composable
private fun SeekIndicator(direction: SeekDirection) {
    Surface(
        color = Color.White.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (direction == SeekDirection.BACKWARD) {
                Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Text("10s", color = Color.White, style = MaterialTheme.typography.labelSmall)
            } else {
                Text("10s", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

private suspend fun loadVideoPreviewFrame(
    context: android.content.Context,
    uri: Uri,
    positionMs: Long,
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(
                positionMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: return@runCatching null

            Bitmap.createScaledBitmap(bitmap, 256, 144, true).also { scaled ->
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
            }
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private enum class AlbumMenuAction {
    Move,
    Copy,
}

@Composable
private fun AlbumPickerDialog(
    albums: List<Album>,
    title: String,
    onDismiss: () -> Unit,
    onSelect: (Album) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (albums.isEmpty()) {
                Text("No custom albums available")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    albums.forEach { album ->
                        TextButton(
                            onClick = { onSelect(album) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(album.name, modifier = Modifier.fillMaxWidth())
                        }
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
private fun RenameMediaDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) {
        mutableStateOf(initialName.substringBeforeLast('.', initialName))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("File name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun setAsWallpaper(context: android.content.Context, media: MediaItem) {
    val wallpaperIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(media.uri, media.mimeType)
        putExtra("mimeType", media.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(wallpaperIntent, "Set as wallpaper"))
}

private fun setAsContactAvatar(context: android.content.Context, media: MediaItem) {
    val contactIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(media.uri, media.mimeType)
        putExtra("mimeType", media.mimeType)
        putExtra("contact", true)
        putExtra("finishActivityOnSaveCompleted", true)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(contactIntent, "Set as contact avatar"))
}

private suspend fun convertImageToPdf(context: android.content.Context, media: MediaItem): Uri =
    withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(context, media.uri)
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        document.finishPage(page)

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val outputUri = resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, media.name.substringBeforeLast('.', media.name) + ".pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AndroidGallery")
                }
            },
        ) ?: error("Unable to create PDF file")

        resolver.openOutputStream(outputUri)?.use { output ->
            document.writeTo(output)
        } ?: error("Unable to write PDF file")

        document.close()
        bitmap.recycle()
        outputUri
    }

private suspend fun convertImageToCompatibleFormat(
    context: android.content.Context,
    media: MediaItem,
): Uri = withContext(Dispatchers.IO) {
    val bitmap = decodeBitmap(context, media.uri)
    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val outputUri = resolver.insert(
        collection,
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, media.name.substringBeforeLast('.', media.name) + "_compatible.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AndroidGallery/Compatible")
            }
        },
    ) ?: error("Unable to create compatible image")

    resolver.openOutputStream(outputUri)?.use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
            "Unable to encode JPEG"
        }
    } ?: error("Unable to write compatible image")

    bitmap.recycle()
    outputUri
}

private suspend fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

@Composable
private fun OcrTextPanel(
    ocrText: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f),
        ) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Text in Image",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Long-press text to select and copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Reading text from image…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    else -> {
                        SelectionContainer(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = ocrText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSheet(media: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var exifInfo by remember(media.uri) { mutableStateOf(com.gallery.android.utils.ExifInfo()) }
    LaunchedEffect(media.uri) {
        exifInfo = withContext(Dispatchers.IO) { ExifUtils.getExifInfo(context, media.uri) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Details & EXIF Metadata", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("Name", media.name)
                InfoRow("Date", DateUtils.formatDetailDate(media.dateAdded))
                InfoRow("Size", DateUtils.formatFileSize(media.size))
                val w = if (exifInfo.width > 0) exifInfo.width else media.width
                val h = if (exifInfo.height > 0) exifInfo.height else media.height
                if (w > 0 && h > 0) {
                    InfoRow("Resolution", "$w × $h")
                }
                if (media.mediaType == MediaType.VIDEO && media.duration > 0) {
                    InfoRow("Duration", DateUtils.formatDuration(media.duration))
                }
                InfoRow("Album", media.bucketName.ifBlank { "Unknown" })
                InfoRow("Path", media.path)

                // EXIF Section
                if (exifInfo.make != null || exifInfo.model != null || exifInfo.iso != null || exifInfo.aperture != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Camera Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    if (!exifInfo.make.isNullOrBlank() || !exifInfo.model.isNullOrBlank()) {
                        InfoRow("Camera", "${exifInfo.make.orEmpty()} ${exifInfo.model.orEmpty()}".trim())
                    }
                    if (!exifInfo.aperture.isNullOrBlank()) InfoRow("Aperture", exifInfo.aperture.orEmpty())
                    if (!exifInfo.focalLength.isNullOrBlank()) InfoRow("Focal Length", exifInfo.focalLength.orEmpty())
                    if (!exifInfo.iso.isNullOrBlank()) InfoRow("ISO", "ISO ${exifInfo.iso}")
                    if (!exifInfo.exposureTime.isNullOrBlank()) InfoRow("Shutter Speed", exifInfo.exposureTime.orEmpty())
                    if (!exifInfo.flash.isNullOrBlank()) InfoRow("Flash", exifInfo.flash.orEmpty())
                }

                if (exifInfo.latitude != null && exifInfo.longitude != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Location (GPS)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Coordinates", "%.5f, %.5f".format(exifInfo.latitude, exifInfo.longitude))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}











