package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import android.view.HapticFeedbackConstants
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import kotlinx.coroutines.launch
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * Modern Albums Screen with rectangular pill tabs
 * Features:
 * - Header with statusBarsPadding
 * - Rectangular pill tabs for main albums (music app style)
 * - Other albums section with same pill design
 * - Wide special action buttons at bottom
 */
@Composable
fun AlbumsScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToAllAlbums: () -> Unit,
    onNavigateToRecycleBin: () -> Unit = {},
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var categorizedAlbums by remember { mutableStateOf<CategorizedAlbums?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedMainAlbumIndex by remember { mutableStateOf(0) }

    // Load albums on launch
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val repository = AlbumRepository(context)
                val result = repository.loadCategorizedAlbums()
                categorizedAlbums = result
                selectedMainAlbumIndex = 0
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (categorizedAlbums == null || categorizedAlbums!!.mainAlbums.isEmpty()) {
            Text(
                text = "No albums found",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            val navBarHeight = calculateFloatingNavBarHeight()
            
            Column(modifier = Modifier.fillMaxSize()) {
                // Sticky Header - use MainTabHeader for main tabs
                com.prantiux.pixelgallery.ui.components.MainTabHeader(title = "Albums")
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ),
                    contentPadding = PaddingValues(top = 16.dp, bottom = navBarHeight)
                ) {
                    // Main Album Tabs (rectangular pills) with View All button
                    item {
                        MainAlbumTabs(
                            albums = categorizedAlbums!!.mainAlbums,
                            selectedIndex = selectedMainAlbumIndex,
                            onTabSelected = { index ->
                                selectedMainAlbumIndex = index
                            },
                            onViewAllClick = onNavigateToAllAlbums
                        )
                    }

                    // Highlighted section for selected album
                    if (selectedMainAlbumIndex < categorizedAlbums!!.mainAlbums.size) {
                        item {
                            HighlightAlbumSection(
                                albums = categorizedAlbums!!.mainAlbums,
                                viewModel = viewModel,
                                currentTabIndex = selectedMainAlbumIndex,
                                onTabChange = { newIndex ->
                                    selectedMainAlbumIndex = newIndex
                                },
                                onViewAllClick = { 
                                    onNavigateToAlbum(categorizedAlbums!!.mainAlbums[selectedMainAlbumIndex].id) 
                                }
                            )
                        }
                    }

                    // Other Albums Section
                    if (categorizedAlbums!!.otherAlbums.isNotEmpty()) {
                        item {
                            OtherAlbumsSection(
                                albums = categorizedAlbums!!.otherAlbums,
                                onAlbumClick = onNavigateToAlbum
                            )
                        }
                    }

                    // Special Action Buttons
                    item {
                        SpecialActionButtons(
                            onStarredClick = { /* TODO */ },
                            onSecureClick = { /* TODO */ },
                            onRecycleBinClick = onNavigateToRecycleBin
                        )
                    }
                }
            }
        }
    }
}

/**
 * Header with "Albums" title - respects system status bar
 */
@Composable
fun AlbumsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Text(
            text = "Albums",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

/**
 * Main Album Tabs - rectangular pills with rounded corners (music app style)
 * Acts as real tabs with one active at a time, plus View All button
 */
@Composable
fun MainAlbumTabs(
    albums: List<Album>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onViewAllClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(albums.size) { index ->
            RectangularPillTab(
                label = albums[index].name,
                count = albums[index].itemCount,
                isSelected = selectedIndex == index,
                onClick = { onTabSelected(index) }
            )
        }
        
        // View All button at the end
        item {
            ViewAllPillButton(
                onClick = onViewAllClick
            )
        }
    }
}

/**
 * Rectangular pill tab with indicator - inspired by music app tabs
 */
@Composable
fun RectangularPillTab(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = backgroundColor,
            modifier = Modifier.height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = contentColor
                )
            }
        }
        
        // Indicator line below selected tab
        if (isSelected) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * View All pill button - same style as album tabs but without selection
 */
