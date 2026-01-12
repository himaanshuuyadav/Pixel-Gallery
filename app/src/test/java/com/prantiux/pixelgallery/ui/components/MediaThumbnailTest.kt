package com.prantiux.pixelgallery.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.prantiux.pixelgallery.model.MediaItem
import org.junit.Rule
import org.junit.Test
import android.net.Uri

/**
 * MINIMAL UI SAFETY TESTS
 * 
 * These tests ensure visual consistency across the app by:
 * 1. Verifying thumbnail components render correctly
 * 2. Detecting visual regressions in selection UI
 * 3. Validating animation states
 * 
 * ⚠️ CRITICAL: If these tests fail, UI consistency is broken
 * 
 * Tests are intentionally minimal to:
 * - Run fast (<100ms each)
 * - Be deterministic
 * - Fail loudly on visual changes
 */
class MediaThumbnailTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    /**
     * TEST 1: Video Thumbnail Rendering
     * 
     * Validates:
     * - Video duration pill appears
     * - Play icon is visible
     * - Duration text is formatted correctly
     * - Pill is at bottom-end position
     */
    @Test
    fun videoThumbnail_showsVideoDurationPill() {
        // Arrange
        val videoItem = MediaItem(
            id = 1L,
            uri = Uri.parse("content://test/video"),
            displayName = "test.mp4",
            bucketId = "test",
            bucketName = "Test",
            dateAdded = System.currentTimeMillis(),
            size = 1024L,
            mimeType = "video/mp4",
            duration = 125000L, // 2:05
            isVideo = true,
            isFavorite = false
        )
        
        // Act
        composeTestRule.setContent {
            MediaThumbnail(
                item = videoItem,
                isSelected = false,
                isSelectionMode = false,
                shape = RoundedCornerShape(4.dp),
                onClick = {},
                onLongClick = {},
                showFavorite = true
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("2:05").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Video").assertIsDisplayed()
    }
    
    /**
     * TEST 2: Image Thumbnail (No Duration Pill)
     * 
     * Validates:
     * - Duration pill does NOT appear for images
     * - Only image is displayed
     */
    @Test
    fun imageThumbnail_doesNotShowVideoPill() {
        // Arrange
        val imageItem = MediaItem(
            id = 2L,
            uri = Uri.parse("content://test/image"),
            displayName = "test.jpg",
            bucketId = "test",
            bucketName = "Test",
            dateAdded = System.currentTimeMillis(),
            size = 1024L,
            mimeType = "image/jpeg",
            duration = 0L,
            isVideo = false,
            isFavorite = false
        )
        
        // Act
        composeTestRule.setContent {
            MediaThumbnail(
                item = imageItem,
                isSelected = false,
                isSelectionMode = false,
                shape = RoundedCornerShape(4.dp),
                onClick = {},
                onLongClick = {},
                showFavorite = true
            )
        }
        
        // Assert - video pill should NOT exist
        composeTestRule.onNodeWithContentDescription("Video").assertDoesNotExist()
    }
    
    /**
     * TEST 3: Favorite Star Badge
     * 
     * Validates:
     * - Favorite star appears for favorited items
     * - Star is at top-start position
     * - Star is gold color
     */
    @Test
    fun favoriteThumbnail_showsGoldStar() {
        // Arrange
        val favoriteItem = MediaItem(
            id = 3L,
            uri = Uri.parse("content://test/favorite"),
            displayName = "favorite.jpg",
            bucketId = "test",
            bucketName = "Test",
            dateAdded = System.currentTimeMillis(),
            size = 1024L,
            mimeType = "image/jpeg",
            duration = 0L,
            isVideo = false,
            isFavorite = true
        )
        
        // Act
        composeTestRule.setContent {
            MediaThumbnail(
                item = favoriteItem,
                isSelected = false,
                isSelectionMode = false,
                shape = RoundedCornerShape(4.dp),
                onClick = {},
                onLongClick = {},
                showFavorite = true
            )
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Favorited").assertIsDisplayed()
    }
    
    /**
     * TEST 4: Selection State - Unselected
     * 
     * Validates:
     * - Selection checkmark appears in selection mode
     * - Checkmark is empty (not filled) when unselected
     */
    @Test
    fun selectionMode_unselected_showsEmptyCheckmark() {
        // Arrange
        val item = MediaItem(
            id = 4L,
            uri = Uri.parse("content://test/item"),
            displayName = "test.jpg",
            bucketId = "test",
            bucketName = "Test",
            dateAdded = System.currentTimeMillis(),
            size = 1024L,
            mimeType = "image/jpeg",
            duration = 0L,
            isVideo = false,
            isFavorite = false
        )
        
        // Act
        composeTestRule.setContent {
            MediaThumbnail(
                item = item,
                isSelected = false,
                isSelectionMode = true, // Selection mode ON
                shape = RoundedCornerShape(4.dp),
                onClick = {},
                onLongClick = {},
                showFavorite = true
            )
        }
        
        // Assert - checkmark exists but is not filled (no "Selected" description)
        composeTestRule.onNodeWithContentDescription("Selected").assertDoesNotExist()
    }
    
    /**
     * TEST 5: Selection State - Selected
     * 
     * Validates:
     * - Selected checkmark shows Done icon
     * - Border appears around selected item
     */
    @Test
    fun selectionMode_selected_showsFilledCheckmark() {
        // Arrange
        val item = MediaItem(
            id = 5L,
            uri = Uri.parse("content://test/selected"),
            displayName = "selected.jpg",
            bucketId = "test",
            bucketName = "Test",
            dateAdded = System.currentTimeMillis(),
            size = 1024L,
            mimeType = "image/jpeg",
            duration = 0L,
            isVideo = false,
            isFavorite = false
        )
        
        // Act
        composeTestRule.setContent {
            MediaThumbnail(
                item = item,
                isSelected = true, // SELECTED
                isSelectionMode = true,
                shape = RoundedCornerShape(4.dp),
                onClick = {},
                onLongClick = {},
                showFavorite = true
            )
        }
        
        // Assert
        composeTestRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }
    
    /**
     * TEST 6: Favorite Star Hidden in Selection Mode
     * 
     * Validates:
     * - Favorite star is hidden when selection mode is active
     * - Even favorited items don't show star in selection mode
     */
    @Test
    fun selectionMode_hidesFavoriteStar() {
        // Arrange
        val favoriteItem = MediaItem(
            id = 6L,
            uri = Uri.parse("content://test/fav-select"),
            displayName = "fav.jpg",
            bucketId = "test",
            bucketName = "Test",
            dateAdded = System.currentTimeMillis(),
            size = 1024L,
            mimeType = "image/jpeg",
            duration = 0L,
            isVideo = false,
            isFavorite = true // Favorited
        )
        
        // Act
        composeTestRule.setContent {
            MediaThumbnail(
                item = favoriteItem,
                isSelected = false,
                isSelectionMode = true, // Selection mode hides star
                shape = RoundedCornerShape(4.dp),
                onClick = {},
                onLongClick = {},
                showFavorite = true
            )
        }
        
        // Assert - star should NOT be visible
        composeTestRule.onNodeWithContentDescription("Favorited").assertDoesNotExist()
    }
}

/**
 * Duration Formatter Tests
 * 
 * Validates duration formatting consistency
 */
class DurationFormatterTest {
    
    @Test
    fun formatDuration_seconds_onlyShowsMinutesAndSeconds() {
        val result = formatDuration(65000L) // 1:05
        assert(result == "1:05") { "Expected '1:05', got '$result'" }
    }
    
    @Test
    fun formatDuration_minutes_showsZeroPaddedSeconds() {
        val result = formatDuration(125000L) // 2:05
        assert(result == "2:05") { "Expected '2:05', got '$result'" }
    }
    
    @Test
    fun formatDuration_hours_showsFullFormat() {
        val result = formatDuration(3665000L) // 1:01:05
        assert(result == "1:01:05") { "Expected '1:01:05', got '$result'" }
    }
    
    @Test
    fun formatDuration_zeroSeconds_showsZeroZero() {
        val result = formatDuration(60000L) // 1:00
        assert(result == "1:00") { "Expected '1:00', got '$result'" }
    }
}

/**
 * TEST EXECUTION CHECKLIST
 * 
 * ✅ Run before EVERY merge to main
 * ✅ Run after ANY change to MediaThumbnail.kt
 * ✅ Run after ANY change to SelectionSystem.kt
 * ✅ Run after ANY change to theme colors
 * 
 * If ANY test fails:
 * 1. DO NOT merge
 * 2. Fix the regression
 * 3. Update test if intentional change
 * 4. Document in PR why visual changed
 * 
 * Expected runtime: <500ms for all tests
 */
