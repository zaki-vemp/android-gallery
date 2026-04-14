package com.gallery.android.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.domain.model.MediaItem
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

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

    fun currentMedia(): MediaItem? = _uiState.value.mediaList.getOrNull(_uiState.value.currentIndex)
}
