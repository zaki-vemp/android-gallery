package com.gallery.android.domain.model

data class MediaListQuery(
    val bucketId: Long? = null,
    val restrictToIds: List<Long>? = null,
    val imagesOnly: Boolean = false,
    val videosOnly: Boolean = false,
    val screenshotsOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val motionPhotoOnly: Boolean = false,
    val editedOnly: Boolean = false,
)

data class TimelineAnchor(
    val label: String,
    val itemIndex: Int,
)
