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
import com.gallery.android.domain.repository.AlbumRepository
import com.gallery.android.domain.usecase.FavoriteMediaUseCase
import com.gallery.android.domain.usecase.GetMediaUseCase
import com.gallery.android.domain.usecase.MoveToTrashUseCase
import com.gallery.android.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GalleryFilter(val label: String) {
    PHOTO("Photo"),
    VIDEOS("Videos"),
    MOTION_PHOTO("Motion Photo"),
    SCREENSHOTS_AND_RECORDINGS("Screenshots & screen recordings"),
    FAVOURITES("Favourites"),
    EDITED("Edited"),
    ALL("All");

    fun matches(media: MediaItem): Boolean {
        val searchText = listOf(
            media.name,
            media.path,
            media.mimeType,
            media.bucketName,
        ).joinToString(" ").lowercase()

        return when (this) {
            PHOTO -> media.mediaType == com.gallery.android.domain.model.MediaType.IMAGE
            VIDEOS -> media.mediaType == com.gallery.android.domain.model.MediaType.VIDEO
            MOTION_PHOTO -> media.mediaType == com.gallery.android.domain.model.MediaType.IMAGE &&
                ("motion" in searchText || "live photo" in searchText || "motionphoto" in searchText)
            SCREENSHOTS_AND_RECORDINGS ->
                "screenshot" in searchText ||
                "screen_record" in searchText ||
                "screenrecord" in searchText ||
                "screen recording" in searchText
            FAVOURITES -> media.isFavorite
            EDITED ->
                "edit" in searchText ||
                "edited" in searchText ||
                media.dateModified > media.dateAdded + 3600
            ALL -> true
        }
    }
}

data class GalleryUiState(
    val isLoading: Boolean = false,
    val gridColumns: Int = 3,
    val selectedIds: Set<Long> = emptySet(),
    val activeFilter: GalleryFilter = GalleryFilter.ALL,
    val error: String? = null,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val favoriteMediaUseCase: FavoriteMediaUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
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

    fun getMediaByCustomAlbum(albumId: Long): Flow<List<MediaItem>> =
        combine(
            mediaRepository.getAllMedia(),
            albumRepository.getMediaForAlbum(albumId),
        ) { mediaList, mediaIds ->
            val ids = mediaIds.toSet()
            mediaList.filter { it.id in ids }
        }

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

    fun setFilter(filter: GalleryFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }
}
