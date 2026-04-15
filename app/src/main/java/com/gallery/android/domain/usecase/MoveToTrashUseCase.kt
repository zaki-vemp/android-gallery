package com.gallery.android.domain.usecase

import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MoveToTrashUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend fun moveToTrash(mediaId: Long) = mediaRepository.moveToTrash(mediaId)
    suspend fun restore(mediaId: Long) = mediaRepository.restoreFromTrash(mediaId)
    suspend fun deletePermanently(mediaId: Long) = mediaRepository.deleteFromTrash(mediaId)
    fun getTrash(): Flow<List<MediaItem>> = mediaRepository.getTrash()
    suspend fun cleanupExpired() = mediaRepository.cleanupExpiredTrash()
}
