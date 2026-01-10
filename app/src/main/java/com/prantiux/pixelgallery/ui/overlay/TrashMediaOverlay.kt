package com.prantiux.pixelgallery.ui.overlay

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TrashMediaOverlay(
    viewModel: MediaViewModel,
    trashedItems: List<MediaItem>,
    currentIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    
    var displayIndex by remember { mutableIntStateOf(currentIndex) }
    var showControls by remember { mutableStateOf(true) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // ExoPlayer for videos
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    val horizontalOffset = remember { Animatable(0f) }
    
    DisposableEffect(context) {
        val player = ExoPlayer.Builder(context).build()
        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
        }
    }
    
    // Update video playback when current item changes
    LaunchedEffect(displayIndex, trashedItems) {
        exoPlayer?.let { player ->
            val currentItem = trashedItems.getOrNull(displayIndex)
            if (currentItem?.isVideo == true) {
                val mediaItem = ExoMediaItem.fromUri(currentItem.uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                isPlaying = true
            } else {
                player.pause()
                player.clearMediaItems()
                isPlaying = false
            }
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }
    
    BackHandler { onDismiss() }
    
    // Restore dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Item?") },
            text = { Text("This item will be restored to your gallery.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            trashedItems.getOrNull(displayIndex)?.let { item ->
                                viewModel.restoreFromTrash(context, item)
                                onDismiss()
                            }
                        }
                        showRestoreDialog = false
                    }
                ) {
                    Text("Restore", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Permanently?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            trashedItems.getOrNull(displayIndex)?.let { item ->
                                viewModel.permanentlyDelete(context, item)
                                onDismiss()
                            }
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val threshold = screenWidth * 0.3f
                            if (abs(horizontalOffset.value) > threshold) {
                                val direction = if (horizontalOffset.value > 0) -1 else 1
                                val newIndex = (displayIndex + direction).coerceIn(0, trashedItems.lastIndex)
                                if (newIndex != displayIndex) {
                                    displayIndex = newIndex
                                }
                            }
                            horizontalOffset.animateTo(0f)
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        horizontalOffset.snapTo(horizontalOffset.value + dragAmount)
                    }
                }
            }
    ) {
        // Media content
        val currentItem = trashedItems.getOrNull(displayIndex)
        currentItem?.let { item ->
            if (item.isVideo) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = horizontalOffset.value
                        }
                )
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = horizontalOffset.value
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }
        
        // Top controls
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        FontIcon(
                            unicode = FontIcons.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            size = 24.sp
                        )
                    }
                    
                    Text(
                        text = "${displayIndex + 1} / ${trashedItems.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Bottom controls - Only Restore and Delete
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Restore button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        FloatingActionButton(
                            onClick = { showRestoreDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ) {
                            FontIcon(
                                unicode = FontIcons.Refresh,
                                contentDescription = "Restore",
                                size = 24.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Restore",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    // Delete button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        FloatingActionButton(
                            onClick = { showDeleteDialog = true },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ) {
                            FontIcon(
                                unicode = FontIcons.Delete,
                                contentDescription = "Delete",
                                size = 24.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Delete",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
