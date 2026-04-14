package com.gallery.android.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.repository.AlbumRepository
import com.gallery.android.domain.repository.MediaRepository
import com.gallery.android.domain.usecase.FavoriteMediaUseCase
import com.gallery.android.domain.usecase.GetMediaUseCase
import com.gallery.android.domain.usecase.MoveToTrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerUiState(
    val mediaList: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val showInfo: Boolean = false,
)

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val favoriteMediaUseCase: FavoriteMediaUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    val customAlbums: StateFlow<List<Album>> = albumRepository.getAlbums()
        .map { albums -> albums.filter { it.isUserCreated } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            getMediaUseCase.getAll().collect { list ->
                _uiState.update { it.copy(mediaList = list, isLoading = false) }
            }
        }
    }

    fun jumpToMedia(mediaId: Long) {
        val index = _uiState.value.mediaList.indexOfFirst { it.id == mediaId }
        if (index >= 0) _uiState.update { it.copy(currentIndex = index) }
    }

    fun setCurrentIndex(index: Int) = _uiState.update { it.copy(currentIndex = index) }

    fun toggleInfo() = _uiState.update { it.copy(showInfo = !it.showInfo) }

    fun toggleFavorite() {
        val media = currentMedia() ?: return
        viewModelScope.launch { favoriteMediaUseCase.toggleFavorite(media.id, !media.isFavorite) }
    }

    fun moveToTrash(onDone: () -> Unit) {
        val media = currentMedia() ?: return
        viewModelScope.launch {
            moveToTrashUseCase.moveToTrash(media.id)
            onDone()
        }
    }

    fun moveToPrivateSafe(onDone: () -> Unit, onResult: (String) -> Unit) {
        val media = currentMedia() ?: return
        viewModelScope.launch {
            mediaRepository.moveToSafe(media.id, "")
            onResult("Moved to Private Safe")
            onDone()
        }
    }

    fun copyToAlbum(albumId: Long, onResult: (String) -> Unit) {
        val media = currentMedia() ?: return
        viewModelScope.launch {
            albumRepository.addMediaToAlbum(albumId, media.id)
            onResult("Copied to album")
        }
    }

    fun moveToAlbum(albumId: Long, onResult: (String) -> Unit) {
        val media = currentMedia() ?: return
        viewModelScope.launch {
            customAlbums.value.forEach { album ->
                albumRepository.removeMediaFromAlbum(album.id, media.id)
            }
            albumRepository.addMediaToAlbum(albumId, media.id)
            onResult("Moved to album")
        }
    }

    fun renameCurrentMedia(newName: String, onResult: (Result<Unit>) -> Unit) {
        val media = currentMedia() ?: return
        viewModelScope.launch {
            runCatching {
                mediaRepository.renameMedia(media.id, newName)
            }.onSuccess {
                onResult(Result.success(Unit))
            }.onFailure { error ->
                onResult(Result.failure(error))
            }
        }
    }

    fun currentMedia(): MediaItem? = _uiState.value.mediaList.getOrNull(_uiState.value.currentIndex)
}
