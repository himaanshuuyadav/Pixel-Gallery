package com.prantiux.pixelgallery.ui.screens.edit.refra

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.Crop
import com.prantiux.pixelgallery.domain.model.editor.DrawMode
import com.prantiux.pixelgallery.domain.model.editor.DrawType
import com.prantiux.pixelgallery.domain.model.editor.ImageFilter
import com.prantiux.pixelgallery.domain.model.editor.PathProperties
import com.prantiux.pixelgallery.domain.model.editor.SaveFormat
import com.prantiux.pixelgallery.domain.model.editor.SuggestionPreset
import com.prantiux.pixelgallery.domain.model.editor.VariableFilter
// Removed MediaRepository import
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.Flip
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.Markup
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.Rotate90CW
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Rotate
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Denoise
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Sharpness
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Vignette
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.VariableFilterTypes
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.overlayBitmaps
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.applyColorMatrix
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.resizeBitmap
// Removed WorkManager imports


// Removed Hilt imports
// Removed WorkInfo and WorkManager imports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

class EditViewModel : ViewModel() {

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap = _originalBitmap.asStateFlow()

    private val _targetBitmap = MutableStateFlow(originalBitmap.value)
    val targetBitmap = _targetBitmap.asStateFlow()

    private val _previewMatrix = MutableStateFlow<ColorMatrix?>(null)
    val previewMatrix = _previewMatrix.asStateFlow()

    private val _previewRotation = MutableStateFlow(0f)
    val previewRotation = _previewRotation.asStateFlow()

    private val _previewRotation90 = MutableStateFlow(0f)
    val previewRotation90 = _previewRotation90.asStateFlow()

    private val _previewFlipH = MutableStateFlow(false)
    val previewFlipH = _previewFlipH.asStateFlow()

    private val bitmaps = mutableStateListOf<Pair<Bitmap?, Adjustment?>>()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap = _currentBitmap.asStateFlow()

    private val _appliedAdjustments = MutableStateFlow<List<Adjustment>>(emptyList())
    val appliedAdjustments = _appliedAdjustments.asStateFlow()

    private val activeMedia = MutableStateFlow<Uri?>(null)

    private val _isSaving = MutableStateFlow(true)
    val isSaving = _isSaving.asStateFlow()

    private val _canOverride = MutableStateFlow(false)
    val canOverride = _canOverride.asStateFlow()

    private val _hasOriginalBackup = MutableStateFlow(false)
    val hasOriginalBackup = _hasOriginalBackup.asStateFlow()

    private val _isReverting = MutableStateFlow(false)
    val isReverting = _isReverting.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _uri = MutableStateFlow<Uri?>(null)
    val uri = _uri.asStateFlow()

    private val _paths = MutableStateFlow<List<Pair<Path, PathProperties>>>(emptyList())
    val paths = _paths.asStateFlow()

    private val _pathsUndone = MutableStateFlow<List<Pair<Path, PathProperties>>>(emptyList())
    val pathsUndone = _pathsUndone.asStateFlow()

    private val _currentPosition = MutableStateFlow(Offset.Unspecified)
    val currentPosition = _currentPosition.asStateFlow()

    private val _previousPosition = MutableStateFlow(Offset.Unspecified)
    val previousPosition = _previousPosition.asStateFlow()

    private val _drawMode = MutableStateFlow(DrawMode.Draw)
    val drawMode = _drawMode.asStateFlow()

    private val _drawType = MutableStateFlow(DrawType.Stylus)
    val drawType = _drawType.asStateFlow()

    private val _currentPath = MutableStateFlow(Path())
    val currentPath = _currentPath.asStateFlow()

    private val _currentPathProperty = MutableStateFlow(PathProperties())
    val currentPathProperty = _currentPathProperty.asStateFlow()

    private val _selectedPreset = MutableStateFlow<SuggestionPreset?>(null)
    val selectedPreset = _selectedPreset.asStateFlow()

