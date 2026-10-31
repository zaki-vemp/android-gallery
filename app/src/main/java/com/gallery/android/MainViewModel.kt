package com.gallery.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.data.media.MediaStoreObserver
import com.gallery.android.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaStoreObserver: MediaStoreObserver,
) : ViewModel() {

    fun onPermissionNewlyGranted() {
        viewModelScope.launch {
            runCatching { mediaRepository.syncMediaStore(force = true) }
            mediaStoreObserver.ping()
        }
    }
}
