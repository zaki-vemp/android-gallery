package com.gallery.android.domain.model

import android.net.Uri

data class Album(
    val id: Long,
    val name: String,
    val coverUri: Uri?,
    val mediaCount: Int,
    val bucketId: Long = 0L,
    val isUserCreated: Boolean = false,
)
