package com.gallery.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gallery.android.data.local.database.dao.AlbumDao
import com.gallery.android.data.local.database.dao.FavoriteDao
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.database.dao.OcrMetadataDao
import com.gallery.android.data.local.entity.AlbumEntity
import com.gallery.android.data.local.entity.AlbumMediaCrossRef
import com.gallery.android.data.local.entity.FavoriteEntity
import com.gallery.android.data.local.entity.MediaEntity
import com.gallery.android.data.local.entity.OcrMetadataEntity

@Database(
    entities = [
        MediaEntity::class,
        AlbumEntity::class,
        AlbumMediaCrossRef::class,
        FavoriteEntity::class,
        OcrMetadataEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun ocrMetadataDao(): OcrMetadataDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ocr_metadata (
                        mediaId INTEGER NOT NULL PRIMARY KEY,
                        extractedText TEXT NOT NULL,
                        category TEXT NOT NULL,
                        scannedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ocr_metadata_mediaId ON ocr_metadata(mediaId)"
                )
            }
        }
    }
}
