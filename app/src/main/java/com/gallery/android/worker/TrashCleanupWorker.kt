package com.gallery.android.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gallery.android.domain.usecase.MoveToTrashUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val moveToTrashUseCase: MoveToTrashUseCase,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            moveToTrashUseCase.cleanupExpired()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