    private val redoStack = mutableStateListOf<Pair<Bitmap?, Adjustment?>>()
    private val _redoAdjustments = MutableStateFlow<List<Adjustment>>(emptyList())

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    private val _filterIntensity = MutableStateFlow(1f)
    val filterIntensity = _filterIntensity.asStateFlow()

    private val _previewVignette = MutableStateFlow(0f)
    val previewVignette = _previewVignette.asStateFlow()

    private val _previewBlur = MutableStateFlow(0f)
    val previewBlur = _previewBlur.asStateFlow()

    private val _previewSharpness = MutableStateFlow(0f)
    val previewSharpness = _previewSharpness.asStateFlow()

    private val _activeFilterFlow = MutableStateFlow<ImageFilter?>(null)
    val activeFilter = _activeFilterFlow.asStateFlow()
    private var _activeFilter: ImageFilter?
        get() = _activeFilterFlow.value
        set(value) { _activeFilterFlow.value = value }
    private var _previewJob: Job? = null
    private var _intensityJob: Job? = null

    private fun updateUndoRedoState() {
        _canUndo.value = _appliedAdjustments.value.isNotEmpty()
        _canRedo.value = _redoAdjustments.value.isNotEmpty()
    }

    private fun Adjustment.isMatrixBased(): Boolean = when (this) {
        is VariableFilter -> colorMatrix() != null
        is ImageFilter -> colorMatrix() != null
        else -> false
    }

    private fun Adjustment.getColorMatrix(): ColorMatrix? = when (this) {
        is VariableFilter -> colorMatrix()
        is ImageFilter -> colorMatrix()
        else -> null
    }

    /** Find the last real bitmap in the bitmaps stack */
    private fun lastRealBitmap(): Bitmap? =
        bitmaps.lastOrNull { it.first != null }?.first

    /** Recompute the composed matrix from all trailing matrix-only entries */
    private fun recomputeComposedMatrix() {
        val trailing = bitmaps.takeLastWhile { it.first == null }
        if (trailing.isEmpty()) {
            _previewMatrix.value = null
            return
        }
        val composed = identityColorMatrix()
        for ((_, adj) in trailing) {
            adj?.getColorMatrix()?.let { composed.timesAssign(it) }
        }
        _previewMatrix.value = composed
    }

    /** Get a temporary bitmap with composed matrix applied (for previews, doesn't modify state) */
    private fun bitmapWithComposedMatrix(): Bitmap? {
        val base = lastRealBitmap() ?: return null
        val matrix = _previewMatrix.value ?: return base
        return applyColorMatrix(base, matrix.values)
    }

    /** Bake the composed matrix into a real bitmap checkpoint */
    private suspend fun flattenComposedMatrix() {
        val matrix = _previewMatrix.value ?: return
        val base = lastRealBitmap() ?: return
        val trailing = bitmaps.takeLastWhile { it.first == null }
        if (trailing.isEmpty()) return
        val flattened = applyColorMatrix(base, matrix.values)
        // Remove all trailing null-bitmap entries and replace with one real bitmap
        repeat(trailing.size) { bitmaps.removeAt(bitmaps.lastIndex) }
        bitmaps.add(flattened to null) // null adjustment = flatten checkpoint
        _currentBitmap.value = flattened
        _targetBitmap.value = flattened
        // Clear preview on Main so the UI renders the flattened bitmap first
        withContext(Dispatchers.Main) {
            _previewMatrix.value = null
        }
    }

    /**
     * Pick the best save format based on the source image's MIME type.
     * JPEG is ~5-10x faster to compress than PNG with negligible quality loss for photos.
     */
    private fun bestSaveFormat(context: Context): SaveFormat {
        val mime = context.contentResolver.getType(activeMedia.value ?: Uri.EMPTY)?.lowercase()
        return when {
            mime?.contains("png") == true -> SaveFormat.PNG
            mime?.contains("webp") == true -> SaveFormat.WEBP_LOSSY
            else -> SaveFormat.JPEG // JPEG is the fast default for photos
        }
    }

    private fun clearRedoStack() {
        redoStack.clear()
        _redoAdjustments.value = emptyList()
        updateUndoRedoState()
    }

