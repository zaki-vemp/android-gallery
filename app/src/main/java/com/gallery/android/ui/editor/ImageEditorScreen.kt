package com.gallery.android.ui.editor

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    mediaUri: Uri,
    onBack: () -> Unit,
    onSaved: (Uri) -> Unit,
    viewModel: ImageEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mediaUri) {
        viewModel.loadImage(mediaUri)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::resetEdits,
                        enabled = !uiState.isSaving,
                    ) {
                        Text("Reset")
                    }
                    Button(
                        onClick = {
                            viewModel.saveEditedImage { uri ->
                                if (uri != null) onSaved(uri) else { /* error shown via snackbar */ }
                            }
                        },
                        enabled = !uiState.isSaving && uiState.originalBitmap != null,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
        ) {
            // Preview area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uiState.isProcessing -> {
                        CircularProgressIndicator(color = Color.White)
                    }
                    uiState.previewBitmap != null -> {
                        if (uiState.activeTab == EditorTab.CROP) {
                            CropOverlay(
                                bitmap = uiState.previewBitmap!!,
                                cropRect = uiState.editState.cropRect ?: CropRect(),
                                onCropChanged = viewModel::updateCrop,
                            )
                        } else {
                            Image(
                                bitmap = uiState.previewBitmap!!.asImageBitmap(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    else -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(mediaUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Tab bar
            EditorTabRow(
                activeTab = uiState.activeTab,
                onTabSelected = viewModel::setActiveTab,
            )

            // Controls panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                when (uiState.activeTab) {
                    EditorTab.ADJUST -> AdjustPanel(
                        editState = uiState.editState,
                        onBrightnessChange = viewModel::updateBrightness,
                        onContrastChange = viewModel::updateContrast,
                        onSaturationChange = viewModel::updateSaturation,
                        onRotateLeft = viewModel::rotateLeft,
                        onRotateRight = viewModel::rotateRight,
                        onFlipH = viewModel::flipHorizontal,
                        onFlipV = viewModel::flipVertical,
                    )
                    EditorTab.FILTERS -> FiltersPanel(
                        selectedFilter = uiState.editState.selectedFilter,
                        previewBitmap = uiState.originalBitmap,
                        onFilterSelected = viewModel::selectFilter,
                    )
                    EditorTab.CROP -> CropHintPanel()
                }
            }
        }
    }
}

@Composable
private fun EditorTabRow(activeTab: EditorTab, onTabSelected: (EditorTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        EditorTab.entries.forEach { tab ->
            val label = when (tab) {
                EditorTab.ADJUST -> "Adjust"
                EditorTab.FILTERS -> "Filters"
                EditorTab.CROP -> "Crop"
            }
            val icon = when (tab) {
                EditorTab.ADJUST -> Icons.Default.Tune
                EditorTab.FILTERS -> Icons.Default.AutoFixHigh
                EditorTab.CROP -> Icons.Default.Crop
            }
            val selected = tab == activeTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun AdjustPanel(
    editState: EditState,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Triple(Icons.Default.RotateLeft, "Rotate left", onRotateLeft),
                Triple(Icons.Default.RotateRight, "Rotate right", onRotateRight),
                Triple(Icons.Default.Flip, "Flip horizontal", onFlipH),
                Triple(Icons.Default.SwapVert, "Flip vertical", onFlipV),
            ).forEach { (icon, label, action) ->
                FilledTonalIconButton(
                    onClick = action,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
                }
            }
        }

        AdjustSlider(
            label = "Brightness",
            value = editState.brightness,
            onValueChange = onBrightnessChange,
            valueRange = -1f..1f,
        )
        AdjustSlider(
            label = "Contrast",
            value = editState.contrast,
            onValueChange = onContrastChange,
            valueRange = 0.5f..2f,
        )
        AdjustSlider(
            label = "Saturation",
            value = editState.saturation,
            onValueChange = onSaturationChange,
            valueRange = 0f..2f,
        )
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FiltersPanel(
    selectedFilter: ImageFilter,
    previewBitmap: android.graphics.Bitmap?,
    onFilterSelected: (ImageFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(ImageFilter.entries) { filter ->
            FilterThumbnail(
                filter = filter,
                isSelected = filter == selectedFilter,
                previewBitmap = previewBitmap,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterThumbnail(
    filter: ImageFilter,
    isSelected: Boolean,
    previewBitmap: android.graphics.Bitmap?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = filter.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Text(
            text = filter.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CropHintPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Drag the corners of the overlay to crop your image",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CropOverlay(
    bitmap: android.graphics.Bitmap,
    cropRect: CropRect,
    onCropChanged: (CropRect) -> Unit,
) {
    var rect by remember(cropRect) { mutableStateOf(cropRect) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dxFrac = dragAmount.x / size.width
                        val dyFrac = dragAmount.y / size.height
                        rect = rect.copy(
                            left = (rect.left + dxFrac).coerceIn(0f, rect.right - 0.05f),
                            top = (rect.top + dyFrac).coerceIn(0f, rect.bottom - 0.05f),
                            right = (rect.right + dxFrac).coerceIn(rect.left + 0.05f, 1f),
                            bottom = (rect.bottom + dyFrac).coerceIn(rect.top + 0.05f, 1f),
                        )
                        onCropChanged(rect)
                    }
                },
        )
    }
}
