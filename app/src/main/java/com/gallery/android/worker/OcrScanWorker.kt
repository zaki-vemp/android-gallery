package com.gallery.android.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gallery.android.data.local.database.dao.MediaDao
import com.gallery.android.data.local.database.dao.OcrMetadataDao
import com.gallery.android.data.local.entity.OcrMetadataEntity
import com.gallery.android.utils.CategoryClassifier
import com.google.mlkit.vision.common.InputImage
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val allMedia = mediaDao.getAllIds()
            val scannedIds = ocrMetadataDao.getAllScannedIds().toSet()
            val pending = allMedia.filter { it !in scannedIds }

            if (pending.isEmpty()) return@withContext Result.success()

            val batchSize = 20
            val batch = pending.take(batchSize)

            for (mediaId in batch) {
                try {
                    val entity = mediaDao.getById(mediaId) ?: continue
                    if (!entity.mimeType.startsWith("image/")) {
                        ocrMetadataDao.insert(
                            OcrMetadataEntity(
                                mediaId = mediaId,
                                extractedText = "",
                                category = CategoryClassifier.classify(
                                    bucketName = entity.bucketName,
                                    mimeType = entity.mimeType,
                                ).name,
                                scannedAt = System.currentTimeMillis(),
                            )
                        )
                        continue
                    }

                    val uri = Uri.parse(entity.uri)
                    val image = try {
                        InputImage.fromFilePath(applicationContext, uri)
                    } catch (e: Exception) {
                        null
                    }

                    val extractedText = if (image != null) {
                        try {
                            val result = recognizer.process(image).await()
                            result.text
                        } catch (e: Exception) {
                            ""
                        }
                    } else {
                        ""
                    }

                    val category = CategoryClassifier.classify(
                        bucketName = entity.bucketName,
                        mimeType = entity.mimeType,
                        ocrText = extractedText,
                    )

                    ocrMetadataDao.insert(
                        OcrMetadataEntity(
                            mediaId = mediaId,
                            extractedText = extractedText,
                            category = category.name,
                            scannedAt = System.currentTimeMillis(),
                        )
                    )
                } catch (e: Exception) {
                    // Skip individual failures silently; they will be retried next run
                }
            }

            if (pending.size > batchSize) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "ocr_scan_worker"

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<OcrScanWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(false)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

        fun buildOneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<OcrScanWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()
    }
}
