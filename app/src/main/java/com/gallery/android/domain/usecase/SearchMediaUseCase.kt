package com.gallery.android.domain.usecase

import com.gallery.android.domain.model.MediaCategory
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.repository.MediaRepository
import javax.inject.Inject

class SearchMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(query: String): List<MediaItem> =
        mediaRepository.searchMediaWithOcr(query)

    suspend fun byCategory(category: MediaCategory): List<MediaItem> =
        mediaRepository.getMediaByCategory(category)
}