@Composable
fun ViewAllPillButton(
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .height(52.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "View All",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Highlighted section showing the selected main album with 6-photo grid
 * Supports horizontal swipe gestures to switch between tabs with smooth animation
 */
@Composable
fun HighlightAlbumSection(
    albums: List<Album>,
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    currentTabIndex: Int,
    onTabChange: (Int) -> Unit,
    onViewAllClick: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    val dragOffset = remember { Animatable(0f) }
    var cardWidth by remember { mutableStateOf(0f) }
    val swipeThreshold = 0.3f // 30% of card width to trigger tab change
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .onGloballyPositioned { coordinates ->
                cardWidth = coordinates.size.width.toFloat()
            }
            .pointerInput(currentTabIndex) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        // Cancel any ongoing animation
                        scope.launch { dragOffset.stop() }
                    },
                    onDragEnd = {
                        scope.launch {
                            val progress = dragOffset.value / cardWidth
                            val threshold = swipeThreshold
                            
                            // Determine target index based on drag direction and threshold
                            val targetIndex = when {
                                progress < -threshold && currentTabIndex < albums.size - 1 -> {
                                    // Swiped left enough -> next tab
                                    currentTabIndex + 1
                                }
                                progress > threshold && currentTabIndex > 0 -> {
                                    // Swiped right enough -> previous tab
                                    currentTabIndex - 1
                                }
                                else -> currentTabIndex // Stay on current
                            }
                            
                            if (targetIndex != currentTabIndex) {
                                // Complete the swipe transition
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                val targetOffset = if (targetIndex > currentTabIndex) -cardWidth else cardWidth
                                dragOffset.animateTo(
                                    targetValue = targetOffset,
                                    animationSpec = tween(200)
                                )
                                onTabChange(targetIndex)
                                dragOffset.snapTo(0f)
                            } else {
                                // Snap back to center
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(200)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffset.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        scope.launch {
                            val newOffset = dragOffset.value + dragAmount
                            
                            // Apply resistance at boundaries
                            val constrainedOffset = when {
                                currentTabIndex == 0 && newOffset > 0 -> {
                                    // At first tab, resist right swipe
                                    newOffset * 0.3f
                                }
                                currentTabIndex == albums.size - 1 && newOffset < 0 -> {
                                    // At last tab, resist left swipe
                                    newOffset * 0.3f
                                }
                                else -> newOffset
                            }
                            
                            dragOffset.snapTo(constrainedOffset)
                        }
                        change.consume()
                    }
                )
            }
    ) {
        // Render cards in layers: previous (if exists), current, next (if exists)
        val currentAlbum = albums[currentTabIndex]
        val previousAlbum = if (currentTabIndex > 0) albums[currentTabIndex - 1] else null
        val nextAlbum = if (currentTabIndex < albums.size - 1) albums[currentTabIndex + 1] else null
        
        val offset = dragOffset.value
        val progress = if (cardWidth > 0) offset / cardWidth else 0f
        
        // Previous card (revealed when swiping right)
        previousAlbum?.let {
            AlbumPreviewCard(
                album = it,
                viewModel = viewModel,
                onViewAllClick = onViewAllClick,
                modifier = Modifier
                    .zIndex(0f)
                    .graphicsLayer {
                        translationX = -cardWidth + offset
                        alpha = if (offset > 0) 1f else 0f
                    }
            )
            // Translucent overlay on previous card
            if (offset > 0) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(0.5f)
                        .graphicsLayer {
                            translationX = -cardWidth + offset
                        }
                        .background(Color.Black.copy(alpha = (1f - progress).coerceIn(0f, 0.4f)))
                )
            }
        }
        
        // Next card (revealed when swiping left)
        nextAlbum?.let {
            AlbumPreviewCard(
                album = it,
                viewModel = viewModel,
                onViewAllClick = onViewAllClick,
                modifier = Modifier
                    .zIndex(0f)
                    .graphicsLayer {
                        translationX = cardWidth + offset
                        alpha = if (offset < 0) 1f else 0f
                    }
            )
            // Translucent overlay on next card
            if (offset < 0) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(0.5f)
                        .graphicsLayer {
                            translationX = cardWidth + offset
                        }
                        .background(Color.Black.copy(alpha = (1f + progress).coerceIn(0f, 0.4f)))
                )
            }
        }
        
        // Current card (moves with finger)
        AlbumPreviewCard(
            album = currentAlbum,
            viewModel = viewModel,
            onViewAllClick = onViewAllClick,
            modifier = Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationX = offset
                }
        )
    }
}

/**
 * Individual album preview card with 6-photo grid
 */
