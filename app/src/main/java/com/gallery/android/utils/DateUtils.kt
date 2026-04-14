package com.gallery.android.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateUtils {
    private fun startOfDay(offsetDays: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, offsetDays)
    }.timeInMillis

    fun formatGroupDate(timestamp: Long): String {
        val millis = timestamp * 1000L
        val today = startOfDay()
        val yesterday = startOfDay(-1)
        return when {
            millis >= today -> "Today"
            millis >= yesterday -> "Yesterday"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(millis))
        }
    }

    fun formatViewerHeaderDate(timestamp: Long): String {
        val millis = timestamp * 1000L
        val today = startOfDay()
        val tomorrow = startOfDay(1)
        val dayAfterTomorrow = startOfDay(2)
        return when {
            millis >= today && millis < tomorrow -> "Today"
            millis >= tomorrow && millis < dayAfterTomorrow -> "Tomorrow"
            else -> SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()).format(Date(millis))
        }
    }

    fun formatViewerHeaderTime(timestamp: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp * 1000L))

    fun formatDetailDate(timestamp: Long): String =
        SimpleDateFormat("EEE, MMM d yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp * 1000L))

    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        else String.format("%d:%02d", minutes, seconds % 60)
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun trashExpiryTime(): Long = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
}
