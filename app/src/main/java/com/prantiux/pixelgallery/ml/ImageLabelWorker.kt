package com.prantiux.pixelgallery.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.prantiux.pixelgallery.data.AppDatabase
import com.prantiux.pixelgallery.data.MediaLabelEntity
import com.prantiux.pixelgallery.data.MediaRepository
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Background worker for processing images with ML Kit Image Labeling
 * 
 * Features:
 * - Runs only when device is charging
 * - Processes images in batches (10-20 per run)
 * - Skips already-processed images
 * - Uses downscaled bitmaps (224x224) for efficiency
 * - 100% on-device, no cloud APIs
 */
class ImageLabelWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ImageLabelWorker"
        private const val TARGET_IMAGE_SIZE = 224 // Downscale to 224x224 for ML
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        private const val PROGRESS_UPDATE_INTERVAL = 5 // Update progress every 5 images
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "🤖 ML IMAGE LABELING WORKER STARTED")
        Log.d(TAG, "═══════════════════════════════════════")
        
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val labelDao = database.mediaLabelDao()
            val repository = MediaRepository(applicationContext)
            
            // Get all media from MediaStore
            Log.d(TAG, "📸 Loading images from MediaStore...")
            val allImages = repository.loadImages()
            val totalImages = allImages.size
            Log.d(TAG, "📊 Total images found: $totalImages")
            
            // Get already processed IDs
            Log.d(TAG, "🔍 Checking already processed images...")
            val processedIds = labelDao.getAllProcessedIds().toSet()
            Log.d(TAG, "✅ Already labeled: ${processedIds.size} images")
            
            // Filter unprocessed images
            val unprocessedImages = allImages.filter { it.id !in processedIds }
            Log.d(TAG, "⏳ Remaining to process: ${unprocessedImages.size} images")
            
            if (unprocessedImages.isEmpty()) {
                Log.d(TAG, "")
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "🎉 ALL IMAGES ALREADY LABELED!")
                Log.d(TAG, "📊 Total: $totalImages / $totalImages (100%)")
                Log.d(TAG, "═══════════════════════════════════════")
                return Result.success(workDataOf(
                    KEY_PROGRESS to totalImages,
                    KEY_TOTAL to totalImages
                ))
            }
            
            Log.d(TAG, "")
            Log.d(TAG, "📦 Processing ALL ${unprocessedImages.size} images continuously...")
            Log.d(TAG, "───────────────────────────────────────")
            
            // Process ALL unprocessed images (no batching)
            val batch = unprocessedImages
            
            // Initialize ML Kit Image Labeler (on-device only)
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.6f) // Only labels with >60% confidence
                .build()
            val labeler = ImageLabeling.getClient(options)
            
            // Process ALL images continuously with progress updates
            var successCount = 0
            batch.forEachIndexed { index, mediaItem ->
                val currentNum = index + 1
                val totalCount = batch.size
                try {
                    // Log every image
                    Log.d(TAG, "[$currentNum/$totalCount] ${mediaItem.displayName}")
                    
                    // Load downscaled bitmap
                    val bitmap = loadDownscaledBitmap(mediaItem.uri.toString(), TARGET_IMAGE_SIZE)
                    
                    if (bitmap != null) {
                        // Run ML Kit labeling
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val labels = labeler.process(image).await()
                        
                        // Extract label text (lowercase for case-insensitive search)
                        val labelText = labels.joinToString(",") { label -> 
                            "${label.text.lowercase()}(${(label.confidence * 100).toInt()}%)"
                        }
                        
                        // Store in database with confidence scores
                        labelDao.insert(
                            MediaLabelEntity(
                                mediaId = mediaItem.id,
                                labels = labels.joinToString(",") { it.text.lowercase() },
                                labelsWithConfidence = labels.joinToString(",") { label ->
                                    "${label.text.lowercase()}:${label.confidence}"
                                }
                            )
                        )
                        
                        Log.d(TAG, "  ✅ $labelText")
                        successCount++
                        
                        // Update progress every 5 images for UI (not every single image)
                        if (currentNum % PROGRESS_UPDATE_INTERVAL == 0 || currentNum == totalCount) {
                            val currentProcessed = processedIds.size + successCount
                            setProgressAsync(workDataOf(
                                KEY_PROGRESS to currentProcessed,
                                KEY_TOTAL to totalImages
                            ))
                            val progressPercent = (currentProcessed * 100.0 / totalImages).toInt()
                            Log.d(TAG, "  📊 Progress: $currentProcessed / $totalImages ($progressPercent%)")
                        }
                        
                        // Recycle bitmap
                        bitmap.recycle()
                    } else {
                        Log.w(TAG, "  ⚠️  Failed to load bitmap")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ❌ Error: ${e.message}")
                }
            }
            
            // Close labeler
            labeler.close()
            
            val processedCount = processedIds.size + successCount
            val progressPercent = (processedCount.toFloat() / totalImages * 100).toInt()
            
            Log.d(TAG, "")
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "🎉 ML LABELING COMPLETE!")
            Log.d(TAG, "  • Successfully labeled: $successCount new images")
            Log.d(TAG, "  • Total labeled: $processedCount / $totalImages ($progressPercent%)")
            Log.d(TAG, "  • Failed: ${unprocessedImages.size - successCount} images")
            Log.d(TAG, "✅ Ready for object-based search")
            Log.d(TAG, "═══════════════════════════════════════")
            
            Result.success(workDataOf(
                KEY_PROGRESS to processedCount,
                KEY_TOTAL to totalImages
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.failure()
        }
    }
    
    /**
     * Load bitmap downscaled to target size for efficient ML processing
     * Never loads full-resolution images to prevent memory issues
     */
    private fun loadDownscaledBitmap(uriString: String, targetSize: Int): Bitmap? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            
            // First decode bounds only (no bitmap allocation)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            // Calculate inSampleSize for downscaling
            val width = options.outWidth
            val height = options.outHeight
            var inSampleSize = 1
            
            if (height > targetSize || width > targetSize) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                
                while ((halfHeight / inSampleSize) >= targetSize &&
                       (halfWidth / inSampleSize) >= targetSize) {
                    inSampleSize *= 2
                }
            }
            
            // Decode downscaled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inJustDecodeBounds = false
            }
            
            applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Error loading bitmap", e)
            null
        }
    }
}
