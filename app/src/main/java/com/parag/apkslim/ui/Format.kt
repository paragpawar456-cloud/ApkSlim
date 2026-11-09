package com.parag.apkslim.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatBytes(bytes: Long, signed: Boolean = false): String {
    val sign = when {
        !signed -> ""
        bytes > 0 -> "+"
        bytes < 0 -> "−"
        else -> ""
    }
    val b = abs(bytes).toDouble()
    val text = when {
        b >= 1024.0 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
        b >= 1024.0 * 1024 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024))
        b >= 1024.0 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "${b.toLong()} B"
    }
    return sign + text
}

fun formatPercent(p: Float): String = String.format(Locale.US, "%.1f%%", p)

fun formatDate(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
