package com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prantiux.pixelgallery.domain.model.editor.EditorItems
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.SupportiveLazyLayout
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.safeSystemGesturesPadding

@Composable
fun EditorSelector(
    modifier: Modifier = Modifier,
    selectedItem: EditorItems? = null,
    isSupportingPanel: Boolean,
    onItemClick: (EditorItems) -> Unit = {}
) {
    val displayItems = EditorItems.entries

    if (isSupportingPanel) {
        val padding = remember { PaddingValues(0.dp) }
        SupportiveLazyLayout(
            modifier = modifier
                .safeSystemGesturesPadding(onlyRight = true)
                .clipToBounds()
                .clip(RoundedCornerShape(16.dp)),
            isSupportingPanel = true,
            contentPadding = padding
        ) {
            itemsIndexed(
                items = displayItems,
                key = { _, it -> it.name }
            ) { index, editorItem ->
                EditorItem(
                    unicode = editorItem.icon,
                    title = editorItem.translatedName,
                    horizontal = true,
                    onItemClick = { onItemClick(editorItem) }
                )
                if (index < displayItems.size - 1) {
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }
        }
    } else {
        val scrollState = rememberScrollState()
        // Auto-scroll to selected item when it changes
        LaunchedEffect(selectedItem) {
            if (selectedItem != null) {
                val index = displayItems.indexOf(selectedItem)
                if (index >= 0) {
                    // Approximate scroll position: each item ~80dp + 4dp spacing
                    val targetPx = (index * 84 * 2.5f).toInt() // rough px approximation
                    scrollState.animateScrollTo(targetPx.coerceAtMost(scrollState.maxValue))
                }
            }
        }
        val view = LocalView.current
        Row(
            modifier = modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            displayItems.forEachIndexed { index, editorItem ->
                val isSelected = editorItem == selectedItem
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabText"
                )
                com.prantiux.pixelgallery.ui.components.AlbumTabAnimation(
                    index = index,
                    selectedIndex = displayItems.indexOf(selectedItem),
                    selectedColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedColor = Color.Transparent,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onItemClick(editorItem)
                    }
                ) {
                    Text(
                        text = editorItem.translatedName,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}
