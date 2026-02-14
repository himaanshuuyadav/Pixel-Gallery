package com.prantiux.pixelgallery.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Observes MediaStore for changes (new photos/videos added or deleted)
 * and triggers refresh callback with debounce to prevent rapid repeated calls.
 * 
 * Usage:
 * val observer = MediaContentObserver(context) { onMediaChanged() }
 * observer.register()
 * observer.unregister()
 */
class MediaContentObserver(
    private val context: Context,
    private val onMediaChanged: suspend () -> Unit
) {
    companion object {
        private const val TAG = "MediaContentObserver"
        private const val DEBOUNCE_MS = 500L
    }
    
    private var contentObserver: ContentObserver? = null
    private var debounceJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Register observer to watch for MediaStore changes
     * Should be called from ViewModel when app launches
     */
    fun register(coroutineScope: CoroutineScope) {
        if (contentObserver != null) {
            Log.w(TAG, "Observer already registered")
            return
        }
        
        try {
            contentObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    
                    // Debounce: cancel previous job and schedule new one
                    // This prevents rapid-fire refresh calls when multiple files change
                    debounceJob?.cancel()
                    debounceJob = coroutineScope.launch {
                        delay(DEBOUNCE_MS)
                        onMediaChanged()
                    }
                }
            }
            
            // Register observer for both images and videos on external storage
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true, // notifyForDescendants
                contentObserver!!
            )
            
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true, // notifyForDescendants
                contentObserver!!
            )
            
            Log.d(TAG, "MediaStore observer registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering MediaStore observer", e)
        }
    }
    
    /**
     * Unregister observer to stop watching
     * Should be called from ViewModel.onCleared() to prevent memory leaks
     */
    fun unregister() {
        try {
            if (contentObserver != null) {
                context.contentResolver.unregisterContentObserver(contentObserver!!)
                contentObserver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering MediaStore observer", e)
        }
        
        // Cancel any pending debounce job
        debounceJob?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
