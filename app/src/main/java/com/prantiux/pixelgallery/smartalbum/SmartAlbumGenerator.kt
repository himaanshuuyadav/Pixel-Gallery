package com.prantiux.pixelgallery.smartalbum

import android.content.Context
import android.net.Uri
import com.prantiux.pixelgallery.data.AppDatabase
import com.prantiux.pixelgallery.data.MediaLabelEntity
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.search.SearchResultFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generator for ML-based smart albums using existing image labeling results.
 * Creates virtual/query-based albums without modifying the ML pipeline or file system.
 * 
 * All albums are filtered using existing SearchResultFilter logic.
 */
object SmartAlbumGenerator {
    
    // Minimum items required to show a smart album
    const val MIN_ITEMS_THRESHOLD = 5
    
    // Smart album ID prefix to distinguish from regular albums
    private const val SMART_PREFIX = "smart_"
    
    /**
     * Smart album definitions with their query rules
     */
    enum class SmartAlbumType(
        val id: String,
        val displayName: String,
        val icon: String, // Unicode emoji
        val labels: Set<String>,
        val minConfidence: Float
    ) {
        ANIMALS(
            id = "${SMART_PREFIX}animals",
            displayName = "Animals",
            icon = "🐾",
            labels = setOf("cat", "dog", "animal", "bird", "pet", "mammal", "wildlife", "feline", "canine"),
            minConfidence = 0.75f
        ),
        FOOD(
            id = "${SMART_PREFIX}food",
            displayName = "Food",
            icon = "🍽️",
            labels = setOf("food", "dish", "meal", "cuisine", "dessert", "drink", "fruit", "vegetable"),
            minConfidence = 0.70f
        ),
        NATURE(
            id = "${SMART_PREFIX}nature",
            displayName = "Nature",
            icon = "🌿",
            labels = setOf("mountain", "beach", "forest", "sky", "sunset", "landscape", "nature", "outdoor", "tree", "water", "ocean"),
            minConfidence = 0.70f
        ),
        DOCUMENTS(
            id = "${SMART_PREFIX}documents",
            displayName = "Documents",
            icon = "📄",
            labels = setOf("document", "text", "paper"),
            minConfidence = 0.65f
        );
        
        companion object {
            fun fromId(id: String): SmartAlbumType? {
                return values().find { it.id == id }
            }
            
            fun isSmartAlbum(id: String): Boolean {
                return id.startsWith(SMART_PREFIX)
            }
        }
    }
    
    /**
     * Generate all smart albums with item counts
     * Only returns albums that meet the minimum threshold
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
        
        // Generate albums for each type
        SmartAlbumType.values().mapNotNull { albumType ->
            val matchingLabels = findMatchingLabels(allLabels, albumType)
            
            if (matchingLabels.size >= MIN_ITEMS_THRESHOLD) {
                // Find the highest confidence item for cover image
                val topItem = matchingLabels.maxByOrNull { labelEntity ->
                    val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(labelEntity.labelsWithConfidence)
                    parsedLabels.filter { it.label in albumType.labels }
                        .maxOfOrNull { it.confidence } ?: 0f
                }
                
                Album(
                    id = albumType.id,
                    name = "${albumType.icon} ${albumType.displayName}",
                    coverUri = null, // Will be resolved from media items
                    itemCount = matchingLabels.size,
                    bucketDisplayName = albumType.displayName,
                    isMainAlbum = false,
                    topMediaUris = emptyList(),
                    topMediaItems = emptyList()
                )
            } else null
        }
    }
    
    /**
     * Get media items for a specific smart album
     * Uses existing SearchResultFilter logic for confidence filtering
     */
    suspend fun getMediaForSmartAlbum(
        context: Context,
        smartAlbumId: String,
        allMediaItems: List<MediaItem>
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val albumType = SmartAlbumType.fromId(smartAlbumId) ?: return@withContext emptyList()
        val database = AppDatabase.getDatabase(context)
        val labelDao = database.mediaLabelDao()
        
        // Query for matching labels
        val matchingLabels = try {
            albumType.labels.flatMap { label ->
                labelDao.searchByLabel(label.lowercase())
            }.distinctBy { it.mediaId }
        } catch (e: Exception) {
            emptyList()
        }
        
        if (matchingLabels.isEmpty()) {
            return@withContext emptyList()
        }
        
        // Apply confidence filtering using existing SearchResultFilter
        val filteredResults = matchingLabels.mapNotNull { labelEntity ->
            val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(labelEntity.labelsWithConfidence)
            
            // Check if any of the album's labels match with sufficient confidence
            val hasMatchingLabel = parsedLabels.any { label ->
                albumType.labels.contains(label.label) && label.confidence >= albumType.minConfidence
            }
            
            if (hasMatchingLabel) {
                // Find the matching media item
                allMediaItems.find { it.id == labelEntity.mediaId }
            } else null
        }
        
        // Apply negative signal filtering for Animals & Food
        when (albumType) {
            SmartAlbumType.ANIMALS, SmartAlbumType.FOOD -> {
                // Use stricter filtering to remove false positives
                filteredResults.filter { mediaItem ->
                    val labelEntity = matchingLabels.find { it.mediaId == mediaItem.id }
                    labelEntity?.let { entity ->
                        val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(entity.labelsWithConfidence)
                        !hasStrongNegativeSignal(parsedLabels, albumType)
                    } ?: false
                }
            }
            else -> filteredResults
        }
    }
    
    /**
     * Check for strong negative signals that indicate false positives
     */
    private fun hasStrongNegativeSignal(
        labels: List<SearchResultFilter.LabelWithConfidence>,
        albumType: SmartAlbumType
    ): Boolean {
        val STRONG_THRESHOLD = 0.85f
        
        return when (albumType) {
            SmartAlbumType.ANIMALS -> {
                // Suppress if strong person signal
                labels.any { 
                    it.label in setOf("person", "people", "human", "portrait") && 
                    it.confidence >= STRONG_THRESHOLD 
                }
            }
            SmartAlbumType.FOOD -> {
                // Suppress if strong building/architecture signal
                labels.any {
                    it.label in setOf("building", "architecture", "house") &&
                    it.confidence >= STRONG_THRESHOLD
                }
            }
            else -> false
        }
    }
    
    /**
     * Find matching labels for a smart album type
     */
    private fun findMatchingLabels(
        allLabels: List<MediaLabelEntity>,
        albumType: SmartAlbumType
    ): List<MediaLabelEntity> {
        return allLabels.filter { labelEntity ->
            val parsedLabels = SearchResultFilter.parseLabelsWithConfidence(labelEntity.labelsWithConfidence)
            
            // Check if any label matches the album type with sufficient confidence
            parsedLabels.any { label ->
                albumType.labels.contains(label.label) && 
                label.confidence >= albumType.minConfidence
            } && !hasStrongNegativeSignal(parsedLabels, albumType)
        }
    }
    
    /**
     * Check if an album ID represents a smart album
     */
    fun isSmartAlbum(albumId: String): Boolean {
        return SmartAlbumType.isSmartAlbum(albumId)
    }
}
