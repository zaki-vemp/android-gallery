package com.gallery.android.domain.usecase

import com.gallery.android.domain.model.Album
import com.gallery.android.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAlbumsUseCase @Inject constructor(
    private val albumRepository: AlbumRepository
) {
    operator fun invoke(): Flow<List<Album>> = albumRepository.getAlbums()
}
