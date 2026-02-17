package com.prantiux.pixelgallery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ═════════════════════════════════════════════════════════════════════
    // PRIMARY QUERY - ALL MEDIA (Room-first architecture)
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // DATE SORTING (Primary sort mode)
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getMediaByDateDesc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY dateAdded ASC")
    fun getMediaByDateAsc(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // NAME SORTING
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY displayName ASC")
    fun getMediaByNameAsc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY displayName DESC")
    fun getMediaByNameDesc(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // SIZE SORTING
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY size DESC")
    fun getMediaBySizeDesc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY size ASC")
    fun getMediaBySizeAsc(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // TYPE FILTERING (by video/image)
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media WHERE isVideo = 0 ORDER BY dateAdded DESC")
    fun getAllImages(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isVideo = 1 ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // SEARCH QUERY (Room-based search - displayName + bucketName)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE displayName LIKE '%' || :query || '%'
        OR bucketName LIKE '%' || :query || '%'
        ORDER BY dateAdded DESC
    """)
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - ML LABELS (Future implementation when labels table ready)
    // Search by ML-generated labels (e.g., "dog", "sunset", "mountain")
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT DISTINCT m.* FROM media m
        LEFT JOIN media_labels ml ON m.id = ml.mediaId
        WHERE ml.label LIKE '%' || :query || '%'
        ORDER BY m.dateAdded DESC
    """)
    fun searchMediaByLabel(query: String): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - DATE RANGE
    // Find media within a specific date range
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE dateAdded BETWEEN :startDate AND :endDate
        ORDER BY dateAdded DESC
    """)
    fun searchMediaByDateRange(startDate: Long, endDate: Long): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - SIZE RANGE
    // Find media within a specific file size range (in bytes)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE size BETWEEN :minSize AND :maxSize
        ORDER BY dateAdded DESC
    """)
    fun searchMediaBySize(minSize: Long, maxSize: Long): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // SMART ALBUMS - SCREENSHOTS
    // Find all screenshots (path contains "Screenshots")
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE path LIKE '%Screenshot%'
        ORDER BY dateAdded DESC
    """)
    fun getScreenshots(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ALBUMS QUERY (Group by bucket)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT 
            bucketId,
            bucketName,
            COUNT(*) as itemCount,
            MAX(dateAdded) as lastModified,
            MIN(uri) as coverUri
        FROM media
        WHERE bucketId IS NOT NULL
        GROUP BY bucketId
        ORDER BY lastModified DESC
    """)
    fun getAlbumsRaw(): Flow<List<AlbumData>>

    // ═════════════════════════════════════════════════════════════════════
    // GET MEDIA BY BUCKET (for album detail view)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE bucketId = :bucketId
        ORDER BY dateAdded DESC
    """)
    fun getMediaByBucket(bucketId: String): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // SMART ALBUMS - RECENTLY ADDED
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE dateAdded >= :threshold
        ORDER BY dateAdded DESC
    """)
    fun getRecentlyAdded(threshold: Long): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // SMART ALBUMS - FAVORITES (JOIN with favorites table)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT m.* FROM media m
        INNER JOIN favorites f ON m.id = f.mediaId
        ORDER BY f.timestamp DESC
    """)
    fun getFavoriteMedia(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // UTILITY QUERIES
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT id FROM media")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun getMediaCount(): Int

    // ═════════════════════════════════════════════════════════════════════
    // SYNC OPERATIONS
    // ═════════════════════════════════════════════════════════════════════
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media")
    suspend fun deleteAll()
}

// Data class for album grouping result
data class AlbumData(
    val bucketId: String?,
    val bucketName: String?,
    val itemCount: Int,
    val lastModified: Long,
    val coverUri: String
)
