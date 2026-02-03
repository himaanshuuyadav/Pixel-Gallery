package com.prantiux.pixelgallery.search

import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.data.MediaLabelEntity

/**
 * Post-processing filter and ranker for ML-based search results.
 * 
 * Improves search accuracy by:
 * 1. Confidence thresholding per label
 * 2. Negative signal filtering (e.g., suppress cat results if strong person signal)
 * 3. Intelligent ranking based on confidence scores
 * 
 * All filtering happens at query-time, not during ML inference or storage.
 */
object SearchResultFilter {
    
    /**
     * Configurable thresholds for search accuracy
     * Adjust these values to tune search behavior
     */
    object Thresholds {
        // Minimum confidence for animal-related searches (pets, wildlife)
        const val ANIMAL_MIN_CONFIDENCE = 0.75f
        
        // Minimum confidence for object searches (food, vehicle, etc.)
        const val OBJECT_MIN_CONFIDENCE = 0.70f
        
        // Minimum confidence for general searches
        const val GENERAL_MIN_CONFIDENCE = 0.65f
        
        // Strong negative signals (if these are high, suppress weak matches)
        const val STRONG_SIGNAL_THRESHOLD = 0.85f
        
        // Weak match threshold (anything below this can be suppressed by strong negatives)
        const val WEAK_MATCH_THRESHOLD = 0.75f
        
        // Categories that are prone to false positives
        val ANIMAL_LABELS = setOf(
            "cat", "dog", "bird", "animal", "pet", "mammal",
            "wildlife", "feline", "canine", "fish", "insect"
        )
        
        val FOOD_LABELS = setOf(
            "food", "dish", "meal", "cuisine", "dessert", "drink",
            "fruit", "vegetable", "snack"
        )
        
        // Strong negative signals that often indicate false positives
        val PERSON_LABELS = setOf("person", "people", "human", "face", "portrait")
        val BUILDING_LABELS = setOf("building", "architecture", "house", "structure")
    }
    
    /**
     * Parsed label with confidence score
     */
    data class LabelWithConfidence(
        val label: String,
        val confidence: Float
    )
    
    /**
     * Search result with ranking score
     */
    data class RankedResult(
        val mediaItem: MediaItem,
        val matchedLabel: String,
        val confidence: Float,
        val rankScore: Float, // Combined score for sorting
        val suppressReason: String? = null // Why result was down-ranked (for debugging)
    )
    
