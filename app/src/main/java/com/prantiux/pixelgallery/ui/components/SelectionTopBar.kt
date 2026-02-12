@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * Selection Top Bar - Reusable Component
 * 
 * A pill-shaped overlay that slides down from the top when selection mode is active.
 * Shows the number of selected items and provides a cancel button to clear selection.
 * 
 * Features:
 * - Smooth spring animations
 * - Animated appearance from top with slide and fade
 * - Pill shape with rounded corners
 * - Selected count on the left
 * - Cancel button on the right
 * 
 * Usage:
 * ```
 * SelectionTopBar(
 *     isVisible = isSelectionMode,
 *     selectedCount = selectedItems.size,
 *     onCancelSelection = { viewModel.exitSelectionMode() },
 *     view = view
 * )
 * ```
 */
@Composable
fun SelectionTopBar(
    isVisible: Boolean,
    selectedCount: Int,
    onCancelSelection: () -> Unit,
    modifier: Modifier = Modifier,
    successMessage: String? = null,
    view: View? = null
) {
    // Animated visibility with custom enter/exit transitions - slides up from bottom
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it }, // Slide up from bottom
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it }, // Slide down to bottom
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(
            animationSpec = tween(200, easing = FastOutLinearInEasing)
        ),
        modifier = modifier
    ) {
        SelectionTopBarContent(
            selectedCount = selectedCount,
            onCancelSelection = {
                view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCancelSelection()
            },
            successMessage = successMessage
        )
    }
}

/**
 * Internal content of the selection top bar
 */
@Composable
private fun SelectionTopBarContent(
    selectedCount: Int,
    onCancelSelection: () -> Unit,
    successMessage: String?
) {
    // Animate the count change with a subtle scale effect
    val countAnimatable = remember { Animatable(selectedCount.toFloat()) }
    
    LaunchedEffect(selectedCount) {
        countAnimatable.animateTo(
            targetValue = selectedCount.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            )
        )
    }
    
    // Container with proper spacing from edges - matches nav bar padding (24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 0.dp)
    ) {
        // Pill-shaped container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp), // Fully rounded pill shape
            color = MaterialTheme.colorScheme.surfaceContainer, // Match navigation bar color
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Selected count or success message
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Icon (Checkmark for selection, Check circle for success)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (successMessage != null) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            FontIcon(
                                unicode = if (successMessage != null) 
                                    FontIcons.Check 
                                else 
                                    FontIcons.Done,
                                contentDescription = null,
                                tint = if (successMessage != null)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.primary,
                                size = 20.sp
                            )
                        }
                    }
                    
                    // Text with animation
                    Text(
                        text = successMessage ?: "$selectedCount selected",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // Right side: Cancel button (hidden during success message)
                if (successMessage == null) {
                    TextButton(
                        onClick = onCancelSelection,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}
