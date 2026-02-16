package com.prantiux.pixelgallery.ui.screens

import com.prantiux.pixelgallery.model.MediaGroup
import com.prantiux.pixelgallery.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun groupMediaByDate(media: List<MediaItem>): List<MediaGroup> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    return media.groupBy { item ->
        calendar.timeInMillis = item.dateAdded * 1000
        dateFormat.format(calendar.time)
    }.map { (date, items) ->
        calendar.timeInMillis = items.first().dateAdded * 1000
        val itemYear = calendar.get(Calendar.YEAR)

        val displayDate = when {
            isToday(calendar) -> "Today"
            isYesterday(calendar) -> "Yesterday"
            itemYear == currentYear -> {
                // Same year: "12 Dec"
                SimpleDateFormat("d MMM", Locale.getDefault()).format(calendar.time)
            }
            else -> {
                // Different year: "28 Jan 2024"
                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(calendar.time)
            }
        }

        // Find most common location for this date
        val mostCommonLocation = items
            .mapNotNull { it.location }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        MediaGroup(date, displayDate, items, mostCommonLocation)
    }.sortedByDescending { it.date }
}

fun groupMediaByMonth(media: List<MediaItem>): List<MediaGroup> {
    val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val calendar = Calendar.getInstance()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    return media.groupBy { item ->
        calendar.timeInMillis = item.dateAdded * 1000
        monthFormat.format(calendar.time)
    }.map { (month, items) ->
        calendar.timeInMillis = items.first().dateAdded * 1000
        val itemYear = calendar.get(Calendar.YEAR)

        val displayDate = if (itemYear == currentYear) {
            // Current year: "January"
            SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
        } else {
            // Different year: "December 2025"
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        }

        // Find most common location for this month
        val mostCommonLocation = items
            .mapNotNull { it.location }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        MediaGroup(month, displayDate, items, mostCommonLocation)
    }.sortedByDescending { it.date }
}

fun isToday(calendar: Calendar): Boolean {
    val today = Calendar.getInstance()
    return calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

fun isYesterday(calendar: Calendar): Boolean {
    val yesterday = Calendar.getInstance()
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
}
