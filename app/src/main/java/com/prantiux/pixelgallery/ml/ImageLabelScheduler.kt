package com.prantiux.pixelgallery.ml

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Manages ML-based image labeling using WorkManager
 * 
 * Features:
 * - Schedules background work only when charging
 * - Respects battery constraints
 * - Periodic checks every 6 hours (when charging)
 * - One-time immediate scan when user opens app
 */
object ImageLabelScheduler {
    
    private const val LABEL_WORK_TAG = "image_labeling"
    private const val PERIODIC_WORK_NAME = "periodic_image_labeling"
    
    /**
     * Schedule periodic image labeling (runs when charging)
     */
    fun schedulePeriodicLabeling(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true) // Only run when device is charging
            .setRequiresBatteryNotLow(true)
            .build()
        
        val periodicWork = PeriodicWorkRequestBuilder<ImageLabelWorker>(
            repeatInterval = 6, // Every 6 hours (minimum allowed)
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(LABEL_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work
            periodicWork
        )
    }
    
    /**
     * Trigger immediate one-time labeling
     * Called when user opens app - starts IMMEDIATELY with no charging requirement
     */
    fun triggerImmediateLabelingIfCharging(context: Context) {
        // No charging check, no constraints - ML starts immediately
        val oneTimeWork = OneTimeWorkRequestBuilder<ImageLabelWorker>()
            .addTag(LABEL_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            "immediate_labeling",
            ExistingWorkPolicy.REPLACE,
            oneTimeWork
        )
    }
    
    /**
     * Schedule deferred one-time labeling (only if needed)
     * Uses KEEP policy to prevent duplicate workers
     */
    fun scheduleDeferredLabeling(context: Context) {
        val oneTimeWork = OneTimeWorkRequestBuilder<ImageLabelWorker>()
            .addTag(LABEL_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            "image_labeling",
            ExistingWorkPolicy.KEEP,
            oneTimeWork
        )
    }
    
    /**
     * Check if device is currently charging
     */
    fun isCharging(context: Context): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }
    
    /**
     * Get current work info for UI display
     */
    fun getLabelingWorkInfo(context: Context) = 
        WorkManager.getInstance(context).getWorkInfosByTagLiveData(LABEL_WORK_TAG)
    
    /**
     * Cancel all labeling work (for debugging)
     */
    fun cancelAllWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(LABEL_WORK_TAG)
    }
}
