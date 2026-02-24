package com.prantiux.pixelgallery.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.R
import com.prantiux.pixelgallery.ui.components.EchoLoadingIndicator
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import androidx.core.content.ContextCompat

/**
 * Professional onboarding screen for media permission request.
 * 
 * Displayed on first launch before user can access the gallery.
 * Explains why permission is needed and handles permission requests.
 * 
 * @param onPermissionGranted Callback when all required permissions are granted
 */
@Composable
fun OnboardingScreen(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }
    
    // Permissions to request based on API level
    val permissions = if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    // Check if all permissions are granted
    fun checkPermissions(): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        isChecking = true
        val allGranted = results.values.all { it }
        
        if (allGranted) {
            android.util.Log.d("ONBOARDING", "All permissions granted from launcher")
            isChecking = false
            onPermissionGranted()
        } else {
            android.util.Log.d("ONBOARDING", "Some permissions denied: $results")
            showSettingsPrompt = true
            isChecking = false
        }
    }
    
    // Check permissions on composition
    LaunchedEffect(Unit) {
        if (checkPermissions()) {
            android.util.Log.d("ONBOARDING", "Permissions already granted")
            onPermissionGranted()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                FontIcon(
                    unicode = FontIcons.Home,
                    contentDescription = "Gallery",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
            
            // Title
            Text(
                text = "Welcome to Pixel Gallery",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Description
            Text(
                text = "To view and organize your photos and videos, Pixel Gallery needs permission to access your media files.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Permission features list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureItem(
                    icon = FontIcons.Home,
                    text = "Browse your photo library"
                )
                FeatureItem(
                    icon = FontIcons.Person,
                    text = "Organize into albums"
                )
                FeatureItem(
                    icon = FontIcons.Search,
                    text = "Search your media"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Grant button
            if (isChecking) {
                EchoLoadingIndicator(
                    modifier = Modifier.size(64.dp),
                    size = 48.dp,
                    label = "Checking..."
                )
            } else {
                Button(
                    onClick = {
                        isChecking = true
                        android.util.Log.d("ONBOARDING", "Grant Access button clicked")
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Grant Access",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            // Settings prompt when permissions denied
            if (showSettingsPrompt) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Permission Denied",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Please enable media permission in Settings to use the gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = {
                            android.util.Log.d("ONBOARDING", "Opening app settings")
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text("Go to Settings", fontSize = 13.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Privacy note
            Text(
                text = "Your photos and videos are never uploaded or shared. All processing happens locally on your device.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun FeatureItem(
    icon: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FontIcon(
            unicode = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
