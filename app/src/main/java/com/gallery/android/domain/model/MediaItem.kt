package com.gallery.android.domain.model

import android.net.Uri

enum class MediaType { IMAGE, VIDEO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val duration: Long = 0L,
    val bucketId: Long = 0L,
    val bucketName: String = "",
    val mediaType: MediaType,
    val isFavorite: Boolean = false,
    val isInTrash: Boolean = false,
    val trashedAt: Long = 0L,
    val isInSafe: Boolean = false,
)
