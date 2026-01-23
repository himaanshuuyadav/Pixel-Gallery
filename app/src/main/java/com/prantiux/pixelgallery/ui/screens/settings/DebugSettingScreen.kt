package com.prantiux.pixelgallery.ui.screens.settings

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var metadataList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            val result = extractImageMetadata(context, it)
            metadataList = result.first
            errorMessage = result.second
        }
    }
    
    SubPageScaffold(
        title = "Debug",
        subtitle = "Developer tools",
        onNavigateBack = onBackClick
    ) {
        // Add consistent spacing
        item {
            Spacer(modifier = Modifier.height(28.dp))
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
 * Extract all EXIF metadata from an image
 */
private fun extractImageMetadata(context: Context, uri: Uri): Pair<List<Pair<String, String>>, String?> {
    val metadataList = mutableListOf<Pair<String, String>>()
    var error: String? = null
    
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
                metadataList.add("GPS Coordinates" to "${latLong[0]}, ${latLong[1]}")
            }
            
            if (metadataList.isEmpty()) {
                error = "No EXIF metadata found in this image"
            }
        }
    } catch (e: IOException) {
        error = "Error reading image: ${e.message}"
    } catch (e: Exception) {
        error = "Error extracting metadata: ${e.message}"
    }
    
    return metadataList to error
}
