package com.prantiux.pixelgallery.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Scrollbar behavior mode
 */
enum class ScrollbarMode {
    /**
     * Jumps between date groups (e.g., "Today", "Yesterday", "12 Dec")
     * Shows "Month Year" in center overlay
     */
    DATE_JUMPING,
    
    /**
     * Jumps between day-left groups (e.g., "30 days left", "1 day left")
     * Shows day-left text in center overlay
     */
    DAY_LEFT_JUMPING,
    
    /**
     * Smooth scrolling without jumping to specific groups
     * No center overlay
     */
    SMOOTH_SCROLLING
}

/**
 * Data class for date-based groups (Photos/Gallery screen)
 */
data class DateGroupInfo(
    val date: String,
    val displayDate: String,
    val itemCount: Int
)

/**
 * Data class for day-left groups (RecycleBin screen)
 */
data class DayLeftGroupInfo(
    val daysLeft: Int,
    val displayText: String,
    val itemCount: Int
)

/**
 * Unified Scrollbar Component
 * 
 * A consistent, reusable scrollbar that supports three modes:
 * 1. Date jumping for photo galleries
 * 2. Day-left jumping for recycle bin
 * 3. Smooth scrolling for albums
 * 
 * @param modifier Modifier for positioning the scrollbar
 * @param gridState The LazyGridState to control
 * @param mode The scrollbar behavior mode
 * @param topPadding Padding from top of screen (to avoid header)
 * @param dateGroups List of date groups (for DATE_JUMPING mode)
 * @param dayLeftGroups List of day-left groups (for DAY_LEFT_JUMPING mode)
 * @param totalItems Total number of items in the grid (for SMOOTH_SCROLLING mode)
 * @param coroutineScope Coroutine scope for launching scroll operations
 * @param isDarkTheme Whether dark theme is active
 * @param onScrollbarVisibilityChanged Callback when scrollbar visibility changes
 * @param onOverlayTextChanged Callback when overlay text changes (for center display)
 */
