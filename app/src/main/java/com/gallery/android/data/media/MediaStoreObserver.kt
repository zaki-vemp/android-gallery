package com.gallery.android.data.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pings = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    fun ping() {
        pings.tryEmit(SystemClock.elapsedRealtimeNanos())
    }

    @OptIn(FlowPreview::class)
    val changes: Flow<Long> = merge(
        pings,
        callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(SystemClock.elapsedRealtimeNanos())
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    trySend(SystemClock.elapsedRealtimeNanos())
                }
            }
            val resolver = context.contentResolver
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
            resolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
            awaitClose { resolver.unregisterContentObserver(observer) }
        },
    )
        .debounce(450)
        .onStart { emit(0L) }
        .distinctUntilChanged()
        .stateIn(
            scope = observerScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L,
        )
}
