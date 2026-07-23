package com.prantiux.pixelgallery.ui.components

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable scaffold for sub-pages with an Expressive Zenith-Style Header.
 * 
 * Features:
 * - Fixed sticky ExpressiveSubHeader with gradient glass background.
 * - Content scrolls beautifully underneath the header's gradient.
 * - Title and subtitle left-aligned with back arrow.
 * 
 * Usage Example:
 * ```
 * SubPageScaffold(
 *     title = "Grid type",
 *     subtitle = "Choose how to group your photos",
 *     onNavigateBack = { /* navigate back */ },
 *     actions = {
 *         IconButton(onClick = { /* action */ }) {
 *             Icon(FontIcons.MoreVert, "More")
 *         }
 *     }
 * ) {
 *     item {
 *         Text("Content item 1")
 *     }
 *     items(10) { index ->
 *         Text("Item $index")
 *     }
 * }
 * ```
 * 
 * DO use SubPageScaffold for:
 * - Settings sub-pages
 * - Detail/configuration screens
 * - Any secondary screen with scrollable content
 * 
 * DO NOT use for:
 * - Main tab screens (Photos, Albums, Search, Settings)
 * - Those should use MainTabHeader or their current implementation
 * 
 * @param title The main title text
 * @param subtitle Optional subtitle text
 * @param onNavigateBack Back navigation callback
 * @param actions Optional action icons in the top bar
 * @param useCustomCollapseColors Ignored in this implementation
 * @param content The scrollable content using LazyColumn scope
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPageScaffold(
    title: String,
    subtitle: String? = null,
    onNavigateBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    useCustomCollapseColors: Boolean = false,
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn(
            flingBehavior = rememberZenithFlingBehavior(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, 
                end = 16.dp, 
                top = 72.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp, 
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }

        ExpressiveSubHeader(
            title = title,
            subtitle = subtitle,
            onNavigateBack = onNavigateBack,
            actions = actions,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
