package com.prantiux.pixelgallery.model

/**
 * Represents an item in the main photos grid, which can be either a date header or a media item.
 * This is used to seamlessly integrate sticky/interleaved headers with Paging 3's PagingData.
 */
sealed class MediaGridItem {
    @androidx.compose.runtime.Immutable
    data class Header(
        val displayDate: String,
        val dateGroupKey: String // Can be day (yyyy-MM-dd) or month (yyyy-MM) depending on grid type
    ) : MediaGridItem()

    @androidx.compose.runtime.Immutable
    data class Media(
        val mediaItem: MediaItem
    ) : MediaGridItem()
}
