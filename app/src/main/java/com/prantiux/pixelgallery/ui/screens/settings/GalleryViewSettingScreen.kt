package com.prantiux.pixelgallery.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.data.SettingsDataStore
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryViewSettingScreen(
    albumRepository: AlbumRepository,
    settingsDataStore: SettingsDataStore,
    onBackClick: () -> Unit = {}
) {
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selectedAlbums by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isInitialized by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    // Load albums once
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            val categorized = albumRepository.loadCategorizedAlbums()
            albums = categorized.mainAlbums + categorized.otherAlbums
            isLoading = false
        }
    }
    
    // Load saved selections
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.selectedAlbumsFlow.collect { saved ->
                if (!isInitialized) {
                    if (saved.isEmpty() && albums.isNotEmpty()) {
                        // First time - select all albums by default
                        selectedAlbums = albums.map { it.id }.toSet()
                        settingsDataStore.saveSelectedAlbums(selectedAlbums)
                    } else {
                        selectedAlbums = saved
                    }
                    isInitialized = true
                }
            }
        }
    }
    
    SubPageScaffold(
        title = "Gallery view",
        subtitle = "Choose folder to show in main gallery",
        onNavigateBack = onBackClick
    ) {
        // Add consistent spacing like LayoutSettingScreen
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
        
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    com.prantiux.pixelgallery.ui.components.ExpressiveLoadingIndicator(
                        size = 48.dp
                    )
                }
            }
        } else {
            items(albums.size) { index ->
            val album = albums[index]
            val position = when {
                albums.size == 1 -> SettingPosition.SINGLE
                index == 0 -> SettingPosition.TOP
                index == albums.size - 1 -> SettingPosition.BOTTOM
                else -> SettingPosition.MIDDLE
            }
            
            AlbumCheckboxCard(
                album = album,
                isChecked = selectedAlbums.contains(album.id),
                position = position,
                onCheckedChange = { checked ->
                    selectedAlbums = if (checked) {
                        selectedAlbums + album.id
                    } else {
                        selectedAlbums - album.id
                    }
                    // Save immediately on change
                    scope.launch {
                        settingsDataStore.saveSelectedAlbums(selectedAlbums)
                    }
                }
            )
            }
            
            // Warning text with icon - only show after loading
            item {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.prantiux.pixelgallery.ui.icons.FontIcon(
                    unicode = com.prantiux.pixelgallery.ui.icons.FontIcons.Info,
                    contentDescription = null,
                    size = 20.sp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Excluded folders remain accessible in Albums",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
        }
    }
}

@Composable
private fun AlbumCheckboxCard(
    album: Album,
    isChecked: Boolean,
    position: SettingPosition,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(12.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
    
    Surface(
        onClick = { onCheckedChange(!isChecked) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail instead of icon
            Image(
                painter = rememberAsyncImagePainter(album.coverUri),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${album.itemCount} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Checkbox
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
