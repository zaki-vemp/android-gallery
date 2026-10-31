package com.gallery.android.utils

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import com.gallery.android.nativeengine.NativeMediaEngine

data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val iso: String? = null,
    val exposureTime: String? = null,
    val flash: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val dateTime: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

object ExifUtils {

    fun getExifInfo(context: Context, uri: Uri): ExifInfo {
        val native = parseNative(context, uri)
        if (native != null && (native.make != null || native.model != null || native.width > 0 || native.iso != null)) {
            return native
        }
        return parsePlatform(context, uri)
    }

    private fun parseNative(context: Context, uri: Uri): ExifInfo? {
        if (!NativeMediaEngine.isAvailable) return null
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(128 * 1024)
                val read = input.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            } ?: return null
            val fields = NativeMediaEngine.parseExif(bytes) ?: return null
            ExifInfo(
                make = fields.getOrNull(0),
                model = fields.getOrNull(1),
                aperture = fields.getOrNull(2),
                focalLength = fields.getOrNull(3),
                iso = fields.getOrNull(4),
                exposureTime = fields.getOrNull(5),
                flash = fields.getOrNull(6),
                width = fields.getOrNull(7)?.toIntOrNull() ?: 0,
                height = fields.getOrNull(8)?.toIntOrNull() ?: 0,
                dateTime = fields.getOrNull(9),
                latitude = fields.getOrNull(10)?.toDoubleOrNull(),
                longitude = fields.getOrNull(11)?.toDoubleOrNull(),
            )
        }.getOrNull()
    }

    private fun parsePlatform(context: Context, uri: Uri): ExifInfo {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)
                val hasLatLong = exif.getLatLong(latLong)

                ExifInfo(
                    make = exif.getAttribute(ExifInterface.TAG_MAKE),
                    model = exif.getAttribute(ExifInterface.TAG_MODEL),
                    aperture = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
                        ?: exif.getAttribute(ExifInterface.TAG_F_NUMBER),
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { "$it mm" },
                    iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
                    exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let {
                        val sec = it.toDoubleOrNull()
                        if (sec != null && sec < 1.0 && sec > 0) "1/${(1.0 / sec).toInt()}s" else "${it}s"
                    },
                    flash = when (exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)) {
                        0 -> "Off"
                        1 -> "On"
                        else -> null
                    },
                    width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
                    height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0),
                    dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
                    latitude = if (hasLatLong) latLong[0].toDouble() else null,
                    longitude = if (hasLatLong) latLong[1].toDouble() else null,
                )
            }
        }.getOrNull() ?: ExifInfo()
    }
}
