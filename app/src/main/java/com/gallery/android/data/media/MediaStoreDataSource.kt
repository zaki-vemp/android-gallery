package com.gallery.android.data.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.net.Uri
import com.gallery.android.domain.model.Album
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaListQuery
import com.gallery.android.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val collection: Uri = MediaStore.Files.getContentUri("external")

    suspend fun queryPage(
        query: MediaListQuery,
        hiddenIds: Set<Long>,
        favoriteIds: Set<Long>,
        offset: Int,
        limit: Int,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val selection = buildSelection(query, hiddenIds, favoriteIds)
        queryMedia(
            selection = selection.first,
            args = selection.second,
            limit = limit,
            offset = offset,
            favoriteIds = favoriteIds,
        )
    }

    suspend fun queryById(id: Long, favoriteIds: Set<Long> = emptySet()): MediaItem? =
        withContext(Dispatchers.IO) {
            queryMedia(
                selection = "${MediaStore.Files.FileColumns._ID}=?",
                args = arrayOf(id.toString()),
                limit = 1,
                offset = 0,
                favoriteIds = favoriteIds,
            ).firstOrNull()
        }

    suspend fun queryByIds(ids: List<Long>, favoriteIds: Set<Long> = emptySet()): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            ids.chunked(400).flatMap { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                queryMedia(
                    selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders)",
                    args = chunk.map { it.toString() }.toTypedArray(),
                    limit = chunk.size,
                    offset = 0,
                    favoriteIds = favoriteIds,
                    sortByIds = chunk,
                )
            }
        }

    suspend fun searchByName(
        nameQuery: String,
        hiddenIds: Set<Long>,
        favoriteIds: Set<Long>,
        limit: Int = 200,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = nameQuery.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val hiddenClause = hiddenInClause(hiddenIds)
        queryMedia(
            selection = buildString {
                append(BASE_SELECTION)
                append(" AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
                if (hiddenClause != null) append(" AND $hiddenClause")
            },
            args = buildList {
                add("%$trimmed%")
                addAll(hiddenIds.map { it.toString() })
            }.toTypedArray(),
            limit = limit,
            offset = 0,
            favoriteIds = favoriteIds,
        )
    }

    suspend fun count(
        query: MediaListQuery,
        hiddenIds: Set<Long>,
        favoriteIds: Set<Long>,
    ): Int = withContext(Dispatchers.IO) {
        val selection = buildSelection(query, hiddenIds, favoriteIds)
        val projection = arrayOf("COUNT(${MediaStore.Files.FileColumns._ID})")
        context.contentResolver.query(
            collection,
            projection,
            selection.first,
            selection.second,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } ?: 0
    }

    suspend fun queryDateAdded(
        query: MediaListQuery,
        hiddenIds: Set<Long>,
        favoriteIds: Set<Long>,
    ): List<Long> = withContext(Dispatchers.IO) {
        val selection = buildSelection(query, hiddenIds, favoriteIds)
        val dates = ArrayList<Long>(512)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DATE_ADDED),
            selection.first,
            selection.second,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                dates.add(cursor.getLong(col))
            }
        }
        dates
    }

    suspend fun queryBucketAlbums(hiddenIds: Set<Long>): List<Album> = withContext(Dispatchers.IO) {
        val hiddenClause = hiddenInClause(hiddenIds)
        val selection = buildString {
            append(BASE_SELECTION)
            if (hiddenClause != null) append(" AND $hiddenClause")
        }
        val args = hiddenIds.map { it.toString() }.toTypedArray().let { if (it.isEmpty()) null else it }
        val buckets = LinkedHashMap<Long, MutableAlbum>()
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            ),
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdCol)
                val existing = buckets[bucketId]
                if (existing == null) {
                    val id = cursor.getLong(idCol)
                    val mediaType = cursor.getInt(typeCol)
                    buckets[bucketId] = MutableAlbum(
                        bucketId = bucketId,
                        name = cursor.getString(bucketNameCol).orEmpty().ifBlank { "Unknown" },
                        coverUri = uriFor(id, mediaType),
                        count = 1,
                    )
                } else {
                    existing.count++
                }
            }
        }
        buckets.values.map {
            Album(
                id = it.bucketId,
                name = it.name,
                coverUri = it.coverUri,
                mediaCount = it.count,
                bucketId = it.bucketId,
                isUserCreated = false,
            )
        }.sortedByDescending { it.mediaCount }
    }

    private fun queryMedia(
        selection: String,
        args: Array<String>?,
        limit: Int,
        offset: Int,
        favoriteIds: Set<Long>,
        sortByIds: List<Long>? = null,
    ): List<MediaItem> {
        val items = ArrayList<MediaItem>(limit.coerceAtMost(256))
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val extras = Bundle().apply {
                putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED))
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            }
            context.contentResolver.query(collection, PROJECTION, extras, null)
        } else {
            context.contentResolver.query(
                collection,
                PROJECTION,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT $limit OFFSET $offset",
            )
        }
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dataCol = it.getColumnIndex(MediaStore.MediaColumns.DATA)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dateModCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val typeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val mediaTypeFlag = it.getInt(typeCol)
                val mediaType = if (mediaTypeFlag == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    MediaType.VIDEO
                } else {
                    MediaType.IMAGE
                }
                items.add(
                    MediaItem(
                        id = id,
                        uri = uriFor(id, mediaTypeFlag),
                        name = it.getString(nameCol).orEmpty(),
                        path = if (dataCol >= 0) it.getString(dataCol).orEmpty() else "",
                        mimeType = it.getString(mimeCol) ?: if (mediaType == MediaType.VIDEO) "video/*" else "image/*",
                        size = it.getLong(sizeCol),
                        dateAdded = it.getLong(dateAddedCol),
                        dateModified = it.getLong(dateModCol),
                        width = it.getInt(widthCol),
                        height = it.getInt(heightCol),
                        duration = it.getLong(durationCol),
                        bucketId = it.getLong(bucketIdCol),
                        bucketName = it.getString(bucketNameCol).orEmpty(),
                        mediaType = mediaType,
                        isFavorite = id in favoriteIds,
                    )
                )
            }
        }
        if (sortByIds != null) {
            val order = sortByIds.withIndex().associate { it.value to it.index }
            items.sortBy { order[it.id] ?: Int.MAX_VALUE }
        }
        return items
    }

    private fun buildSelection(
        query: MediaListQuery,
        hiddenIds: Set<Long>,
        favoriteIds: Set<Long>,
    ): Pair<String, Array<String>?> {
        val clauses = mutableListOf(BASE_SELECTION)
        val args = mutableListOf<String>()

        when {
            query.videosOnly -> clauses += "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            query.imagesOnly || query.motionPhotoOnly || query.editedOnly ->
                clauses += "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
        }

        query.bucketId?.let {
            clauses += "${MediaStore.MediaColumns.BUCKET_ID}=?"
            args += it.toString()
        }

        if (query.screenshotsOnly) {
            clauses += "(" +
                "LOWER(${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME}) LIKE ? OR " +
                "LOWER(${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME}) LIKE ? OR " +
                "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?" +
                ")"
            args += "%screenshot%"
            args += "%screenrecord%"
            args += "%screenshot%"
        }

        val idFilter = when {
            query.favoritesOnly -> favoriteIds
            query.restrictToIds != null -> query.restrictToIds.toSet()
            else -> null
        }
        if (idFilter != null) {
            if (idFilter.isEmpty()) {
                clauses += "0"
            } else {
                clauses += "${MediaStore.Files.FileColumns._ID} IN (${idFilter.joinToString(",") { "?" }})"
                args += idFilter.map { it.toString() }
            }
        }

        hiddenInClause(hiddenIds)?.let { clause ->
            clauses += clause
            args += hiddenIds.map { it.toString() }
        }

        val argsArray = args.toTypedArray()
        return clauses.joinToString(" AND ") to argsArray.takeIf { it.isNotEmpty() }
    }

    private fun hiddenInClause(hiddenIds: Set<Long>): String? {
        if (hiddenIds.isEmpty()) return null
        val placeholders = hiddenIds.joinToString(",") { "?" }
        return "${MediaStore.Files.FileColumns._ID} NOT IN ($placeholders)"
    }

    private fun uriFor(id: Long, mediaType: Int): Uri {
        val base = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(base, id)
    }

    private data class MutableAlbum(
        val bucketId: Long,
        val name: String,
        val coverUri: Uri?,
        var count: Int,
    )

    companion object {
        private const val BASE_SELECTION =
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}" +
                " OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

        private val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
    }
}
