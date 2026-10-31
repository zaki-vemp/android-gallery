package com.gallery.android.ui.collage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
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
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gallery.android.domain.model.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    onBack: () -> Unit,
    viewModel: CollageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showPhotoPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collage Creator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.exportCollage { message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        enabled = !uiState.isExporting && uiState.selectedMedia.isNotEmpty(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Done, contentDescription = "Export")
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Live Preview Canvas Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(uiState.spacingDp.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uiState.selectedMedia.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Select 2 to 6 photos to create a collage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        CollagePreviewGrid(
                            selectedMedia = uiState.selectedMedia,
                            layoutType = uiState.selectedLayout,
                            cornerRadiusDp = uiState.cornerRadiusDp,
                            spacingDp = uiState.spacingDp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Control Sliders
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Corner Radius: ${uiState.cornerRadiusDp.toInt()} dp",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = uiState.cornerRadiusDp,
                        onValueChange = viewModel::setCornerRadius,
                        valueRange = 0f..32f,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Spacing: ${uiState.spacingDp.toInt()} dp",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = uiState.spacingDp,
                        onValueChange = viewModel::setSpacing,
                        valueRange = 0f..24f,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Select Layout Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layout Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showPhotoPicker = true }) {
                    Icon(Icons.Default.GridView, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Select Photos (${uiState.selectedMedia.size}/6)")
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val layouts = listOf(
                    CollageLayoutType.GRID_2_HORIZONTAL to "Side 1:1",
                    CollageLayoutType.GRID_2_VERTICAL to "Stack 2",
                    CollageLayoutType.GRID_3_TOP_HERO to "Hero Top",
                    CollageLayoutType.GRID_3_LEFT_HERO to "Hero Left",
                    CollageLayoutType.GRID_4_SQUARE to "Grid 2x2",
                    CollageLayoutType.GRID_6_GALLERY to "Grid 3x2",
                )
                items(layouts) { (layout, label) ->
                    FilterChip(
                        selected = uiState.selectedLayout == layout,
                        onClick = { viewModel.selectLayout(layout) },
                        label = { Text(label) },
                        leadingIcon = if (uiState.selectedLayout == layout) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
        }
    }

    // Photo Picker Dialog
    if (showPhotoPicker) {
        AlertDialog(
            onDismissRequest = { showPhotoPicker = false },
            title = { Text("Choose Photos for Collage") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(uiState.availableMedia) { media ->
                        val isSelected = uiState.selectedMedia.any { it.id == media.id }
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleSelectMedia(media) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(media.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .background(Color.White, CircleShape)
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoPicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun CollagePreviewGrid(
    selectedMedia: List<MediaItem>,
    layoutType: CollageLayoutType,
    cornerRadiusDp: Float,
    spacingDp: Float,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val count = selectedMedia.size
        when (layoutType) {
            CollageLayoutType.GRID_2_HORIZONTAL -> {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                    selectedMedia.take(2).forEach { media ->
                        CollageMediaItem(media, cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            CollageLayoutType.GRID_2_VERTICAL -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                    selectedMedia.take(2).forEach { media ->
                        CollageMediaItem(media, cornerRadiusDp, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            CollageLayoutType.GRID_3_TOP_HERO -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                    if (count >= 1) CollageMediaItem(selectedMedia[0], cornerRadiusDp, Modifier.weight(1f).fillMaxWidth())
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                        if (count >= 2) CollageMediaItem(selectedMedia[1], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                        if (count >= 3) CollageMediaItem(selectedMedia[2], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            CollageLayoutType.GRID_3_LEFT_HERO -> {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                    if (count >= 1) CollageMediaItem(selectedMedia[0], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                        if (count >= 2) CollageMediaItem(selectedMedia[1], cornerRadiusDp, Modifier.weight(1f).fillMaxWidth())
                        if (count >= 3) CollageMediaItem(selectedMedia[2], cornerRadiusDp, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            CollageLayoutType.GRID_4_SQUARE -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                        if (count >= 1) CollageMediaItem(selectedMedia[0], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                        if (count >= 2) CollageMediaItem(selectedMedia[1], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                        if (count >= 3) CollageMediaItem(selectedMedia[2], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                        if (count >= 4) CollageMediaItem(selectedMedia[3], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            CollageLayoutType.GRID_6_GALLERY -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                        for (i in 0 until 3) {
                            if (i < count) CollageMediaItem(selectedMedia[i], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
                        for (i in 3 until 6) {
                            if (i < count) CollageMediaItem(selectedMedia[i], cornerRadiusDp, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollageMediaItem(media: MediaItem, cornerRadiusDp: Float, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(media.uri)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(cornerRadiusDp.dp)),
    )
}
