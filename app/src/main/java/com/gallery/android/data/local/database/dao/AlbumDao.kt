package com.gallery.android.data.local.database.dao

import androidx.room.*
import com.gallery.android.data.local.entity.AlbumEntity
import com.gallery.android.data.local.entity.AlbumMediaCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Query("UPDATE albums SET name = :name WHERE id = :albumId")
    suspend fun renameAlbum(albumId: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaToAlbum(crossRef: AlbumMediaCrossRef)

    @Delete
    suspend fun removeMediaFromAlbum(crossRef: AlbumMediaCrossRef)

    @Query("SELECT mediaId FROM album_media WHERE albumId = :albumId")
    fun getMediaIdsForAlbum(albumId: Long): Flow<List<Long>>
}
