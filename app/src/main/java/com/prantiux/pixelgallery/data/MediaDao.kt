package com.prantiux.pixelgallery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class BucketInfo(
    val bucketId: String,
    val bucketName: String?,
    val count: Int
)

@Dao
interface MediaDao {

    // ═════════════════════════════════════════════════════════════════════
    // PRIMARY QUERY - ALL MEDIA (Room-first architecture)
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getPagedMedia(): androidx.paging.PagingSource<Int, MediaEntity>



    // ═════════════════════════════════════════════════════════════════════
    // DATE SORTING (Primary sort mode)
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getMediaByDateDesc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    fun getPagedMediaByDateDesc(): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media ORDER BY dateAdded ASC")
    fun getMediaByDateAsc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY dateAdded ASC")
    fun getPagedMediaByDateAsc(): androidx.paging.PagingSource<Int, MediaEntity>

    // ═════════════════════════════════════════════════════════════════════
    // NAME SORTING
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY displayName ASC")
    fun getMediaByNameAsc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY displayName ASC")
    fun getPagedMediaByNameAsc(): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media ORDER BY displayName DESC")
    fun getMediaByNameDesc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY displayName DESC")
    fun getPagedMediaByNameDesc(): androidx.paging.PagingSource<Int, MediaEntity>

