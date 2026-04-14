package com.gallery.android.ui.gallery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.usecase.FavoriteMediaUseCase
import com.gallery.android.domain.usecase.GetMediaUseCase
import com.gallery.android.domain.usecase.MoveToTrashUseCase
import com.gallery.android.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val isLoading: Boolean = false,
    val gridColumns: Int = 3,
    val selectedIds: Set<Long> = emptySet(),
    val error: String? = null,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val favoriteMediaUseCase: FavoriteMediaUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val mediaRepository: MediaRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    val mediaPaged: Flow<PagingData<MediaItem>> = getMediaUseCase()
        .cachedIn(viewModelScope)

    val allMedia: StateFlow<List<MediaItem>> = getMediaUseCase.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val GRID_COLUMNS_KEY = intPreferencesKey("grid_columns")

    init {
        viewModelScope.launch {
            dataStore.data.map { prefs -> prefs[GRID_COLUMNS_KEY] ?: 3 }
                .collect { columns -> _uiState.update { it.copy(gridColumns = columns) } }
        }
        syncMedia()
    }

    fun syncMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                mediaRepository.syncMediaStore()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun getMediaByBucket(bucketId: Long): Flow<List<MediaItem>> =
        getMediaUseCase.getByBucket(bucketId)

    fun toggleSelection(mediaId: Long) {
        _uiState.update { state ->
            val newSelected = if (state.selectedIds.contains(mediaId))
                state.selectedIds - mediaId
            else state.selectedIds + mediaId
            state.copy(selectedIds = newSelected)
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selectedIds = emptySet()) }

    fun toggleFavorite(mediaId: Long, isFavorite: Boolean) {
        viewModelScope.launch { favoriteMediaUseCase.toggleFavorite(mediaId, isFavorite) }
    }

    fun moveToTrash(mediaId: Long) {
        viewModelScope.launch { moveToTrashUseCase.moveToTrash(mediaId) }
    }

    fun moveSelectedToTrash() {
        val ids = _uiState.value.selectedIds
        viewModelScope.launch {
            ids.forEach { moveToTrashUseCase.moveToTrash(it) }
            clearSelection()
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[GRID_COLUMNS_KEY] = columns }
        }
    }
}
