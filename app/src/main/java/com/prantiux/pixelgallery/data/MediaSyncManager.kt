package com.prantiux.pixelgallery.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages MediaStore content changes and triggers incremental syncing
 * 
 * Features:
 * - Registers ContentObserver on MediaStore.Images/Videos
 * - Debounces rapid changes (300-500ms)
 * - Triggers incremental sync on changes
 * - Lifecycle tied to ViewModel - unregisters on cleanup
 * - No memory leaks with proper handler management
 */
class MediaSyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: MediaRepository,
    private val settingsDataStore: SettingsDataStore,
    private val onSyncTriggered: (suspend () -> Unit)
) {
    companion object {
        private const val TAG = "MediaSyncManager"
        private const val DEBOUNCE_MS = 500L // Wait 500ms for change notifications to settle
    }
    
    private var contentObserver: ContentObserver? = null
    private var debounceJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Register ContentObserver to watch for MediaStore changes
     * Should be called when ViewModel initializes
     */
    fun registerObserver(coroutineScope: CoroutineScope) {
        try {
            val mediaStoreUris = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.INTERNAL_CONTENT_URI
            )
            
            contentObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Log.d(TAG, "MediaStore change detected at: $uri")
                    
                    // Debounce rapid changes
                    debounceJob?.cancel()
                    debounceJob = coroutineScope.launch {
                        delay(DEBOUNCE_MS)
                        Log.d(TAG, "Triggering sync after debounce")
                        onSyncTriggered()
                    }
                }
            }
            
            // Register observer for both images and videos
            mediaStoreUris.forEach { uri ->
                context.contentResolver.registerContentObserver(
                    uri,
                    true, // notifyForDescendants
                    contentObserver!!
                )
            }
            
            Log.d(TAG, "ContentObserver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering ContentObserver", e)
        }
    }
    
    /**
     * Unregister ContentObserver when ViewModel is cleared
     * Prevents memory leaks
     */
    fun unregisterObserver() {
        try {
            if (contentObserver != null) {
                context.contentResolver.unregisterContentObserver(contentObserver!!)
                contentObserver = null
                Log.d(TAG, "ContentObserver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering ContentObserver", e)
        }
        
        // Cancel any pending debounce
        debounceJob?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Perform incremental sync based on lastSyncTimestamp
     * 
     * Only queries MediaStore for items added after last sync
     * Updates database incrementally instead of replacing
     */
    suspend fun performIncrementalSync(lastSyncTimestamp: Long) {
        try {
            Log.d(TAG, "Starting incremental sync from timestamp: $lastSyncTimestamp")
            val start = System.currentTimeMillis()
            
            // Query MediaStore for all items (will filter by timestamp)
            val allImages = repository.loadImages()
            val allVideos = repository.loadVideos()
            val mediaStoreAll = allImages + allVideos
            
            // Find new items added since last sync (dateAdded is in seconds, convert timestamp to seconds)
            val lastSyncSeconds = lastSyncTimestamp / 1000
            val newItems = mediaStoreAll.filter { it.dateAdded > lastSyncSeconds }
            
            Log.d(TAG, "Found +${newItems.size} new items since last sync")
            
            // Get all current IDs from database
            val cachedIds = database.mediaDao().getAllMediaIds().toSet()
            
            // Get all current IDs from MediaStore
            val mediaStoreIds = mediaStoreAll.map { it.id }.toSet()
            
            // Find deleted items (in DB but not in MediaStore)
            val deletedIds = cachedIds - mediaStoreIds
            Log.d(TAG, "Found -${deletedIds.size} deleted items")
            
            // Insert/update only new items
            if (newItems.isNotEmpty()) {
                val newEntities = newItems.map { MediaEntity.fromMediaItem(it) }
                database.mediaDao().upsertMedia(newEntities)
                Log.d(TAG, "Inserted/updated ${newEntities.size} new items")
            }
            
            // Delete removed items
            if (deletedIds.isNotEmpty()) {
                database.mediaDao().deleteByIds(deletedIds.toList())
                Log.d(TAG, "Deleted ${deletedIds.size} removed items")
            }
            
            // Update last sync timestamp to NOW
            settingsDataStore.saveLastSyncTimestamp(System.currentTimeMillis())
            
            val elapsed = System.currentTimeMillis() - start
            Log.d(TAG, "Incremental sync complete in ${elapsed}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error in incremental sync", e)
        }
    }
}