    // ═════════════════════════════════════════════════════════════════════
    // SIZE SORTING
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media ORDER BY size DESC")
    fun getMediaBySizeDesc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY size DESC")
    fun getPagedMediaBySizeDesc(): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media ORDER BY size ASC")
    fun getMediaBySizeAsc(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY size ASC")
    fun getPagedMediaBySizeAsc(): androidx.paging.PagingSource<Int, MediaEntity>

    // ═════════════════════════════════════════════════════════════════════
    // TYPE FILTERING (by video/image)
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media WHERE isVideo = 0 ORDER BY dateAdded DESC")
    fun getAllImages(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isVideo = 0")
    suspend fun getAllImagesOnce(): List<MediaEntity>

    // ═════════════════════════════════════════════════════════════════════
    // GET IMAGES BY MULTIPLE BUCKETS (for Photos View album filter)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE isVideo = 0 AND bucketId IN (:bucketIds)
        ORDER BY dateAdded DESC
    """)
    fun getImagesByBucketIds(bucketIds: List<String>): Flow<List<MediaEntity>>

    @Query("""
        SELECT * FROM media
        WHERE bucketId IN (:bucketIds)
        ORDER BY dateAdded DESC
    """)
    fun getPagedMediaByBucketIdsDateDesc(bucketIds: List<String>): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("""
        SELECT * FROM media
        WHERE bucketId IN (:bucketIds)
        ORDER BY dateAdded ASC
    """)
    fun getPagedMediaByBucketIdsDateAsc(bucketIds: List<String>): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("""
        SELECT * FROM media
        WHERE bucketId IN (:bucketIds)
        ORDER BY displayName ASC
    """)
    fun getPagedMediaByBucketIdsNameAsc(bucketIds: List<String>): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("""
        SELECT * FROM media
        WHERE bucketId IN (:bucketIds)
        ORDER BY displayName DESC
    """)
    fun getPagedMediaByBucketIdsNameDesc(bucketIds: List<String>): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("""
        SELECT * FROM media
        WHERE bucketId IN (:bucketIds)
        ORDER BY size DESC
    """)
    fun getPagedMediaByBucketIdsSizeDesc(bucketIds: List<String>): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("""
        SELECT * FROM media
        WHERE bucketId IN (:bucketIds)
        ORDER BY size ASC
    """)
    fun getPagedMediaByBucketIdsSizeAsc(bucketIds: List<String>): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media WHERE isVideo = 1 ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // GET VIDEOS BY MULTIPLE BUCKETS (for Photos View album filter)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE isVideo = 1 AND bucketId IN (:bucketIds)
        ORDER BY dateAdded DESC
    """)
    fun getVideosByBucketIds(bucketIds: List<String>): Flow<List<MediaEntity>>

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

    @Query("""
        SELECT * FROM media
        WHERE displayName LIKE '%' || :query || '%'
        OR bucketName LIKE '%' || :query || '%'
        ORDER BY dateAdded DESC
    """)
    fun searchPagedMedia(query: String): androidx.paging.PagingSource<Int, MediaEntity>

    // ═════════════════════════════════════════════════════════════════════

    // GET MEDIA BY BUCKET (for album detail view)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE bucketId = :bucketId
        ORDER BY dateAdded DESC
    """)
    fun getMediaByBucket(bucketId: String): Flow<List<MediaEntity>>

    @Query("""
        SELECT bucketId, bucketName, COUNT(*) as count 
        FROM media 
        WHERE bucketId IS NOT NULL 
        GROUP BY bucketId, bucketName 
        ORDER BY count DESC
    """)
    fun getAllBuckets(): List<BucketInfo>

    @Query("""
        SELECT bucketId, bucketName, COUNT(*) as count 
        FROM media 
        WHERE bucketId IS NOT NULL 
        GROUP BY bucketId, bucketName 
        ORDER BY count DESC
    """)
    fun getAllBucketsFlow(): Flow<List<BucketInfo>>

    @Query("SELECT * FROM media WHERE bucketId = :bucketId ORDER BY dateAdded DESC LIMIT :limit")
    fun getTopMediaForBucket(bucketId: String, limit: Int): List<MediaEntity>

    // ═════════════════════════════════════════════════════════════════════
    // SELECTION QUERIES (For Select All in Paging)
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM media WHERE dateGroupDay = :dateGroupDay")
    suspend fun getMediaByDateGroupDay(dateGroupDay: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE dateGroupMonth = :dateGroupMonth")
    suspend fun getMediaByDateGroupMonth(dateGroupMonth: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE bucketId = :bucketId")
    suspend fun getMediaByBucketOnce(bucketId: String): List<MediaEntity>

    // ═════════════════════════════════════════════════════════════════════
    // GET MEDIA BY MULTIPLE BUCKETS (for Photos View album filter)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE bucketId IN (:bucketIds)
        ORDER BY dateAdded DESC
    """)
    fun getMediaByBucketIds(bucketIds: List<String>): Flow<List<MediaEntity>>

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
    // ADVANCED SEARCH - DATE FILTERING (with name search)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        AND dateAdded >= :startMs AND dateAdded <= :endMs
        ORDER BY dateAdded DESC
    """)
    fun searchByDateRange(query: String, startMs: Long, endMs: Long): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - MEDIA TYPE FILTERING (photo/video with name search)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        AND isVideo = :isVideo
        ORDER BY dateAdded DESC
    """)
    fun searchByMediaType(query: String, isVideo: Boolean): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - SIZE FILTERING (with name search)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        AND size BETWEEN :minSize AND :maxSize
        ORDER BY dateAdded DESC
    """)
    fun searchBySize(query: String, minSize: Long, maxSize: Long): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - GIF FILTERING (mimeType)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' 
              OR bucketName LIKE '%' || :query || '%')
          AND mimeType LIKE '%gif%'
        ORDER BY dateAdded DESC
    """)
    fun searchByGif(query: String): Flow<List<MediaEntity>>

    @Query("""
        SELECT * FROM media
        WHERE mimeType LIKE '%gif%'
        ORDER BY dateAdded DESC
    """)
    fun searchByGifOnly(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - SCREENSHOT FILTERING (special case)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' 
              OR bucketName LIKE '%' || :query || '%')
          AND (bucketName LIKE '%screenshot%' OR displayName LIKE '%screenshot%')
        ORDER BY dateAdded DESC
    """)
    fun searchByScreenshots(query: String): Flow<List<MediaEntity>>

    @Query("""
        SELECT * FROM media
        WHERE bucketName LIKE '%screenshot%'
           OR displayName LIKE '%screenshot%'
        ORDER BY dateAdded DESC
    """)
    fun searchScreenshotsOnly(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - CAMERA FILTERING (special case)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' 
              OR bucketName LIKE '%' || :query || '%')
          AND (bucketName LIKE '%camera%' OR bucketName LIKE '%dcim%')
        ORDER BY dateAdded DESC
    """)
    fun searchByCamera(query: String): Flow<List<MediaEntity>>

    @Query("""
        SELECT * FROM media
        WHERE bucketName LIKE '%camera%'
           OR bucketName LIKE '%dcim%'
        ORDER BY dateAdded DESC
    """)
    fun searchByCameraOnly(): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // ADVANCED SEARCH - ML LABEL FILTERING (with JOIN to media_labels)
    // ═════════════════════════════════════════════════════════════════════
    @Query("""
        SELECT DISTINCT m.* FROM media m
        INNER JOIN media_labels ml ON m.id = ml.mediaId
        WHERE ml.labels LIKE '%' || :label || '%'
        ORDER BY m.dateAdded DESC
    """)
    fun searchByLabel(label: String): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // COMPOSITE SEARCHES - MULTIPLE FILTERS
    // ═════════════════════════════════════════════════════════════════════
    
    // Type + Date
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        AND isVideo = :isVideo
        AND dateAdded >= :startMs AND dateAdded <= :endMs
        ORDER BY dateAdded DESC
    """)
    fun searchByTypeAndDate(query: String, isVideo: Boolean, startMs: Long, endMs: Long): Flow<List<MediaEntity>>

    // Type + Size
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        AND isVideo = :isVideo
        AND size BETWEEN :minSize AND :maxSize
        ORDER BY dateAdded DESC
    """)
    fun searchByTypeAndSize(query: String, isVideo: Boolean, minSize: Long, maxSize: Long): Flow<List<MediaEntity>>

    // Size + Date
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        AND size BETWEEN :minSize AND :maxSize
        AND dateAdded >= :startMs AND dateAdded <= :endMs
        ORDER BY dateAdded DESC
    """)
    fun searchBySizeAndDate(query: String, minSize: Long, maxSize: Long, startMs: Long, endMs: Long): Flow<List<MediaEntity>>

    // Type + Size + Date
    @Query("""
        SELECT * FROM media
        WHERE (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        AND isVideo = :isVideo
        AND size BETWEEN :minSize AND :maxSize
        AND dateAdded >= :startMs AND dateAdded <= :endMs
        ORDER BY dateAdded DESC
    """)
    fun searchByTypeAndSizeAndDate(query: String, isVideo: Boolean, minSize: Long, maxSize: Long, startMs: Long, endMs: Long): Flow<List<MediaEntity>>

    // ═════════════════════════════════════════════════════════════════════
    // UTILITY QUERIES
    // ═════════════════════════════════════════════════════════════════════
    @Query("SELECT id FROM media")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun getMediaCount(): Int

    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun getMediaByIdOnce(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<Long>): List<MediaEntity>

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


