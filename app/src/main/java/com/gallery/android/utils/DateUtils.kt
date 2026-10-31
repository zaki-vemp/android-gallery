package com.gallery.android.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {
    private val groupFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val viewerDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.getDefault())
    private val viewerTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val detailFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d yyyy • HH:mm", Locale.getDefault())

    fun formatGroupDate(timestamp: Long): String {
        val date = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> groupFormatter.format(date)
        }
    }

    fun formatViewerHeaderDate(timestamp: Long): String {
        val zoned = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
        val date = zoned.toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> viewerDateFormatter.format(date)
        }
    }

    fun formatViewerHeaderTime(timestamp: Long): String =
        viewerTimeFormatter.format(Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()))

    fun formatDetailDate(timestamp: Long): String =
        detailFormatter.format(Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()))

    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%d:%02d", minutes, seconds % 60)
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun trashExpiryTime(): Long = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
}
