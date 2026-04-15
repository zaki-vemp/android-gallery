package com.gallery.android.domain.repository

import androidx.paging.PagingData
import com.gallery.android.domain.model.MediaCategory
import com.gallery.android.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getMediaPaged(): Flow<PagingData<MediaItem>>
    fun getAllMedia(): Flow<List<MediaItem>>
    fun getMediaByBucket(bucketId: Long): Flow<List<MediaItem>>
    fun getFavorites(): Flow<List<MediaItem>>
    fun getTrash(): Flow<List<MediaItem>>
    fun getSafeMedia(): Flow<List<MediaItem>>
    suspend fun searchMedia(query: String): List<MediaItem>
    suspend fun searchMediaWithOcr(query: String): List<MediaItem>
    suspend fun getMediaByCategory(category: MediaCategory): List<MediaItem>
    suspend fun toggleFavorite(mediaId: Long, isFavorite: Boolean)
    suspend fun moveToTrash(mediaId: Long)
    suspend fun restoreFromTrash(mediaId: Long)
    suspend fun deleteFromTrash(mediaId: Long)
    suspend fun cleanupExpiredTrash()
    suspend fun moveToSafe(mediaId: Long, encryptedPath: String)
    suspend fun restoreFromSafe(mediaId: Long)
    suspend fun renameMedia(mediaId: Long, newName: String)
    suspend fun syncMediaStore()
}
