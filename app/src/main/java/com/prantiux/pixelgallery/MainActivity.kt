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
import androidx.core.view.WindowCompat
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.prantiux.pixelgallery.navigation.AppNavigation
import com.prantiux.pixelgallery.ui.theme.PixelGalleryTheme
import com.prantiux.pixelgallery.viewmodel.MediaViewModel

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
        
        // Set navigation bar to solid black
        val navBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = navBarColor
        Log.d(TAG, "navigationBarColor set to: #${Integer.toHexString(navBarColor)} (BLACK)")
        
        // Set status bar to solid black (will be overridden per-screen by headers)
        val statusBarColor = android.graphics.Color.BLACK  
        window.statusBarColor = statusBarColor
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
            // Clear any translucent flags
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            Log.d(TAG, "Cleared translucent flags, added FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS")
        }
        
        // Set light/dark status bar icons based on status bar color
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false // Light icons for dark status bar
        insetsController.isAppearanceLightNavigationBars = false // Light icons for dark nav bar
        Log.d(TAG, "isAppearanceLightStatusBars = false, isAppearanceLightNavigationBars = false")
        
        // Log final values to verify
        Log.d(TAG, "FINAL statusBarColor: #${Integer.toHexString(window.statusBarColor)}")
        Log.d(TAG, "FINAL navigationBarColor: #${Integer.toHexString(window.navigationBarColor)}")
        Log.d(TAG, "=== SYSTEM BARS SETUP END ===")
        
        // Configure Coil ImageLoader with video support
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)
        
        viewModel.initialize(this)
        
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
            PixelGalleryTheme {
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}
