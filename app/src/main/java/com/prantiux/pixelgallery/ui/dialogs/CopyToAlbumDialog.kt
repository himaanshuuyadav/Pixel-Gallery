package com.prantiux.pixelgallery.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyToAlbumDialog(
    viewModel: MediaViewModel,
    albumRepository: AlbumRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isCopying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Load albums
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            val categorized = albumRepository.loadCategorizedAlbums()
            // Filter out albums in restricted directories (Android/)
            val allAlbums = categorized.mainAlbums + categorized.otherAlbums
            val restrictedAlbums = mutableListOf<String>()
            albums = allAlbums.filter { album ->
                // Check if album has media items with paths
                if (album.topMediaItems.isNotEmpty()) {
                    val samplePath = album.topMediaItems.first().path
                    // Exclude albums in Android directory (not writable via MediaStore)
                    val isRestricted = samplePath.contains("/Android/", ignoreCase = true)
                    if (isRestricted) {
                        restrictedAlbums.add(album.name)
                    }
                    !isRestricted
                } else {
                    // If no sample path, allow it (shouldn't happen)
                    true
                }
            }
            if (restrictedAlbums.isNotEmpty()) {
                android.util.Log.d("CopyToAlbumDialog", "Filtered out ${restrictedAlbums.size} restricted albums: ${restrictedAlbums.joinToString()}")
            }
            android.util.Log.d("CopyToAlbumDialog", "Available albums for copy: ${albums.size}")
            isLoading = false
        }
    }
    
    // Categorize albums
    val systemAlbums = remember(albums) {
        albums.filter { album ->
            val name = album.name.lowercase()
            name == "camera" || 
            name == "screenshots" || 
            name == "screen recordings" ||
            name == "screen recording" ||
            name == "screenrecorder" ||
            name == "movies" ||
            name == "downloads" ||
            name == "download" ||
            name == "pictures" ||
            name == "dcim"
        }
    }
    
    val userAlbums = remember(albums) {
        albums.filter { !systemAlbums.contains(it) }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Copy to album",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        com.prantiux.pixelgallery.ui.icons.FontIcon(
                            unicode = com.prantiux.pixelgallery.ui.icons.FontIcons.Close,
                            contentDescription = "Close",
                            size = 24.sp
                        )
                    }
                }
                
                Divider()
                
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        com.prantiux.pixelgallery.ui.components.ExpressiveLoadingIndicator(
                            size = 48.dp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // System Albums Section
                        if (systemAlbums.isNotEmpty()) {
                            item {
                                Text(
                                    text = "System Albums",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                )
                            }
                            
                            items(systemAlbums) { album ->
                                AlbumItem(
                                    album = album,
                                    enabled = !isCopying,
                                    onClick = {
                                        scope.launch {
                                            isCopying = true
                                            val success = viewModel.copyToAlbum(context, album)
                                            isCopying = false
                                            if (success) {
                                                onDismiss()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        // User Albums Section
                        if (userAlbums.isNotEmpty()) {
                            item {
                                Text(
                                    text = "User Albums",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                                )
                            }
                            
                            items(userAlbums) { album ->
                                AlbumItem(
                                    album = album,
                                    enabled = !isCopying,
                                    onClick = {
                                        scope.launch {
                                            isCopying = true
                                            val success = viewModel.copyToAlbum(context, album)
                                            isCopying = false
                                            if (success) {
                                                onDismiss()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (isCopying) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumItem(
    album: Album,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Image(
                painter = rememberAsyncImagePainter(album.coverUri),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Album info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${album.itemCount} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Arrow icon
            com.prantiux.pixelgallery.ui.icons.FontIcon(
                unicode = com.prantiux.pixelgallery.ui.icons.FontIcons.KeyboardArrowRight,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
