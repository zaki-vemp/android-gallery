package com.gallery.android.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.database.dao.OcrMetadataDao
import com.gallery.android.data.local.entity.MediaEntity
import com.gallery.android.data.media.MediaStoreDataSource
import com.gallery.android.data.media.MediaStoreObserver
import com.gallery.android.data.media.MediaStorePagingSource
import com.gallery.android.data.media.buildTimelineAnchors
import com.gallery.android.domain.model.MediaCategory
import com.gallery.android.ml.ConceptLexicon
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaListQuery
import com.gallery.android.domain.model.MediaType
import com.gallery.android.domain.model.TimelineAnchor
import com.gallery.android.domain.repository.MediaRepository
import com.gallery.android.utils.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val ocrMetadataDao: OcrMetadataDao,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaStoreObserver: MediaStoreObserver,
) : MediaRepository {

    private val mediaSyncMutex = Mutex()
    @Volatile
    private var hasSyncedMediaStore = false
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val overlayIds = combine(
        mediaDao.observeHiddenIds().distinctUntilChanged(),
        mediaDao.observeFavoriteIds().distinctUntilChanged(),
    ) { hidden, favorites ->
        hidden.toSet() to favorites.toSet()
    }
        .distinctUntilChanged()
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptySet<Long>() to emptySet())

    override fun getMediaPaged(query: MediaListQuery): Flow<PagingData<MediaItem>> {
        return overlayIds.flatMapLatest { (hidden, favorites) ->
            channelFlow {
                val factory = InvalidatingPagingSourceFactory {
                    MediaStorePagingSource(
                        dataSource = mediaStoreDataSource,
                        query = query,
                        hiddenIds = hidden,
                        favoriteIds = favorites,
                    )
                }
                val invalidations = launch {
                    mediaStoreObserver.changes
                        .filter { it != 0L }
                        .collect { factory.invalidate() }
                }
                try {
                    Pager(
                        config = PagingConfig(
                            pageSize = PAGE_SIZE,
                            prefetchDistance = 48,
                            initialLoadSize = PAGE_SIZE,
                            enablePlaceholders = false,
                            maxSize = 600,
                        ),
                        pagingSourceFactory = factory,
                    ).flow.collect { send(it) }
                } finally {
                    invalidations.cancel()
                }
            }
        }
    }

    override fun getAllMedia(): Flow<List<MediaItem>> =
        mediaDao.getAllMedia().map { list -> list.map { it.toDomain() } }

    override fun getRecentMedia(limit: Int): Flow<List<MediaItem>> {
        return overlayContext().mapLatest { (hidden, favorites) ->
            mediaStoreDataSource.queryPage(MediaListQuery(), hidden, favorites, 0, limit)
        }.flowOn(Dispatchers.IO)
    }

    override fun getMediaByBucket(bucketId: Long): Flow<List<MediaItem>> =
        getRecentForQuery(MediaListQuery(bucketId = bucketId), limit = 2_000)

    override fun getFavorites(): Flow<List<MediaItem>> {
        return overlayContext().mapLatest { (_, favorites) ->
            mediaStoreDataSource.queryByIds(favorites.toList(), favorites)
        }.flowOn(Dispatchers.IO)
    }

    override fun getTrash(): Flow<List<MediaItem>> =
        mediaDao.getTrash().map { list -> list.map { it.toDomain() } }

    override fun getSafeMedia(): Flow<List<MediaItem>> =
        mediaDao.getSafeMedia().map { list -> list.map { it.toDomain() } }

    override fun observeMediaCount(query: MediaListQuery): Flow<Int> {
        return overlayContext().mapLatest { (hidden, favorites) ->
            mediaStoreDataSource.count(query, hidden, favorites)
        }.flowOn(Dispatchers.IO)
    }

    override fun observeTimeline(query: MediaListQuery): Flow<List<TimelineAnchor>> {
        return overlayContext().mapLatest { (hidden, favorites) ->
            buildTimelineAnchors(mediaStoreDataSource.queryDateAdded(query, hidden, favorites))
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getMediaById(id: Long): MediaItem? = withContext(Dispatchers.IO) {
        val favorites = mediaDao.getFavoriteIds().toSet()
        mediaStoreDataSource.queryById(id, favorites)
            ?: mediaDao.getById(id)?.toDomain()
    }

    override suspend fun loadGalleryIndex(query: MediaListQuery): List<MediaItem> = withContext(Dispatchers.IO) {
        val hidden = mediaDao.getHiddenIds().toSet()
        val favorites = mediaDao.getFavoriteIds().toSet()
        val items = ArrayList<MediaItem>()
        var offset = 0
        while (true) {
            val page = mediaStoreDataSource.queryPage(query, hidden, favorites, offset, 400)
            if (page.isEmpty()) break
            items += page
            offset += page.size
            if (page.size < 400) break
        }
        items
    }

    override suspend fun searchMedia(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val hidden = mediaDao.getHiddenIds().toSet()
        val favorites = mediaDao.getFavoriteIds().toSet()
        mediaStoreDataSource.searchByName(query, hidden, favorites)
    }

    override suspend fun searchMediaWithOcr(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val terms = ConceptLexicon.parse(trimmed)
        val byName = searchMedia(trimmed)
        if (terms.isEmpty()) return@withContext byName

        val strongTerms = terms.filter { it.weight >= 0.8f }.ifEmpty { terms }
        val rows = ocrMetadataDao.getIndexedSearchRows()
        val scored = rows.mapNotNull { row ->
            val haystack = buildString {
                append(row.name); append(' ')
                append(row.path); append(' ')
                append(row.bucketName); append(' ')
                append(row.labels); append(' ')
                append(row.extractedText); append(' ')
                append(row.concepts)
            }.lowercase()
            var score = 0f
            var strongHits = 0
            terms.forEach { term ->
                val matchedAlias = term.aliases.firstOrNull { alias -> haystack.contains(alias) }
                if (matchedAlias != null) {
                    val exact = haystack.contains(term.word)
                    score += term.weight * if (exact) 3.2f else 1.6f
                    if (term.weight >= 0.8f) strongHits++
                }
            }
            if (strongHits == 0) return@mapNotNull null
            if (strongHits == strongTerms.size) score += 6f
            else score += strongHits * 1.4f
            row.id to score
        }.sortedByDescending { it.second }

        val rankedIds = scored.map { it.first }
        val byId = rankedIds.chunked(400)
            .flatMap { mediaDao.getByIds(it) }
            .associateBy { it.id }
        val semantic = rankedIds.mapNotNull { id -> byId[id]?.toDomain() }
        (semantic + byName).distinctBy { it.id }
    }

    override suspend fun getMediaByCategory(category: MediaCategory): List<MediaItem> = withContext(Dispatchers.IO) {
        val hidden = mediaDao.getHiddenIds().toSet()
        val favorites = mediaDao.getFavoriteIds().toSet()
        when (category) {
            MediaCategory.FAVORITES -> mediaStoreDataSource.queryByIds(favorites.toList(), favorites)
            MediaCategory.VIDEOS -> mediaStoreDataSource.queryPage(
                MediaListQuery(videosOnly = true), hidden, favorites, 0, 400,
            )
            MediaCategory.SCREENSHOTS -> mediaStoreDataSource.queryPage(
                MediaListQuery(screenshotsOnly = true), hidden, favorites, 0, 400,
            )
            else -> {
                val ocrResults = ocrMetadataDao.getByCategory(category.name).map { it.toDomain() }
                val bucketKeywords = when (category) {
                    MediaCategory.PEOPLE -> listOf("selfie", "portrait", "family", "people", "wedding", "graduation")
                    MediaCategory.DOCUMENTS -> listOf("document", "scan", "receipt", "invoice", "ticket", "pdf")
                    MediaCategory.FOOD -> listOf("food", "recipe", "restaurant", "cafe", "meal", "menu")
                    MediaCategory.TRAVEL -> listOf("travel", "trip", "vacation", "holiday", "tour", "flight")
                    MediaCategory.OTHERS -> emptyList()
                    else -> emptyList()
                }
                val bucketResults = bucketKeywords
                    .flatMap { kw -> mediaDao.searchByBucketKeyword(kw).map { it.toDomain() } }
                val merged = (bucketResults + ocrResults).distinctBy { it.id }
                if (merged.isNotEmpty() || category != MediaCategory.OTHERS) {
                    merged
                } else {
                    mediaStoreDataSource.queryPage(MediaListQuery(imagesOnly = true), hidden, favorites, 0, 200)
                }
            }
        }
    }

    override suspend fun toggleFavorite(mediaId: Long, isFavorite: Boolean) {
        ensureLocalRow(mediaId)
        mediaDao.updateFavorite(mediaId, isFavorite)
    }

    override suspend fun moveToTrash(mediaId: Long) {
        ensureLocalRow(mediaId)
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
        ensureLocalRow(mediaId)
        mediaDao.moveToSafe(mediaId, encryptedPath)
    }

    override suspend fun restoreFromSafe(mediaId: Long) {
        mediaDao.restoreFromSafe(mediaId)
    }

    override suspend fun renameMedia(mediaId: Long, newName: String) {
        val media = mediaDao.getById(mediaId) ?: mediaStoreDataSource.queryById(mediaId)?.toEntity()
            ?: error("Media not found")
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
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, normalizedName)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val updatedRows = context.contentResolver.update(uri, values, null, null)
        if (updatedRows <= 0) {
            error("Unable to rename media")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }

        val newPath = media.path.substringBeforeLast('/', missingDelimiterValue = media.path)
            .let { parent ->
                if (parent == media.path) normalizedName else "$parent/$normalizedName"
            }
        ensureLocalRow(mediaId)
        mediaDao.renameMedia(mediaId, normalizedName, newPath, System.currentTimeMillis() / 1000)
    }

    override suspend fun syncMediaStore(force: Boolean) {
        if (!force && hasSyncedMediaStore) return
        mediaSyncMutex.withLock {
            if (!force && hasSyncedMediaStore) return@withLock
            withContext(Dispatchers.IO) {
                val seen = ArrayList<Long>()
                var offset = 0
                while (true) {
                    val page = mediaStoreDataSource.queryPage(
                        query = MediaListQuery(),
                        hiddenIds = emptySet(),
                        favoriteIds = emptySet(),
                        offset = offset,
                        limit = 400,
                    )
                    if (page.isEmpty()) break
                    upsertPreservingFlags(page.map { it.toEntity() })
                    seen += page.map { it.id }
                    offset += page.size
                    if (page.size < 400) break
                }
                val protectedIds = mediaDao.getSafeIds().toSet()
                val keep = seen.toHashSet().apply { addAll(protectedIds) }
                val stale = mediaDao.getAllIds().filter { it !in keep }
                stale.chunked(400).forEach { chunk -> mediaDao.deleteByIds(chunk) }
            }
            hasSyncedMediaStore = true
        }
    }

    private fun getRecentForQuery(query: MediaListQuery, limit: Int): Flow<List<MediaItem>> {
        return overlayContext().mapLatest { (hidden, favorites) ->
            mediaStoreDataSource.queryPage(query, hidden, favorites, 0, limit)
        }.flowOn(Dispatchers.IO)
    }

    private fun overlayContext(): Flow<Pair<Set<Long>, Set<Long>>> {
        return overlayIds.flatMapLatest { overlay ->
            mediaStoreObserver.changes
                .filter { it != 0L }
                .map { overlay }
                .onStart { emit(overlay) }
        }
    }

    private suspend fun ensureLocalRow(mediaId: Long) {
        if (mediaDao.getById(mediaId) != null) return
        val item = mediaStoreDataSource.queryById(mediaId) ?: return
        mediaDao.insert(item.toEntity())
    }

    private suspend fun upsertPreservingFlags(incoming: List<MediaEntity>) {
        if (incoming.isEmpty()) return
        val existing = mediaDao.getByIds(incoming.map { it.id }).associateBy { it.id }
        val merged = incoming.map { item ->
            val prev = existing[item.id] ?: return@map item
            item.copy(
                isFavorite = prev.isFavorite,
                isInTrash = prev.isInTrash,
                trashedAt = prev.trashedAt,
                isInSafe = prev.isInSafe,
                encryptedPath = prev.encryptedPath,
            )
        }
        mediaDao.insertAll(merged)
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

    private fun MediaItem.toEntity(): MediaEntity = MediaEntity(
        id = id,
        uri = uri.toString(),
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
        mediaType = mediaType.name,
        isFavorite = isFavorite,
        isInTrash = isInTrash,
        trashedAt = trashedAt,
        isInSafe = isInSafe,
    )

    private companion object {
        const val PAGE_SIZE = 80
    }
}
