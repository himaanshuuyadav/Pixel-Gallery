package com.prantiux.pixelgallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * ⛔ FORBIDDEN COMPONENTS - COMPILE-TIME ENFORCEMENT
 * 
 * These functions are marked with @Deprecated(level = ERROR) to prevent
 * developers from creating custom implementations when shared components exist.
 * 
 * If you see a compile error pointing here, you MUST use the shared component instead.
 */

/**
 * ⛔ FORBIDDEN: Do not create custom thumbnail composables
 * 
 * ✅ USE INSTEAD:
 * - `MediaThumbnail` for basic thumbnails
 * - `SelectableMediaItem` for thumbnails with selection
 * 
 * Location: com.prantiux.pixelgallery.ui.components.MediaThumbnail
 */
@Deprecated(
    message = "❌ FORBIDDEN: Custom thumbnail implementations violate the shared component architecture. " +
            "Use MediaThumbnail or SelectableMediaItem instead. " +
            "See SHARED_COMPONENTS_ARCHITECTURE.md",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "MediaThumbnail(item, isSelected, isSelectionMode, shape, onClick, onLongClick)",
        "com.prantiux.pixelgallery.ui.components.MediaThumbnail"
    )
)
@Composable
fun CustomMediaThumbnail(/* any signature */) {
    error("This should never be called - compile error should prevent this")
}

/**
 * ⛔ FORBIDDEN: Do not create custom video duration pills
 * 
 * ✅ USE INSTEAD: `VideoDurationPill` from MediaThumbnail.kt
 * 
 * Location: com.prantiux.pixelgallery.ui.components.VideoDurationPill
 */
@Deprecated(
    message = "❌ FORBIDDEN: Custom video duration pills create UI inconsistency. " +
            "Use VideoDurationPill from MediaThumbnail.kt. " +
            "See SHARED_COMPONENTS_ARCHITECTURE.md",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "VideoDurationPill(duration)",
        "com.prantiux.pixelgallery.ui.components.VideoDurationPill"
    )
)
@Composable
fun CustomVideoPill(/* any signature */) {
    error("This should never be called")
}

/**
 * ⛔ FORBIDDEN: Do not create custom favorite star badges
 * 
 * ✅ USE INSTEAD: `FavoriteStarBadge` from MediaThumbnail.kt
 * 
 * Location: com.prantiux.pixelgallery.ui.components.FavoriteStarBadge
 */
@Deprecated(
    message = "❌ FORBIDDEN: Custom favorite badges create UI inconsistency. " +
            "Use FavoriteStarBadge from MediaThumbnail.kt. " +
            "See SHARED_COMPONENTS_ARCHITECTURE.md",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "FavoriteStarBadge()",
        "com.prantiux.pixelgallery.ui.components.FavoriteStarBadge"
    )
)
@Composable
fun CustomFavoriteStar(/* any signature */) {
    error("This should never be called")
}

/**
 * ⛔ FORBIDDEN: Do not create custom selection indicators
 * 
 * ✅ USE INSTEAD: `SelectionCheckmark` from MediaThumbnail.kt
 * 
 * Location: com.prantiux.pixelgallery.ui.components.SelectionCheckmark
 */
@Deprecated(
    message = "❌ FORBIDDEN: Custom selection indicators create UI inconsistency. " +
            "Use SelectionCheckmark from MediaThumbnail.kt or SelectableMediaItem. " +
            "See SHARED_COMPONENTS_ARCHITECTURE.md",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "SelectionCheckmark(isSelected)",
        "com.prantiux.pixelgallery.ui.components.SelectionCheckmark"
    )
)
@Composable
fun CustomSelectionIndicator(/* any signature */) {
    error("This should never be called")
}

/**
 * ⛔ FORBIDDEN: Do not create custom duration formatters
 * 
 * ✅ USE INSTEAD: `formatDuration()` from MediaThumbnail.kt
 * 
 * Location: com.prantiux.pixelgallery.ui.components.formatDuration
 */
@Deprecated(
    message = "❌ FORBIDDEN: Custom duration formatters create inconsistency. " +
            "Use formatDuration() from MediaThumbnail.kt. " +
            "See SHARED_COMPONENTS_ARCHITECTURE.md",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "formatDuration(durationMs)",
        "com.prantiux.pixelgallery.ui.components.formatDuration"
    )
)
fun customFormatDuration(durationMs: Long): String {
    error("This should never be called")
}

/**
 * ⛔ FORBIDDEN: Do not create custom animation specs for media overlays
 * 
 * ✅ USE INSTEAD:
 * - `mediaOpenAnimationSpec()` for opening animations
 * - `mediaCloseAnimationSpec()` for closing animations
 * 
 * Location: com.prantiux.pixelgallery.ui.animation.MediaGridAnimations
 */
@Deprecated(
    message = "❌ FORBIDDEN: Custom animation specs create inconsistent transitions. " +
            "Use mediaOpenAnimationSpec() or mediaCloseAnimationSpec(). " +
            "See SHARED_COMPONENTS_ARCHITECTURE.md",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "mediaOpenAnimationSpec()",
        "com.prantiux.pixelgallery.ui.animation.mediaOpenAnimationSpec"
    )
)
fun customMediaAnimationSpec(): androidx.compose.animation.core.AnimationSpec<Float> {
    error("This should never be called")
}

/**
 * ⛔ FORBIDDEN: Do not implement custom selection click handlers
 * 
 * ✅ USE INSTEAD:
 * - `SelectableMediaItem` (automatic handling)
 * - `handleMediaItemClick()` from SelectionSystem.kt
 * 
 * Location: com.prantiux.pixelgallery.ui.components.SelectionSystem
 */
@Deprecated(
    message = "❌ FORBIDDEN: Custom selection logic creates inconsistent behavior. " +
            "Use SelectableMediaItem or handleMediaItemClick(). " +
            "See SHARED_COMPONENTS_ARCHITECTURE.md",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "handleMediaItemClick(isSelectionMode, item, thumbnailBounds, viewModel, mediaType, albumId, index)",
        "com.prantiux.pixelgallery.ui.components.handleMediaItemClick"
    )
)
fun customHandleItemClick(/* any signature */) {
    error("This should never be called")
}
