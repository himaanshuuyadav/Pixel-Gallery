package com.prantiux.pixelgallery

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.prantiux.pixelgallery.navigation.AppNavigation
import com.prantiux.pixelgallery.ui.theme.PixelGalleryTheme
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val viewModel: MediaViewModel by viewModels()
    
    // Activity result launcher for trash request (Android 11+)
    private val trashRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User confirmed, items moved to trash
            viewModel.onDeleteConfirmed(this)
        } else {
            // User cancelled
            viewModel.onDeleteCancelled()
        }
    }
    
    // Activity result launcher for restore from trash
    private val restoreRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User confirmed, restore items
            viewModel.onRestoreConfirmed(this)
        } else {
            // User cancelled
            viewModel.onRestoreCancelled()
        }
    }
    
    // Activity result launcher for permanent delete from trash
    private val permanentDeleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User confirmed, permanently delete items
            viewModel.onPermanentDeleteConfirmed(this)
        } else {
            // User cancelled
            viewModel.onPermanentDeleteCancelled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "=== SYSTEM BARS SETUP START ===")
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")
        
        // We need edge-to-edge for proper content padding with statusBarsPadding()
        // BUT we want solid bar colors, not transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Log.d(TAG, "setDecorFitsSystemWindows = false (edge-to-edge enabled)")
        
        // CRITICAL: For solid system bar colors on Android 10+, we must set them AFTER edge-to-edge
        // and ensure contrast enforcement is disabled
        
        // Modern approach: Use WindowInsetsController for system bar colors (non-deprecated)
        val navBarColor = android.graphics.Color.BLACK
        val statusBarColor = android.graphics.Color.BLACK
        
        // Set colors - suppress deprecation warning as these APIs are still needed for compatibility
        // Note: These are deprecated in Java but still the standard way to set colors on all API levels
        @Suppress("DEPRECATION")
        window.statusBarColor = statusBarColor
        @Suppress("DEPRECATION")
        window.navigationBarColor = navBarColor
        
        Log.d(TAG, "navigationBarColor set to: #${Integer.toHexString(navBarColor)} (BLACK)")
        Log.d(TAG, "statusBarColor set to: #${Integer.toHexString(statusBarColor)} (BLACK)")
        
        // Disable navigation bar contrast enforcement on Android Q+
        // This is CRITICAL - contrast enforcement can override our solid color with scrim
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            Log.d(TAG, "isNavigationBarContrastEnforced = false")
        }
        
        // IMPORTANT: Also clear any window flags that might cause transparency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Ensure the window draws system bar backgrounds
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            // Clear any translucent flags (deprecated but necessary for older devices)
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            Log.d(TAG, "Cleared translucent flags, added FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS")
        }
        
        // Set light/dark status bar icons based on status bar color
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false // Light icons for dark status bar
        insetsController.isAppearanceLightNavigationBars = false // Light icons for dark nav bar
        Log.d(TAG, "isAppearanceLightStatusBars = false, isAppearanceLightNavigationBars = false")
        
        // Log final values to verify
        Log.d(TAG, "FINAL statusBarColor: BLACK")
        Log.d(TAG, "FINAL navigationBarColor: BLACK")
        Log.d(TAG, "=== SYSTEM BARS SETUP END ===")
        
        // Configure Coil ImageLoader with video support
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)
        
        viewModel.initialize(this)
        
        // Initialize ML-based image labeling scheduler (non-blocking, runs in background)
        // This does NOT affect gallery loading performance
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Schedule periodic work (runs when charging)
            com.prantiux.pixelgallery.ml.ImageLabelScheduler.schedulePeriodicLabeling(this@MainActivity)
            
            // Trigger immediate labeling if device is charging
            com.prantiux.pixelgallery.ml.ImageLabelScheduler.triggerImmediateLabelingIfCharging(this@MainActivity)
        }
        
        // Observe labeling work progress (updates UI state)
        com.prantiux.pixelgallery.ml.ImageLabelScheduler.getLabelingWorkInfo(this).observe(this) { workInfoList ->
            val workInfo = workInfoList?.firstOrNull()
            if (workInfo != null) {
                val isRunning = workInfo.state == androidx.work.WorkInfo.State.RUNNING
                viewModel.setLabelingInProgress(isRunning)
                
                if (isRunning) {
                    val progress = workInfo.progress.getInt(
                        com.prantiux.pixelgallery.ml.ImageLabelWorker.KEY_PROGRESS, 0
                    )
                    val total = workInfo.progress.getInt(
                        com.prantiux.pixelgallery.ml.ImageLabelWorker.KEY_TOTAL, 0
                    )
                    if (total > 0) {
                        viewModel.updateLabelingProgress(progress, total)
                    }
                }
            }
        }
        
        // Set the trash request launcher in the ViewModel
        viewModel.setTrashRequestLauncher { pendingIntent ->
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                trashRequestLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error launching trash request", e)
                viewModel.onDeleteCancelled()
            }
        }
        
        // Set the restore request launcher
        viewModel.setRestoreRequestLauncher { pendingIntent ->
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                restoreRequestLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error launching restore request", e)
                viewModel.onRestoreCancelled()
            }
        }
        
        // Set the permanent delete request launcher
        viewModel.setPermanentDeleteRequestLauncher { pendingIntent ->
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                permanentDeleteRequestLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error launching permanent delete request", e)
                viewModel.onPermanentDeleteCancelled()
            }
        }
        
        setContent {
            val settingsDataStore = remember { com.prantiux.pixelgallery.data.SettingsDataStore(this) }
            var appTheme by remember { mutableStateOf("System Default") }
            var dynamicColor by remember { mutableStateOf(true) }
            var amoledMode by remember { mutableStateOf(false) }
            var defaultTab by remember { mutableStateOf("Gallery") }
            var lastUsedTab by remember { mutableStateOf("Gallery") }
            
            // Load theme settings
            LaunchedEffect(Unit) {
                lifecycleScope.launch {
                    settingsDataStore.appThemeFlow.collect { theme ->
                        appTheme = theme
                    }
                }
            }
            
            LaunchedEffect(Unit) {
                lifecycleScope.launch {
                    settingsDataStore.dynamicColorFlow.collect { enabled ->
                        dynamicColor = enabled
                    }
                }
            }
            
            LaunchedEffect(Unit) {
                lifecycleScope.launch {
                    settingsDataStore.amoledModeFlow.collect { enabled ->
                        amoledMode = enabled
                    }
                }
            }
            
            LaunchedEffect(Unit) {
                lifecycleScope.launch {
                    settingsDataStore.defaultTabFlow.collect { tab ->
                        defaultTab = tab
                    }
                }
            }
            
            LaunchedEffect(Unit) {
                lifecycleScope.launch {
                    settingsDataStore.lastUsedTabFlow.collect { tab ->
                        lastUsedTab = tab
                    }
                }
            }
            
            // Determine dark theme based on settings
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (appTheme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemDark
            }
            
            // Log theme state for debugging
            LaunchedEffect(appTheme, isSystemDark, darkTheme) {
                android.util.Log.d(TAG, "=== THEME STATE ===")
                android.util.Log.d(TAG, "appTheme setting: $appTheme")
                android.util.Log.d(TAG, "isSystemInDarkTheme: $isSystemDark")
                android.util.Log.d(TAG, "Computed darkTheme: $darkTheme")
                android.util.Log.d(TAG, "==================")
            }
            
            // Update navigation bar color based on theme
            // Use SideEffect to ensure it runs on every recomposition
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                val navBarColor = if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                
                android.util.Log.d(TAG, "=== NAVIGATION BAR UPDATE ===")
                android.util.Log.d(TAG, "darkTheme: $darkTheme")
                android.util.Log.d(TAG, "appTheme: $appTheme")
                android.util.Log.d(TAG, "isSystemDark: $isSystemDark")
                android.util.Log.d(TAG, "Target color: ${if (darkTheme) "BLACK (#000000)" else "WHITE (#FFFFFF)"}")
                android.util.Log.d(TAG, "Current window.navigationBarColor BEFORE: #${Integer.toHexString(window.navigationBarColor)}")
                
                @Suppress("DEPRECATION")
                window.navigationBarColor = navBarColor
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                
                android.util.Log.d(TAG, "Current window.navigationBarColor AFTER: #${Integer.toHexString(window.navigationBarColor)}")
                android.util.Log.d(TAG, "isAppearanceLightNavigationBars: ${!darkTheme}")
                
                // Disable contrast enforcement to ensure solid color
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    android.util.Log.d(TAG, "isNavigationBarContrastEnforced: false")
                }
                android.util.Log.d(TAG, "===========================")
            }
            
            PixelGalleryTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                amoledMode = amoledMode
            ) {
                AppNavigation(
                    viewModel = viewModel,
                    settingsDataStore = settingsDataStore,
                    defaultTab = defaultTab,
                    lastUsedTab = lastUsedTab,
                    onTabChanged = { tab ->
                        lastUsedTab = tab
                        lifecycleScope.launch {
                            settingsDataStore.saveLastUsedTab(tab)
                        }
                    }
                )
            }
        }
    }
}
