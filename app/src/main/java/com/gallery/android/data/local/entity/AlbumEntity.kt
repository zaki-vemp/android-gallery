package com.gallery.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isUserCreated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "album_media", primaryKeys = ["albumId", "mediaId"])
data class AlbumMediaCrossRef(
    val albumId: Long,
    val mediaId: Long,
)
