package com.gallery.android.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import android.util.Size
import com.gallery.android.nativeengine.NativeMediaEngine
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GridThumbnail(
    val uri: Uri,
    val id: Long,
    val isVideo: Boolean,
)

class GridThumbnailFetcher(
    private val data: GridThumbnail,
    private val context: Context,
    private val pixelSize: Int,
) : Fetcher {

    override suspend fun fetch(): DrawableResult? = withContext(Dispatchers.IO) {
        val bitmap = loadThumbnail(context, data, pixelSize) ?: return@withContext null
        DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<GridThumbnail> {
        override fun create(data: GridThumbnail, options: Options, imageLoader: ImageLoader): Fetcher {
            val requested = maxOf(
                options.size.width.pxOr(256),
                options.size.height.pxOr(256),
            ).coerceIn(96, 512)
            return GridThumbnailFetcher(data, options.context, requested)
        }
    }
}

private fun Dimension.pxOr(fallback: Int): Int = when (this) {
    is Dimension.Pixels -> px
    else -> fallback
}

internal fun loadThumbnail(context: Context, data: GridThumbnail, pixelSize: Int): Bitmap? {
    val stored = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(data.uri, Size(pixelSize, pixelSize), null)
        } else {
            @Suppress("DEPRECATION")
            if (data.isVideo) {
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    data.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null,
                )
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver,
                    data.id,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null,
                )
            }
        }
    }.getOrNull()
    if (stored != null || data.isVideo) return stored

    return runCatching {
        context.contentResolver.openFileDescriptor(data.uri, "r")?.use { pfd ->
            NativeMediaEngine.decodeThumbnail(pfd, pixelSize)
        }
    }.getOrNull()
}
