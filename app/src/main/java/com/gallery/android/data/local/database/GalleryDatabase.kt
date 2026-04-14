package com.gallery.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gallery.android.data.local.database.dao.AlbumDao
import com.gallery.android.data.local.database.dao.FavoriteDao
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.entity.AlbumEntity
import com.gallery.android.data.local.entity.AlbumMediaCrossRef
import com.gallery.android.data.local.entity.FavoriteEntity
import com.gallery.android.data.local.entity.MediaEntity

@Database(
    entities = [
        MediaEntity::class,
        AlbumEntity::class,
        AlbumMediaCrossRef::class,
        FavoriteEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
    abstract fun favoriteDao(): FavoriteDao
}
