package com.prantiux.pixelgallery.search

import com.prantiux.pixelgallery.model.MediaItem
import java.util.*

/**
 * Smart search engine for Pixel Gallery
 * Supports: file name, album, media type, date, and size filtering
 */
object SearchEngine {
    
    /**
     * Search results with priority ordering
     */
    data class SearchResult(
        val matchedAlbums: List<AlbumMatch>,
        val matchedMedia: List<MediaItem>,
        val query: String
    )
    
    data class AlbumMatch(
        val albumName: String,
        val items: List<MediaItem>,
        val matchPriority: Int // Lower is higher priority
    )
    
    /**
     * Main search function with priority ordering:
     * 1. Album/Folder name match
     * 2. Date-based keyword match
     * 3. Media type match
     * 4. File name match
     * 5. Size filter match
     */
    fun search(query: String, allMedia: List<MediaItem>): SearchResult {
        if (query.isBlank()) {
            return SearchResult(emptyList(), emptyList(), query)
        }
        
        val normalizedQuery = query.lowercase(Locale.getDefault()).trim()
        
        // Detect search type and filters
        val dateFilter = detectDateFilter(normalizedQuery)
        val typeFilter = detectMediaTypeFilter(normalizedQuery)
        val sizeFilter = detectSizeFilter(normalizedQuery)
        val remainingQuery = removeFilterKeywords(normalizedQuery)
        
        // Priority 1: Album/Folder name match
        val albumMatches = if (remainingQuery.isNotEmpty()) {
            findMatchingAlbums(remainingQuery, allMedia, dateFilter, typeFilter, sizeFilter)
        } else {
            emptyList()
        }
        
        // Priority 2-5: Media item matches
        var matchedMedia = allMedia.toList()
        
        // Apply filters in order
        if (dateFilter != null) {
            matchedMedia = applyDateFilter(matchedMedia, dateFilter)
        }
        
        if (typeFilter != null) {
            matchedMedia = applyMediaTypeFilter(matchedMedia, typeFilter)
        }
        
        if (sizeFilter != null) {
            matchedMedia = applySizeFilter(matchedMedia, sizeFilter)
        }
        
        if (remainingQuery.isNotEmpty()) {
            matchedMedia = applyFileNameFilter(matchedMedia, remainingQuery)
        }
        
        return SearchResult(albumMatches, matchedMedia, query)
    }
    
    /**
     * Detect date-based filters
     */
    private fun detectDateFilter(query: String): DateFilter? {
        return when {
            query.contains("today") -> DateFilter.Today
            query.contains("yesterday") -> DateFilter.Yesterday
            query.contains("this week") -> DateFilter.ThisWeek
            query.contains("last week") -> DateFilter.LastWeek
            query.contains("this month") -> DateFilter.ThisMonth
            query.contains("last month") -> DateFilter.LastMonth
            query.matches(Regex(".*\\b(202[0-9]|201[0-9])\\b.*")) -> {
                val year = Regex("\\b(202[0-9]|201[0-9])\\b").find(query)?.value?.toInt()
                if (year != null) DateFilter.Year(year) else null
            }
            query.contains("january") || query.contains("jan ") -> DateFilter.Month(Calendar.JANUARY)
            query.contains("february") || query.contains("feb ") -> DateFilter.Month(Calendar.FEBRUARY)
            query.contains("march") || query.contains("mar ") -> DateFilter.Month(Calendar.MARCH)
            query.contains("april") || query.contains("apr ") -> DateFilter.Month(Calendar.APRIL)
            query.contains("may") -> DateFilter.Month(Calendar.MAY)
            query.contains("june") || query.contains("jun ") -> DateFilter.Month(Calendar.JUNE)
            query.contains("july") || query.contains("jul ") -> DateFilter.Month(Calendar.JULY)
            query.contains("august") || query.contains("aug ") -> DateFilter.Month(Calendar.AUGUST)
            query.contains("september") || query.contains("sep ") -> DateFilter.Month(Calendar.SEPTEMBER)
            query.contains("october") || query.contains("oct ") -> DateFilter.Month(Calendar.OCTOBER)
            query.contains("november") || query.contains("nov ") -> DateFilter.Month(Calendar.NOVEMBER)
            query.contains("december") || query.contains("dec ") -> DateFilter.Month(Calendar.DECEMBER)
            else -> null
        }
    }
    
