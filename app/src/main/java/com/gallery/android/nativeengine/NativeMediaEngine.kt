package com.gallery.android.nativeengine

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor

object NativeMediaEngine {
    val isAvailable: Boolean = try {
        System.loadLibrary("gallery_native")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic
    external fun decodeThumbnail(fd: Int, maxSize: Int): Bitmap?

    @JvmStatic
    external fun parseExif(bytes: ByteArray): Array<String?>?

    @JvmStatic
    external fun buildTimeline(dates: LongArray, zoneOffsetSeconds: Int): IntArray

    @JvmStatic
    external fun applyColorMatrix(bitmap: Bitmap, matrix: FloatArray): Boolean

    @JvmStatic
    external fun blitCover(
        src: Bitmap,
        dst: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean

    fun decodeThumbnail(pfd: ParcelFileDescriptor, maxSize: Int): Bitmap? {
        if (!isAvailable || Build.VERSION.SDK_INT < 30) return null
        return runCatching { decodeThumbnail(pfd.fd, maxSize) }.getOrNull()
    }
}
