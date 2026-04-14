package com.gallery.android.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.entity.MediaEntity
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.domain.repository.MediaRepository
import com.gallery.android.utils.DateUtils
import com.gallery.android.utils.MediaUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
) : MediaRepository {

    override fun getMediaPaged(): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = false),
        pagingSourceFactory = { mediaDao.getMediaPaged() }
    ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun getAllMedia(): Flow<List<MediaItem>> =
        mediaDao.getAllMedia().map { list -> list.map { it.toDomain() } }

    override fun getMediaByBucket(bucketId: Long): Flow<List<MediaItem>> =
        mediaDao.getMediaByBucket(bucketId).map { list -> list.map { it.toDomain() } }

    override fun getFavorites(): Flow<List<MediaItem>> =
        mediaDao.getFavorites().map { list -> list.map { it.toDomain() } }

    override fun getTrash(): Flow<List<MediaItem>> =
        mediaDao.getTrash().map { list -> list.map { it.toDomain() } }

    override fun getSafeMedia(): Flow<List<MediaItem>> =
        mediaDao.getSafeMedia().map { list -> list.map { it.toDomain() } }

    override suspend fun searchMedia(query: String): List<MediaItem> =
        mediaDao.searchByName(query).map { it.toDomain() }

    override suspend fun toggleFavorite(mediaId: Long, isFavorite: Boolean) {
        mediaDao.updateFavorite(mediaId, isFavorite)
    }

    override suspend fun moveToTrash(mediaId: Long) {
        mediaDao.moveToTrash(mediaId, System.currentTimeMillis())
    }

    override suspend fun restoreFromTrash(mediaId: Long) {
        mediaDao.restoreFromTrash(mediaId)
    }

    override suspend fun deleteFromTrash(mediaId: Long) {
        mediaDao.deleteById(mediaId)
    }

    override suspend fun cleanupExpiredTrash() {
        mediaDao.deleteExpiredTrash(DateUtils.trashExpiryTime())
    }

    override suspend fun moveToSafe(mediaId: Long, encryptedPath: String) {
        mediaDao.moveToSafe(mediaId, encryptedPath)
    }

    override suspend fun restoreFromSafe(mediaId: Long) {
        mediaDao.restoreFromSafe(mediaId)
    }

    override suspend fun renameMedia(mediaId: Long, newName: String) {
        val media = mediaDao.getById(mediaId) ?: error("Media not found")
        val trimmedName = newName.trim()
        require(trimmedName.isNotEmpty()) { "Name cannot be empty" }

        val extension = media.name.substringAfterLast('.', missingDelimiterValue = "")
        val normalizedName = if ('.' in trimmedName || extension.isBlank()) {
            trimmedName
        } else {
            "$trimmedName.$extension"
        }

        val uri = Uri.parse(media.uri)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, normalizedName)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val updatedRows = context.contentResolver.update(uri, values, null, null)
        if (updatedRows <= 0) {
            error("Unable to rename media")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }

        val newPath = media.path.substringBeforeLast('/', missingDelimiterValue = media.path)
            .let { parent ->
                if (parent == media.path) normalizedName else "$parent/$normalizedName"
            }
        mediaDao.renameMedia(mediaId, normalizedName, newPath, System.currentTimeMillis() / 1000)
    }

    override suspend fun syncMediaStore() {
        val deviceMedia = MediaUtils.queryAllMedia(context)
        mediaDao.insertAll(deviceMedia)
        val deviceIds = deviceMedia.map { it.id }.toSet()
        val dbIds = mediaDao.getAllIds().toSet()
        val toRemove = dbIds - deviceIds
        if (toRemove.isNotEmpty()) {
            mediaDao.deleteNotIn(deviceIds.toList())
        }
    }

    private fun MediaEntity.toDomain(): MediaItem = MediaItem(
        id = id,
        uri = Uri.parse(uri),
        name = name,
        path = path,
        mimeType = mimeType,
        size = size,
        dateAdded = dateAdded,
        dateModified = dateModified,
        width = width,
        height = height,
        duration = duration,
        bucketId = bucketId,
        bucketName = bucketName,
        mediaType = if (mediaType == MediaType.VIDEO.name) MediaType.VIDEO else MediaType.IMAGE,
        isFavorite = isFavorite,
        isInTrash = isInTrash,
        trashedAt = trashedAt,
        isInSafe = isInSafe,
    )
}
