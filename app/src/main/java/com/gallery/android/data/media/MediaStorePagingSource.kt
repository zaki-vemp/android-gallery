package com.gallery.android.data.media

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaListQuery
import com.gallery.android.nativeengine.NativeMediaEngine
import com.gallery.android.utils.DateUtils
import java.time.Instant
import java.time.ZoneId

class MediaStorePagingSource(
    private val dataSource: MediaStoreDataSource,
    private val query: MediaListQuery,
    private val hiddenIds: Set<Long>,
    private val favoriteIds: Set<Long>,
) : PagingSource<Int, MediaItem>() {

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(page.data.size) ?: page.nextKey?.minus(page.data.size)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        return runCatching {
            val offset = params.key ?: 0
            val pageSize = params.loadSize
            var items = dataSource.queryPage(query, hiddenIds, favoriteIds, offset, pageSize)
            if (query.motionPhotoOnly || query.editedOnly) {
                items = items.filter { item ->
                    val haystack = "${item.name} ${item.path} ${item.mimeType} ${item.bucketName}".lowercase()
                    when {
                        query.motionPhotoOnly ->
                            "motion" in haystack || "live photo" in haystack || "motionphoto" in haystack
                        query.editedOnly ->
                            "edit" in haystack || item.dateModified > item.dateAdded + 3600
                        else -> true
                    }
                }
            }
            LoadResult.Page(
                data = items,
                prevKey = if (offset == 0) null else (offset - pageSize).coerceAtLeast(0),
                nextKey = if (items.size < pageSize) null else offset + items.size,
            )
        }.getOrElse { LoadResult.Error(it) }
    }
}

fun buildTimelineAnchors(dates: List<Long>): List<com.gallery.android.domain.model.TimelineAnchor> {
    if (dates.isEmpty()) return emptyList()
    if (NativeMediaEngine.isAvailable) {
        val packed = NativeMediaEngine.buildTimeline(
            dates.toLongArray(),
            ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds,
        )
        val anchors = ArrayList<com.gallery.android.domain.model.TimelineAnchor>(packed.size / 2)
        var i = 0
        while (i + 1 < packed.size) {
            val gridIndex = packed[i]
            val dateIndex = packed[i + 1].coerceIn(0, dates.lastIndex)
            anchors += com.gallery.android.domain.model.TimelineAnchor(
                label = DateUtils.formatGroupDate(dates[dateIndex]),
                itemIndex = gridIndex,
            )
            i += 2
        }
        if (anchors.isNotEmpty()) return anchors
    }
    val anchors = ArrayList<com.gallery.android.domain.model.TimelineAnchor>()
    var index = 0
    var lastLabel: String? = null
    for (date in dates) {
        val label = DateUtils.formatGroupDate(date)
        if (label != lastLabel) {
            anchors += com.gallery.android.domain.model.TimelineAnchor(label, index)
            lastLabel = label
            index += 1
        }
        index += 1
    }
    return anchors
}
