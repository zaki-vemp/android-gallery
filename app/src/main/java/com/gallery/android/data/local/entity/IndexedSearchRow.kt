package com.gallery.android.data.local.entity

data class IndexedSearchRow(
    val id: Long,
    val name: String,
    val path: String,
    val bucketName: String,
    val labels: String,
    val extractedText: String,
    val concepts: String,
)