@Composable
fun AlbumPreviewCard(
    album: Album,
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // 6-photo grid (2 rows x 3 columns) with clickable items
            if (album.topMediaItems.isNotEmpty()) {
                val itemsToShow = album.topMediaItems.take(6)
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // First row (3 items)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsToShow.take(3).forEachIndexed { index, mediaItem ->
                            var thumbnailBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                AsyncImage(
                                    model = mediaItem.uri,
                                    contentDescription = mediaItem.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(index))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .onGloballyPositioned { coordinates ->
                                            val position = coordinates.positionInWindow()
                                            val size = coordinates.size
                                            thumbnailBounds = androidx.compose.ui.geometry.Rect(
                                                position.x,
                                                position.y,
                                                position.x + size.width,
                                                position.y + size.height
                                            )
                                        }
                                        .clickable {
                                            thumbnailBounds?.let { bounds ->
                                                viewModel.showMediaOverlay(
                                                    mediaType = "album",
                                                    albumId = album.id,
                                                    selectedIndex = index,
                                                    thumbnailBounds = com.prantiux.pixelgallery.viewmodel.MediaViewModel.ThumbnailBounds(
                                                        startLeft = bounds.left,
                                                        startTop = bounds.top,
                                                        startWidth = bounds.width,
                                                        startHeight = bounds.height
                                                    )
                                                )
                                            } ?: run {
                                                viewModel.showMediaOverlay(
                                                    mediaType = "album",
                                                    albumId = album.id,
                                                    selectedIndex = index,
                                                    thumbnailBounds = null
                                                )
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Video indicator
                                if (mediaItem.isVideo) {
                                    FontIcon(
                                        unicode = FontIcons.PlayArrow,
                                        contentDescription = "Video",
                                        size = 32.sp,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            )
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                        // Fill empty slots if less than 3 items
                        repeat(3 - minOf(3, itemsToShow.size)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(itemsToShow.size))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Second row (3 items)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsToShow.drop(3).take(3).forEachIndexed { relIndex, mediaItem ->
                            val index = relIndex + 3
                            var thumbnailBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                AsyncImage(
                                    model = mediaItem.uri,
                                    contentDescription = mediaItem.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(index))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .onGloballyPositioned { coordinates ->
                                            val position = coordinates.positionInWindow()
                                            val size = coordinates.size
                                            thumbnailBounds = androidx.compose.ui.geometry.Rect(
                                                position.x,
                                                position.y,
                                                position.x + size.width,
                                                position.y + size.height
                                            )
                                        }
                                        .clickable {
                                            thumbnailBounds?.let { bounds ->
                                                viewModel.showMediaOverlay(
                                                    mediaType = "album",
                                                    albumId = album.id,
                                                    selectedIndex = index,
                                                    thumbnailBounds = com.prantiux.pixelgallery.viewmodel.MediaViewModel.ThumbnailBounds(
                                                        startLeft = bounds.left,
                                                        startTop = bounds.top,
                                                        startWidth = bounds.width,
                                                        startHeight = bounds.height
                                                    )
                                                )
                                            } ?: run {
                                                viewModel.showMediaOverlay(
                                                    mediaType = "album",
                                                    albumId = album.id,
                                                    selectedIndex = index,
                                                    thumbnailBounds = null
                                                )
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Video indicator
                                if (mediaItem.isVideo) {
                                    FontIcon(
                                        unicode = FontIcons.PlayArrow,
                                        contentDescription = "Video",
                                        size = 32.sp,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            )
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                        // Fill empty slots if less than 6 items total
                        repeat(3 - minOf(3, maxOf(0, itemsToShow.size - 3))) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(itemsToShow.size + it))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            } else {
                // Fallback if no media items available
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No media available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Album info with View All button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${album.itemCount} items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onViewAllClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("View All")
                }
            }
        }
    }
}

/**
 * Other Albums Section - horizontally scrollable pills
 */
@Composable
fun OtherAlbumsSection(
    albums: List<Album>,
    onAlbumClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Other Albums",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(albums.size) { index ->
                OtherAlbumPillButton(
                    album = albums[index],
                    onClick = { onAlbumClick(albums[index].id) }
                )
            }
        }
    }
}

/**
 * Square album item for other albums with overlaid pill labels
 * Matches the visual style from the screenshot
 */
@Composable
fun OtherAlbumPillButton(
    album: Album,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        // Square album cover image
        if (album.coverUri != null) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = "${album.name} cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback background if no cover image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        
        // Overlaid pill label at bottom center
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * Special Action Buttons - wider buttons visually different from album tabs
 */
@Composable
fun SpecialActionButtons(
    onStarredClick: () -> Unit,
    onSecureClick: () -> Unit,
    onRecycleBinClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 16.dp) // Extra bottom padding for navigation bar
    ) {
        Text(
            text = "Special Folders",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpecialActionButton(
                iconUnicode = FontIcons.Star,
                label = "Starred",
                description = "Your favorite photos",
                onClick = onStarredClick
            )
            SpecialActionButton(
                iconUnicode = FontIcons.Lock,
                label = "Secure Folder",
                description = "Protected content",
                onClick = onSecureClick
            )
            SpecialActionButton(
                iconUnicode = FontIcons.Delete,
                label = "Recycle Bin",
                description = "Recently deleted",
                onClick = onRecycleBinClick
            )
        }
    }
}

/**
 * Wide special action button - clearly different from album buttons
 */
@Composable
fun SpecialActionButton(
    iconUnicode: String,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                FontIcon(
                    unicode = iconUnicode,
                    contentDescription = label,
                    size = 24.sp,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
