package com.gallery.android.domain.usecase

import androidx.paging.PagingData
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaListQuery
import com.gallery.android.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(query: MediaListQuery = MediaListQuery()): Flow<PagingData<MediaItem>> =
        mediaRepository.getMediaPaged(query)

    fun getAll(): Flow<List<MediaItem>> = mediaRepository.getAllMedia()
    fun getRecent(limit: Int = 24): Flow<List<MediaItem>> = mediaRepository.getRecentMedia(limit)
    fun getByBucket(bucketId: Long): Flow<List<MediaItem>> = mediaRepository.getMediaByBucket(bucketId)
    fun observeCount(query: MediaListQuery = MediaListQuery()) = mediaRepository.observeMediaCount(query)
    fun observeTimeline(query: MediaListQuery = MediaListQuery()) = mediaRepository.observeTimeline(query)
}
