package com.prantiux.pixelgallery.ui.components

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable scaffold for sub-pages with GRID layout and an Expressive Zenith-Style Header.
 * 
 * Same as SubPageScaffold but uses LazyVerticalGrid instead of LazyColumn.
 * Perfect for image galleries, album views, and grid-based content.
 * 
 * @param title The main title text
 * @param subtitle Optional subtitle text (ignored in ExpressiveSubHeader)
 * @param onNavigateBack Back navigation callback
 * @param actions Optional action icons in the top bar
 * @param columns Number of grid columns (default 3)
 * @param useCustomCollapseColors Ignored in this implementation
 * @param contentPadding Padding for the grid content
 * @param horizontalArrangement Horizontal arrangement (default 2.dp spacing)
 * @param verticalArrangement Vertical arrangement (default 2.dp spacing)
 * @param content The scrollable grid content using LazyVerticalGrid scope
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPageScaffoldGrid(
    title: String,
    subtitle: String? = null,
    onNavigateBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    columns: Int = 3,
    useCustomCollapseColors: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 2.dp, end = 2.dp, top = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(2.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(2.dp),
    content: LazyGridScope.() -> Unit
) {
    val gridState = rememberLazyGridState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LazyVerticalGrid(
            flingBehavior = rememberZenithFlingBehavior(),
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                end = contentPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = 72.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement
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
