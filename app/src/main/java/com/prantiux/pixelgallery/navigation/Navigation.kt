package com.prantiux.pixelgallery.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prantiux.pixelgallery.ui.screens.*
import com.prantiux.pixelgallery.ui.overlay.MediaOverlay
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

data class NavItem(
    val route: String,
    val title: String,
    val iconUnicode: String
)

sealed class Screen(val route: String, val title: String, val iconUnicode: String? = null) {
    object Photos : Screen("photos", "Gallery", FontIcons.Home)
    object Albums : Screen("albums", "Albums", FontIcons.Person)
    object Search : Screen("search", "Search", FontIcons.Search)
    object Settings : Screen("settings", "Settings", FontIcons.Settings)
    object AlbumDetail : Screen("album/{albumId}", "Album") {
        fun createRoute(albumId: String) = "album/$albumId"
    }
    object AllAlbums : Screen("all_albums", "All Albums")
    object RecycleBin : Screen("recycle_bin", "Recycle Bin")
}

// Smooth transition animations for navigation
private fun enterTransition() = fadeIn(
    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
) + slideInVertically(
    initialOffsetY = { it / 20 },
    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
)

private fun exitTransition() = fadeOut(
    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
) + slideOutVertically(
    targetOffsetY = { -it / 20 },
    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
)

private fun popEnterTransition() = fadeIn(
    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
) + slideInVertically(
    initialOffsetY = { -it / 20 },
    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
)

private fun popExitTransition() = fadeOut(
    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
) + slideOutVertically(
    targetOffsetY = { it / 20 },
    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
)

@Composable
fun PixelStyleFloatingNavBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemSelected: (NavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get system navigation bar inset
    val navBarInsetPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Outer padding here acts like margin so the pill truly floats
    Box(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = navBarInsetPadding)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainer, // Make pill fully opaque
            tonalElevation = 0.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    PixelNavBarItem(
                        item = item,
                        selected = selectedRoute == item.route,
                        onClick = { onItemSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelNavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // Spring animation specs for smooth, Pixel-like motion
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.95f,
        animationSpec = springSpec,
        label = "iconScale"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconColor"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "textColor"
    )
    
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = springSpec,
        label = "capsuleAlpha"
    )
    
    val capsuleScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.8f,
        animationSpec = springSpec,
        label = "capsuleScale"
    )
    
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple effect
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Soft capsule background for selected item - animates smoothly
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .graphicsLayer {
                        scaleX = capsuleScale
                        scaleY = capsuleScale
                        alpha = capsuleAlpha
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )
            
            // Icon with smooth scale animation
            FontIcon(
                unicode = item.iconUnicode,
                contentDescription = item.title,
                tint = iconColor,
                size = 24.sp,
                filled = selected,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Label with smooth color transition
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = textColor
        )
    }
}

