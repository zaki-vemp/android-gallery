package com.gallery.android.di

import android.content.Context
import androidx.room.Room
import com.gallery.android.data.local.database.GalleryDatabase
import com.gallery.android.data.local.database.dao.AlbumDao
import com.gallery.android.data.local.database.dao.FavoriteDao
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.database.dao.OcrMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GalleryDatabase =
        Room.databaseBuilder(context, GalleryDatabase::class.java, "gallery.db")
            .addMigrations(GalleryDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMediaDao(db: GalleryDatabase): MediaDao = db.mediaDao()

    @Provides
    fun provideAlbumDao(db: GalleryDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideFavoriteDao(db: GalleryDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideOcrMetadataDao(db: GalleryDatabase): OcrMetadataDao = db.ocrMetadataDao()
}
