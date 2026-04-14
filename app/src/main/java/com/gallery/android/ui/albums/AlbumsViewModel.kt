package com.gallery.android.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.usecase.GetAlbumsUseCase
import com.gallery.android.domain.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    getAlbumsUseCase: GetAlbumsUseCase,
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    val albums: StateFlow<List<Album>> = getAlbumsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createAlbum(name: String) {
        viewModelScope.launch { albumRepository.createAlbum(name) }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch { albumRepository.deleteAlbum(albumId) }
    }
}
