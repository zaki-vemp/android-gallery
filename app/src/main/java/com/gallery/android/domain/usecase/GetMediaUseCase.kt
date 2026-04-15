package com.gallery.android.domain.usecase

import androidx.paging.PagingData
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<PagingData<MediaItem>> = mediaRepository.getMediaPaged()
    fun getAll(): Flow<List<MediaItem>> = mediaRepository.getAllMedia()
    fun getByBucket(bucketId: Long): Flow<List<MediaItem>> = mediaRepository.getMediaByBucket(bucketId)
}
