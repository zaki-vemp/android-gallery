package com.gallery.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: Long,
    val uri: String,
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
    val mediaType: String,
    val isFavorite: Boolean = false,
    val isInTrash: Boolean = false,
    val trashedAt: Long = 0L,
    val isInSafe: Boolean = false,
    val encryptedPath: String = "",
)
