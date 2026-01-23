package com.prantiux.pixelgallery.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingScreen(
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore,
    onBackClick: () -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf("System Default") }
    var dynamicColorEnabled by remember { mutableStateOf(true) }
    var amoledModeEnabled by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Load theme settings
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.appThemeFlow.collect { theme ->
                selectedTheme = theme
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.dynamicColorFlow.collect { enabled ->
                dynamicColorEnabled = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.amoledModeFlow.collect { enabled ->
                amoledModeEnabled = enabled
            }
        }
    }
    
    // Determine icon based on selected theme
    val themeIcon = when {
        selectedTheme.contains("System", ignoreCase = true) -> "\ue20c"
        selectedTheme.contains("Light", ignoreCase = true) -> FontIcons.LightMode
        selectedTheme.contains("Dark", ignoreCase = true) -> FontIcons.DarkMode
        else -> FontIcons.Palette
    }
    
    SubPageScaffold(
        title = "Theme",
        subtitle = "Customize app appearance",
        onNavigateBack = onBackClick
    ) {
        // Add consistent spacing
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
        
        item {
            GroupedSettingItem(
                title = "App theme",
                subtitle = selectedTheme,
                iconUnicode = themeIcon,
                position = SettingPosition.TOP,
                onClick = { showThemeDialog = true }
            )
        }
        
        item {
            GroupedSettingToggle(
                title = "Dynamic color",
                subtitle = "Material You theming",
                iconUnicode = FontIcons.ColorLens,
                checked = dynamicColorEnabled,
                onCheckedChange = { 
                    dynamicColorEnabled = it
                    scope.launch {
                        settingsDataStore.saveDynamicColor(it)
                    }
                },
                position = SettingPosition.MIDDLE
            )
        }
        
        item {
            GroupedSettingToggle(
                title = "AMOLED mode",
                subtitle = "Pure black background",
                iconUnicode = FontIcons.Brightness2,
                checked = amoledModeEnabled,
                onCheckedChange = { 
                    amoledModeEnabled = it
                    scope.launch {
                        settingsDataStore.saveAmoledMode(it)
                    }
                },
                position = SettingPosition.BOTTOM,
                enabled = selectedTheme == "Dark" || (selectedTheme == "System Default" && isSystemInDarkTheme)
            )
        }
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = selectedTheme,
            onThemeSelected = { 
                selectedTheme = it
                scope.launch {
                    settingsDataStore.saveAppTheme(it)
                }
                // Disable AMOLED mode if not in dark theme
                if (it != "Dark" && amoledModeEnabled) {
                    amoledModeEnabled = false
                    scope.launch {
                        settingsDataStore.saveAmoledMode(false)
                    }
                }
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon at top - dynamic based on selection
                val dialogIcon = when {
                    currentTheme.contains("System", ignoreCase = true) -> "\ue20c"
                    currentTheme.contains("Light", ignoreCase = true) -> FontIcons.LightMode
                    currentTheme.contains("Dark", ignoreCase = true) -> FontIcons.DarkMode
                    else -> FontIcons.Palette
                }
                
                FontIcon(
                    unicode = dialogIcon,
                    contentDescription = null,
                    size = 40.sp,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = "App theme",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Theme options
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column {
                        ThemeOption(
                            iconUnicode = "\ue20c", // System theme icon
                            label = "System Default",
                            isSelected = currentTheme == "System Default",
                            onClick = { onThemeSelected("System Default") },
                            position = SettingPosition.TOP
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        ThemeOption(
                            iconUnicode = FontIcons.LightMode, // Light mode icon
                            label = "Light",
                            isSelected = currentTheme == "Light",
                            onClick = { onThemeSelected("Light") },
                            position = SettingPosition.MIDDLE
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        ThemeOption(
                            iconUnicode = FontIcons.DarkMode, // Dark mode icon
                            label = "Dark",
                            isSelected = currentTheme == "Dark",
                            onClick = { onThemeSelected("Dark") },
                            position = SettingPosition.BOTTOM
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    iconUnicode: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: SettingPosition
) {
    // Apply rounded corners based on position
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(0.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(16.dp)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FontIcon(
            unicode = iconUnicode,
            contentDescription = null,
            size = 24.sp,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium, // Smaller font size
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        
        if (isSelected) {
            FontIcon(
                unicode = FontIcons.Check,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
