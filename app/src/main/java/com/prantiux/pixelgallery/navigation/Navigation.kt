@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)

package com.prantiux.pixelgallery.navigation

import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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
import com.prantiux.pixelgallery.ui.dialogs.CopyToAlbumDialog
import com.prantiux.pixelgallery.ui.dialogs.MoveToAlbumDialog
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

data class NavItem(
    val route: String,
    val title: String,
    val iconUnicode: String
)

sealed class Screen(val route: String, val title: String, val iconUnicode: String? = null) {
    object Photos : Screen("photos", "Photos", FontIcons.Home)
    object Albums : Screen("albums", "Albums", FontIcons.Person)
    object Search : Screen("search", "Search", FontIcons.Search)
    object Settings : Screen("settings", "Settings", FontIcons.Settings)
    object AlbumDetail : Screen("album/{albumId}", "Album") {
        fun createRoute(albumId: String) = "album/$albumId"
    }
    
    object SmartAlbumView : Screen("smartalbum/{albumId}", "Smart Album") {
        fun createRoute(albumId: String) = "smartalbum/$albumId"
    }
    object AllAlbums : Screen("all_albums", "All Albums")
    object RecycleBin : Screen("recycle_bin", "Recycle Bin")
    object Favorites : Screen("favorites", "Favourites")
    object GridTypeSetting : Screen("grid_type_setting", "Layout")
    object GalleryViewSetting : Screen("gallery_view_setting", "Photos view")
    object ThemeSetting : Screen("theme_setting", "Theme")
    object PreviewsSetting : Screen("previews_setting", "Previews")
    object GesturesSetting : Screen("gestures_setting", "Gestures")
    object PlaybackSetting : Screen("playback_setting", "Playback")
    object DebugSetting : Screen("debug_setting", "Debug")
}

// Expressive transition animations for navigation (Material 3 standard)
private fun enterTransition() = fadeIn(
    animationSpec = tween(durationMillis = 400, easing = EaseInOutCubic)
) + slideInVertically(
    initialOffsetY = { it / 15 },
    animationSpec = tween(durationMillis = 400, easing = EaseInOutCubic)
)

private fun exitTransition() = fadeOut(
    animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic)
) + slideOutVertically(
    targetOffsetY = { -it / 15 },
    animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic)
)

private fun popEnterTransition() = fadeIn(
    animationSpec = tween(durationMillis = 400, easing = EaseInOutCubic)
) + slideInVertically(
    initialOffsetY = { -it / 15 },
    animationSpec = tween(durationMillis = 400, easing = EaseInOutCubic)
)

private fun popExitTransition() = fadeOut(
    animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic)
) + slideOutVertically(
    targetOffsetY = { it / 15 },
    animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic)
)

/**
 * Draws a solid background behind the system navigation bar.
 * 
 * WHY THIS WORKS:
 * - WindowInsets.navigationBars provides the exact height of the system navigation bar
 * - windowInsetsBottomHeight creates a Spacer with that exact height
 * - Background modifier fills that space with solid color
 * - Works on all Android versions (not specific to Android 15+)
 * 
 * IMPORTANT:
 * - Must be placed BEHIND all other content in the root container
 * - Uses MaterialTheme.colorScheme.surface for background
 * - Logs dimensions for debugging
 */
@Composable
fun NavigationBarBackground() {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val navBarHeight = WindowInsets.navigationBars.getBottom(density)
    
    // Log for debugging
    androidx.compose.runtime.LaunchedEffect(navBarHeight, backgroundColor) {
        Log.d("NavigationBarBackground", "=== NAV BAR BACKGROUND ===")
        Log.d("NavigationBarBackground", "Height: ${navBarHeight}px")
        Log.d("NavigationBarBackground", "MaterialTheme.colorScheme.surface: #${Integer.toHexString(backgroundColor.toArgb())}")
        Log.d("NavigationBarBackground", "Color RGB: R=${backgroundColor.red}, G=${backgroundColor.green}, B=${backgroundColor.blue}")
        if (navBarHeight == 0) {
            Log.w("NavigationBarBackground", "⚠ Navigation bar height is 0 - background will not be visible")
        } else {
            Log.i("NavigationBarBackground", "✓ Background drawable created: ${navBarHeight}px tall")
        }
        Log.d("NavigationBarBackground", "========================")
    }
    
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(backgroundColor)
    )
}

