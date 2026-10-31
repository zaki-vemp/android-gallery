package com.gallery.android.ui.collage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.model.MediaType
import com.gallery.android.domain.usecase.GetMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.gallery.android.nativeengine.NativeMediaEngine

enum class CollageLayoutType {
    GRID_2_HORIZONTAL,
    GRID_2_VERTICAL,
    GRID_3_TOP_HERO,
    GRID_3_LEFT_HERO,
    GRID_4_SQUARE,
    GRID_6_GALLERY,
}

data class CollageUiState(
    val availableMedia: List<MediaItem> = emptyList(),
    val selectedMedia: List<MediaItem> = emptyList(),
    val selectedLayout: CollageLayoutType = CollageLayoutType.GRID_4_SQUARE,
    val cornerRadiusDp: Float = 12f,
    val spacingDp: Float = 8f,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
)

@HiltViewModel
class CollageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMediaUseCase: GetMediaUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollageUiState())
    val uiState: StateFlow<CollageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getMediaUseCase.getRecent(160).collect { media ->
                val imagesOnly = media.filter { it.mediaType == MediaType.IMAGE }
                _uiState.update { state ->
                    state.copy(
                        availableMedia = imagesOnly,
                        selectedMedia = if (state.selectedMedia.isEmpty()) imagesOnly.take(4) else state.selectedMedia
                    )
                }
            }
        }
    }

    fun toggleSelectMedia(item: MediaItem) {
        _uiState.update { state ->
            val current = state.selectedMedia.toMutableList()
            if (current.any { it.id == item.id }) {
                current.removeAll { it.id == item.id }
            } else {
                if (current.size < 6) {
                    current.add(item)
                }
            }
            val newLayout = when (current.size) {
                2 -> CollageLayoutType.GRID_2_HORIZONTAL
                3 -> CollageLayoutType.GRID_3_TOP_HERO
                4 -> CollageLayoutType.GRID_4_SQUARE
                5, 6 -> CollageLayoutType.GRID_6_GALLERY
                else -> state.selectedLayout
            }
            state.copy(selectedMedia = current, selectedLayout = newLayout)
        }
    }

    fun selectLayout(layout: CollageLayoutType) {
        _uiState.update { it.copy(selectedLayout = layout) }
    }

    fun setCornerRadius(radius: Float) {
        _uiState.update { it.copy(cornerRadiusDp = radius) }
    }

    fun setSpacing(spacing: Float) {
        _uiState.update { it.copy(spacingDp = spacing) }
    }

    fun exportCollage(onComplete: (String) -> Unit) {
        val state = _uiState.value
        if (state.selectedMedia.isEmpty()) return

        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val canvasSize = 1200
                val outputBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(outputBitmap)
                canvas.drawColor(AndroidColor.WHITE)

                val loader = ImageLoader(context)
                val loadedBitmaps = state.selectedMedia.map { media ->
                    val req = ImageRequest.Builder(context)
                        .data(media.uri)
                        .allowHardware(false)
                        .build()
                    val res = (loader.execute(req) as? SuccessResult)?.drawable
                    res?.let { drawableToBitmap(it) }
                }

                drawCollageLayout(
                    canvas = canvas,
                    outputBitmap = outputBitmap,
                    bitmaps = loadedBitmaps.filterNotNull(),
                    layoutType = state.selectedLayout,
                    width = canvasSize,
                    height = canvasSize,
                    spacingPx = state.spacingDp * 2f,
                    cornerRadiusPx = state.cornerRadiusDp * 2f
                )

                val savedUri = saveBitmapToMediaStore(context, outputBitmap)
                savedUri
            }.onSuccess { savedUri ->
                _uiState.update { it.copy(isExporting = false, exportSuccessMessage = "Collage saved to Pictures!") }
                withContext(Dispatchers.Main) {
                    onComplete("Collage saved successfully to Gallery!")
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isExporting = false) }
                withContext(Dispatchers.Main) {
                    onComplete("Failed to save collage: ${err.message}")
                }
            }
        }
    }

    private fun ensureSoftware(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(100),
            drawable.intrinsicHeight.coerceAtLeast(100),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun drawCollageLayout(
        canvas: Canvas,
        outputBitmap: Bitmap,
        bitmaps: List<Bitmap>,
        layoutType: CollageLayoutType,
        width: Int,
        height: Int,
        spacingPx: Float,
        cornerRadiusPx: Float
    ) {
        if (bitmaps.isEmpty()) return
        val count = bitmaps.size

        val rects = mutableListOf<RectF>()
        val halfW = width / 2f
        val halfH = height / 2f

        when (layoutType) {
            CollageLayoutType.GRID_2_HORIZONTAL -> {
                rects.add(RectF(spacingPx, spacingPx, halfW - spacingPx / 2, height - spacingPx))
                rects.add(RectF(halfW + spacingPx / 2, spacingPx, width - spacingPx, height - spacingPx))
            }
            CollageLayoutType.GRID_2_VERTICAL -> {
                rects.add(RectF(spacingPx, spacingPx, width - spacingPx, halfH - spacingPx / 2))
                rects.add(RectF(spacingPx, halfH + spacingPx / 2, width - spacingPx, height - spacingPx))
            }
            CollageLayoutType.GRID_3_TOP_HERO -> {
                rects.add(RectF(spacingPx, spacingPx, width - spacingPx, halfH - spacingPx / 2))
                rects.add(RectF(spacingPx, halfH + spacingPx / 2, halfW - spacingPx / 2, height - spacingPx))
                rects.add(RectF(halfW + spacingPx / 2, halfH + spacingPx / 2, width - spacingPx, height - spacingPx))
            }
            CollageLayoutType.GRID_3_LEFT_HERO -> {
                rects.add(RectF(spacingPx, spacingPx, halfW - spacingPx / 2, height - spacingPx))
                rects.add(RectF(halfW + spacingPx / 2, spacingPx, width - spacingPx, halfH - spacingPx / 2))
                rects.add(RectF(halfW + spacingPx / 2, halfH + spacingPx / 2, width - spacingPx, height - spacingPx))
            }
            CollageLayoutType.GRID_4_SQUARE -> {
                rects.add(RectF(spacingPx, spacingPx, halfW - spacingPx / 2, halfH - spacingPx / 2))
                rects.add(RectF(halfW + spacingPx / 2, spacingPx, width - spacingPx, halfH - spacingPx / 2))
                rects.add(RectF(spacingPx, halfH + spacingPx / 2, halfW - spacingPx / 2, height - spacingPx))
                rects.add(RectF(halfW + spacingPx / 2, halfH + spacingPx / 2, width - spacingPx, height - spacingPx))
            }
            CollageLayoutType.GRID_6_GALLERY -> {
                val thirdW = width / 3f
                rects.add(RectF(spacingPx, spacingPx, thirdW - spacingPx / 2, halfH - spacingPx / 2))
                rects.add(RectF(thirdW + spacingPx / 2, spacingPx, thirdW * 2 - spacingPx / 2, halfH - spacingPx / 2))
                rects.add(RectF(thirdW * 2 + spacingPx / 2, spacingPx, width - spacingPx, halfH - spacingPx / 2))
                rects.add(RectF(spacingPx, halfH + spacingPx / 2, thirdW - spacingPx / 2, height - spacingPx))
                rects.add(RectF(thirdW + spacingPx / 2, halfH + spacingPx / 2, thirdW * 2 - spacingPx / 2, height - spacingPx))
                rects.add(RectF(thirdW * 2 + spacingPx / 2, halfH + spacingPx / 2, width - spacingPx, height - spacingPx))
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (i in 0 until rects.size.coerceAtMost(count)) {
            val rect = rects[i]
            val bmp = ensureSoftware(bitmaps[i])
            val usedNative = NativeMediaEngine.isAvailable && NativeMediaEngine.blitCover(
                bmp,
                outputBitmap,
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt(),
            )
            if (!usedNative) {
                canvas.save()
                val path = android.graphics.Path().apply {
                    addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(path)
                val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                canvas.drawBitmap(bmp, srcRect, rect, paint)
                canvas.restore()
            }
        }
    }

    private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap): Uri? {
        val filename = "Collage_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Collages")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        MediaScannerConnection.scanFile(context, arrayOf(uri.path), arrayOf("image/jpeg"), null)
        return uri
    }
}
