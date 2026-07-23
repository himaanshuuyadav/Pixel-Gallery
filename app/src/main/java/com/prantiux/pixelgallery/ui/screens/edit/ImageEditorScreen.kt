package com.prantiux.pixelgallery.ui.screens.edit

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.viewmodel.EditorViewModel
import com.smarttoolfactory.cropper.ImageCropper
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropOutlineProperty
import com.smarttoolfactory.cropper.model.OutlineType
import com.smarttoolfactory.cropper.model.RectCropShape
import com.smarttoolfactory.cropper.settings.CropType
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class EditorTab {
    CROP, ADJUST, FILTERS, MARKUP
}

@Composable
fun EditorOverlayLauncher(
    mediaUri: Uri,
    onNavigateUp: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val editorViewModel: EditorViewModel = viewModel()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(mediaUri) {
        withContext(Dispatchers.IO) {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(context.contentResolver, mediaUri)
                ) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, mediaUri)
            }
            withContext(Dispatchers.Main) {
                editorViewModel.setBitmap(bitmap)
                isLoading = false
            }
        }
    }
    
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        ImageEditorScreen(editorViewModel, onNavigateUp, onSave)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    viewModel: EditorViewModel,
    onNavigateUp: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val editorState by viewModel.editorState.collectAsState()
    var currentTab by remember { mutableStateOf(EditorTab.ADJUST) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        FontIcon(unicode = FontIcons.Cancel, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { 
                        previewBitmap?.let { onSave(it) }
                    }) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            EditorBottomBar(currentTab = currentTab, onTabSelected = { currentTab = it })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            previewBitmap?.let { bitmap ->
                when (currentTab) {
                    EditorTab.CROP -> {
                        ImageCropper(
                            modifier = Modifier.fillMaxSize(),
                            imageBitmap = bitmap.asImageBitmap(),
                            contentDescription = "Crop",
                            cropProperties = CropDefaults.properties(
                                cropType = CropType.Dynamic,
                                handleSize = 40f,
                                cropOutlineProperty = CropOutlineProperty(
                                    OutlineType.Rect,
                                    RectCropShape(0, "Rect")
                                )
                            ),
                            onCropStart = {},
                            onCropSuccess = { croppedBitmap ->
                                // Crop done
                            }
                        )
                    }
                    else -> {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Controls Overlay
            if (currentTab != EditorTab.CROP) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(16.dp)
                ) {
                    when (currentTab) {
                        EditorTab.ADJUST -> AdjustmentControls(
                            brightness = editorState.brightness,
                            contrast = editorState.contrast,
                            saturation = editorState.saturation,
                            onBrightnessChange = viewModel::updateBrightness,
                            onContrastChange = viewModel::updateContrast,
                            onSaturationChange = viewModel::updateSaturation
                        )
                        EditorTab.FILTERS -> FilterControls(
                            currentFilter = editorState.currentFilter,
                            onFilterSelected = viewModel::setFilter
                        )
                        EditorTab.MARKUP -> MarkupControls(
                            onUndo = viewModel::undoLastPath
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun EditorBottomBar(currentTab: EditorTab, onTabSelected: (EditorTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            icon = { FontIcon(unicode = FontIcons.Crop, contentDescription = "Crop") },
            label = { Text("Crop") },
            selected = currentTab == EditorTab.CROP,
            onClick = { onTabSelected(EditorTab.CROP) }
        )
        NavigationBarItem(
            icon = { FontIcon(unicode = FontIcons.Adjust, contentDescription = "Adjust") },
            label = { Text("Adjust") },
            selected = currentTab == EditorTab.ADJUST,
            onClick = { onTabSelected(EditorTab.ADJUST) }
        )
        NavigationBarItem(
            icon = { FontIcon(unicode = FontIcons.Filter, contentDescription = "Filters") },
            label = { Text("Filters") },
            selected = currentTab == EditorTab.FILTERS,
            onClick = { onTabSelected(EditorTab.FILTERS) }
        )
        NavigationBarItem(
            icon = { FontIcon(unicode = FontIcons.Edit, contentDescription = "Markup") },
            label = { Text("Markup") },
            selected = currentTab == EditorTab.MARKUP,
            onClick = { onTabSelected(EditorTab.MARKUP) }
        )
    }
}

@Composable
fun AdjustmentControls(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit
) {
    Column {
        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = -1f..1f
        )
        
        Text("Contrast", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = contrast,
            onValueChange = onContrastChange,
            valueRange = 0f..2f
        )
        
        Text("Saturation", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = saturation,
            onValueChange = onSaturationChange,
            valueRange = 0f..2f
        )
    }
}

@Composable
fun FilterControls(
    currentFilter: String,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf("None", "Grayscale", "Sepia", "Invert")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == currentFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter) }
            )
        }
    }
}

@Composable
fun MarkupControls(
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onUndo) {
            FontIcon(unicode = FontIcons.Undo, contentDescription = "Undo")
        }
    }
}
