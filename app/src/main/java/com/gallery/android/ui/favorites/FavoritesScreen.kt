package com.gallery.android.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.usecase.FavoriteMediaUseCase
import com.gallery.android.ui.components.GalleryEmptyState
import com.gallery.android.ui.components.GalleryMotion
import com.gallery.android.ui.components.MediaThumbnail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    favoriteMediaUseCase: FavoriteMediaUseCase,
) : ViewModel() {
    val favorites: StateFlow<List<MediaItem>> = favoriteMediaUseCase.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onMediaClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites", fontWeight = FontWeight.Bold) },
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
        if (favorites.isEmpty()) {
            GalleryEmptyState(
                icon = Icons.Default.FavoriteBorder,
                title = "No favorites yet",
                subtitle = "Tap the heart on a photo to save it here",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(favorites, key = { it.id }) { media ->
                    MediaThumbnail(
                        media = media,
                        isSelected = false,
                        onClick = { onMediaClick(media.id) },
                        cornerRadius = 8,
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
