package com.gallery.android.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: Long,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showControls by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(initialMediaId, uiState.mediaList) {
        if (uiState.mediaList.isNotEmpty()) viewModel.jumpToMedia(initialMediaId)
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.currentIndex,
        pageCount = { uiState.mediaList.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentIndex(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.mediaList.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val media = uiState.mediaList[page]
                MediaPage(
                    media = media,
                    onTap = { showControls = !showControls },
                )
            }
        }

        // Top controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.currentMedia()?.name ?: "",
                        color = Color.White,
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
                        val isFavorite = uiState.currentMedia()?.isFavorite == true
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFF4081) else Color.White,
                        )
                    }
                    IconButton(onClick = viewModel::toggleInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
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
            val media = uiState.currentMedia()
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

        // Info sheet
        if (uiState.showInfo) {
            uiState.currentMedia()?.let { media ->
                MediaInfoSheet(media = media, onDismiss = viewModel::toggleInfo)
            }
        }
    }
}

@Composable
private fun MediaPage(media: MediaItem, onTap: () -> Unit) {
    if (media.mediaType == MediaType.VIDEO) {
        VideoPlayer(uri = media.uri, onTap = onTap)
    } else {
        ZoomableImage(uri = media.uri, name = media.name, onTap = onTap)
    }
}

@Composable
private fun ZoomableImage(uri: Uri, name: String, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        if (scale == 1f) offset = Offset.Zero
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) offset += pan
                    else offset = Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
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
