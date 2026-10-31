package com.gallery.android.worker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.database.dao.OcrMetadataDao
import com.gallery.android.data.local.entity.OcrMetadataEntity
import com.gallery.android.ml.ConceptLexicon
import com.gallery.android.ml.TensorFlowImageClassifier
import com.gallery.android.utils.CategoryClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class OcrScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaDao: MediaDao,
    private val ocrMetadataDao: OcrMetadataDao,
) : CoroutineWorker(context, params) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build(),
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val classifier = TensorFlowImageClassifier(applicationContext)
        try {
            val batch = ocrMetadataDao.getPendingScanIds(BATCH_SIZE)
            if (batch.isEmpty()) return@withContext Result.success()

            for (mediaId in batch) {
                try {
                    indexOne(mediaId, classifier)
                } catch (_: Exception) {
                    // Retry this item on a later pass.
                }
            }

            val remaining = ocrMetadataDao.getPendingScanIds(1)
            if (remaining.isNotEmpty()) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.failure()
        } finally {
            classifier.close()
            recognizer.close()
            labeler.close()
        }
    }

    private suspend fun indexOne(mediaId: Long, classifier: TensorFlowImageClassifier) {
        val entity = mediaDao.getById(mediaId) ?: return
        if (!entity.mimeType.startsWith("image/")) {
            ocrMetadataDao.insert(
                OcrMetadataEntity(
                    mediaId = mediaId,
                    extractedText = "",
                    labels = "video",
                    concepts = "video",
                    category = CategoryClassifier.classify(
                        bucketName = entity.bucketName,
                        mimeType = entity.mimeType,
                    ).name,
                    scannedAt = System.currentTimeMillis(),
                    sourceModified = entity.dateModified,
                ),
            )
            return
        }

        val uri = Uri.parse(entity.uri)
        val bitmap = TensorFlowImageClassifier.decodeForModel(applicationContext, uri)
        val tfLabels = if (bitmap != null && classifier.isReady) {
            classifier.classify(bitmap)
        } else {
            emptyList()
        }
        val mlLabels = runMlLabels(uri, bitmap)
        val extractedText = runOcr(uri, bitmap)
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()

        val labels = (mlLabels + tfLabels).distinct()
        val concepts = ConceptLexicon.conceptsForLabels(labels)

        val category = CategoryClassifier.classify(
            bucketName = entity.bucketName,
            mimeType = entity.mimeType,
            ocrText = extractedText,
            labels = "$labels $concepts",
        )

        ocrMetadataDao.insert(
            OcrMetadataEntity(
                mediaId = mediaId,
                extractedText = extractedText,
                labels = labels.joinToString(", "),
                concepts = concepts,
                category = category.name,
                scannedAt = System.currentTimeMillis(),
                sourceModified = entity.dateModified,
            ),
        )
    }

    private suspend fun runMlLabels(uri: Uri, bitmap: Bitmap?): List<String> {
        val image = runCatching {
            if (bitmap != null) InputImage.fromBitmap(bitmap, 0)
            else InputImage.fromFilePath(applicationContext, uri)
        }.getOrNull() ?: return emptyList()
        return runCatching {
            labeler.process(image).await()
                .map { it.text.trim().lowercase() }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private suspend fun runOcr(uri: Uri, bitmap: Bitmap?): String {
        val image = runCatching {
            if (bitmap != null) InputImage.fromBitmap(bitmap, 0)
            else InputImage.fromFilePath(applicationContext, uri)
        }.getOrNull() ?: return ""
        return runCatching { recognizer.process(image).await().text }.getOrDefault("")
    }

    companion object {
        const val WORK_NAME = "ocr_scan_worker"
        private const val BATCH_SIZE = 12

        fun enqueue(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                WORK_NAME + "_once",
                ExistingWorkPolicy.KEEP,
                buildOneTimeRequest(),
            )
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodicRequest(),
            )
        }

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<OcrScanWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

        fun buildOneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<OcrScanWorker>()
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
    }
}
