package com.gallery.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.gallery.android.worker.TrashCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GalleryApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            // Register explicitly so content:// MediaStore videos always resolve to a frame,
            // including on providers that don't expose a useful file extension.
            add(VideoFrameDecoder.Factory())
        }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        scheduleTrashCleanup()
    }

    private fun scheduleTrashCleanup() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trash_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