    /**
     * Detect media type filters
     */
    private fun detectMediaTypeFilter(query: String): MediaTypeFilter? {
        return when {
            query.contains("video") -> MediaTypeFilter.Videos
            query.contains("photo") || query.contains("image") -> MediaTypeFilter.Photos
            query.contains("gif") -> MediaTypeFilter.Gifs
            query.contains("screenshot") -> MediaTypeFilter.Screenshots
            query.contains("camera") -> MediaTypeFilter.Camera
            else -> null
        }
    }
    
    /**
     * Detect size filters
     */
    private fun detectSizeFilter(query: String): SizeFilter? {
        return when {
            query.contains("small") -> SizeFilter.Small
            query.contains("medium") -> SizeFilter.Medium
            query.contains("large") -> SizeFilter.Large
            else -> null
        }
    }
    
    /**
     * Remove filter keywords to get the actual search query
     */
    private fun removeFilterKeywords(query: String): String {
        var cleaned = query
        
        // Remove date keywords
        val dateKeywords = listOf(
            "today", "yesterday", "this week", "last week", "this month", "last month",
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
        )
        dateKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        // Remove year patterns
        cleaned = cleaned.replace(Regex("\\b(202[0-9]|201[0-9])\\b"), "")
        
        // Remove type keywords
        val typeKeywords = listOf("photo", "photos", "image", "images", "video", "videos", "gif", "gifs")
        typeKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        // Remove size keywords
        val sizeKeywords = listOf("small", "medium", "large")
        sizeKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        // Remove common words
        val commonWords = listOf("from", "in", "on", "the", "a", "an")
        commonWords.forEach { cleaned = cleaned.replace(Regex("\\b$it\\b"), "") }
        
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }
    
    /**
     * Find albums matching the query
     */
    private fun findMatchingAlbums(
        query: String,
        allMedia: List<MediaItem>,
        dateFilter: DateFilter?,
        typeFilter: MediaTypeFilter?,
        sizeFilter: SizeFilter?
    ): List<AlbumMatch> {
        val albumGroups = allMedia.groupBy { it.bucketName }
        
        return albumGroups.mapNotNull { (albumName, items) ->
            if (albumName.lowercase(Locale.getDefault()).contains(query)) {
                var filteredItems = items
                
                if (dateFilter != null) filteredItems = applyDateFilter(filteredItems, dateFilter)
                if (typeFilter != null) filteredItems = applyMediaTypeFilter(filteredItems, typeFilter)
                if (sizeFilter != null) filteredItems = applySizeFilter(filteredItems, sizeFilter)
                
                if (filteredItems.isNotEmpty()) {
                    AlbumMatch(albumName, filteredItems, 1)
                } else null
            } else null
        }.sortedBy { it.matchPriority }
    }
    
