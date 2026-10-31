package com.gallery.android.ui.gallery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaListQuery
import com.gallery.android.domain.model.TimelineAnchor
import com.gallery.android.domain.repository.AlbumRepository
import com.gallery.android.domain.usecase.FavoriteMediaUseCase
import com.gallery.android.domain.usecase.GetMediaUseCase
import com.gallery.android.domain.usecase.MoveToTrashUseCase
import com.gallery.android.domain.repository.MediaRepository
import com.gallery.android.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    fun toQuery(bucketId: Long? = null, restrictToIds: List<Long>? = null): MediaListQuery =
        MediaListQuery(
            bucketId = bucketId,
            restrictToIds = restrictToIds,
            imagesOnly = this == PHOTO || this == MOTION_PHOTO || this == EDITED,
            videosOnly = this == VIDEOS,
            screenshotsOnly = this == SCREENSHOTS_AND_RECORDINGS,
            favoritesOnly = this == FAVOURITES,
            motionPhotoOnly = this == MOTION_PHOTO,
            editedOnly = this == EDITED,
        )
}

data class GalleryUiState(
    val isLoading: Boolean = false,
    val gridColumns: Int = 3,
    val selectedIds: Set<Long> = emptySet(),
    val activeFilter: GalleryFilter = GalleryFilter.ALL,
    val mediaCount: Int = 0,
    val error: String? = null,
)

sealed interface GalleryGridItem {
    data class Header(val date: String, val location: String) : GalleryGridItem
    data class Media(val item: MediaItem) : GalleryGridItem
}

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val source = MutableStateFlow<GallerySource>(GallerySource.All)
    private val filter = MutableStateFlow(GalleryFilter.ALL)

    private val query: Flow<MediaListQuery> = combine(source, filter) { src, activeFilter ->
        src to activeFilter
    }.flatMapLatest { (src, activeFilter) ->
        when (src) {
            is GallerySource.CustomAlbum -> albumRepository.getMediaForAlbum(src.albumId)
                .map { ids -> activeFilter.toQuery(restrictToIds = ids) }
            is GallerySource.Bucket -> flowOf(activeFilter.toQuery(bucketId = src.bucketId))
            GallerySource.All -> flowOf(activeFilter.toQuery())
        }
    }.distinctUntilChanged()

    val gridItems: Flow<PagingData<GalleryGridItem>> = query
        .flatMapLatest { getMediaUseCase(it) }
        .map { pagingData ->
            pagingData
                .map { GalleryGridItem.Media(it) as GalleryGridItem }
                .insertSeparators { before, after ->
                    val afterMedia = (after as? GalleryGridItem.Media)?.item ?: return@insertSeparators null
                    val beforeMedia = (before as? GalleryGridItem.Media)?.item
                    val afterLabel = DateUtils.formatGroupDate(afterMedia.dateAdded)
                    val beforeLabel = beforeMedia?.let { DateUtils.formatGroupDate(it.dateAdded) }
                    if (beforeLabel != afterLabel) {
                        GalleryGridItem.Header(
                            date = afterLabel,
                            location = afterMedia.bucketName,
                        )
                    } else {
                        null
                    }
                }
        }
        .cachedIn(viewModelScope)

    val timelineAnchors: StateFlow<List<TimelineAnchor>> = query
        .flatMapLatest { getMediaUseCase.observeTimeline(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val GRID_COLUMNS_KEY = intPreferencesKey("grid_columns")

    init {
        viewModelScope.launch {
            dataStore.data.map { prefs -> prefs[GRID_COLUMNS_KEY] ?: 3 }
                .collect { columns -> _uiState.update { it.copy(gridColumns = columns) } }
        }
        viewModelScope.launch {
            query.flatMapLatest { getMediaUseCase.observeCount(it) }
                .collect { count -> _uiState.update { it.copy(mediaCount = count) } }
        }
        viewModelScope.launch {
            runCatching { mediaRepository.syncMediaStore() }
        }
    }

    fun setSource(bucketId: Long?, customAlbumId: Long?) {
        source.value = when {
            customAlbumId != null -> GallerySource.CustomAlbum(customAlbumId)
            bucketId != null -> GallerySource.Bucket(bucketId)
            else -> GallerySource.All
        }
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

    fun setFilter(next: GalleryFilter) {
        filter.value = next
        _uiState.update { it.copy(activeFilter = next) }
    }

    private sealed interface GallerySource {
        data object All : GallerySource
        data class Bucket(val bucketId: Long) : GallerySource
        data class CustomAlbum(val albumId: Long) : GallerySource
    }
}
