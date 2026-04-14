package com.gallery.android.ui.viewer

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: Long,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customAlbums by viewModel.customAlbums.collectAsStateWithLifecycle()
    var showControls by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var canDismissBySwipe by remember { mutableStateOf(true) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var albumAction by remember { mutableStateOf<AlbumMenuAction?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Swipe-down-to-dismiss state
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = with(LocalDensity.current) { 150.dp.toPx() }
    var isDismissing by remember { mutableStateOf(false) }
    var isSnappingBack by remember { mutableStateOf(false) }
    val animatedOffsetY = remember { Animatable(0f) }

    // Use animated value when snapping back, raw drag value otherwise
    val currentOffsetY = if (isSnappingBack) animatedOffsetY.value else dragOffsetY

    // Fraction of dismiss progress (0 = normal, 1 = fully dismissed)
    val dismissProgress = (abs(currentOffsetY) / dismissThreshold).coerceIn(0f, 1f)
    val bgAlpha = 1f - dismissProgress * 0.6f
    val contentScale = 1f - dismissProgress * 0.15f

    // Snap-back animation
    LaunchedEffect(isSnappingBack) {
        if (isSnappingBack) {
            animatedOffsetY.snapTo(dragOffsetY)
            animatedOffsetY.animateTo(0f, tween(250))
            dragOffsetY = 0f
            isSnappingBack = false
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { uiState.mediaList.size },
    )

    // Scroll to the clicked image once the list is loaded
    LaunchedEffect(initialMediaId, uiState.mediaList) {
        if (uiState.mediaList.isNotEmpty()) {
            val index = uiState.mediaList.indexOfFirst { it.id == initialMediaId }
            if (index >= 0) {
                pagerState.scrollToPage(index)
            }
        }
    }

    // Sync pager position back to ViewModel
    LaunchedEffect(pagerState.currentPage, uiState.mediaList) {
        if (uiState.mediaList.isNotEmpty()) {
            viewModel.setCurrentIndex(pagerState.currentPage)
            canDismissBySwipe = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
    ) {
        val currentMedia = viewModel.currentMedia()

        if (uiState.mediaList.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, currentOffsetY.roundToInt()) }
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    }
                    .then(
                        if (canDismissBySwipe) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount
                                        if (abs(dragOffsetY) > 10f) {
                                            showControls = false
                                        }
                                    },
                                    onDragEnd = {
                                        if (abs(dragOffsetY) > dismissThreshold) {
                                            isDismissing = true
                                        } else if (abs(dragOffsetY) > 1f) {
                                            isSnappingBack = true
                                        }
                                    },
                                    onDragCancel = {
                                        if (abs(dragOffsetY) > 1f) {
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
                userScrollEnabled = !isSnappingBack && abs(dragOffsetY) < 8f,
            ) { page ->
                val media = uiState.mediaList[page]
                MediaPage(
                    media = media,
                    isCurrentPage = page == pagerState.currentPage,
                    onTap = { showControls = !showControls },
                    onZoomStateChanged = { isZoomed -> canDismissBySwipe = !isZoomed },
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
            TopAppBar(
                title = {
                    Text(
                        viewModel.currentMedia()?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        val isFavorite = viewModel.currentMedia()?.isFavorite == true
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFF4081) else Color.White,
                        )
                    }
                    IconButton(onClick = viewModel::toggleInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showActionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions", tint = Color.White)
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
                                    } else if (media != null) {
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

        // Bottom controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val media = viewModel.currentMedia()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = {
                    media?.let { m ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = m.mimeType
                            putExtra(Intent.EXTRA_STREAM, m.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = {
                    viewModel.moveToTrash(onDone = onBack)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
                IconButton(onClick = {
                    media?.let { m ->
                        val wallpaperIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                            setDataAndType(m.uri, m.mimeType)
                            putExtra("mimeType", m.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(wallpaperIntent, "Set as wallpaper"))
                    }
                }) {
                    Icon(Icons.Default.Wallpaper, contentDescription = "Set wallpaper", tint = Color.White)
                }
            }
        }

        // Dismiss and navigate back
        LaunchedEffect(isDismissing) {
            if (isDismissing) {
                onBack()
            }
        }

        // Info sheet
        if (uiState.showInfo) {
            viewModel.currentMedia()?.let { media ->
                MediaInfoSheet(media = media, onDismiss = viewModel::toggleInfo)
            }
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
private fun MediaPage(
    media: MediaItem,
    isCurrentPage: Boolean,
    onTap: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit,
) {
    if (media.mediaType == MediaType.VIDEO) {
        LaunchedEffect(isCurrentPage) {
            if (isCurrentPage) {
                onZoomStateChanged(false)
            }
        }
        VideoPlayer(uri = media.uri, onTap = onTap)
    } else {
        ZoomableImage(
            uri = media.uri,
            name = media.name,
            onTap = onTap,
            isCurrentPage = isCurrentPage,
            onZoomStateChanged = onZoomStateChanged,
        )
    }
}

@Composable
private fun ZoomableImage(
    uri: Uri,
    name: String,
    onTap: () -> Unit,
    isCurrentPage: Boolean,
    onZoomStateChanged: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCurrentPage, scale) {
        if (isCurrentPage) {
            onZoomStateChanged(scale > 1.02f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val isTransformGesture = event.changes.size > 1 || scale > 1f
                        if (isTransformGesture) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(200)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

@Composable
private fun VideoPlayer(uri: Uri, onTap: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
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
private fun MediaInfoSheet(media: MediaItem, onDismiss: () -> Unit) {
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
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(Modifier.height(16.dp))
                InfoRow("Name", media.name)
                InfoRow("Date", DateUtils.formatDetailDate(media.dateAdded))
                InfoRow("Size", DateUtils.formatFileSize(media.size))
                if (media.width > 0 && media.height > 0)
                    InfoRow("Resolution", "${media.width} × ${media.height}")
                if (media.mediaType == MediaType.VIDEO && media.duration > 0)
                    InfoRow("Duration", DateUtils.formatDuration(media.duration))
                InfoRow("Album", media.bucketName.ifBlank { "Unknown" })
                InfoRow("Path", media.path)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
