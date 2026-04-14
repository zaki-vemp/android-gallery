package com.gallery.android.domain.usecase

import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.repository.MediaRepository
import javax.inject.Inject

class SearchMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(query: String): List<MediaItem> =
        mediaRepository.searchMedia(query)
}
