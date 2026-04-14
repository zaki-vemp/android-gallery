package com.gallery.android.di

import com.gallery.android.data.repository.AlbumRepositoryImpl
import com.gallery.android.data.repository.MediaRepositoryImpl
import com.gallery.android.domain.repository.AlbumRepository
import com.gallery.android.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository
}