    val mutex = Mutex()

    fun addPath(path: Path, properties: PathProperties) {
        _paths.value += path to properties
    }

    fun clearPathsUndone() {
        _pathsUndone.value = emptyList()
    }

    fun setCurrentPosition(offset: Offset) {
        _currentPosition.value = offset
    }

    fun setPreviousPosition(offset: Offset) {
        _previousPosition.value = offset
    }

    fun setDrawMode(mode: DrawMode) {
        setCurrentPathProperty(
            _currentPathProperty.value.copy(
                eraseMode = mode == DrawMode.Erase
            )
        )
        _drawMode.value = mode
    }

    fun setDrawType(type: DrawType) {
        when (type) {
            DrawType.Stylus -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 20f,
                        color = _currentPathProperty.value.color.copy(alpha = 1f),
                        strokeCap = StrokeCap.Round
                    )
                )
            }

            DrawType.Highlighter -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 30f,
                        color = _currentPathProperty.value.color.copy(alpha = 0.4f),
                        strokeCap = StrokeCap.Square
                    )
                )
            }

            DrawType.Marker -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 40f,
                        color = _currentPathProperty.value.color.copy(alpha = 1f),
                        strokeCap = StrokeCap.Round
                    )
                )
            }
        }
        _drawType.value = type
    }

    fun setCurrentPath(path: Path) {
        _currentPath.value = path
    }

    fun setCurrentPathProperty(properties: PathProperties) {
        _currentPathProperty.value = properties
    }

    fun setSelectedPreset(preset: SuggestionPreset?) {
        _selectedPreset.value = preset
        if (preset != null) {
            // Compose preset preview with existing stacked matrix adjustments
            val trailing = bitmaps.toList().takeLastWhile { it.first == null }
            val composed = identityColorMatrix()
            for ((_, adj) in trailing) {
                adj?.getColorMatrix()?.let { composed.timesAssign(it) }
            }
            composed.timesAssign(preset.colorMatrix())
            _previewMatrix.value = composed
        } else {
            recomputeComposedMatrix()
        }
    }

    fun undoLastPath() {
        val paths = _paths.value
        if (paths.isNotEmpty()) {
            val lastPath = paths.last()
            _paths.value = paths.dropLast(1)
            _pathsUndone.value += lastPath
        }
    }

    fun redoLastPath() {
        val pathsUndone = _pathsUndone.value
        if (pathsUndone.isNotEmpty()) {
            val lastPath = pathsUndone.last()
            _pathsUndone.value = pathsUndone.dropLast(1)
            _paths.value += lastPath
        }
    }

    fun clearDrawingBoard() {
        _paths.value = emptyList()
        _pathsUndone.value = emptyList()
        _currentPath.value = Path()
        _currentPathProperty.value = PathProperties()
        _drawMode.value = DrawMode.Draw
    }

    fun setSourceData(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uri.value = uri
            activeMedia.value = uri
            _canOverride.value = true
            _hasOriginalBackup.value = false

            setOriginalBitmap(context)
        }
    }

    private fun setOriginalBitmap(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(activeMedia.value)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit()
                .get()
            _originalBitmap.value = bitmap
            _targetBitmap.value = bitmap
            if (_currentBitmap.value == null) {
                _currentBitmap.value = bitmap
            }
            bitmaps.add(0, bitmap to null)
            _isSaving.value = false
        }
    }

    fun removeLast() {
        viewModelScope.launch(Dispatchers.IO) {
            val adjustments = _appliedAdjustments.value
            if (adjustments.isNotEmpty()) {
                val removedAdj = adjustments.last()
                _appliedAdjustments.value = adjustments.dropLast(1)

                // Push to redo stack
                if (bitmaps.isNotEmpty()) {
                    val removedEntry = bitmaps.last()
                    redoStack.add(removedEntry)
                    _redoAdjustments.value = _redoAdjustments.value + removedAdj
                    bitmaps.removeAt(bitmaps.lastIndex)
                }

                // Update current bitmap to last real bitmap
                _currentBitmap.value = lastRealBitmap()
                _targetBitmap.value = _currentBitmap.value
                _previewMatrix.value = null

                // If we undid a filter, check if there's a previous filter underneath
                if (removedAdj is ImageFilter) {
                    val prevFilter = _appliedAdjustments.value
                        .filterIsInstance<ImageFilter>()
                        .lastOrNull()
                    _activeFilter = if (prevFilter != null && prevFilter.name != "None") prevFilter else null
                    _filterIntensity.value = 1f
                }

                updateUndoRedoState()
            }
        }
    }

    fun redoLast() {
        viewModelScope.launch(Dispatchers.IO) {
            val redoAdjs = _redoAdjustments.value
            if (redoAdjs.isNotEmpty() && redoStack.isNotEmpty()) {
                val restoredAdj = redoAdjs.last()
                val restoredEntry = redoStack.last()
                _redoAdjustments.value = redoAdjs.dropLast(1)
                redoStack.removeAt(redoStack.lastIndex)

                _appliedAdjustments.value = _appliedAdjustments.value + restoredAdj
                bitmaps.add(restoredEntry)
                _currentBitmap.value = lastRealBitmap()
                _targetBitmap.value = _currentBitmap.value
                _previewMatrix.value = null
                updateUndoRedoState()
            }
        }
    }

    fun setFilterIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        _filterIntensity.value = clamped
        val filter = _activeFilter ?: return

        // GPU-only preview — commitFilter() bakes when leaving Filters section
        val baseBitmap = bitmaps.toList()
            .filter { it.second !is ImageFilter }
            .lastOrNull()?.first ?: return

        if (clamped <= 0f) {
            _currentBitmap.value = baseBitmap
            _previewMatrix.value = null
        } else {
            val blendedMatrix = lerpColorMatrix(identityColorMatrix(), filter.colorMatrix(), clamped)
            _currentBitmap.value = baseBitmap
            _previewMatrix.value = blendedMatrix
        }
    }

    private fun identityColorMatrix(): ColorMatrix = ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    private fun lerpColorMatrix(from: ColorMatrix, to: ColorMatrix?, t: Float): ColorMatrix {
        if (to == null) return from
        val result = FloatArray(20)
        for (i in 0 until 20) {
            result[i] = from.values[i] + t * (to.values[i] - from.values[i])
        }
        return ColorMatrix(result)
    }

    fun removeKind(variableFilterTypes: VariableFilterTypes) {
        viewModelScope.launch(Dispatchers.IO) {
            val filters = _appliedAdjustments.value.toMutableList()
            filters.removeAll { it.name.equals(variableFilterTypes.name, ignoreCase = true) }
            bitmaps.removeAll { it.second?.name.equals(variableFilterTypes.name, ignoreCase = true) }
            _appliedAdjustments.value = filters
            _currentBitmap.value = lastRealBitmap()
            _targetBitmap.value = _currentBitmap.value
            _previewMatrix.value = null
        }
    }

    fun applyAdjustment(adjustment: Adjustment) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            println("Applying adjustment: $adjustment")
            val filters = _appliedAdjustments.value
            clearRedoStack()

            // Update applied-adjustments list (dedup same-kind for VariableFilter / ImageFilter).
            // adjustmentsWithout is the list with any previous entry of this kind removed; it is
            // also what we fall back to when the new adjustment turns out to be a no-op (#957/#961).
            val adjustmentsWithout: List<Adjustment> = when (adjustment) {
                is VariableFilter -> filters.filterNot { it.name.equals(adjustment.name, ignoreCase = true) }
                is ImageFilter -> filters.filterNot { it is ImageFilter }
                else -> filters
            }
            _appliedAdjustments.value = adjustmentsWithout + adjustment

            // Always create a new bitmap (original behaviour)
            _currentBitmap.value?.let {
                if (adjustment is ImageFilter) {
                    bitmaps.removeAll { entry -> entry.second is ImageFilter }
                    _targetBitmap.value = bitmaps.lastOrNull()?.first
                }
                if (adjustment is VariableFilter) {
                    bitmaps.removeAll { entry -> entry.second?.name.equals(adjustment.name, ignoreCase = true) }
                    _targetBitmap.value = bitmaps.lastOrNull()?.first
                }

                val baseBitmap =
                    if (adjustment is VariableFilter || adjustment is ImageFilter)
                        _targetBitmap.value ?: _originalBitmap.value ?: it
                    else
                        bitmaps.lastOrNull()?.first ?: it

                val newBitmap = adjustment.apply(baseBitmap)
                // No-op guard: drop adjustments that produce no visible change so the tool icon
                // stops showing as "modified" (#957) and identical actions don't stack on the
                // undo/revert history (#961). A variable filter scrubbed back to its default
                // value is treated as a no-op directly: pixel comparison alone is unreliable
                // because some filters aren't perfectly pixel-identical at their default and,
                // when this is the only filter, the base falls back to the already-filtered
                // bitmap. Falling back to adjustmentsWithout removes the filter entirely.
                val isDefaultVariableFilter = adjustment is VariableFilter &&
                        kotlin.math.abs(adjustment.value - adjustment.defaultValue) < 1e-4f
                if (isDefaultVariableFilter || newBitmap.sameAs(baseBitmap)) {
                    _appliedAdjustments.value = adjustmentsWithout
                    withContext(Dispatchers.Main) {
                        _currentBitmap.value = baseBitmap
                        _previewMatrix.value = null
                        if (adjustment is Rotate) _previewRotation.value = 0f
                        if (adjustment is Rotate90CW) _previewRotation90.value = 0f
                        if (adjustment is Flip) _previewFlipH.value = false
                        clearGpuPreviewEffects()
                    }
                    updateUndoRedoState()
                    _isProcessing.value = false
                    return@launch
                }
                _currentBitmap.value = newBitmap
                if (adjustment !is ImageFilter) {
                    _targetBitmap.value = newBitmap
                }
                bitmaps.add(newBitmap to adjustment)
                // Clear previews on Main after bitmap is set, so the UI
                // renders the new bitmap before the preview overlay disappears
                withContext(Dispatchers.Main) {
                    _previewMatrix.value = null
                    if (adjustment is Rotate) {
                        _previewRotation.value = 0f
                    }
                    if (adjustment is Rotate90CW) {
                        _previewRotation90.value = 0f
                    }
                    if (adjustment is Flip) {
                        _previewFlipH.value = false
                    }
                    clearGpuPreviewEffects()
                }
            } ?: println("Current bitmap is null")

            updateUndoRedoState()
            _isProcessing.value = false
        }
    }

    /**
     * Extracts and removes the latest Crop adjustment if it is the very last adjustment in the stack.
     * This allows the UI to re-enter the Crop Scrubber in the pre-cropped state and restore the crop UI parameters.
     */
    fun extractAndRemoveLastCrop(): Crop? {
        val lastEntry = bitmaps.lastOrNull()
        val lastAdjustment = lastEntry?.second
        if (lastAdjustment is Crop) {
            // Remove it from the stack
            bitmaps.removeAt(bitmaps.lastIndex)
            _appliedAdjustments.value = _appliedAdjustments.value.dropLast(1)
            
            // Restore current bitmap to the previous state
            val baseBitmap = bitmaps.lastOrNull()?.first ?: _originalBitmap.value
            _currentBitmap.value = baseBitmap
            _targetBitmap.value = baseBitmap
            
            updateUndoRedoState()
            return lastAdjustment
        }
        return null
    }

    fun applyRotate90() {
        // Instant GPU preview, then bake
        _previewRotation90.value += 90f
        applyAdjustment(Rotate90CW(90f))
    }

    fun applyFlipH() {
        // Instant GPU preview, then bake
        _previewFlipH.value = !_previewFlipH.value
        applyAdjustment(Flip(horizontal = true))
    }

    private var applyDrawingJob: Job? = null

    fun applyDrawing(graphicsImage: Bitmap, onFinish: () -> Unit) {
        applyDrawingJob?.cancel()
        applyDrawingJob = viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                // Flatten any pending matrix adjustments before markup
                flattenComposedMatrix()
                val currentImage = lastRealBitmap()
                if (currentImage != null) {
                    try {
                        val newWidth = currentImage.width
                        val newHeight = currentImage.height
                        if (newWidth > 0 && newHeight > 0) {
                            val finalBitmap = overlayBitmaps(
                                currentImage,
                                graphicsImage.scale(newWidth, newHeight)
                            )
                            if (!currentImage.sameAs(finalBitmap)) {
                                applyAdjustment(Markup(finalBitmap))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                clearDrawingBoard()
                withContext(Dispatchers.Main) {
                    onFinish()
                }
            }
        }

    }

    fun toggleFilter(filter: ImageFilter) {
        _intensityJob?.cancel()
        // GPU-only preview — bitmap is baked later by commitFilter()
        val baseBitmap = bitmaps.toList()
            .filter { it.second !is ImageFilter }
            .lastOrNull()?.first ?: return

        if (filter.name != "None") {
            _activeFilter = filter
            _filterIntensity.value = 1f
            // Show base + GPU color matrix overlay
            _currentBitmap.value = baseBitmap
            _previewMatrix.value = filter.colorMatrix()
            clearGpuPreviewEffects()
        } else {
            _activeFilter = null
            _filterIntensity.value = 1f
            _currentBitmap.value = baseBitmap
            _previewMatrix.value = null
        }
    }

    /**
     * Bake the currently previewed filter into a real bitmap.
     * Called when navigating away from the Filters section.
     */
    fun commitFilter() {
        val filter = _activeFilter
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _intensityJob?.cancel()

            val baseBitmap = bitmaps.toList()
                .filter { it.second !is ImageFilter }
                .lastOrNull()?.first ?: run { _isProcessing.value = false; return@launch }

            val intensity = _filterIntensity.value

            if (filter != null && filter.name != "None") {
                val matrix = if (intensity < 1f) {
                    lerpColorMatrix(identityColorMatrix(), filter.colorMatrix(), intensity)
                } else {
                    filter.colorMatrix()
                }

                val newBitmap = if (matrix != null) {
                    applyColorMatrix(baseBitmap, matrix.values)
                } else {
                    filter.apply(baseBitmap)
                }
                _currentBitmap.value = newBitmap
                bitmaps.add(newBitmap to filter)
                _appliedAdjustments.value = _appliedAdjustments.value + filter
            } else if (_previewMatrix.value != null) {
                // Had a preview but switched to None — nothing to bake
                _currentBitmap.value = baseBitmap
            }

            // Clear preview on Main so the UI renders the new bitmap first
            withContext(Dispatchers.Main) {
                _previewMatrix.value = null
            }
            clearRedoStack()
            updateUndoRedoState()
            _isProcessing.value = false
        }
    }

    private fun clearGpuPreviewEffects() {
        _previewVignette.value = 0f
        _previewBlur.value = 0f
        _previewSharpness.value = 0f
    }

    /**
     * Cached downscaled copy of a base bitmap used to keep non-matrix
     * filter previews (Posterize, Edges, Borders) responsive while scrubbing.
     */
    private var previewBaseCache: Pair<Bitmap, Bitmap>? = null

    private fun previewBaseFor(base: Bitmap): Bitmap {
        previewBaseCache?.let { (original, scaled) ->
            if (original === base) return scaled
        }
        val scaled = if (base.width > 1280 || base.height > 1280) {
            resizeBitmap(base, 1280, 1280)
        } else base
        previewBaseCache = base to scaled
        return scaled
    }

    fun previewAdjustment(adjustment: Adjustment) {
        _previewJob?.cancel()
        when {
            adjustment is Vignette -> {
                // Show base bitmap without existing vignette, then overlay GPU preview
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewVignette.value = adjustment.value
                _previewBlur.value = 0f
                _previewSharpness.value = 0f
            }
            adjustment is Denoise -> {
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewBlur.value = adjustment.value
                _previewVignette.value = 0f
                _previewSharpness.value = 0f
            }
            adjustment is Sharpness -> {
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewSharpness.value = adjustment.value
                _previewVignette.value = 0f
                _previewBlur.value = 0f
            }
            adjustment is Rotate -> {
                _previewRotation.value = adjustment.value
            }
            adjustment is VariableFilter && adjustment.colorMatrix() != null -> {
                // Show base bitmap (without this adjustment)
                // and apply only this adjustment's colorMatrix as GPU filter
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewMatrix.value = adjustment.colorMatrix()
                clearGpuPreviewEffects()
            }
            adjustment is VariableFilter -> {
                // Non-matrix variable filters (Posterize, Edges, Borders): render apply()
                // on a downscaled copy of the base bitmap so scrubbing stays responsive.
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first ?: lastRealBitmap()
                _previewMatrix.value = null
                clearGpuPreviewEffects()
                if (baseBitmap == null || adjustment.value == adjustment.defaultValue) {
                    _currentBitmap.value = baseBitmap
                } else {
                    _previewJob = viewModelScope.launch(Dispatchers.Default) {
                        // Debounce rapid scrubber ticks: a newer tick cancels this job
                        // during the delay, so only the latest value is rendered. This
                        // prevents out-of-order results from flickering on screen.
                        delay(48)
                        if (!isActive) return@launch
                        val preview = previewBaseFor(baseBitmap)
                        val result = adjustment.apply(preview)
                        if (!isActive) return@launch
                        withContext(Dispatchers.Main) {
                            _currentBitmap.value = result
                        }
                    }
                }
            }
            else -> {
                clearGpuPreviewEffects()
            }
        }
    }

    fun saveCopy(
        context: Context,
        saveFormat: SaveFormat? = null,
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            val format = saveFormat ?: bestSaveFormat(context)
            flattenComposedMatrix()
            val uri = activeMedia.value ?: return@launch onFail().also { _isSaving.value = false }
            
            // Retrieve original timestamps and path
            var originalDateAdded = System.currentTimeMillis() / 1000
            var originalDateTaken = System.currentTimeMillis()
            var originalRelativePath = Environment.DIRECTORY_PICTURES + "/PixelGallery"

            val projection = mutableListOf(
                android.provider.MediaStore.Images.Media.DATE_ADDED,
                android.provider.MediaStore.Images.Media.DATE_TAKEN
            ).apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    add(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
                }
            }.toTypedArray()

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dateAddedCol = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_ADDED)
                        val dateTakenCol = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                        
                        if (dateAddedCol != -1) originalDateAdded = cursor.getLong(dateAddedCol)
                        if (dateTakenCol != -1) originalDateTaken = cursor.getLong(dateTakenCol)
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val relativePathCol = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
                            if (relativePathCol != -1) {
                                val path = cursor.getString(relativePathCol)
                                if (!path.isNullOrEmpty()) {
                                    originalRelativePath = if (path.endsWith("/")) path else "$path/"
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            lastRealBitmap()?.let { bitmap ->
                try {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Edited_${System.currentTimeMillis()}.${format.format.name.lowercase()}")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                        put(android.provider.MediaStore.Images.Media.DATE_ADDED, originalDateAdded)
                        put(android.provider.MediaStore.Images.Media.DATE_MODIFIED, originalDateAdded)
                        put(android.provider.MediaStore.Images.Media.DATE_TAKEN, originalDateTaken)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            var safeRelativePath = "Pictures/PixelGallery/"
                            if (originalRelativePath.startsWith("DCIM/") || originalRelativePath.startsWith("Pictures/")) {
                                safeRelativePath = originalRelativePath
                            }
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, safeRelativePath)
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val newUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (newUri != null) {
                        resolver.openOutputStream(newUri)?.use { out ->
                            bitmap.compress(format.format, 100, out)
                        }
                        
                        // Write EXIF Date
                        val exactFrameTimeMs = if (originalDateTaken > 0) originalDateTaken else originalDateAdded * 1000
                        try {
                            resolver.openFileDescriptor(newUri, "rw")?.use { pfd ->
                                val exif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                                val date = java.util.Date(exactFrameTimeMs)
                                val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                                val dateString = sdf.format(date)
                                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, dateString)
                                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, dateString)
                                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, dateString)
                                
                                val offsetSdf = java.text.SimpleDateFormat("XXX", java.util.Locale.US)
                                val offsetString = offsetSdf.format(date)
                                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME, offsetString)
                                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetString)
                                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offsetString)
                                
                                exif.saveAttributes()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val updateValues = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                                put(android.provider.MediaStore.Images.Media.DATE_TAKEN, exactFrameTimeMs)
                                put(android.provider.MediaStore.Images.Media.DATE_ADDED, originalDateAdded)
                                put(android.provider.MediaStore.Images.Media.DATE_MODIFIED, originalDateAdded)
                            }
                            resolver.update(newUri, updateValues, null, null)
                        }

                        viewModelScope.launch(Dispatchers.Main) {
                            com.prantiux.pixelgallery.model.MediaEventBus.lastSavedUri.value = newUri
                            _isSaving.value = false
                            onSuccess()
                        }
                    } else {
                        viewModelScope.launch(Dispatchers.Main) {
                            _isSaving.value = false
                            onFail()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    viewModelScope.launch(Dispatchers.Main) {
                        _isSaving.value = false
                        onFail()
                    }
                }
            } ?: viewModelScope.launch(Dispatchers.Main) {
                _isSaving.value = false
                onFail()
            }
        }
    }

    fun saveOverride(
        context: Context,
        saveFormat: SaveFormat? = null,
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {},
        onRequestPermission: ((androidx.activity.result.IntentSenderRequest) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            val format = saveFormat ?: bestSaveFormat(context)
            flattenComposedMatrix()
            val uri = activeMedia.value ?: return@launch run {
                viewModelScope.launch(Dispatchers.Main) {
                    _isSaving.value = false
                    onFail()
                }
            }
            
            lastRealBitmap()?.let { bitmap ->
                try {
                    val resolver = context.contentResolver
                    // Attempt to overwrite the original URI directly with "wt" (write-truncate)
                    resolver.openOutputStream(uri, "wt")?.use { out ->
                        bitmap.compress(format.format, 100, out)
                    }
                    
                    // Update MediaStore modified date
                    val updateValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    }
                    resolver.update(uri, updateValues, null, null)
                    
                    // Invalidate Coil Cache so the UI updates
                    try {
                        val imageLoader = coil.Coil.imageLoader(context)
                        imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(uri.toString()))
                        imageLoader.diskCache?.remove(uri.toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    viewModelScope.launch(Dispatchers.Main) {
                        // Small delay to allow MediaContentObserver and Room DB to sync
                        // so the gallery overlay updates instantly when we return
                        kotlinx.coroutines.delay(600)
                        _isSaving.value = false
                        onSuccess()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && e is SecurityException && onRequestPermission != null) {
                        try {
                            val intentSender = android.provider.MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
                            viewModelScope.launch(Dispatchers.Main) {
                                onRequestPermission(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
                                // Keep isSaving true so UI shows loading state while permission dialog is open
                            }
                            return@launch
                        } catch (innerE: Exception) {
                            innerE.printStackTrace()
                        }
                    } else if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException && onRequestPermission != null) {
                        viewModelScope.launch(Dispatchers.Main) {
                            onRequestPermission(androidx.activity.result.IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                            // Keep isSaving true
                        }
                        return@launch
                    }
                    
                    // If Scoped Storage blocks the direct overwrite, fallback to saving a copy
                    saveCopy(context, saveFormat, onSuccess, onFail)
                }
            } ?: viewModelScope.launch(Dispatchers.Main) {
                _isSaving.value = false
                onFail()
            }
        }
    }

    fun cancelSaving() {
        _isSaving.value = false
    }

    fun revertToOriginal(
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {}
    ) {
        onFail() // Not implemented in basic copy
    }
}
