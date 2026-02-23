package com.prantiux.pixelgallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * Reusable scaffold for sub-pages with Material 3 Expressive Medium Flexible Header.
 * 
 * Features:
 * - MediumTopAppBar that collapses smoothly on scroll
 * - Shows title and optional subtitle when expanded
 * - Collapses into compact top app bar with exitUntilCollapsed behavior
 * - Consistent with Material 3 Expressive design (Android 16 Settings style)
 * - Optimized for performance (no recompositions on scroll)
 * 
 * Usage Example:
 * ```
 * SubPageScaffold(
 *     title = "Grid type",
 *     subtitle = "Choose how to group your photos",
 *     onNavigateBack = { /* navigate back */ },
 *     actions = {
 *         IconButton(onClick = { /* action */ }) {
 *             Icon(Icons.Default.MoreVert, "More")
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
 * @param subtitle Optional subtitle text (fades during collapse)
 * @param onNavigateBack Back navigation callback
 * @param actions Optional action icons in the top bar
 * @param useCustomCollapseColors When true, uses custom colors on collapse (for special pages)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )
    
    // Calculate collapse progress (0 = expanded, 1 = collapsed)
    val collapseFraction = if (scrollBehavior.state.collapsedFraction.isNaN()) {
        0f
    } else {
        scrollBehavior.state.collapsedFraction
    }
    val subtitleAlpha = (1f - collapseFraction * 1.2f).coerceIn(0.2f, 1f)
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .alpha(subtitleAlpha)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        FontIcon(
                            unicode = FontIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}
