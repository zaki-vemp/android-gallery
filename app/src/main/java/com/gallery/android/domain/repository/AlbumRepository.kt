package com.gallery.android.domain.repository

import com.gallery.android.domain.model.Album
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(): Flow<List<Album>>
    suspend fun createAlbum(name: String): Long
    suspend fun deleteAlbum(albumId: Long)
    suspend fun renameAlbum(albumId: Long, newName: String)
    suspend fun addMediaToAlbum(albumId: Long, mediaId: Long)
    suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long)
    fun getMediaForAlbum(albumId: Long): Flow<List<Long>>
}
