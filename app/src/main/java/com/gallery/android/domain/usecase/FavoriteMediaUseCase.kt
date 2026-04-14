package com.gallery.android.domain.usecase

import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FavoriteMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    fun getFavorites(): Flow<List<MediaItem>> = mediaRepository.getFavorites()
    suspend fun toggleFavorite(mediaId: Long, isFavorite: Boolean) =
        mediaRepository.toggleFavorite(mediaId, isFavorite)
}
