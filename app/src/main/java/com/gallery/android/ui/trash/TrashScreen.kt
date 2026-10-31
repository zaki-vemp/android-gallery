package com.gallery.android.ui.trash

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.usecase.MoveToTrashUseCase
import com.gallery.android.ui.components.GalleryEmptyState
import com.gallery.android.ui.components.GalleryMotion
import com.gallery.android.ui.components.scaleOnPress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val moveToTrashUseCase: MoveToTrashUseCase,
) : ViewModel() {
    val trashItems: StateFlow<List<MediaItem>> = moveToTrashUseCase.getTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(mediaId: Long) = viewModelScope.launch { moveToTrashUseCase.restore(mediaId) }
    fun deletePermanently(mediaId: Long) = viewModelScope.launch { moveToTrashUseCase.deletePermanently(mediaId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val items by viewModel.trashItems.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recently Deleted", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (items.isEmpty()) {
                GalleryEmptyState(
                    icon = Icons.Default.Delete,
                    title = "Trash is empty",
                    subtitle = "Deleted items stay here for 30 days",
                )
            } else {
                Text(
                    "Items are permanently deleted after 30 days",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(items, key = { it.id }) { media ->
                        TrashItem(
                            media = media,
                            onRestore = { viewModel.restore(media.id) },
                            onDelete = { viewModel.deletePermanently(media.id) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = GalleryMotion.Medium,
                                placementSpec = GalleryMotion.Placement,
                                fadeOutSpec = GalleryMotion.Fast,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashItem(
    media: MediaItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scaleOnPress(0.95f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { showMenu = true },
                onLongClick = { showMenu = true },
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(media.uri)
                .crossfade(220)
                .build(),
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Restore") },
                onClick = {
                    onRestore()
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Delete permanently") },
                onClick = {
                    onDelete()
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            )
        }
    }
}