@Composable
fun AppNavigation(viewModel: MediaViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()

    val bottomNavItems = listOf(
        NavItem(Screen.Photos.route, "Gallery", FontIcons.Home),
        NavItem(Screen.Albums.route, "Albums", FontIcons.Person),
        NavItem(Screen.Search.route, "Search", FontIcons.Search),
        NavItem(Screen.Settings.route, "Settings", FontIcons.Settings)
    )
    
    val selectionModeItems = listOf(
        NavItem("copy", "Copy to", FontIcons.Add),
        NavItem("share", "Share", FontIcons.Share),
        NavItem("delete", "Delete", FontIcons.Delete),
        NavItem("more", "More", FontIcons.MoreVert)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Photos.route,
                modifier = Modifier.fillMaxSize()
            ) {
            composable(
                route = Screen.Photos.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                PhotosScreen(viewModel = viewModel)
            }

            composable(
                route = Screen.Albums.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                AlbumsScreen(
                    onNavigateToAlbum = { albumId ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateToAllAlbums = {
                        navController.navigate(Screen.AllAlbums.route)
                    },
                    onNavigateToRecycleBin = {
                        navController.navigate(Screen.RecycleBin.route)
                    },
                    viewModel = viewModel
                )
            }

            composable(
                route = Screen.AllAlbums.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                AllAlbumsScreen(
                    onNavigateToAlbum = { albumId ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.RecycleBin.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                RecycleBinScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Search.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                SearchScreen(viewModel = viewModel, navController = navController)
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
                AlbumDetailScreen(
                    viewModel = viewModel,
                    albumId = albumId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToViewer = { }
                )
            }
        }

            // Media overlay state
            val overlayState by viewModel.overlayState.collectAsState()
            val allMedia = remember(images, videos) {
                (images + videos).sortedByDescending { it.dateAdded }
            }
            
            // Get search results for overlay
            val searchResults by viewModel.searchResults.collectAsState()
            
            // Filter media based on overlay state (album or all media)
            val overlayMediaItems = remember(overlayState.mediaType, overlayState.albumId, overlayState.searchQuery, allMedia, searchResults) {
                when (overlayState.mediaType) {
                    "album" -> allMedia.filter { it.bucketId == overlayState.albumId }
                        .sortedByDescending { it.dateAdded }
                    "search" -> {
                        // Use actual search results from SearchEngine, not simple filter
                        searchResults?.matchedMedia ?: emptyList()
                    }
                    else -> allMedia
                }
            }

            // Media overlay (always present, visibility controlled by state)
            // Wrapped in layout {} to prevent overlay from affecting gallery scroll position
            // Skip if trash mode (RecycleBinScreen shows its own overlay)
            if (overlayState.mediaType != "trash") {
                androidx.compose.ui.layout.Layout(
                    content = {
                        MediaOverlay(
                            viewModel = viewModel,
                            overlayState = overlayState,
                            mediaItems = overlayMediaItems,
                            onDismiss = { viewModel.hideMediaOverlay() }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { measurables, constraints ->
                    // Measure overlay independently
                    val placeable = measurables.firstOrNull()?.measure(constraints)
                    
                    // Layout at full size without affecting parent
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable?.place(0, 0)
                    }
                }
            }

            // Animate navbar hide/show when overlay is visible or scrollbar is being dragged
            val isOverlayVisible = overlayState.isVisible
            val isScrollbarDragging by viewModel.isScrollbarDragging.collectAsState()
            
            val navBarAnimProgress by animateFloatAsState(
                targetValue = if (isOverlayVisible || isScrollbarDragging) 0f else 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "navBarAnimation"
            )
            
            if (currentRoute in bottomNavItems.map { it.route }) {
                // Get system navigation bar inset to properly position the floating bar
                val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomPadding = if (navBarInset > 0.dp) 8.dp else 24.dp
                
                val selectedItems by viewModel.selectedItems.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current

                // Use regular navbar layout but with different icons in selection mode
                PixelStyleFloatingNavBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding)
                        .graphicsLayer {
                            translationY = (1f - navBarAnimProgress) * 200f
                            alpha = navBarAnimProgress
                        },
                    items = if (isSelectionMode && currentRoute == Screen.Photos.route) {
                        listOf(
                            NavItem("copy", "Copy", FontIcons.Add),
                            NavItem("share", "Share", FontIcons.Share),
                            NavItem("delete", "Delete", FontIcons.Delete),
                            NavItem("more", "More", FontIcons.MoreVert)
                        )
                    } else {
                        bottomNavItems
                    },
                    selectedRoute = currentRoute ?: Screen.Photos.route,
                    onItemSelected = { item ->
                        if (isSelectionMode && currentRoute == Screen.Photos.route) {
                            // Handle selection mode actions
                            when (item.route) {
                                "copy" -> { /* TODO: Copy functionality */ }
                                "share" -> { viewModel.shareSelectedItems(context) }
                                "delete" -> { 
                                    // Directly trigger delete - will show system dialog on Android 11+
                                    viewModel.deleteSelectedItems(context) { success ->
                                        // Handle result for older Android versions
                                    }
                                }
                                "more" -> { /* TODO: More options */ }
                            }
                        } else {
                            // Normal navigation
                            navController.navigate(item.route) {
                                popUpTo(Screen.Photos.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}