    /**
     * Apply date filter
     */
    private fun applyDateFilter(media: List<MediaItem>, filter: DateFilter): List<MediaItem> {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        return media.filter { item ->
            val itemDate = item.dateAdded * 1000 // Convert to milliseconds
            val itemCalendar = Calendar.getInstance().apply { timeInMillis = itemDate }
            
            when (filter) {
                DateFilter.Today -> {
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    itemDate >= calendar.timeInMillis
                }
                DateFilter.Yesterday -> {
                    val yesterdayStart = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    val yesterdayEnd = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }
                    itemDate in yesterdayStart.timeInMillis..yesterdayEnd.timeInMillis
                }
                DateFilter.ThisWeek -> {
                    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    itemDate >= calendar.timeInMillis
                }
                DateFilter.LastWeek -> {
                    val lastWeekStart = Calendar.getInstance().apply {
                        add(Calendar.WEEK_OF_YEAR, -1)
                        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    val lastWeekEnd = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        add(Calendar.MILLISECOND, -1)
                    }
                    itemDate in lastWeekStart.timeInMillis..lastWeekEnd.timeInMillis
                }
                DateFilter.ThisMonth -> {
                    itemCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                    itemCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
                }
                DateFilter.LastMonth -> {
                    val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    itemCalendar.get(Calendar.MONTH) == lastMonth.get(Calendar.MONTH) &&
                    itemCalendar.get(Calendar.YEAR) == lastMonth.get(Calendar.YEAR)
                }
                is DateFilter.Year -> {
                    itemCalendar.get(Calendar.YEAR) == filter.year
                }
                is DateFilter.Month -> {
                    itemCalendar.get(Calendar.MONTH) == filter.month
                }
            }
        }
    }
    
    /**
     * Apply media type filter
     */
    private fun applyMediaTypeFilter(media: List<MediaItem>, filter: MediaTypeFilter): List<MediaItem> {
        return when (filter) {
            MediaTypeFilter.Photos -> media.filter { !it.isVideo }
            MediaTypeFilter.Videos -> media.filter { it.isVideo }
            MediaTypeFilter.Gifs -> media.filter { it.mimeType.contains("gif", ignoreCase = true) }
            MediaTypeFilter.Screenshots -> media.filter { 
                it.bucketName.contains("screenshot", ignoreCase = true) ||
                it.displayName.contains("screenshot", ignoreCase = true)
            }
            MediaTypeFilter.Camera -> media.filter { 
                it.bucketName.contains("camera", ignoreCase = true) ||
                it.bucketName.contains("dcim", ignoreCase = true)
            }
        }
    }
    
    /**
     * Apply size filter
     */
    private fun applySizeFilter(media: List<MediaItem>, filter: SizeFilter): List<MediaItem> {
        val fiveMB = 5 * 1024 * 1024L
        val hundredMB = 100 * 1024 * 1024L
        
        return when (filter) {
            SizeFilter.Small -> media.filter { it.size < fiveMB }
            SizeFilter.Medium -> media.filter { it.size in fiveMB..hundredMB }
            SizeFilter.Large -> media.filter { it.size > hundredMB }
        }
    }
    
    /**
     * Apply file name filter
     */
    private fun applyFileNameFilter(media: List<MediaItem>, query: String): List<MediaItem> {
        return media.filter { 
            it.displayName.lowercase(Locale.getDefault()).contains(query) ||
            it.bucketName.lowercase(Locale.getDefault()).contains(query)
        }
    }
    
    /**
     * Get quick filter suggestions
     */
    fun getQuickFilters(): List<QuickFilter> {
        return listOf(
            QuickFilter("Photos", MediaTypeFilter.Photos),
            QuickFilter("Videos", MediaTypeFilter.Videos),
            QuickFilter("Screenshots", MediaTypeFilter.Screenshots),
            QuickFilter("Camera", MediaTypeFilter.Camera),
            QuickFilter("Large Files", SizeFilter.Large)
        )
    }
    
    /**
     * Get date shortcuts
     */
    fun getDateShortcuts(): List<DateShortcut> {
        return listOf(
            DateShortcut("Today", DateFilter.Today),
            DateShortcut("Yesterday", DateFilter.Yesterday),
            DateShortcut("This Week", DateFilter.ThisWeek),
            DateShortcut("This Month", DateFilter.ThisMonth)
        )
    }
    
    // Filter types
    sealed class DateFilter {
        object Today : DateFilter()
        object Yesterday : DateFilter()
        object ThisWeek : DateFilter()
        object LastWeek : DateFilter()
        object ThisMonth : DateFilter()
        object LastMonth : DateFilter()
        data class Year(val year: Int) : DateFilter()
        data class Month(val month: Int) : DateFilter()
    }
    
    sealed class MediaTypeFilter {
        object Photos : MediaTypeFilter()
        object Videos : MediaTypeFilter()
        object Gifs : MediaTypeFilter()
        object Screenshots : MediaTypeFilter()
        object Camera : MediaTypeFilter()
    }
    
    sealed class SizeFilter {
        object Small : SizeFilter()
        object Medium : SizeFilter()
        object Large : SizeFilter()
    }
    
    data class QuickFilter(val label: String, val filter: Any)
    data class DateShortcut(val label: String, val filter: DateFilter)
}
