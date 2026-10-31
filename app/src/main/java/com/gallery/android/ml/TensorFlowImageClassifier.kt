package com.gallery.android.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.Closeable

/**
 * On-device EfficientNet-Lite0 classifier. Labels are cached in Room by the index worker.
 */
class TensorFlowImageClassifier(context: Context) : Closeable {

    private val classifier: ImageClassifier? = runCatching {
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setNumThreads(2).build())
            .setMaxResults(5)
            .setScoreThreshold(0.12f)
            .build()
        ImageClassifier.createFromFileAndOptions(context, MODEL_ASSET, options)
    }.getOrNull()

    val isReady: Boolean get() = classifier != null

    fun classify(bitmap: Bitmap): List<String> {
        val client = classifier ?: return emptyList()
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
        return runCatching {
            client.classify(TensorImage.fromBitmap(argb))
                .flatMap { it.categories }
                .sortedByDescending { it.score }
                .map { it.label.replace('_', ' ').trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList())
    }

    override fun close() {
        runCatching { classifier?.close() }
    }

    companion object {
        const val MODEL_ASSET = "efficientnet_lite0.tflite"
        private const val TARGET = 256

        fun decodeForModel(context: Context, uri: Uri): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return null
            val w = bounds.outWidth.coerceAtLeast(1)
            val h = bounds.outHeight.coerceAtLeast(1)
            var sample = 1
            while (w / sample > TARGET * 2 && h / sample > TARGET * 2) {
                sample *= 2
            }
            val decode = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decode)
            }
        }
    }
}
