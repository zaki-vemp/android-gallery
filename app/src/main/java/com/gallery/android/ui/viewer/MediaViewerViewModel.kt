package com.gallery.android.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.data.local.database.dao.OcrMetadataDao
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.domain.repository.AlbumRepository
import com.gallery.android.domain.repository.MediaRepository
import com.gallery.android.domain.usecase.FavoriteMediaUseCase
import com.gallery.android.domain.usecase.MoveToTrashUseCase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ViewerUiState(
    val preview: MediaItem? = null,
    val mediaList: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val showInfo: Boolean = false,
    val showOcrPanel: Boolean = false,
    val ocrText: String = "",
    val isOcrLoading: Boolean = false,
)

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteMediaUseCase: FavoriteMediaUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val mediaRepository: MediaRepository,
    private val albumRepository: AlbumRepository,
    private val ocrMetadataDao: OcrMetadataDao,
) : ViewModel() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()
    private val _openedId = MutableStateFlow(0L)
    private var openJob: Job? = null

    val customAlbums: StateFlow<List<Album>> = albumRepository.getUserCreatedAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun open(mediaId: Long) {
        if (_openedId.value == mediaId &&
            (openJob?.isActive == true || _uiState.value.mediaList.any { it.id == mediaId })
        ) return
        _openedId.value = mediaId
        openJob?.cancel()
        openJob = viewModelScope.launch {
            val first = mediaRepository.getMediaById(mediaId)
            if (first != null) {
                _uiState.update { state ->
                    if (state.mediaList.any { it.id == mediaId }) {
                        state.copy(preview = first)
                    } else {
                        state.copy(
                            preview = first,
                            mediaList = emptyList(),
                            currentIndex = 0,
                            isLoading = true,
                        )
                    }
                }
            }
            val all = mediaRepository.loadGalleryIndex()
            val index = all.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
            val resolvedList = all.ifEmpty { listOfNotNull(first) }
            _uiState.update { state ->
                state.copy(
                    preview = first ?: state.preview,
                    mediaList = resolvedList,
                    currentIndex = index,
                    isLoading = false,
                )
            }
        }
    }

    fun setCurrentIndex(index: Int) = _uiState.update { state ->
        val safeIndex = index.coerceIn(0, state.mediaList.lastIndex.coerceAtLeast(0))
        if (safeIndex == state.currentIndex) {
            state
        } else {
            state.copy(
                currentIndex = safeIndex,
                showInfo = false,
                showOcrPanel = false,
                ocrText = "",
                isOcrLoading = false,
            )
        }
    }

    fun toggleInfo() = _uiState.update { it.copy(showInfo = !it.showInfo) }

    fun toggleOcrPanel() {
        val current = _uiState.value.showOcrPanel
        if (!current) {
            // Opening: load OCR text for the current image
            _uiState.update { it.copy(showOcrPanel = true) }
            loadOcrText()
        } else {
            _uiState.update { it.copy(showOcrPanel = false, ocrText = "") }
        }
    }

    private fun loadOcrText() {
        val media = currentMedia() ?: return
        if (media.mediaType != MediaType.IMAGE) {
            _uiState.update { it.copy(ocrText = "OCR is only available for images.", isOcrLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrLoading = true, ocrText = "") }
            // Check the local DB cache first
            val cached = withContext(Dispatchers.IO) { ocrMetadataDao.getByMediaId(media.id) }
            if (cached != null && cached.extractedText.isNotBlank()) {
                _uiState.update { it.copy(ocrText = cached.extractedText, isOcrLoading = false) }
                return@launch
            }
            // Run live OCR
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val image = InputImage.fromFilePath(context, media.uri)
                    recognizer.process(image).await().text
                }.getOrElse { "" }
            }
            _uiState.update {
                it.copy(
                    ocrText = text.ifBlank { "No text found in this image." },
                    isOcrLoading = false,
                )
            }
        }
    }

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
