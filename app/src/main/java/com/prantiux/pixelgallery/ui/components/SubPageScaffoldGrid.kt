package com.prantiux.pixelgallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
 * Reusable scaffold for sub-pages with GRID layout and Material 3 Expressive Medium Flexible Header.
 * 
 * Same as SubPageScaffold but uses LazyVerticalGrid instead of LazyColumn.
 * Perfect for image galleries, album views, and grid-based content.
 * 
 * @param title The main title text
 * @param subtitle Optional subtitle text (fades during collapse)
 * @param onNavigateBack Back navigation callback
 * @param actions Optional action icons in the top bar
 * @param columns Number of grid columns (default 3)
 * @param useCustomCollapseColors When true, uses custom colors on collapse
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    val collapseFraction = if (scrollBehavior.state.collapsedFraction.isNaN()) {
        0f
    } else {
        scrollBehavior.state.collapsedFraction
    }
    val subtitleAlpha = (1f - collapseFraction * 1.2f).coerceIn(0.2f, 1f)
    
    val gridState = rememberLazyGridState()
    
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement
        ) {
            content()
        }
    }
}
