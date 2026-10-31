package com.gallery.android.data.repository

import android.net.Uri
import com.gallery.android.data.local.database.dao.AlbumDao
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.entity.AlbumEntity
import com.gallery.android.data.local.entity.AlbumMediaCrossRef
import com.gallery.android.data.media.MediaStoreDataSource
import com.gallery.android.data.media.MediaStoreObserver
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.repository.AlbumRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaStoreObserver: MediaStoreObserver,
) : AlbumRepository {

    override fun getAlbums(): Flow<List<Album>> {
        val bucketAlbums = combine(
            mediaStoreObserver.changes,
            mediaDao.observeHiddenIds(),
        ) { _, hidden -> hidden.toSet() }
            .mapLatest { hidden -> mediaStoreDataSource.queryBucketAlbums(hidden) }
            .flowOn(Dispatchers.IO)

        val userAlbums = combine(
            albumDao.getAllAlbums(),
            albumDao.getAllAlbumMedia(),
            mediaDao.observeFavoriteIds(),
            mediaStoreObserver.changes,
        ) { dbAlbums, albumMedia, _, _ ->
            Triple(dbAlbums, albumMedia, Unit)
        }.mapLatest { (dbAlbums, albumMedia, _) ->
            val mediaIdsByAlbum = albumMedia.groupBy { it.albumId }
            dbAlbums.map { entity ->
                val ids = mediaIdsByAlbum[entity.id].orEmpty().map { it.mediaId }
                val cover = mediaStoreDataSource.queryByIds(ids.take(1)).firstOrNull()
                Album(
                    id = entity.id,
                    name = entity.name,
                    coverUri = cover?.uri,
                    mediaCount = ids.size,
                    isUserCreated = true,
                )
            }
        }.flowOn(Dispatchers.IO)

        return combine(bucketAlbums, userAlbums) { buckets, mine -> buckets + mine }
    }

    override fun getUserCreatedAlbums(): Flow<List<Album>> =
        albumDao.getAllAlbums().map { albums ->
            albums.map { entity ->
                Album(
                    id = entity.id,
                    name = entity.name,
                    coverUri = null,
                    mediaCount = 0,
                    isUserCreated = true,
                )
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
