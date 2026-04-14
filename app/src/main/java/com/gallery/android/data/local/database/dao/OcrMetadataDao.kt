package com.gallery.android.data.local.database.dao

import androidx.room.*
import com.gallery.android.data.local.entity.OcrMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: OcrMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadataList: List<OcrMetadataEntity>)

    @Query("SELECT * FROM ocr_metadata WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): OcrMetadataEntity?

    @Query("SELECT mediaId FROM ocr_metadata")
    suspend fun getAllScannedIds(): List<Long>

    @Query("""
        SELECT m.* FROM media m
        INNER JOIN ocr_metadata ocr ON m.id = ocr.mediaId
        WHERE ocr.extractedText LIKE '%' || :query || '%'
        AND m.isInTrash = 0 AND m.isInSafe = 0
        ORDER BY m.dateAdded DESC
    """)
    suspend fun searchByOcrText(query: String): List<com.gallery.android.data.local.entity.MediaEntity>

    @Query("""
        SELECT m.* FROM media m
        INNER JOIN ocr_metadata ocr ON m.id = ocr.mediaId
        WHERE ocr.category = :category
        AND m.isInTrash = 0 AND m.isInSafe = 0
        ORDER BY m.dateAdded DESC
    """)
    suspend fun getByCategory(category: String): List<com.gallery.android.data.local.entity.MediaEntity>

    @Query("DELETE FROM ocr_metadata WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM ocr_metadata WHERE mediaId NOT IN (:validIds)")
    suspend fun deleteOrphaned(validIds: List<Long>)
}
