package com.prantiux.pixelgallery.smartalbum

import android.content.Context
import android.net.Uri
import com.prantiux.pixelgallery.data.AppDatabase
import com.prantiux.pixelgallery.data.MediaLabelEntity
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.search.SearchResultFilter
import com.prantiux.pixelgallery.data.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generator for ML-based smart albums using existing image labeling results.
 * Creates virtual/query-based albums dynamically based on what labels exist in the library.
 * 
 * All albums are filtered using existing SearchResultFilter logic.
 */
object SmartAlbumGenerator {
    
    // Minimum items required to show a smart album
    const val MIN_ITEMS_THRESHOLD = 5
    
    // Smart album ID prefix to distinguish from regular albums
    private const val SMART_PREFIX = "smart_"
    
    /**
     * Smart album definition
     */
    data class SmartAlbumCategory(
        val id: String,
        val displayName: String,
        val icon: String, // Unicode emoji
        val positiveLabels: Set<String>,
        val negativeLabels: Set<String> = emptySet(), // Strong negative signals (must be > 0.85 to suppress)
        val minConfidence: Float = 0.70f
    )
    
    /**
     * Curated library of possible Smart Albums.
     * Only categories where the user has >= 5 photos will be generated.
     */
    val CATEGORY_LIBRARY = listOf(
        // The original 4 categories
        SmartAlbumCategory(
            id = "${SMART_PREFIX}animals",
            displayName = "Animals",
            icon = "🐾",
            positiveLabels = setOf("cat", "dog", "animal", "bird", "pet", "mammal", "wildlife", "feline", "canine"),
            negativeLabels = setOf("person", "people", "human", "portrait"),
            minConfidence = 0.75f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}food",
            displayName = "Food",
            icon = "🍽️",
            positiveLabels = setOf("food", "dish", "meal", "cuisine", "dessert", "drink", "fruit", "vegetable"),
            negativeLabels = setOf("building", "architecture", "house"),
            minConfidence = 0.70f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}nature",
            displayName = "Nature",
            icon = "🌿",
            positiveLabels = setOf("mountain", "beach", "forest", "sky", "sunset", "landscape", "nature", "outdoor", "tree", "water", "ocean"),
            minConfidence = 0.70f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}documents",
            displayName = "Documents",
            icon = "📄",
            positiveLabels = setOf("document", "text", "paper", "receipt", "screenshot"),
            minConfidence = 0.65f
        ),
        
        // Expanded Dynamic Categories
        SmartAlbumCategory(
            id = "${SMART_PREFIX}vehicles",
            displayName = "Vehicles",
            icon = "🚗",
            positiveLabels = setOf("car", "vehicle", "truck", "motorcycle", "bicycle", "bus", "transportation"),
            minConfidence = 0.75f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}architecture",
            displayName = "Architecture",
            icon = "🏙️",
            positiveLabels = setOf("building", "architecture", "house", "skyscraper", "monument", "city", "urban"),
            negativeLabels = setOf("food", "dish", "meal"),
            minConfidence = 0.75f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}people",
            displayName = "People",
            icon = "👥",
            positiveLabels = setOf("person", "people", "human", "face", "portrait", "smile", "crowd", "child"),
            negativeLabels = setOf("animal", "cat", "dog"),
            minConfidence = 0.75f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}sports",
            displayName = "Sports",
            icon = "⚽",
            positiveLabels = setOf("sport", "stadium", "ball", "athlete", "soccer", "basketball", "tennis", "gym"),
            minConfidence = 0.70f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}concerts",
            displayName = "Concerts",
            icon = "🎸",
            positiveLabels = setOf("concert", "stage", "music", "performance", "guitar", "band", "festival"),
            minConfidence = 0.70f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}nightlife",
            displayName = "Night",
            icon = "🌃",
            positiveLabels = setOf("night", "darkness", "midnight", "moon", "night sky", "neon", "fireworks"),
            minConfidence = 0.75f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}water",
            displayName = "Water",
            icon = "🌊",
            positiveLabels = setOf("ocean", "sea", "river", "swimming", "pool", "lake", "waterfall"),
            minConfidence = 0.75f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}flowers",
            displayName = "Flowers",
            icon = "🌸",
            positiveLabels = setOf("flower", "plant", "garden", "leaf", "petal", "blossom", "flora"),
            minConfidence = 0.70f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}technology",
            displayName = "Technology",
            icon = "💻",
            positiveLabels = setOf("computer", "phone", "screen", "gadget", "laptop", "keyboard", "electronics"),
            minConfidence = 0.70f
        ),
        SmartAlbumCategory(
            id = "${SMART_PREFIX}fashion",
            displayName = "Fashion",
            icon = "👗",
            positiveLabels = setOf("clothing", "footwear", "dress", "accessory", "shoe", "glasses", "sunglasses", "apparel"),
            minConfidence = 0.70f
        )
    )
    
    fun fromId(id: String): SmartAlbumCategory? {
        return CATEGORY_LIBRARY.find { it.id == id }
    }
    
    fun isSmartAlbum(id: String): Boolean {
        return id.startsWith(SMART_PREFIX)
    }
    
    /**
     * Generate all smart albums with item counts
     * Only returns albums that meet the minimum threshold, sorted by item count (descending)
     */
    suspend fun generateSmartAlbums(context: Context): List<Album> = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val labelDao = database.mediaLabelDao()
        
        // Get all labeled media
        val allLabels = try {
            labelDao.getAllLabels()
        } catch (e: Exception) {
            emptyList()
        }
        
        if (allLabels.isEmpty()) {
            return@withContext emptyList()
        }
        
        // Generate albums dynamically from library
        val generatedAlbums = CATEGORY_LIBRARY.mapNotNull { category ->
            val matchingLabels = findMatchingLabels(allLabels, category)
            
            if (matchingLabels.size >= MIN_ITEMS_THRESHOLD) {
                // Find the highest confidence item for cover image
                val topItem = matchingLabels.maxByOrNull { labelEntity ->
                    val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(labelEntity.labelsWithConfidence)
                    parsedLabels.filter { it.label in category.positiveLabels }
                        .maxOfOrNull { it.confidence } ?: 0f
                }
                val coverUriString = topItem?.mediaId?.let { database.mediaDao().getMediaByIdOnce(it)?.uri }
                
                Album(
                    id = category.id,
                    name = category.displayName,
                    coverUri = coverUriString?.let { android.net.Uri.parse(it) },
                    itemCount = matchingLabels.size,
                    bucketDisplayName = category.displayName,
                    isMainAlbum = false,
                    topMediaUris = emptyList(),
                    topMediaItems = emptyList()
                )
            } else null
        }
        
        // Sort the albums so the most populated categories appear first
        generatedAlbums.sortedByDescending { it.itemCount }
    }
    
    /**
     * Get media items for a specific smart album
     * Uses existing SearchResultFilter logic for confidence filtering
     */
    suspend fun getMediaForSmartAlbum(
        context: Context,
        smartAlbumId: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val category = fromId(smartAlbumId) ?: return@withContext emptyList()
        val database = AppDatabase.getDatabase(context)
        val labelDao = database.mediaLabelDao()
        
        // Query for matching labels
        val matchingLabels = try {
            category.positiveLabels.flatMap { label ->
                labelDao.searchByLabel(label.lowercase())
            }.distinctBy { it.mediaId }
        } catch (e: Exception) {
            emptyList()
        }
        
        val favIds = database.favoriteDao().getAllFavoriteIds().toSet()
        
        val validMediaIds = matchingLabels.mapNotNull { labelEntity ->
            val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(labelEntity.labelsWithConfidence)
            
            // Check if any of the album's labels match with sufficient confidence
            val hasMatchingLabel = parsedLabels.any { label ->
                category.positiveLabels.contains(label.label) && label.confidence >= category.minConfidence
            }
            
            if (hasMatchingLabel) labelEntity.mediaId else null
        }
        
        if (validMediaIds.isEmpty()) return@withContext emptyList()
        
        val entities = database.mediaDao().getMediaByIds(validMediaIds)
        val filteredResults = entities.map { it.toMediaItem(favIds.contains(it.id)) }
        
        // Apply data-driven negative signal filtering if the category has negative labels
        if (category.negativeLabels.isNotEmpty()) {
            filteredResults.filter { mediaItem ->
                val labelEntity = matchingLabels.find { it.mediaId == mediaItem.id }
                labelEntity?.let { entity ->
                    val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(entity.labelsWithConfidence)
                    !hasStrongNegativeSignal(parsedLabels, category)
                } ?: false
            }
        } else {
            filteredResults
        }
    }
    
    /**
     * Check for strong negative signals that indicate false positives, based on the category's negative labels
     */
    private fun hasStrongNegativeSignal(
        labels: List<SearchResultFilter.LabelWithConfidence>,
        category: SmartAlbumCategory
    ): Boolean {
        if (category.negativeLabels.isEmpty()) return false
        val STRONG_THRESHOLD = 0.85f
        
        return labels.any { 
            it.label in category.negativeLabels && it.confidence >= STRONG_THRESHOLD 
        }
    }
    
    /**
     * Find matching labels for a smart album type
     */
    private fun findMatchingLabels(
        allLabels: List<MediaLabelEntity>,
        category: SmartAlbumCategory
    ): List<MediaLabelEntity> {
        return allLabels.filter { labelEntity ->
            val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(labelEntity.labelsWithConfidence)
            
            // Check if any label matches the album type with sufficient confidence
            parsedLabels.any { label ->
                category.positiveLabels.contains(label.label) && 
                label.confidence >= category.minConfidence
            } && !hasStrongNegativeSignal(parsedLabels, category)
        }
    }
}
