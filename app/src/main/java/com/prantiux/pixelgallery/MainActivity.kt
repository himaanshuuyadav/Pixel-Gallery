package com.prantiux.pixelgallery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.prantiux.pixelgallery.navigation.AppNavigation
import com.prantiux.pixelgallery.ui.theme.PixelGalleryTheme
import com.prantiux.pixelgallery.viewmodel.MediaViewModel

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
        enableEdgeToEdge()
        
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
