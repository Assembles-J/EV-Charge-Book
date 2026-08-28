package com.evchargebook.domain

import java.util.Locale

/** Compact, truthful Trip progress for foreground/lock-screen notification surfaces. */
object TripNotificationProgress {
    fun format(elapsedSeconds: Long, distanceMeters: Double): String {
        val safeSeconds = elapsedSeconds.coerceAtLeast(0L)
        val safeDistance = distanceMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        return "${formatDuration(safeSeconds)} · ${formatDistance(safeDistance)}"
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) {
            "${hours}小时${minutes}分"
        } else {
            "${minutes}分${remainingSeconds}秒"
        }
    }

    private fun formatDistance(meters: Double): String =
        if (meters >= 1000.0) {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        } else {
            String.format(Locale.US, "%.0f m", meters)
        }
}
