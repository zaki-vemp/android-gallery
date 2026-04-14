package com.gallery.android.data.repository

import android.content.Context
import android.net.Uri
import com.gallery.android.data.local.database.dao.AlbumDao
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.entity.AlbumEntity
import com.gallery.android.data.local.entity.AlbumMediaCrossRef
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.repository.AlbumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
) : AlbumRepository {

    override fun getAlbums(): Flow<List<Album>> {
        val mediaFlow = mediaDao.getAllMedia()
        val albumsFlow = albumDao.getAllAlbums()

        return combine(mediaFlow, albumsFlow) { allMedia, dbAlbums ->
            val bucketAlbums = allMedia
                .filter { !it.isInTrash && !it.isInSafe }
                .groupBy { it.bucketId }
                .map { (bucketId, items) ->
                    val first = items.first()
                    Album(
                        id = bucketId,
                        name = first.bucketName.ifBlank { "Unknown" },
                        coverUri = Uri.parse(first.uri),
                        mediaCount = items.size,
                        bucketId = bucketId,
                        isUserCreated = false,
                    )
                }
                .sortedByDescending { it.mediaCount }

            val userAlbums = dbAlbums.map { entity ->
                Album(
                    id = entity.id,
                    name = entity.name,
                    coverUri = null,
                    mediaCount = 0,
                    isUserCreated = true,
                )
            }

            (bucketAlbums + userAlbums).distinctBy { it.name }
        }
    }

    override suspend fun createAlbum(name: String): Long =
        albumDao.insertAlbum(AlbumEntity(name = name))

    override suspend fun deleteAlbum(albumId: Long) = albumDao.deleteAlbum(albumId)

    override suspend fun renameAlbum(albumId: Long, newName: String) =
        albumDao.renameAlbum(albumId, newName)

    override suspend fun addMediaToAlbum(albumId: Long, mediaId: Long) =
        albumDao.addMediaToAlbum(AlbumMediaCrossRef(albumId, mediaId))

    override suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long) =
        albumDao.removeMediaFromAlbum(AlbumMediaCrossRef(albumId, mediaId))

    override fun getMediaForAlbum(albumId: Long): Flow<List<Long>> =
        albumDao.getMediaIdsForAlbum(albumId)
}
