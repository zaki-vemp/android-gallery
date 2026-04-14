package com.gallery.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_metadata",
    indices = [Index("mediaId")],
)
data class OcrMetadataEntity(
    @PrimaryKey val mediaId: Long,
    val extractedText: String,
    val category: String,
    val scannedAt: Long,
)
