package com.prantiux.pixelgallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlbumRepository(private val context: Context) {

    // Threshold for considering an album as "main" based on item count
    private val MAIN_ALBUM_THRESHOLD = 10


    
    /**
     * Load ML-based smart albums (used by Search screen only)
     */
    suspend fun loadSmartAlbums(): List<Album> = withContext(Dispatchers.IO) {
        try {
            SmartAlbumGenerator.generateSmartAlbums(context)
        } catch (e: Exception) {
            android.util.Log.e("AlbumRepository", "Error loading smart albums", e)
            emptyList()
        }
    }

    private suspend fun loadAllAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<String, AlbumData>()

        // Load images
        loadMediaIntoAlbumMap(
            albumMap,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            ),
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )

        // Load videos
        loadMediaIntoAlbumMap(
            albumMap,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED
            ),
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        // Convert to Album list with top media URIs and items
        albumMap.values.map { data ->
            val topItems = loadTopMediaItemsForAlbum(data.bucketId, 6)
            Album(
                id = data.bucketId,
                name = data.bucketName,
                coverUri = data.coverUri,
                itemCount = data.count,
                bucketDisplayName = data.bucketName,
                topMediaUris = topItems.map { it.uri },
                topMediaItems = topItems
            )
        }.sortedByDescending { it.itemCount }
    }

    private fun loadTopMediaForAlbum(bucketId: String, limit: Int): List<Uri> {
        val mediaUris = mutableListOf<Uri>()
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // Load images
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                mediaUris.add(uri)
                count++
            }
        }

        // Load videos if needed
        if (mediaUris.size < limit) {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                var count = mediaUris.size
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    mediaUris.add(uri)
                    count++
                }
            }
        }

        return mediaUris
    }

    private fun loadTopMediaItemsForAlbum(bucketId: String, limit: Int): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // Load images
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
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
            ),
            selection,
            selectionArgs,
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
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            
            while (cursor.moveToNext() && mediaItems.size < limit) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                mediaItems.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameColumn),
                        dateAdded = cursor.getLong(dateColumn),
                        size = cursor.getLong(sizeColumn),
                        mimeType = cursor.getString(mimeColumn),
                        bucketId = cursor.getString(bucketIdColumn),
                        bucketName = cursor.getString(bucketNameColumn),
                        isVideo = false,
                        duration = 0L,
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        path = cursor.getString(pathColumn)
                    )
                )
            }
        }

        // Load videos if needed
        if (mediaItems.size < limit) {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
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
                ),
                selection,
                selectionArgs,
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
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                
                while (cursor.moveToNext() && mediaItems.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    mediaItems.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = cursor.getString(nameColumn),
                            dateAdded = cursor.getLong(dateColumn),
                            size = cursor.getLong(sizeColumn),
                            mimeType = cursor.getString(mimeColumn),
                            bucketId = cursor.getString(bucketIdColumn),
                            bucketName = cursor.getString(bucketNameColumn),
                            isVideo = true,
                            duration = cursor.getLong(durationColumn),
                            width = cursor.getInt(widthColumn),
                            height = cursor.getInt(heightColumn),
                            path = cursor.getString(pathColumn)
                        )
                    )
                }
            }
        }

        return mediaItems.sortedByDescending { it.dateAdded }
    }

    private fun loadMediaIntoAlbumMap(
        albumMap: MutableMap<String, AlbumData>,
        contentUri: Uri,
        projection: Array<String>,
        dateColumn: String,
        baseContentUri: Uri
    ) {
        val sortOrder = "$dateColumn DESC"

        context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(projection[0])
            val bucketIdColumn = cursor.getColumnIndexOrThrow(projection[1])
            val bucketNameColumn = cursor.getColumnIndexOrThrow(projection[2])

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketId = cursor.getString(bucketIdColumn) ?: "unknown"
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"

                val data = albumMap.getOrPut(bucketId) {
                    AlbumData(
                        bucketId = bucketId,
                        bucketName = bucketName,
                        coverUri = ContentUris.withAppendedId(baseContentUri, id),
                        count = 0
                    )
                }

                albumMap[bucketId] = data.copy(count = data.count + 1)
            }
        }
    }

    private fun categorizeAlbums(albums: List<Album>): CategorizedAlbums {
        // Get top 4 albums by item count for main section
        val mainAlbums = albums.take(4)

        // Remaining albums go to other section
        val otherAlbums = albums.drop(4)

        return CategorizedAlbums(
            mainAlbums = mainAlbums,
            otherAlbums = otherAlbums
        )
    }

    private fun isMainAlbum(album: Album): Boolean {
        val nameLower = album.name.lowercase()

        // Check for specific main album names
        val mainAlbumNames = listOf(
            "camera",
            "screenshot",
            "screenshots",
            "whatsapp images",
            "whatsapp video",
            "dcim",
            "download",
            "downloads"
        )

        val isSpecialName = mainAlbumNames.any { nameLower.contains(it) }
        val isHighCount = album.itemCount >= MAIN_ALBUM_THRESHOLD

        return isSpecialName || isHighCount
    }

    private data class AlbumData(
        val bucketId: String,
        val bucketName: String,
        val coverUri: Uri?,
        val count: Int
    )
}
