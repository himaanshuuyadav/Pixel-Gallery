package com.prantiux.pixelgallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

// Result class for move operations
data class MoveResult(
    val success: Boolean,
    val message: String = ""
)

class MediaRepository(private val context: Context) {

    /**
     * Extract GPS coordinates from image EXIF data
     * MediaStore GPS columns are deprecated and return null on Android 10+
     */
    private fun getGpsCoordinates(uri: Uri): Pair<Double?, Double?> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)
                
                if (hasGps) {
                    android.util.Log.d("MediaRepository", "✓✓ EXIF GPS found: (${latLong[0]}, ${latLong[1]})")
                    Pair(latLong[0].toDouble(), latLong[1].toDouble())
                } else {
                    android.util.Log.d("MediaRepository", "✗ No EXIF GPS data")
                    Pair(null, null)
                }
            } ?: Pair(null, null)
        } catch (e: IOException) {
            android.util.Log.e("MediaRepository", "Error reading EXIF: ${e.message}")
            Pair(null, null)
        }
    }

    suspend fun loadImages(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeColumn)
                val bucketId = cursor.getString(bucketIdColumn) ?: "unknown"
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val path = cursor.getString(dataColumn) ?: ""
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                // GPS reading removed to improve gallery load performance
                // GPS data can be read on-demand when viewing individual images

                items.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        latitude = null,
                        longitude = null,
                        displayName = name,
                        dateAdded = dateAdded,
                        size = size,
                        mimeType = mimeType,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        isVideo = false,
                        width = width,
                        height = height,
                        path = path
                    )
                )
            }
        }
        items
    }

    suspend fun loadVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeColumn)
                val bucketId = cursor.getString(bucketIdColumn) ?: "unknown"
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val path = cursor.getString(dataColumn) ?: ""
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                // GPS reading removed to improve gallery load performance
                // GPS data can be read on-demand when viewing individual videos

                items.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        dateAdded = dateAdded,
                        size = size,
                        mimeType = mimeType,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        isVideo = true,
                        duration = duration,
                        width = width,
                        height = height,
                        path = path,
                        latitude = null,
                        longitude = null
                    )
                )
            }
        }
        items
    }

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val images = loadImages()
        val videos = loadVideos()
        val allMedia = images + videos
        val bucketMap = allMedia.groupBy { it.bucketId }
        
        bucketMap.map { (bucketId, items) ->
            Album(
                id = bucketId,
                name = items.first().bucketName,
                coverUri = items.firstOrNull()?.uri,
                itemCount = items.size
            )
        }.sortedByDescending { it.itemCount }
    }

    suspend fun delete(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // On Android R+, use createDeleteRequest for scoped storage
                val uris = listOf(item.uri)
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    uris
                )
                // The pending intent needs to be launched by the activity
                // Return false here to indicate that UI needs to handle the intent
                false
            } else {
                // On older versions, directly delete
                val deleted = context.contentResolver.delete(item.uri, null, null)
                deleted > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createDeleteRequest(items: List<MediaItem>): android.app.PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(
                context.contentResolver,
                items.map { it.uri }
            )
        } else {
            null
        }
    }
    
    // Create delete request from URIs
    fun createDeleteRequestFromUris(uris: List<Uri>): android.app.PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.util.Log.d("MediaRepository", "Creating delete request for ${uris.size} items")
                MediaStore.createDeleteRequest(context.contentResolver, uris)
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "Failed to create delete request", e)
                null
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10, try to get RecoverableSecurityException
            try {
                uris.forEach { uri ->
                    context.contentResolver.delete(uri, null, null)
                }
                null // Successfully deleted
            } catch (e: android.app.RecoverableSecurityException) {
                android.util.Log.d("MediaRepository", "RecoverableSecurityException caught, returning action intent")
                e.userAction.actionIntent
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "Failed to delete on Android 10", e)
                null
            }
        } else {
            null
        }
    }
    
    // Create trash request for Android 11+ (shows system dialog)
    fun createTrashRequest(uris: List<Uri>): android.app.PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.util.Log.d("MediaRepository", "Creating trash request for ${uris.size} items: $uris")
                val pendingIntent = MediaStore.createTrashRequest(
                    context.contentResolver,
                    uris,
                    true  // true = move to trash, false = restore from trash
                )
                android.util.Log.d("MediaRepository", "Trash request created successfully")
                pendingIntent
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "Error creating trash request", e)
                null
            }
        } else {
            android.util.Log.d("MediaRepository", "Trash request not supported on Android < 11")
            null
        }
    }
    
    suspend fun deleteMediaItems(context: Context, uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+, we need to use createTrashRequest which returns a PendingIntent
                // The caller needs to launch this intent for user confirmation
                // This method should not be called directly for Android 11+
                // Instead, use createTrashRequest() and handle the intent in the activity
                android.util.Log.w("MediaRepository", "deleteMediaItems should not be called directly on Android 11+. Use createTrashRequest() instead.")
                false
            } else {
                // For older versions, direct deletion
                var allDeleted = true
                uris.forEach { uri ->
                    val deleted = context.contentResolver.delete(uri, null, null)
                    android.util.Log.d("MediaRepository", "Delete (old) URI: $uri, Deleted: $deleted")
                    if (deleted == 0) allDeleted = false
                }
                allDeleted
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Fatal error in deleteMediaItems", e)
            false
        }
    }
    
    fun shareMediaItems(context: Context, uris: List<Uri>) {
        val intent = android.content.Intent().apply {
            action = if (uris.size == 1) {
                android.content.Intent.ACTION_SEND
            } else {
                android.content.Intent.ACTION_SEND_MULTIPLE
            }
            
            if (uris.size == 1) {
                putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            }
            
            type = "image/*"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooser = android.content.Intent.createChooser(intent, "Share via")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
    
    // Recycle Bin functions
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    suspend fun loadTrashedItems(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            android.util.Log.d("MediaRepository", "Trash not supported on this Android version")
            return@withContext emptyList()
        }
        
        val items = mutableListOf<MediaItem>()
        val currentTime = System.currentTimeMillis() / 1000 // Current time in seconds
        
        // Use MediaStore.Files to query ALL trashed media (images + videos)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.IS_TRASHED,
            MediaStore.Files.FileColumns.IS_PENDING,
            MediaStore.Files.FileColumns.DATE_EXPIRES
        )
        
        val bundle = android.os.Bundle().apply {
            // Match ONLY trashed items
            putInt("android:query-arg-match-trashed", 1)
            
            // Filter: IS_PENDING = 0 AND DATE_EXPIRES > currentTime AND (MEDIA_TYPE = IMAGE OR VIDEO)
            val selection = "${MediaStore.Files.FileColumns.IS_PENDING} = ? AND " +
                    "${MediaStore.Files.FileColumns.DATE_EXPIRES} > ? AND " +
                    "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
            
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    "0", // IS_PENDING = 0
                    currentTime.toString(), // DATE_EXPIRES > currentTime
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )
            )
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
        }
        
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                bundle,
                null
            )?.use { cursor ->
                android.util.Log.d("MediaRepository", "Trashed items query returned ${cursor.count} results")
                
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val dateExpiresColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_EXPIRES)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val mediaType = cursor.getInt(mediaTypeColumn)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val dateExpires = cursor.getLong(dateExpiresColumn)
                    
                    // Build appropriate URI based on media type
                    val uri = if (isVideo) {
                        android.content.ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    } else {
                        android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    }
                    
                    val displayName = cursor.getString(nameColumn) ?: "Unknown"
                    val daysUntilExpiry = (dateExpires - currentTime) / (24 * 60 * 60)
                    
                    android.util.Log.d("MediaRepository", 
                        "Trashed ${if (isVideo) "video" else "image"}: $displayName (expires in $daysUntilExpiry days)")
                    
                    items.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = displayName,
                            dateAdded = cursor.getLong(dateColumn),
                            size = cursor.getLong(sizeColumn),
                            mimeType = cursor.getString(mimeTypeColumn) ?: "",
                            isVideo = isVideo,
                            duration = if (isVideo) cursor.getLong(durationColumn) else 0,
                            bucketId = "",
                            bucketName = "",
                            dateExpires = dateExpires
                        )
                    )
                }
                
                android.util.Log.d("MediaRepository", "=== FINAL: ${items.size} valid trashed items (matching Google Files) ===")
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error loading trashed items", e)
        }
        
        items.sortedByDescending { it.dateAdded }
    }
    
    // Create MediaStore write request for restoring items from trash
    fun createRestoreRequest(uris: List<android.net.Uri>): android.app.PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        
        return try {
            MediaStore.createWriteRequest(context.contentResolver, uris)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error creating restore request", e)
            null
        }
    }
    
    // Create MediaStore delete request for permanent deletion from trash
    fun createPermanentDeleteRequest(uris: List<android.net.Uri>): android.app.PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error creating delete request", e)
            null
        }
    }
    
    // Actually restore items after user grants permission
    suspend fun performRestore(context: Context, uris: List<android.net.Uri>): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext false
        }
        
        var allSuccess = true
        uris.forEach { uri ->
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 0)
                    putNull(MediaStore.MediaColumns.DATE_EXPIRES)
                }
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated <= 0) {
                    allSuccess = false
                    android.util.Log.e("MediaRepository", "Failed to restore: $uri")
                }
            } catch (e: Exception) {
                allSuccess = false
                android.util.Log.e("MediaRepository", "Error restoring: $uri", e)
            }
        }
        allSuccess
    }
    
    // Actually delete items after user grants permission
    suspend fun performPermanentDelete(context: Context, uris: List<android.net.Uri>): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        uris.forEach { uri ->
            try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted <= 0) {
                    allSuccess = false
                    android.util.Log.e("MediaRepository", "Failed to delete: $uri")
                }
            } catch (e: Exception) {
                allSuccess = false
                android.util.Log.e("MediaRepository", "Error deleting: $uri", e)
            }
        }
        allSuccess
    }
    
    /**
     * Copy media items to a target album directory
     * Uses MediaStore to create new entries in the target album's directory
     */
    suspend fun copyMediaToAlbum(
        mediaItems: List<MediaItem>,
        targetAlbum: Album
    ): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        
        android.util.Log.d("MediaRepository", "=== COPY OPERATION START ===")
        android.util.Log.d("MediaRepository", "Target Album: ${targetAlbum.name}, ID: ${targetAlbum.id}")
        android.util.Log.d("MediaRepository", "Items to copy: ${mediaItems.size}")
        
        // Get the actual relative path from the target album's existing media
        val targetRelativePath = if (Build.VERSION.SDK_INT >= 29) {
            if (targetAlbum.topMediaItems.isNotEmpty()) {
                // Extract relative path from an existing item in the album
                val samplePath = targetAlbum.topMediaItems.first().path
                android.util.Log.d("MediaRepository", "Sample path from album: $samplePath")
                
                // Extract directory path (everything except the filename)
                val directory = java.io.File(samplePath).parent
                android.util.Log.d("MediaRepository", "Extracted directory: $directory")
                
                // Convert absolute path to relative path
                val relativePath = when {
                    directory?.contains("/storage/emulated/0/") == true -> {
                        directory.substringAfter("/storage/emulated/0/")
                    }
                    directory?.contains("/sdcard/") == true -> {
                        directory.substringAfter("/sdcard/")
                    }
                    else -> directory?.substringAfterLast("/") ?: "Pictures/${targetAlbum.name}"
                }
                android.util.Log.d("MediaRepository", "Computed relative path: $relativePath")
                relativePath
            } else {
                // Fallback to constructed path
                val fallbackPath = when {
                    targetAlbum.name.contains("Camera", ignoreCase = true) -> "DCIM/Camera"
                    targetAlbum.name.contains("Screenshots", ignoreCase = true) -> "Pictures/Screenshots"
                    else -> "Pictures/${targetAlbum.name}"
                }
                android.util.Log.d("MediaRepository", "Using fallback path: $fallbackPath")
                fallbackPath
            }
        } else {
            null
        }
        
        mediaItems.forEach { item ->
            try {
                android.util.Log.d("MediaRepository", "--- Copying: ${item.displayName} ---")
                android.util.Log.d("MediaRepository", "Source path: ${item.path}")
                android.util.Log.d("MediaRepository", "Source bucketName: ${item.bucketName}")
                
                // Get original timestamps from source media
                var originalDateAdded = item.dateAdded
                var originalDateModified = item.dateAdded
                var originalDateTaken = item.dateAdded * 1000 // Convert to milliseconds
                
                try {
                    val projection = arrayOf(
                        MediaStore.MediaColumns.DATE_ADDED,
                        MediaStore.MediaColumns.DATE_MODIFIED,
                        MediaStore.MediaColumns.DATE_TAKEN
                    )
                    
                    context.contentResolver.query(item.uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            originalDateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
                            originalDateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                            
                            val dateTakenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                            if (dateTakenIndex >= 0) {
                                originalDateTaken = cursor.getLong(dateTakenIndex)
                            }
                            
                            android.util.Log.d("MediaRepository", "Original timestamps - Added: $originalDateAdded, Modified: $originalDateModified, Taken: $originalDateTaken")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MediaRepository", "Failed to read original timestamps, using defaults", e)
                }
                
                // Determine the correct content URI
                val contentUri = if (item.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                
                // Create new MediaStore entry in target location with original timestamps
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                    put(MediaStore.MediaColumns.DATE_ADDED, originalDateAdded)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateModified)
                    put(MediaStore.MediaColumns.DATE_TAKEN, originalDateTaken)
                    
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                
                android.util.Log.d("MediaRepository", "Creating MediaStore entry at: $targetRelativePath")
                val newUri = context.contentResolver.insert(contentUri, values)
                
                if (newUri != null) {
                    android.util.Log.d("MediaRepository", "Created entry with URI: $newUri")
                    
                    // Copy the actual file content
                    var bytesCopied = 0L
                    context.contentResolver.openInputStream(item.uri)?.use { inputStream ->
                        context.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                            bytesCopied = inputStream.copyTo(outputStream)
                        }
                    }
                    android.util.Log.d("MediaRepository", "Copied $bytesCopied bytes")
                    
                    // Mark as completed and update timestamps
                    if (Build.VERSION.SDK_INT >= 29) {
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        // Try to set original timestamps (may not work for DATE_ADDED on some Android versions)
                        values.put(MediaStore.MediaColumns.DATE_ADDED, originalDateAdded)
                        values.put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateModified)
                        context.contentResolver.update(newUri, values, null, null)
                        android.util.Log.d("MediaRepository", "Marked as completed (IS_PENDING=0) and updated timestamps")
                        
                        // Verify timestamps were set correctly
                        try {
                            val verifyProjection = arrayOf(
                                MediaStore.MediaColumns.DATE_ADDED,
                                MediaStore.MediaColumns.DATE_MODIFIED,
                                MediaStore.MediaColumns.DATE_TAKEN
                            )
                            context.contentResolver.query(newUri, verifyProjection, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val actualDateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
                                    val actualDateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                                    val actualDateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                                    
                                    android.util.Log.d("MediaRepository", "Verification - Actual timestamps: Added=$actualDateAdded (wanted $originalDateAdded), Modified=$actualDateModified (wanted $originalDateModified), Taken=$actualDateTaken (wanted $originalDateTaken)")
                                    
                                    if (actualDateAdded != originalDateAdded) {
                                        android.util.Log.w("MediaRepository", "DATE_ADDED not preserved (system-managed field)")
                                    }
                                    if (actualDateModified != originalDateModified) {
                                        android.util.Log.w("MediaRepository", "DATE_MODIFIED not preserved (system-managed field)")
                                    }
                                    if (actualDateTaken != originalDateTaken) {
                                        android.util.Log.w("MediaRepository", "DATE_TAKEN not preserved")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("MediaRepository", "Could not verify timestamps", e)
                        }
                    }
                    
                    // Trigger media scanner to ensure file is indexed
                    try {
                        context.sendBroadcast(
                            android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                                data = newUri
                            }
                        )
                        android.util.Log.d("MediaRepository", "Media scanner broadcast sent")
                    } catch (e: Exception) {
                        android.util.Log.w("MediaRepository", "Media scan broadcast failed", e)
                    }
                    
                    android.util.Log.d("MediaRepository", "✓ Successfully copied ${item.displayName} to ${targetAlbum.name}")
                } else {
                    allSuccess = false
                    android.util.Log.e("MediaRepository", "✗ Failed to create MediaStore entry for: ${item.displayName}")
                }
                
            } catch (e: Exception) {
                allSuccess = false
                android.util.Log.e("MediaRepository", "✗ Error copying ${item.displayName}", e)
            }
        }
        
        android.util.Log.d("MediaRepository", "=== COPY OPERATION END (Success: $allSuccess) ===")
        allSuccess
    }
    
    /**
     * Move media items to a target album directory
     * Uses file system move + MediaStore path update (no deletion/permission needed)
     */
    suspend fun moveMediaToAlbum(
        mediaItems: List<MediaItem>,
        targetAlbum: Album
    ): MoveResult = withContext(Dispatchers.IO) {
        android.util.Log.d("MediaRepository", "=== MOVE OPERATION START ===")
        android.util.Log.d("MediaRepository", "Target Album: ${targetAlbum.name}, ID: ${targetAlbum.id}")
        android.util.Log.d("MediaRepository", "Items to move: ${mediaItems.size}")
        
        // Get target directory path from album
        val targetRelativePath = if (Build.VERSION.SDK_INT >= 29) {
            if (targetAlbum.topMediaItems.isNotEmpty()) {
                val samplePath = targetAlbum.topMediaItems.first().path
                android.util.Log.d("MediaRepository", "Sample path from album: $samplePath")
                
                val directory = java.io.File(samplePath).parent
                android.util.Log.d("MediaRepository", "Extracted directory: $directory")
                
                // Convert absolute path to relative path
                val relativePath = when {
                    directory?.contains("/storage/emulated/0/") == true -> {
                        directory.substringAfter("/storage/emulated/0/")
                    }
                    directory?.contains("/sdcard/") == true -> {
                        directory.substringAfter("/sdcard/")
                    }
                    else -> directory?.substringAfterLast("/") ?: "Pictures/${targetAlbum.name}"
                }
                android.util.Log.d("MediaRepository", "Computed relative path: $relativePath")
                relativePath
            } else {
                val fallbackPath = when {
                    targetAlbum.name.contains("Camera", ignoreCase = true) -> "DCIM/Camera"
                    targetAlbum.name.contains("Screenshots", ignoreCase = true) -> "Pictures/Screenshots"
                    else -> "Pictures/${targetAlbum.name}"
                }
                android.util.Log.d("MediaRepository", "Using fallback path: $fallbackPath")
                fallbackPath
            }
        } else {
            null
        }
        
        var allSuccess = true
        val failedItems = mutableListOf<String>()
        
        mediaItems.forEach { item ->
            try {
                android.util.Log.d("MediaRepository", "--- Moving: ${item.displayName} ---")
                android.util.Log.d("MediaRepository", "Current path: ${item.path}")
                
                // Determine new file path
                val newFilePath = if (Build.VERSION.SDK_INT >= 29) {
                    "/storage/emulated/0/$targetRelativePath/${item.displayName}"
                } else {
                    // For older Android, get target directory from album
                    if (targetAlbum.topMediaItems.isNotEmpty()) {
                        val targetDir = java.io.File(targetAlbum.topMediaItems.first().path).parent
                        "$targetDir/${item.displayName}"
                    } else {
                        null
                    }
                }
                
                if (newFilePath == null) {
                    android.util.Log.e("MediaRepository", "Cannot determine new file path")
                    allSuccess = false
                    failedItems.add(item.displayName)
                    return@forEach
                }
                
                android.util.Log.d("MediaRepository", "Target path: $newFilePath")
                
                // Try file system move first (instant, no copy)
                val sourcePath = java.io.File(item.path)
                val targetPath = java.io.File(newFilePath)
                
                // Ensure target directory exists
                targetPath.parentFile?.mkdirs()
                
                val fileMoved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        // Use NIO for efficient move
                        java.nio.file.Files.move(
                            java.nio.file.Paths.get(sourcePath.absolutePath),
                            java.nio.file.Paths.get(targetPath.absolutePath),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                        android.util.Log.d("MediaRepository", "✓ File moved via NIO")
                        true
                    } catch (e: Exception) {
                        android.util.Log.w("MediaRepository", "NIO move failed, trying traditional: ${e.message}")
                        // Fallback to traditional rename
                        sourcePath.renameTo(targetPath)
                    }
                } else {
                    // Traditional file rename for older Android
                    sourcePath.renameTo(targetPath)
                }
                
                if (!fileMoved) {
                    android.util.Log.e("MediaRepository", "✗ File move failed for ${item.displayName}")
                    allSuccess = false
                    failedItems.add(item.displayName)
                    return@forEach
                }
                
                // Update MediaStore entry to point to new location
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, newFilePath)
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                    }
                    // Update bucket info
                    put(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, targetAlbum.name)
                }
                
                val updated = context.contentResolver.update(item.uri, values, null, null)
                
                if (updated > 0) {
                    android.util.Log.d("MediaRepository", "✓ MediaStore entry updated (rows: $updated)")
                    
                    // Trigger media scanner for new location
                    try {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(newFilePath),
                            arrayOf(item.mimeType)
                        ) { path, uri ->
                            android.util.Log.d("MediaRepository", "Media scanner completed for: $path")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MediaRepository", "Media scanner failed", e)
                    }
                    
                    android.util.Log.d("MediaRepository", "✓ Successfully moved ${item.displayName} to ${targetAlbum.name}")
                } else {
                    android.util.Log.e("MediaRepository", "✗ Failed to update MediaStore entry")
                    // Try to move file back
                    try {
                        targetPath.renameTo(sourcePath)
                    } catch (e: Exception) {
                        android.util.Log.e("MediaRepository", "Failed to rollback file move", e)
                    }
                    allSuccess = false
                    failedItems.add(item.displayName)
                }
                
            } catch (e: Exception) {
                allSuccess = false
                failedItems.add(item.displayName)
                android.util.Log.e("MediaRepository", "✗ Error moving ${item.displayName}", e)
            }
        }
        
        if (failedItems.isNotEmpty()) {
            android.util.Log.e("MediaRepository", "Failed to move ${failedItems.size} files: ${failedItems.joinToString()}")
        }
        
        android.util.Log.d("MediaRepository", "=== MOVE OPERATION END (Success: $allSuccess) ===")
        MoveResult(
            success = allSuccess,
            message = if (allSuccess) "All files moved" else "Some files failed to move"
        )
    }
}
