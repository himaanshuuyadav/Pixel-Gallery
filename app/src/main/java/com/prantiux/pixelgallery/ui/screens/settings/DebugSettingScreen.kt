package com.prantiux.pixelgallery.ui.screens.settings

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingScreen(
    onBackClick: () -> Unit = {},
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var metadataList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var locationAddress by remember { mutableStateOf<String?>(null) }
    var isGeocodingInProgress by remember { mutableStateOf(false) }
    var gpsCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    
    // ML Labeling progress state from viewModel
    val isLabelingInProgress by viewModel.isLabelingInProgress.collectAsState()
    val labelingProgress by viewModel.labelingProgress.collectAsState()
    val isCharging = remember { 
        mutableStateOf(com.prantiux.pixelgallery.ml.ImageLabelScheduler.isCharging(context))
    }

    val themeShapes = MaterialTheme.shapes
    val shapeOptions = remember(themeShapes) {
        buildMaterialShapeOptions(themeShapes)
    }
    var selectedShapeName by remember { mutableStateOf(shapeOptions.firstOrNull()?.name ?: "") }
    LaunchedEffect(shapeOptions) {
        if (shapeOptions.isNotEmpty() && shapeOptions.none { it.name == selectedShapeName }) {
            selectedShapeName = shapeOptions.first().name
        }
    }
    
    // Track next batch status
    var nextBatchStatus by remember { mutableStateOf("Checking...") }
    
    // Update charging state and calculate next batch timing
    LaunchedEffect(Unit) {
        while (true) {
            isCharging.value = com.prantiux.pixelgallery.ml.ImageLabelScheduler.isCharging(context)
            
            // Simple next batch calculation based on current state
            nextBatchStatus = when {
                isLabelingInProgress -> "Running now"
                labelingProgress?.let { (p, t) -> p < t } == true -> "Retry in ~10s"
                else -> "Complete"
            }
            
            kotlinx.coroutines.delay(5000) // Check every 5 seconds
        }
    }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        locationAddress = null
        gpsCoordinates = null
        uri?.let {
            val result = extractImageMetadata(context, it)
            metadataList = result.first
            errorMessage = result.second
            gpsCoordinates = result.third
            
            // Get location from coordinates if available
            result.third?.let { coords ->
                isGeocodingInProgress = true
                scope.launch {
                    val address = getAddressFromCoordinates(context, coords.first, coords.second)
                    locationAddress = address
                    isGeocodingInProgress = false
                }
            }
        }
    }
    
    SubPageScaffold(
        title = "Debug",
        subtitle = "Developer tools",
        onNavigateBack = onBackClick
    ) {
        // ML LABELING DEBUG PANEL
        item {
            CategoryHeader("ML Image Labeling")
        }
        
        item {
            val progress = labelingProgress?.let { (processed, total) ->
                if (total > 0) processed.toFloat() / total.toFloat() else 0f
            } ?: 0f
            
            val isComplete = labelingProgress?.let { (processed, total) ->
                processed >= total && total > 0
            } ?: false
            
            Surface(
                color = if (isLabelingInProgress) 
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                else if (isComplete)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Header with icon and title
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isComplete) "✅" else "🤖",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "ML Image Labeling",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isComplete -> MaterialTheme.colorScheme.primary
                                isLabelingInProgress -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            }
                        ) {
                            Text(
                                text = when {
                                    isComplete -> "DONE"
                                    isLabelingInProgress -> "RUNNING"
                                    else -> "IDLE"
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isComplete -> MaterialTheme.colorScheme.onPrimary
                                    isLabelingInProgress -> MaterialTheme.colorScheme.onTertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Large Progress Display
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Images Labeled",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = labelingProgress?.let { (processed, total) ->
                                    "$processed / $total"
                                } ?: "0 / 0",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Large Percentage
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = if (isComplete) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress Bar with better visibility
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isComplete) 
                            MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HorizontalDivider()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Detailed Status Grid
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Charging Status
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚡",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Device Charging",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (isCharging.value) "Yes" else "No",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCharging.value) 
                                    MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        
                        // ML Engine Status
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🧠",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ML Engine",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (isLabelingInProgress) "Active" else "Standby",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isLabelingInProgress) 
                                    MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Completion Status
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isComplete) "✅" else "📊",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = when {
                                    isComplete -> "Labeling Complete!"
                                    isLabelingInProgress -> "Processing..."
                                    labelingProgress?.first ?: 0 > 0 -> "In Progress"
                                    else -> "Not Started"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isComplete -> MaterialTheme.colorScheme.primary
                                    isLabelingInProgress -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        
                        // Next Batch Schedule (only show if not complete)
                        if (!isComplete) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⏰",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Next Batch",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = nextBatchStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLabelingInProgress) 
                                        MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Info message
                    if (!isComplete) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "💡 ML runs in background without affecting app performance",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Material 3 shapes preview
        item {
            CategoryHeader("Material Shapes")
        }

        if (shapeOptions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No shapes found. Update Material3 to access the shape library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            item {
                SettingsGroup {
                    shapeOptions.forEachIndexed { index, option ->
                        val position = when {
                            shapeOptions.size == 1 -> SettingPosition.SINGLE
                            index == 0 -> SettingPosition.TOP
                            index == shapeOptions.lastIndex -> SettingPosition.BOTTOM
                            else -> SettingPosition.MIDDLE
                        }
                        ShapeSelectionItem(
                            name = option.name,
                            selected = option.name == selectedShapeName,
                            position = position,
                            onClick = { selectedShapeName = option.name }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                val selectedShape = shapeOptions.firstOrNull { it.name == selectedShapeName }?.shape
                Surface(
                    shape = selectedShape ?: MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Preview: ${selectedShapeName.ifEmpty { "medium" }}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
        
        // Image metadata viewer
        item {
            CategoryHeader("Image Metadata")
        }
        
        item {
            Surface(
                onClick = { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FontIcon(
                        unicode = FontIcons.Image,
                        contentDescription = null,
                        size = 24.sp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select image",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedImageUri != null) "Image selected" else "Tap to choose image",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FontIcon(
                        unicode = FontIcons.KeyboardArrowRight,
                        contentDescription = null,
                        size = 24.sp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Error message
        if (errorMessage != null) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        // GPS Location display
        if (gpsCoordinates != null) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                CategoryHeader("GPS Location")
            }
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Coordinates
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📍",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Coordinates",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${gpsCoordinates!!.first}, ${gpsCoordinates!!.second}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        
                        // Address
                        if (isGeocodingInProgress) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Getting location address...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        } else if (locationAddress != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "🗺️",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Location",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = locationAddress ?: "Unknown location",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Metadata display
        if (metadataList.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                CategoryHeader("EXIF Data (${metadataList.size} tags)")
            }
            
            metadataList.forEachIndexed { index, (tag, value) ->
                item {
                    val position = when {
                        metadataList.size == 1 -> SettingPosition.SINGLE
                        index == 0 -> SettingPosition.TOP
                        index == metadataList.lastIndex -> SettingPosition.BOTTOM
                        else -> SettingPosition.MIDDLE
                    }
                    
                    MetadataItem(
                        tag = tag,
                        value = value,
                        position = position
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataItem(
    tag: String,
    value: String,
    position: SettingPosition
) {
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(8.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
    
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Get address from GPS coordinates using Geocoder
 */
private suspend fun getAddressFromCoordinates(context: Context, latitude: Double, longitude: Double): String? {
    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ async approach
                var result: String? = null
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    result = formatAddress(addresses.firstOrNull())
                }
                // Wait a bit for the callback
                kotlinx.coroutines.delay(2000)
                result ?: "Unable to determine location"
            } else {
                // Legacy sync approach
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                formatAddress(addresses?.firstOrNull()) ?: "Unable to determine location"
            }
        } catch (e: Exception) {
            "Geocoding error: ${e.message}"
        }
    }
}

/**
 * Format address from Geocoder result
 */
private fun formatAddress(address: Address?): String? {
    if (address == null) return null
    
    val parts = mutableListOf<String>()
    
    // Add street address
    address.thoroughfare?.let { parts.add(it) }
    
    // Add locality (city)
    address.locality?.let { parts.add(it) }
    
    // Add admin area (state/province)
    address.adminArea?.let { parts.add(it) }
    
    // Add country
    address.countryName?.let { parts.add(it) }
    
    return if (parts.isNotEmpty()) parts.joinToString(", ") else null
}

private data class NamedShape(
    val name: String,
    val shape: Shape
)

private fun buildMaterialShapeOptions(themeShapes: Shapes): List<NamedShape> {
    val baseShapes = listOf(
        NamedShape("extraSmall", themeShapes.extraSmall),
        NamedShape("small", themeShapes.small),
        NamedShape("medium", themeShapes.medium),
        NamedShape("large", themeShapes.large),
        NamedShape("extraLarge", themeShapes.extraLarge)
    )
    val libraryShapes = loadMaterialShapesFromLibrary()
    return (baseShapes + libraryShapes)
        .distinctBy { it.name }
        .sortedBy { it.name }
}

private fun loadMaterialShapesFromLibrary(): List<NamedShape> {
    return runCatching {
        val klass = Class.forName("androidx.compose.material3.MaterialShapes")
        val instance = klass.declaredFields
            .firstOrNull { it.name == "INSTANCE" }
            ?.also { it.isAccessible = true }
            ?.get(null)
            ?: klass.getDeclaredConstructor().newInstance()
        val shapes = mutableListOf<NamedShape>()

        klass.declaredFields
            .filter { Shape::class.java.isAssignableFrom(it.type) }
            .forEach { field ->
                field.isAccessible = true
                val shape = field.get(instance) as? Shape ?: return@forEach
                shapes.add(NamedShape(field.name, shape))
            }

        klass.methods
            .filter { it.parameterCount == 0 && Shape::class.java.isAssignableFrom(it.returnType) }
            .forEach { method ->
                val name = normalizeGetterName(method.name)
                val shape = method.invoke(instance) as? Shape ?: return@forEach
                shapes.add(NamedShape(name, shape))
            }

        shapes
    }.getOrDefault(emptyList())
}

private fun normalizeGetterName(name: String): String {
    return if (name.startsWith("get") && name.length > 3) {
        name.substring(3).replaceFirstChar { it.lowercase() }
    } else {
        name
    }
}

@Composable
private fun ShapeSelectionItem(
    name: String,
    selected: Boolean,
    position: SettingPosition,
    onClick: () -> Unit
) {
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(12.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(24.dp)
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (selected) "Selected" else "Tap to preview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Extract all EXIF metadata from an image
 */
private fun extractImageMetadata(context: Context, uri: Uri): Triple<List<Pair<String, String>>, String?, Pair<Double, Double>?> {
    val metadataList = mutableListOf<Pair<String, String>>()
    var error: String? = null
    var coordinates: Pair<Double, Double>? = null
    
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            
            // All EXIF tags we want to extract
            val exifTags = listOf(
                // Location
                "GPS Latitude" to ExifInterface.TAG_GPS_LATITUDE,
                "GPS Latitude Ref" to ExifInterface.TAG_GPS_LATITUDE_REF,
                "GPS Longitude" to ExifInterface.TAG_GPS_LONGITUDE,
                "GPS Longitude Ref" to ExifInterface.TAG_GPS_LONGITUDE_REF,
                "GPS Altitude" to ExifInterface.TAG_GPS_ALTITUDE,
                "GPS Altitude Ref" to ExifInterface.TAG_GPS_ALTITUDE_REF,
                "GPS Timestamp" to ExifInterface.TAG_GPS_TIMESTAMP,
                "GPS Datestamp" to ExifInterface.TAG_GPS_DATESTAMP,
                "GPS Processing Method" to ExifInterface.TAG_GPS_PROCESSING_METHOD,
                
                // DateTime
                "DateTime" to ExifInterface.TAG_DATETIME,
                "DateTime Original" to ExifInterface.TAG_DATETIME_ORIGINAL,
                "DateTime Digitized" to ExifInterface.TAG_DATETIME_DIGITIZED,
                "SubSec Time" to ExifInterface.TAG_SUBSEC_TIME,
                "SubSec Time Original" to ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                "SubSec Time Digitized" to ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                
                // Camera Info
                "Camera Make" to ExifInterface.TAG_MAKE,
                "Camera Model" to ExifInterface.TAG_MODEL,
                "Software" to ExifInterface.TAG_SOFTWARE,
                
                // Image Info
                "Image Width" to ExifInterface.TAG_IMAGE_WIDTH,
                "Image Height" to ExifInterface.TAG_IMAGE_LENGTH,
                "Orientation" to ExifInterface.TAG_ORIENTATION,
                "X Resolution" to ExifInterface.TAG_X_RESOLUTION,
                "Y Resolution" to ExifInterface.TAG_Y_RESOLUTION,
                "Resolution Unit" to ExifInterface.TAG_RESOLUTION_UNIT,
                
                // Camera Settings
                "F-Number" to ExifInterface.TAG_F_NUMBER,
                "Exposure Time" to ExifInterface.TAG_EXPOSURE_TIME,
                "ISO" to ExifInterface.TAG_ISO_SPEED_RATINGS,
                "Focal Length" to ExifInterface.TAG_FOCAL_LENGTH,
                "Flash" to ExifInterface.TAG_FLASH,
                "White Balance" to ExifInterface.TAG_WHITE_BALANCE,
                "Exposure Program" to ExifInterface.TAG_EXPOSURE_PROGRAM,
                "Metering Mode" to ExifInterface.TAG_METERING_MODE,
                "Scene Type" to ExifInterface.TAG_SCENE_CAPTURE_TYPE,
                
                // Other
                "Artist" to ExifInterface.TAG_ARTIST,
                "Copyright" to ExifInterface.TAG_COPYRIGHT,
                "User Comment" to ExifInterface.TAG_USER_COMMENT,
                "Description" to ExifInterface.TAG_IMAGE_DESCRIPTION
            )
            
            exifTags.forEach { (displayName, tag) ->
                val value = exif.getAttribute(tag)
                if (value != null && value.isNotEmpty()) {
                    metadataList.add(displayName to value)
                }
            }
            
            // Try to get GPS coordinates if available
            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                coordinates = latLong[0].toDouble() to latLong[1].toDouble()
                // Don't add to metadata list - will be shown separately in GPS Location section
            }
            
            if (metadataList.isEmpty() && coordinates == null) {
                error = "No EXIF metadata found in this image"
            }
        }
    } catch (e: IOException) {
        error = "Error reading image: ${e.message}"
    } catch (e: Exception) {
        error = "Error extracting metadata: ${e.message}"
    }
    
    return Triple(metadataList, error, coordinates)
}
