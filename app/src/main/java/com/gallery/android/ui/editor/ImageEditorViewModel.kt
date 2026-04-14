package com.gallery.android.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

data class EditState(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val selectedFilter: ImageFilter = ImageFilter.NONE,
    val cropRect: CropRect? = null,
)

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

enum class ImageFilter(val displayName: String) {
    NONE("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    DRAMATIC("Dramatic"),
    MONO("Mono"),
    FADE("Fade"),
    CHROME("Chrome"),
}

data class EditorUiState(
    val originalBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val editState: EditState = EditState(),
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val savedUri: Uri? = null,
    val errorMessage: String? = null,
    val activeTab: EditorTab = EditorTab.ADJUST,
)

enum class EditorTab { ADJUST, FILTERS, CROP }

@HiltViewModel
class ImageEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var sourceUri: Uri? = null

    fun loadImage(uri: Uri) {
        sourceUri = uri
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val bitmap = withContext(Dispatchers.IO) { decodeBitmapSampled(context, uri, 1024, 1024) }
            if (bitmap != null) {
                _uiState.value = _uiState.value.copy(
                    originalBitmap = bitmap,
                    previewBitmap = bitmap,
                    isProcessing = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Unable to load image",
                )
            }
        }
    }

    fun updateBrightness(value: Float) = applyEdit { it.copy(brightness = value) }
    fun updateContrast(value: Float) = applyEdit { it.copy(contrast = value) }
    fun updateSaturation(value: Float) = applyEdit { it.copy(saturation = value) }
    fun rotateLeft() = applyEdit { it.copy(rotation = (it.rotation - 90f + 360f) % 360f) }
    fun rotateRight() = applyEdit { it.copy(rotation = (it.rotation + 90f) % 360f) }
    fun flipHorizontal() = applyEdit { it.copy(flipHorizontal = !it.flipHorizontal) }
    fun flipVertical() = applyEdit { it.copy(flipVertical = !it.flipVertical) }
    fun selectFilter(filter: ImageFilter) = applyEdit { it.copy(selectedFilter = filter) }
    fun updateCrop(cropRect: CropRect) = applyEdit { it.copy(cropRect = cropRect) }
    fun setActiveTab(tab: EditorTab) { _uiState.value = _uiState.value.copy(activeTab = tab) }

    fun resetEdits() {
        val original = _uiState.value.originalBitmap
        _uiState.value = _uiState.value.copy(
            editState = EditState(),
            previewBitmap = original,
        )
    }

    private fun applyEdit(transform: (EditState) -> EditState) {
        val newState = transform(_uiState.value.editState)
        _uiState.value = _uiState.value.copy(editState = newState)
        regeneratePreview(newState)
    }

    private fun regeneratePreview(editState: EditState) {
        val original = _uiState.value.originalBitmap ?: return
        viewModelScope.launch {
            val processed = withContext(Dispatchers.Default) {
                applyEditsTobitmap(original, editState)
            }
            _uiState.value = _uiState.value.copy(previewBitmap = processed)
        }
    }

    fun saveEditedImage(onComplete: (Uri?) -> Unit) {
        val original = _uiState.value.originalBitmap ?: return
        val editState = _uiState.value.editState
        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val processed = applyEditsTobitmap(original, editState)
                    saveBitmapToGallery(context, processed)
                }
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
            result.onSuccess { uri ->
                _uiState.value = _uiState.value.copy(savedUri = uri)
                onComplete(uri)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.message)
                onComplete(null)
            }
        }
    }

    private fun applyEditsTobitmap(source: Bitmap, editState: EditState): Bitmap {
        var bmp = source

        // Apply crop
        editState.cropRect?.let { crop ->
            val w = bmp.width
            val h = bmp.height
            val left = (crop.left * w).toInt().coerceIn(0, w - 1)
            val top = (crop.top * h).toInt().coerceIn(0, h - 1)
            val right = (crop.right * w).toInt().coerceIn(left + 1, w)
            val bottom = (crop.bottom * h).toInt().coerceIn(top + 1, h)
            bmp = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
        }

        // Apply rotation / flip
        if (editState.rotation != 0f || editState.flipHorizontal || editState.flipVertical) {
            val matrix = Matrix().apply {
                postRotate(editState.rotation, bmp.width / 2f, bmp.height / 2f)
                if (editState.flipHorizontal) postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
                if (editState.flipVertical) postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f)
            }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }

        // Apply color adjustments + filter
        val colorMatrix = buildColorMatrix(editState)
        val output = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(bmp, 0f, 0f, paint)

        return output
    }

    private fun buildColorMatrix(editState: EditState): ColorMatrix {
        val result = ColorMatrix()

        // Brightness: add offset to R,G,B channels
        val brightness = editState.brightness * 100f
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        // Contrast
        val contrast = editState.contrast
        val translate = (1f - contrast) * 128f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        // Saturation
        val saturationMatrix = ColorMatrix().apply { setSaturation(editState.saturation) }

        result.postConcat(brightnessMatrix)
        result.postConcat(contrastMatrix)
        result.postConcat(saturationMatrix)

        // Apply filter on top
        when (editState.selectedFilter) {
            ImageFilter.VIVID -> result.postConcat(ColorMatrix().apply {
                setSaturation(1.6f)
                val cm = floatArrayOf(
                    1.1f, 0f, 0f, 0f, 5f,
                    0f, 1.1f, 0f, 0f, 5f,
                    0f, 0f, 1.1f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f,
                )
                postConcat(ColorMatrix(cm))
            })
            ImageFilter.WARM -> result.postConcat(ColorMatrix(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ))
            ImageFilter.COOL -> result.postConcat(ColorMatrix(
                floatArrayOf(
                    0.8f, 0f, 0f, 0f, -10f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ))
            ImageFilter.DRAMATIC -> {
                result.postConcat(ColorMatrix().apply { setSaturation(0.8f) })
                result.postConcat(ColorMatrix(
                    floatArrayOf(
                        1.3f, 0f, 0f, 0f, -20f,
                        0f, 1.3f, 0f, 0f, -20f,
                        0f, 0f, 1.3f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                ))
            }
            ImageFilter.MONO -> result.postConcat(ColorMatrix().apply { setSaturation(0f) })
            ImageFilter.FADE -> result.postConcat(ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, 25f,
                    0f, 0.9f, 0f, 0f, 25f,
                    0f, 0f, 0.9f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ))
            ImageFilter.CHROME -> {
                result.postConcat(ColorMatrix().apply { setSaturation(1.4f) })
                result.postConcat(ColorMatrix(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, 0f,
                        0f, 1.05f, 0f, 0f, 0f,
                        0f, 0f, 0.9f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                ))
            }
            ImageFilter.NONE -> Unit
        }

        return result
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri {
        val filename = "edited_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AndroidGallery/Edited")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, contentValues)
            ?: throw IOException("Unable to create output file")
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "JPEG encode failed" }
        } ?: throw IOException("Unable to open output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return uri
    }

    private fun decodeBitmapSampled(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
