package com.gallery.android.data.local.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.gallery.android.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    fun getMediaPaged(): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media WHERE isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE bucketId = :bucketId AND isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    fun getMediaByBucket(bucketId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isFavorite = 1 AND isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isInTrash = 1 ORDER BY trashedAt DESC")
    fun getTrash(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isInSafe = 1 ORDER BY dateAdded DESC")
    fun getSafeMedia(): Flow<List<MediaEntity>>

    @Query("""SELECT * FROM media WHERE (name LIKE '%' || :query || '%') AND isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC""")
    suspend fun searchByName(query: String): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Update
    suspend fun update(media: MediaEntity)

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("UPDATE media SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE media SET isInTrash = 1, trashedAt = :trashedAt WHERE id = :id")
    suspend fun moveToTrash(id: Long, trashedAt: Long)

    @Query("UPDATE media SET isInTrash = 0, trashedAt = 0 WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media WHERE isInTrash = 1 AND trashedAt < :expiryTime")
    suspend fun deleteExpiredTrash(expiryTime: Long)

    @Query("UPDATE media SET isInSafe = 1, encryptedPath = :encryptedPath WHERE id = :id")
    suspend fun moveToSafe(id: Long, encryptedPath: String)

    @Query("UPDATE media SET isInSafe = 0, encryptedPath = '' WHERE id = :id")
    suspend fun restoreFromSafe(id: Long)

    @Query("UPDATE media SET name = :name, path = :path, dateModified = :dateModified WHERE id = :id")
    suspend fun renameMedia(id: Long, name: String, path: String, dateModified: Long)

    @Query("SELECT id FROM media")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM media WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<Long>)

    @Query("SELECT * FROM media WHERE isFavorite = 1 AND isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    suspend fun getFavoritesList(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE mimeType LIKE 'video/%' AND isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    suspend fun getVideosList(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE (lower(bucketName) LIKE '%screenshot%' OR lower(bucketName) LIKE '%screenrecord%' OR lower(bucketName) LIKE '%screen record%') AND isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    suspend fun getScreenshots(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE (lower(bucketName) LIKE '%' || lower(:keyword) || '%') AND isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    suspend fun searchByBucketKeyword(keyword: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE isInTrash = 0 AND isInSafe = 0 ORDER BY dateAdded DESC")
    suspend fun getAllMediaList(): List<MediaEntity>
}