    /**
     * Parse labels with confidence from storage format
     * Input: "dog:0.95,animal:0.88,pet:0.75"
     * Output: List of LabelWithConfidence
     */
    fun parseLabelsWithConfidence(labelsWithConfidence: String): List<LabelWithConfidence> {
        if (labelsWithConfidence.isBlank()) return emptyList()
        
        return labelsWithConfidence.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val label = parts[0].trim()
                val confidence = parts[1].toFloatOrNull()
                if (confidence != null) {
                    LabelWithConfidence(label, confidence)
                } else null
            } else null
        }
    }
    
    /**
     * Get minimum confidence threshold for a search query
     */
    private fun getMinConfidenceForQuery(query: String): Float {
        val lowerQuery = query.lowercase()
        return when {
            lowerQuery in Thresholds.ANIMAL_LABELS -> Thresholds.ANIMAL_MIN_CONFIDENCE
            lowerQuery in Thresholds.FOOD_LABELS -> Thresholds.OBJECT_MIN_CONFIDENCE
            else -> Thresholds.GENERAL_MIN_CONFIDENCE
        }
    }
    
    /**
     * Check if result should be suppressed due to negative signals
     * 
     * Example: If searching for "cat" but image has strong "person" label
     * and weak "cat" label, suppress it.
     */
    private fun checkNegativeSignals(
        searchQuery: String,
        matchConfidence: Float,
        allLabels: List<LabelWithConfidence>
    ): String? {
        // Only apply negative filtering for weak matches
        if (matchConfidence >= Thresholds.STRONG_SIGNAL_THRESHOLD) {
            return null // Strong match, don't suppress
        }
        
        val lowerQuery = searchQuery.lowercase()
        
        // Check for animal searches with strong person signals
        if (lowerQuery in Thresholds.ANIMAL_LABELS) {
            val strongPersonSignal = allLabels.any { 
                it.label in Thresholds.PERSON_LABELS && 
                it.confidence >= Thresholds.STRONG_SIGNAL_THRESHOLD 
            }
            
            if (strongPersonSignal && matchConfidence < Thresholds.WEAK_MATCH_THRESHOLD) {
                return "strong_person_signal"
            }
        }
        
        // Check for food searches with strong building/architecture signals
        if (lowerQuery in Thresholds.FOOD_LABELS) {
            val strongBuildingSignal = allLabels.any {
                it.label in Thresholds.BUILDING_LABELS &&
                it.confidence >= Thresholds.STRONG_SIGNAL_THRESHOLD
            }
            
            if (strongBuildingSignal && matchConfidence < Thresholds.WEAK_MATCH_THRESHOLD) {
                return "strong_building_signal"
            }
        }
        
        return null
    }
    
    /**
     * Calculate ranking score for a match
     * Higher scores appear first in results
     * 
     * Factors:
     * - Base confidence of the matched label
     * - Penalty for negative signals
     * - Bonus for related supporting labels
     */
    private fun calculateRankScore(
        matchConfidence: Float,
        suppressReason: String?,
        allLabels: List<LabelWithConfidence>,
        searchQuery: String
    ): Float {
        var score = matchConfidence
        
        // Apply penalty for suppressed results
        if (suppressReason != null) {
            score *= 0.5f // 50% penalty
        }
        
        // Bonus for related supporting labels
        val lowerQuery = searchQuery.lowercase()
        if (lowerQuery in Thresholds.ANIMAL_LABELS) {
            val relatedLabels = allLabels.count { 
                it.label in Thresholds.ANIMAL_LABELS && it.confidence > 0.7f 
            }
            if (relatedLabels >= 2) {
                score *= 1.1f // 10% bonus for multiple animal labels
            }
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Filter and rank search results based on confidence and negative signals
     * 
     * @param query The search query
     * @param matchedLabels ML label entities that matched the query
     * @param mediaItems All available media items
     * @param hardFilter If true, remove low-confidence results; if false, just rank them lower
     * @return Ranked results, sorted by relevance (best matches first)
     */
    fun filterAndRank(
        query: String,
        matchedLabels: List<MediaLabelEntity>,
        mediaItems: List<MediaItem>,
        hardFilter: Boolean = true
    ): List<RankedResult> {
        val minConfidence = getMinConfidenceForQuery(query)
        val lowerQuery = query.lowercase()
        
        // Create media lookup map
        val mediaMap = mediaItems.associateBy { it.id }
        
        // Process each matched label entity
        val rankedResults = matchedLabels.mapNotNull { labelEntity ->
            val mediaItem = mediaMap[labelEntity.mediaId] ?: return@mapNotNull null
            val parsedLabels = parseLabelsWithConfidence(labelEntity.labelsWithConfidence)
            
            // Find the matched label and its confidence
            val matchedLabel = parsedLabels.find { it.label.contains(lowerQuery) || lowerQuery.contains(it.label) }
                ?: return@mapNotNull null
            
            // Apply confidence threshold
            if (matchedLabel.confidence < minConfidence) {
                if (hardFilter) {
                    return@mapNotNull null // Filter out low-confidence matches
                }
            }
            
            // Check for negative signals
            val suppressReason = checkNegativeSignals(
                searchQuery = lowerQuery,
                matchConfidence = matchedLabel.confidence,
                allLabels = parsedLabels
            )
            
            // Hard filter suppressed results if enabled
            if (hardFilter && suppressReason != null && matchedLabel.confidence < Thresholds.WEAK_MATCH_THRESHOLD) {
                return@mapNotNull null
            }
            
            // Calculate ranking score
            val rankScore = calculateRankScore(
                matchConfidence = matchedLabel.confidence,
                suppressReason = suppressReason,
                allLabels = parsedLabels,
                searchQuery = lowerQuery
            )
            
            RankedResult(
                mediaItem = mediaItem,
                matchedLabel = matchedLabel.label,
                confidence = matchedLabel.confidence,
                rankScore = rankScore,
                suppressReason = suppressReason
            )
        }
        
        // Sort by rank score (highest first)
        return rankedResults.sortedByDescending { it.rankScore }
    }
    
    /**
     * Simple wrapper that returns just the media items (for drop-in replacement)
     */
    fun filterAndRankMedia(
        query: String,
        matchedLabels: List<MediaLabelEntity>,
        mediaItems: List<MediaItem>,
        hardFilter: Boolean = true
    ): List<MediaItem> {
        return filterAndRank(query, matchedLabels, mediaItems, hardFilter)
            .map { it.mediaItem }
    }
}