@Composable
fun PixelStyleFloatingNavBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemSelected: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false
) {
    // Get system navigation bar inset
    val navBarInsetPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Outer padding here acts like margin so the pill truly floats
    Box(
        modifier = modifier
            .padding(start = 24.dp, end = 24.dp, bottom = navBarInsetPadding)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = if (isSelectionMode) {
                SmoothCornerShape(
                    cornerRadiusTL = 12.dp, cornerRadiusTR = 12.dp,
                    cornerRadiusBL = 26.dp, cornerRadiusBR = 26.dp,
                    smoothnessAsPercent = 60
                )
            } else {
                SmoothCornerShape(26.dp, 60)
            },
            color = MaterialTheme.colorScheme.surfaceContainer, // Make pill fully opaque
            tonalElevation = 0.dp,
            shadowElevation = 8.dp // Increased shadow for better depth
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
                        shape = SmoothCornerShape(16.dp, 60)
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
fun AppNavigation(
    viewModel: MediaViewModel,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore,
    defaultTab: String,
    lastUsedTab: String,
    onTabChanged: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumRepository = remember { com.prantiux.pixelgallery.data.AlbumRepository(context) }
    val videoPositionDataStore = remember { com.prantiux.pixelgallery.data.VideoPositionDataStore(context) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()
    
    // Determine start destination based on default tab setting
    val startDestination = remember(defaultTab, lastUsedTab) {
        val tab = if (defaultTab == "Last used") {
            // Don't open Settings tab on launch, default to Photos
            if (lastUsedTab == "Settings") "Photos" else lastUsedTab
        } else {
            defaultTab
        }
        
        when (tab) {
            "Albums" -> Screen.Albums.route
            "Search" -> Screen.Search.route
            else -> Screen.Photos.route
        }
    }
    
    // Track current tab changes
    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screen.Photos.route -> onTabChanged("Photos")
            Screen.Albums.route -> onTabChanged("Albums")
            Screen.Search.route -> onTabChanged("Search")
            // Settings is no longer a tab, accessed from header icon only
        }
    }

    // Bottom navigation items - Settings removed (open from header only)
    val bottomNavItems = listOf(
        NavItem(Screen.Photos.route, "Photos", FontIcons.Home),
        NavItem(Screen.Albums.route, "Albums", FontIcons.Person),
        NavItem(Screen.Search.route, "Search", FontIcons.Search)
    )
    
    val selectionModeItems = listOf(
        NavItem("copy", "Copy to", FontIcons.Copy),
        NavItem("share", "Share", FontIcons.Share),
        NavItem("delete", "Delete", FontIcons.Delete),
        NavItem("more", "More", FontIcons.MoreVert)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Global navigation bar background - MUST be first to render behind all content
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                NavigationBarBackground()
            }
            
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
            composable(
                route = Screen.Photos.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                PhotosScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
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
                    onNavigateToFavorites = {
                        navController.navigate(Screen.Favorites.route)
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
                    onNavigateBack = { navController.popBackStack() },
                    settingsDataStore = settingsDataStore,
                    videoPositionDataStore = videoPositionDataStore
                )
            }
            
            composable(
                route = Screen.Favorites.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                FavoritesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    settingsDataStore = settingsDataStore
                )
            }

            composable(
                route = Screen.Search.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                SearchScreen(
                    viewModel = viewModel, 
                    navController = navController,
                    settingsDataStore = settingsDataStore
                )
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToGridType = { navController.navigate(Screen.GridTypeSetting.route) },
                    onNavigateToGalleryView = { navController.navigate(Screen.GalleryViewSetting.route) },
                    onNavigateToTheme = { navController.navigate(Screen.ThemeSetting.route) },
                    onNavigateToPreviews = { navController.navigate(Screen.PreviewsSetting.route) },
                    onNavigateToGestures = { navController.navigate(Screen.GesturesSetting.route) },
                    onNavigateToPlayback = { navController.navigate(Screen.PlaybackSetting.route) },
                    onNavigateToDebug = { navController.navigate(Screen.DebugSetting.route) },
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Screen.GridTypeSetting.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.LayoutSettingScreen(
                    viewModel = viewModel,
                    settingsDataStore = settingsDataStore,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.GalleryViewSetting.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.GalleryViewSettingScreen(
                    albumRepository = albumRepository,
                    settingsDataStore = settingsDataStore,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ThemeSetting.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.ThemeSettingScreen(
                    settingsDataStore = settingsDataStore,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.PreviewsSetting.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.PreviewsSettingScreen(
                    settingsDataStore = settingsDataStore,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.GesturesSetting.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.GesturesSettingScreen(
                    settingsDataStore = settingsDataStore,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.PlaybackSetting.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.PlaybackSettingScreen(
                    settingsDataStore = settingsDataStore,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.DebugSetting.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                com.prantiux.pixelgallery.ui.screens.settings.DebugSettingScreen(
                    onBackClick = { navController.popBackStack() },
                    viewModel = viewModel
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
                    onNavigateToViewer = { },
                    settingsDataStore = settingsDataStore
                )
            }
            
            composable(
                route = Screen.SmartAlbumView.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
                SmartAlbumViewScreen(
                    viewModel = viewModel,
                    albumId = albumId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToViewer = { },
                    settingsDataStore = settingsDataStore
                )
            }
        }

            // Media overlay state
            val overlayState by viewModel.overlayState.collectAsState()
            val allMedia = remember(images, videos) {
                (images + videos).sortedByDescending { it.dateAdded }
            }
            
            // Get unfiltered media for album overlay (to show albums even if unchecked in gallery view)
            val allImagesUnfiltered by viewModel.allImagesUnfiltered.collectAsState()
            val allVideosUnfiltered by viewModel.allVideosUnfiltered.collectAsState()
            val allMediaUnfiltered = remember(allImagesUnfiltered, allVideosUnfiltered) {
                (allImagesUnfiltered + allVideosUnfiltered).sortedByDescending { it.dateAdded }
            }
            
            // Get search results for overlay
            val searchResults by viewModel.searchResults.collectAsState()
            
            // Get favorite items for overlay
            val favoriteItems by viewModel.favoriteItems.collectAsState()
            
            // Get context for smart album loading
            val context = androidx.compose.ui.platform.LocalContext.current
            
            val isAlbumOverlay = overlayState.mediaType == "album" || overlayState.mediaType == "smartalbum"
            val isSmartAlbumOverlay = isAlbumOverlay && SmartAlbumGenerator.isSmartAlbum(overlayState.albumId)

            // Filter media based on overlay state (album or all media)
            val overlayMediaItems = remember(overlayState.mediaType, overlayState.albumId, overlayState.searchQuery, allMedia, allMediaUnfiltered, searchResults, favoriteItems) {
                when (overlayState.mediaType) {
                    "album", "smartalbum" -> {
                        if (isSmartAlbumOverlay) {
                            // Smart album - needs async loading, return empty for now
                            // Will be populated by LaunchedEffect below
                            emptyList()
                        } else {
                            // Regular album - filter by bucketId
                            allMediaUnfiltered.filter { it.bucketId == overlayState.albumId }
                                .sortedByDescending { it.dateAdded }
                        }
                    }
                    "search" -> {
                        // Use actual search results from SearchEngine, not simple filter
                        searchResults?.matchedMedia ?: emptyList()
                    }
                    "favorites" -> favoriteItems
                    else -> allMedia
                }
            }
            
            // Smart album media state (loaded asynchronously)
            var smartAlbumOverlayMedia by remember { mutableStateOf<List<com.prantiux.pixelgallery.model.MediaItem>?>(null) }
            val coroutineScope = rememberCoroutineScope()
            
            // Load smart album media if overlay is showing a smart album
            androidx.compose.runtime.LaunchedEffect(overlayState.mediaType, overlayState.albumId, allMediaUnfiltered) {
                if (isSmartAlbumOverlay) {
                    coroutineScope.launch {
                        val smartMedia = SmartAlbumGenerator.getMediaForSmartAlbum(
                            context,
                            overlayState.albumId,
                            allMediaUnfiltered
                        )
                        smartAlbumOverlayMedia = smartMedia.sortedByDescending { it.dateAdded }
                    }
                }
            }
            
            // Final media list with smart album support
            val finalOverlayMediaItems = remember(overlayMediaItems, smartAlbumOverlayMedia, overlayState.mediaType, overlayState.albumId) {
                if (isSmartAlbumOverlay) {
                    smartAlbumOverlayMedia ?: emptyList()
                } else {
                    overlayMediaItems
                }
            }

            // Media overlay (always present, visibility controlled by state)
            // Wrapped in layout {} to prevent overlay from affecting photo grid scroll position
            // Skip if trash mode (RecycleBinScreen shows its own overlay)
            if (overlayState.mediaType != "trash") {
                androidx.compose.ui.layout.Layout(
                    content = {
                        com.prantiux.pixelgallery.ui.overlay.MediaOverlay(
                            viewModel = viewModel,
                            overlayState = overlayState,
                            mediaItems = finalOverlayMediaItems,
                            settingsDataStore = settingsDataStore,
                            videoPositionDataStore = videoPositionDataStore,
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
            
            // Copy to Album Dialog
            val showCopyDialog by viewModel.showCopyToAlbumDialog.collectAsState()
            if (showCopyDialog) {
                CopyToAlbumDialog(
                    viewModel = viewModel,
                    albumRepository = albumRepository,
                    onDismiss = { viewModel.hideCopyToAlbumDialog() }
                )
            }
            
            // Move to Album Dialog
            val showMoveDialog by viewModel.showMoveToAlbumDialog.collectAsState()
            if (showMoveDialog) {
                MoveToAlbumDialog(
                    viewModel = viewModel,
                    albumRepository = albumRepository,
                    onDismiss = { viewModel.hideMoveToAlbumDialog() }
                )
            }
            
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
                var showMoreMenu by remember { mutableStateOf(false) }
                
                // Theme-aware gradient color
                val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                val gradientColor = if (isDarkTheme) {
                    androidx.compose.ui.graphics.Color.Black
                } else {
                    androidx.compose.ui.graphics.Color.White
                }

                // Gradient background behind navigation bar
                // Only show when app navigation bar is visible (not in media overlay)
                // Solid color from system nav to bottom 50% height, then smooth gradient to transparent
                // Extended to go slightly above the app navigation bar
                if (!isOverlayVisible) {
                    val navBarHeight = 72.dp
                    val extendedHeight = navBarInset + navBarHeight + bottomPadding + 14.dp // Extension above
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    
                    // 50% solid from bottom, 50% smooth gradient to transparent
                    val solidHeight = extendedHeight * 0.5f
                    val gradientStartY = with(density) { (extendedHeight - solidHeight).toPx() }
                    val totalHeightPx = with(density) { extendedHeight.toPx() }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(extendedHeight)
                            .graphicsLayer {
                                alpha = navBarAnimProgress // Fade with navbar
                            }
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        gradientColor.copy(alpha = 0.3f),
                                        gradientColor.copy(alpha = 0.6f),
                                        gradientColor.copy(alpha = 0.85f),
                                        gradientColor,
                                        gradientColor
                                    ),
                                    startY = 0f,
                                    endY = totalHeightPx
                                )
                            )
                    )
                }

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
                    isSelectionMode = isSelectionMode && currentRoute == Screen.Photos.route,
                    items = if (isSelectionMode && currentRoute == Screen.Photos.route) {
                        listOf(
                            NavItem("copy", "Copy to", FontIcons.Copy),
                            NavItem("share", "Share", FontIcons.Share),
                            NavItem("delete", "Delete", FontIcons.Delete),
                            NavItem("more", "More", FontIcons.MoreVert)
                        )
                    } else {
                        bottomNavItems
                    },
                    selectedRoute = currentRoute ?: Screen.Photos.route,
                    onItemSelected = { item ->
                        if (isSelectionMode || item.route == "more") {
                            // Handle selection mode actions for any screen
                            when (item.route) {
                                "copy" -> { 
                                    // Show copy to album dialog
                                    viewModel.showCopyToAlbumDialog(selectedItems.toList())
                                }
                                "share" -> { viewModel.shareSelectedItems(context) }
                                "delete" -> { 
                                    // Directly trigger delete - will show system dialog on Android 11+
                                    viewModel.deleteSelectedItems(context) { success ->
                                        // Handle result for older Android versions
                                    }
                                }
                                "more" -> { showMoreMenu = true }
                            }
                        }
                        if (!isSelectionMode) {
                            // Normal navigation
                            navController.navigate(item.route) {
                                popUpTo(Screen.Photos.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
                
                // More options dropdown for selection mode
                // Show on all screens when More button is clicked
                if (showMoreMenu) {
                    // Extract albumId from current route if we're in an album screen
                    val currentAlbumId = remember(currentRoute) {
                        currentRoute?.let {
                            if (it.startsWith("album/")) {
                                it.substringAfter("album/").substringBefore("/")
                            } else null
                        }
                    }
                    
                    // Check if we're in a smart album
                    val isInSmartAlbum = currentAlbumId?.let { 
                        SmartAlbumGenerator.isSmartAlbum(it) 
                    } ?: false
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = navBarInset + 80.dp, end = 16.dp)
                    ) {
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.widthIn(min = 220.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                            shape = SmoothCornerShape(20.dp, 60)
                        ) {
                            // Set as wallpaper
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Surface(
                                            shape = SmoothCornerShape(12.dp, 60),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                FontIcon(
                                                    unicode = FontIcons.Image,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    size = 20.sp
                                                )
                                            }
                                        }
                                        Text(
                                            "Set as wallpaper",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    showMoreMenu = false
                                    selectedItems.firstOrNull()?.let { item ->
                                        if (!item.isVideo) {
                                            val wallpaperIntent = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA).apply {
                                                setDataAndType(item.uri, "image/*")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(wallpaperIntent, "Set as"))
                                        } else {
                                            android.widget.Toast.makeText(context, "Cannot set video as wallpaper", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            // Move to album
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Surface(
                                            shape = SmoothCornerShape(12.dp, 60),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                FontIcon(
                                                    unicode = FontIcons.Move,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    size = 20.sp
                                                )
                                            }
                                        }
                                        Text(
                                            "Move to album",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    showMoreMenu = false
                                    // Show move to album dialog
                                    viewModel.showMoveToAlbumDialog(selectedItems.toList())
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            
                            // Hide from this label (only for smart albums)
                            if (isInSmartAlbum) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Surface(
                                                shape = SmoothCornerShape(12.dp, 60),
                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    FontIcon(
                                                        unicode = FontIcons.VisibilityOff,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        size = 20.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                "Hide from this label",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        // Hide selected items from this smart album
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            viewModel.hideFromSmartAlbum(context, currentAlbumId, selectedItems.toList())
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                viewModel.exitSelectionMode()
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Hidden ${selectedItems.size} ${if (selectedItems.size == 1) "item" else "items"} from this label",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