@Composable
fun UnifiedScrollbar(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    mode: ScrollbarMode,
    topPadding: Dp = 120.dp,
    dateGroups: List<DateGroupInfo> = emptyList(),
    dayLeftGroups: List<DayLeftGroupInfo> = emptyList(),
    totalItems: Int = 0,
    coroutineScope: CoroutineScope,
    isDarkTheme: Boolean = false,
    onScrollbarVisibilityChanged: (Boolean) -> Unit = {},
    onOverlayTextChanged: (String) -> Unit = {}
) {
    // State management
    var scrollbarVisible by remember { mutableStateOf(false) }
    var isDraggingScrollbar by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var lastSnappedKey by remember { mutableStateOf("") }
    
    val view = LocalView.current
    
    // Auto-hide scrollbar when not scrolling or dragging
    LaunchedEffect(gridState.isScrollInProgress, isDraggingScrollbar) {
        if (gridState.isScrollInProgress && !isDraggingScrollbar) {
            scrollbarVisible = true
            onScrollbarVisibilityChanged(true)
        } else if (!gridState.isScrollInProgress && !isDraggingScrollbar) {
            delay(1500)
            scrollbarVisible = false
            onScrollbarVisibilityChanged(false)
        }
    }
    
    // Scrollbar container
    Box(
        modifier = modifier
            .padding(top = topPadding)
            .fillMaxHeight()
            .width(60.dp) // Match touch area width
    ) {
        // Calculate scrollbar position
        val firstVisibleItemIndex = gridState.firstVisibleItemIndex
        val scrollPercentage = when (mode) {
            ScrollbarMode.DATE_JUMPING -> {
                val total = dateGroups.sumOf { it.itemCount + 1 }
                if (total > 0) firstVisibleItemIndex.toFloat() / total.toFloat() else 0f
            }
            ScrollbarMode.DAY_LEFT_JUMPING -> {
                val total = dayLeftGroups.sumOf { it.itemCount + 1 }
                if (total > 0) firstVisibleItemIndex.toFloat() / total.toFloat() else 0f
            }
            ScrollbarMode.SMOOTH_SCROLLING -> {
                if (totalItems > 0) firstVisibleItemIndex.toFloat() / totalItems.toFloat() else 0f
            }
        }
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val scrollableRatio = if (isDraggingScrollbar) 0.95f else 0.7f
            val maxOffsetPx = maxHeight * scrollableRatio
            val density = LocalDensity.current
            val scrollHeight = with(density) { maxHeight.toPx() * scrollableRatio }
            
            val displayOffset = if (isDraggingScrollbar) {
                dragOffset.coerceIn(0f, scrollHeight)
            } else {
                with(density) { maxOffsetPx.toPx() } * scrollPercentage
            }
            
            // Animated pill width and visibility
            val animatedPillWidth by animateDpAsState(
                targetValue = if (isDraggingScrollbar) 36.dp else 6.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "pillWidth"
            )
            
            val animatedPillAlpha by animateFloatAsState(
                targetValue = if (scrollbarVisible || isDraggingScrollbar) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "pillAlpha"
            )
            
            // Animated corner radius for smooth transition
            val animatedCornerRadius by animateDpAsState(
                targetValue = if (isDraggingScrollbar) 30.dp else 12.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "cornerRadius"
            )
            
            // Scrollbar thumb with larger touch area
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = with(density) { displayOffset.toDp() })
                    .width(36.dp) // Large touch area
                    .height(60.dp) // Increased touch height
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Instant detection on touch down
                            val down = awaitFirstDown(requireUnconsumed = false)
                            
                            // Immediately change appearance on touch
                            isDraggingScrollbar = true
                            scrollbarVisible = true
                            onScrollbarVisibilityChanged(true)
                            
                            val currentVisualOffset = with(density) { maxOffsetPx.toPx() } * scrollPercentage
                            if (dragOffset == 0f) {
                                dragOffset = currentVisualOffset
                            }
                            lastSnappedKey = ""
                            onOverlayTextChanged("") // Clear any overlay text
                            
                            // Handle drag
                            drag(down.id) { change ->
                                val dragAmount = change.positionChange()
                                change.consume()
                                dragOffset = (dragOffset + dragAmount.y).coerceIn(0f, scrollHeight)
                                val newPercentage = dragOffset / scrollHeight
                            
                            when (mode) {
                                ScrollbarMode.DATE_JUMPING -> {
                                    handleDateJumping(
                                        newPercentage = newPercentage,
                                        dateGroups = dateGroups,
                                        lastSnappedKey = lastSnappedKey,
                                        onKeyChanged = { lastSnappedKey = it },
                                        onHaptic = { 
                                            view.performHapticFeedback(
                                                HapticFeedbackConstants.CLOCK_TICK,
                                                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                            )
                                        },
                                        onScroll = { index ->
                                            coroutineScope.launch {
                                                gridState.scrollToItem(index)
                                            }
                                        },
                                        onOverlayTextChanged = onOverlayTextChanged
                                    )
                                }
                                ScrollbarMode.DAY_LEFT_JUMPING -> {
                                    handleDayLeftJumping(
                                        newPercentage = newPercentage,
                                        dayLeftGroups = dayLeftGroups,
                                        lastSnappedKey = lastSnappedKey,
                                        onKeyChanged = { lastSnappedKey = it },
                                        onHaptic = {
                                            view.performHapticFeedback(
                                                HapticFeedbackConstants.CLOCK_TICK,
                                                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                            )
                                        },
                                        onScroll = { index ->
                                            coroutineScope.launch {
                                                gridState.scrollToItem(index)
                                            }
                                        },
                                        onOverlayTextChanged = onOverlayTextChanged
                                    )
                                }
                                ScrollbarMode.SMOOTH_SCROLLING -> {
                                    handleSmoothScrolling(
                                        newPercentage = newPercentage,
                                        totalItems = totalItems,
                                        coroutineScope = coroutineScope,
                                        gridState = gridState
                                    )
                                }
                            }
                        }
                        
                        // Reset state when touch is released
                        isDraggingScrollbar = false
                        scrollbarVisible = false
                        onScrollbarVisibilityChanged(false)
                        onOverlayTextChanged("") // Clear overlay text
                    }
                }
            ) {
                // Visual scrollbar pill with smooth animations
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(animatedPillWidth) // Smooth animated width
                        .height(60.dp)
                        .alpha(animatedPillAlpha) // Smooth animated visibility
                        .background(
                            MaterialTheme.colorScheme.primary, // No alpha - full opacity
                            // Rounded corners only on left side with smooth animation
                            RoundedCornerShape(
                                topStart = animatedCornerRadius,
                                bottomStart = animatedCornerRadius,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Arrow icons - only visible when dragging, fully centered
                    if (isDraggingScrollbar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(animatedPillAlpha),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Up arrow - no padding
                                com.prantiux.pixelgallery.ui.icons.FontIcon(
                                    unicode = "\ue5c7",
                                    contentDescription = "Scroll up",
                                    size = 28.sp,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                // Down arrow - placed immediately after with no space
                                com.prantiux.pixelgallery.ui.icons.FontIcon(
                                    unicode = "\ue5c5",
                                    contentDescription = "Scroll down",
                                    size = 28.sp,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handle date jumping logic (for Photos/Gallery screen)
 */
private fun handleDateJumping(
    newPercentage: Float,
    dateGroups: List<DateGroupInfo>,
    lastSnappedKey: String,
    onKeyChanged: (String) -> Unit,
    onHaptic: () -> Unit,
    onScroll: (Int) -> Unit,
    onOverlayTextChanged: (String) -> Unit
) {
    if (dateGroups.isEmpty()) return
    
    val targetGroupIndex = if (newPercentage >= 0.99f) {
        dateGroups.lastIndex
    } else {
        (newPercentage * dateGroups.size).toInt().coerceIn(0, dateGroups.lastIndex)
    }
    
    val targetDate = dateGroups[targetGroupIndex].date
    
    if (targetDate != lastSnappedKey) {
        onKeyChanged(targetDate)
        onHaptic()
        
        // Calculate header index
        var headerIndex = 0
        for (i in 0 until targetGroupIndex) {
            headerIndex += dateGroups[i].itemCount + 1
        }
        
        // No overlay text needed
        
        onScroll(headerIndex)
    }
}

/**
 * Handle day-left jumping logic (for RecycleBin screen)
 */
private fun handleDayLeftJumping(
    newPercentage: Float,
    dayLeftGroups: List<DayLeftGroupInfo>,
    lastSnappedKey: String,
    onKeyChanged: (String) -> Unit,
    onHaptic: () -> Unit,
    onScroll: (Int) -> Unit,
    onOverlayTextChanged: (String) -> Unit
) {
    if (dayLeftGroups.isEmpty()) return
    
    val targetGroupIndex = if (newPercentage >= 0.99f) {
        dayLeftGroups.lastIndex
    } else {
        (newPercentage * dayLeftGroups.size).toInt().coerceIn(0, dayLeftGroups.lastIndex)
    }
    
    val targetKey = dayLeftGroups[targetGroupIndex].daysLeft.toString()
    
    if (targetKey != lastSnappedKey) {
        onKeyChanged(targetKey)
        onHaptic()
        
        // Calculate header index
        var headerIndex = 0
        for (i in 0 until targetGroupIndex) {
            headerIndex += dayLeftGroups[i].itemCount + 1
        }
        
        // No overlay text needed
        
        onScroll(headerIndex)
    }
}

/**
 * Handle smooth scrolling logic (for Album screen)
 */
private fun handleSmoothScrolling(
    newPercentage: Float,
    totalItems: Int,
    coroutineScope: CoroutineScope,
    gridState: LazyGridState
) {
    if (totalItems <= 0) return
    
    val targetIndex = (newPercentage * totalItems).toInt().coerceIn(0, totalItems - 1)
    
    coroutineScope.launch {
        gridState.scrollToItem(targetIndex)
    }
}

/**
 * Composable for displaying the center overlay text
 * Should be placed in the parent Box with Alignment.Center
 */
@Composable
fun ScrollbarOverlayText(
    text: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (isVisible && text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier
        )
    }
}
