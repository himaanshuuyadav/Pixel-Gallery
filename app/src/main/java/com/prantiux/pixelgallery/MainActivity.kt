package com.prantiux.pixelgallery

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SparseIntArray
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.core.app.FrameMetricsAggregator
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.prantiux.pixelgallery.navigation.AppNavigation
import com.prantiux.pixelgallery.startup.FrameDebug
import com.prantiux.pixelgallery.startup.StartupTrace
import com.prantiux.pixelgallery.ui.theme.PixelGalleryTheme
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainActivity"
private const val FRAME_DEBUG_TAG = "FrameDebug"

class MainActivity : ComponentActivity() {
    private val viewModel: MediaViewModel by viewModels()
    private var uiReady by mutableStateOf(false)
    private var deferredStartupScheduled = false
    private var frameMetricsLoggerJob: Job? = null
    private var frameMetricsAggregator: FrameMetricsAggregator? = null
    
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
        FrameDebug.installMainThreadBlockDetector()
        StartupTrace.markStage("MainActivity started", once = true)

        val splashScreen = installSplashScreen()
        StartupTrace.markStage("Splash installed", once = true)
        splashScreen.setKeepOnScreenCondition { !uiReady }
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.view
                .animate()
                .alpha(0f)
                .setDuration(120L)
                .withEndAction {
                    splashScreenViewProvider.remove()
                }
                .start()
        }
        super.onCreate(savedInstanceState)
        StartupTrace.markStage("MainActivity.super.onCreate completed", once = true)
        
        // We need edge-to-edge for proper content padding with statusBarsPadding()
        // BUT we want solid bar colors, not transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // CRITICAL: For solid system bar colors on Android 10+, we must set them AFTER edge-to-edge
        // and ensure contrast enforcement is disabled
        
        // Modern approach: Use WindowInsetsController for system bar colors (non-deprecated)
        val navBarColor = android.graphics.Color.BLACK
        val statusBarColor = android.graphics.Color.BLACK
        
        // Set colors - suppress deprecation warning as these APIs are still needed for compatibility
        // Note: These are deprecated in Java but still the standard way to set colors on all API levels
        @Suppress("DEPRECATION")
        window.statusBarColor = statusBarColor
        @Suppress("DEPRECATION")
        window.navigationBarColor = navBarColor
        
        // Disable navigation bar contrast enforcement on Android Q+
        // This is CRITICAL - contrast enforcement can override our solid color with scrim
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        // IMPORTANT: Also clear any window flags that might cause transparency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Ensure the window draws system bar backgrounds
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            // Clear any translucent flags (deprecated but necessary for older devices)
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
        
        // Set light/dark status bar icons based on status bar color
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false // Light icons for dark status bar
        insetsController.isAppearanceLightNavigationBars = false // Light icons for dark nav bar
        
        setContent {
            var startupReady by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                StartupTrace.markStage("Compose content set", once = true)
                withFrameNanos { }
                StartupTrace.markStage("First Compose UI frame rendered", once = true)

                startupReady = true
                scheduleDeferredStartupWork()
                StartupTrace.markStage("Deferred startup tasks launched", once = true)
            }

            LaunchedEffect(startupReady) {
                if (!startupReady) return@LaunchedEffect
                // Wait one more frame so AppNavigation content is committed before splash exit.
                withFrameNanos { }
                uiReady = true
                StartupTrace.markStage("Splash dismissed", once = true)
            }

            val settingsDataStore = remember { com.prantiux.pixelgallery.data.SettingsDataStore(this) }
            var appTheme by remember { mutableStateOf("System Default") }
            var dynamicColor by remember { mutableStateOf(true) }
            var amoledMode by remember { mutableStateOf(false) }
            var defaultTab by remember { mutableStateOf("Gallery") }
            var lastUsedTab by remember { mutableStateOf("Gallery") }
            
            // Load theme settings
            LaunchedEffect(startupReady) {
                if (!startupReady) return@LaunchedEffect
                lifecycleScope.launch {
                    settingsDataStore.appThemeFlow.collect { theme ->
                        appTheme = theme
                    }
                }
            }
            
            LaunchedEffect(startupReady) {
                if (!startupReady) return@LaunchedEffect
                lifecycleScope.launch {
                    settingsDataStore.dynamicColorFlow.collect { enabled ->
                        dynamicColor = enabled
                    }
                }
            }
            
            LaunchedEffect(startupReady) {
                if (!startupReady) return@LaunchedEffect
                lifecycleScope.launch {
                    settingsDataStore.amoledModeFlow.collect { enabled ->
                        amoledMode = enabled
                    }
                }
            }
            
            LaunchedEffect(startupReady) {
                if (!startupReady) return@LaunchedEffect
                lifecycleScope.launch {
                    settingsDataStore.defaultTabFlow.collect { tab ->
                        defaultTab = tab
                    }
                }
            }
            
            LaunchedEffect(startupReady) {
                if (!startupReady) return@LaunchedEffect
                lifecycleScope.launch {
                    settingsDataStore.lastUsedTabFlow.collect { tab ->
                        lastUsedTab = tab
                    }
                }
            }
            
            // Determine dark theme based on settings
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (appTheme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemDark
            }
            
            // Update navigation bar color based on theme
            // Use SideEffect to ensure it runs on every recomposition
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                val navBarColor = if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                
                // On Android 15+, window.navigationBarColor may be ignored in edge-to-edge
                // The actual color comes from NavigationBarBackground composable
                // But we still need to set appearance for system icons
                @Suppress("DEPRECATION")
                window.navigationBarColor = navBarColor
                
                // CRITICAL: Set the appearance (light/dark icons) for proper contrast
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                
                // Disable contrast enforcement to ensure solid color
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                android.util.Log.d(TAG, "===========================")
            }
            
            PixelGalleryTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                amoledMode = amoledMode
            ) {
                if (startupReady) {
                    AppNavigation(
                        viewModel = viewModel,
                        settingsDataStore = settingsDataStore,
                        defaultTab = defaultTab,
                        lastUsedTab = lastUsedTab,
                        onTabChanged = { tab ->
                            lastUsedTab = tab
                            lifecycleScope.launch {
                                settingsDataStore.saveLastUsedTab(tab)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun scheduleDeferredStartupWork() {
        if (deferredStartupScheduled) return
        deferredStartupScheduled = true

        attachDeferredActivityLaunchers()

        lifecycleScope.launch(Dispatchers.Default) {
            viewModel.initialize(this@MainActivity)
            StartupTrace.markStage("MediaViewModel initialized", once = true)
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val imageLoader = ImageLoader.Builder(this@MainActivity)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .memoryCache {
                    MemoryCache.Builder(this@MainActivity)
                        .maxSizePercent(0.25)
                        .strongReferencesEnabled(true)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(250L * 1024 * 1024)
                        .build()
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build()

            withContext(Dispatchers.Main) {
                coil.Coil.setImageLoader(imageLoader)
                StartupTrace.markStage("Coil ImageLoader ready", once = true)
            }
        }

        lifecycleScope.launch {
            delay(10000)
            if (viewModel.initialSetupInProgress.value) {
                viewModel.initialSetupInProgress.filter { !it }.first()
            }
            withContext(Dispatchers.IO) {
                com.prantiux.pixelgallery.ml.ImageLabelScheduler.schedulePeriodicLabeling(this@MainActivity)
            }
            StartupTrace.markStage("Periodic labeling scheduled", once = true)
        }

        com.prantiux.pixelgallery.ml.ImageLabelScheduler.getLabelingWorkInfo(this).observe(this) { workInfoList ->
            val workInfo = workInfoList?.firstOrNull()
            if (workInfo != null) {
                val isRunning = workInfo.state == androidx.work.WorkInfo.State.RUNNING
                viewModel.setLabelingInProgress(isRunning)

                if (isRunning) {
                    val progress = workInfo.progress.getInt(
                        com.prantiux.pixelgallery.ml.ImageLabelWorker.KEY_PROGRESS,
                        0
                    )
                    val total = workInfo.progress.getInt(
                        com.prantiux.pixelgallery.ml.ImageLabelWorker.KEY_TOTAL,
                        0
                    )
                    if (total > 0) {
                        viewModel.updateLabelingProgress(progress, total)
                    }
                }
            }
        }
        StartupTrace.markStage("Labeling observer attached", once = true)
    }

    private fun attachDeferredActivityLaunchers() {
        viewModel.setTrashRequestLauncher { pendingIntent ->
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                trashRequestLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error launching trash request", e)
                viewModel.onDeleteCancelled()
            }
        }

        viewModel.setRestoreRequestLauncher { pendingIntent ->
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                restoreRequestLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error launching restore request", e)
                viewModel.onRestoreCancelled()
            }
        }

        viewModel.setPermanentDeleteRequestLauncher { pendingIntent ->
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                permanentDeleteRequestLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error launching permanent delete request", e)
                viewModel.onPermanentDeleteCancelled()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startFrameMetricsDiagnostics()
        }
    }

    override fun onStop() {
        stopFrameMetricsDiagnostics()
        super.onStop()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun startFrameMetricsDiagnostics() {
        if (frameMetricsAggregator == null) {
            frameMetricsAggregator = FrameMetricsAggregator(
                FrameMetricsAggregator.TOTAL_DURATION or
                    FrameMetricsAggregator.INPUT_DURATION or
                    FrameMetricsAggregator.LAYOUT_MEASURE_DURATION or
                    FrameMetricsAggregator.DRAW_DURATION or
                    FrameMetricsAggregator.ANIMATION_DURATION
            )
        }

        frameMetricsAggregator?.add(this)
        frameMetricsLoggerJob?.cancel()
        frameMetricsLoggerJob = lifecycleScope.launch {
            while (true) {
                delay(5000)
                logFrameMetricsSnapshot()
            }
        }
    }

    private fun stopFrameMetricsDiagnostics() {
        frameMetricsLoggerJob?.cancel()
        frameMetricsLoggerJob = null
        frameMetricsAggregator?.remove(this)
    }

    private fun logFrameMetricsSnapshot() {
        val metrics = frameMetricsAggregator?.metrics ?: return
        val total = summarizeMetric(metrics.getOrNull(FrameMetricsAggregator.TOTAL_INDEX))
        val input = summarizeMetric(metrics.getOrNull(FrameMetricsAggregator.INPUT_INDEX))
        val layout = summarizeMetric(metrics.getOrNull(FrameMetricsAggregator.LAYOUT_MEASURE_INDEX))
        val draw = summarizeMetric(metrics.getOrNull(FrameMetricsAggregator.DRAW_INDEX))
        val animation = summarizeMetric(metrics.getOrNull(FrameMetricsAggregator.ANIMATION_INDEX))

        Log.d(
            FRAME_DEBUG_TAG,
            "FrameMetrics totalFrames=${total.first} slowFrames=${total.second} inputSlow=${input.second} layoutSlow=${layout.second} drawSlow=${draw.second} animationSlow=${animation.second}"
        )
    }

    private fun summarizeMetric(metric: SparseIntArray?): Pair<Int, Int> {
        if (metric == null) return 0 to 0
        var totalFrames = 0
        var slowFrames = 0
        for (index in 0 until metric.size()) {
            val durationMs = metric.keyAt(index)
            val count = metric.valueAt(index)
            totalFrames += count
            if (durationMs > 16) {
                slowFrames += count
            }
        }
        return totalFrames to slowFrames
    }
}
