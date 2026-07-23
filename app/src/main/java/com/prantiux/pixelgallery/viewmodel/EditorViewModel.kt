package com.prantiux.pixelgallery.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrawnPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float
)

data class EditorState(
    val brightness: Float = 0f, // -1f to 1f
    val contrast: Float = 1f, // 0f to 2f
    val saturation: Float = 1f, // 0f to 2f
    val currentFilter: String = "None",
    val drawnPaths: List<DrawnPath> = emptyList()
)

class EditorViewModel : ViewModel() {
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _editorState = MutableStateFlow(EditorState())
    val editorState: StateFlow<EditorState> = _editorState.asStateFlow()

    fun setBitmap(bitmap: Bitmap) {
        _originalBitmap.value = bitmap
        _previewBitmap.value = bitmap
        _editorState.value = EditorState()
    }

    fun updateBrightness(value: Float) {
        _editorState.update { it.copy(brightness = value) }
        applyAdjustments()
    }

    fun updateContrast(value: Float) {
        _editorState.update { it.copy(contrast = value) }
        applyAdjustments()
    }

    fun updateSaturation(value: Float) {
        _editorState.update { it.copy(saturation = value) }
        applyAdjustments()
    }

    fun setFilter(filter: String) {
        _editorState.update { it.copy(currentFilter = filter) }
        applyAdjustments()
    }

    fun addDrawnPath(path: DrawnPath) {
        _editorState.update { it.copy(drawnPaths = it.drawnPaths + path) }
        // Drawing doesn't necessarily need a full bitmap recompute, but we can do it
    }
    
    fun undoLastPath() {
        _editorState.update {
            if (it.drawnPaths.isNotEmpty()) {
                it.copy(drawnPaths = it.drawnPaths.dropLast(1))
            } else it
        }
    }

    private fun applyAdjustments() {
        val original = _originalBitmap.value ?: return
        val state = _editorState.value

        viewModelScope.launch(Dispatchers.Default) {
            val cm = ColorMatrix()

            // 1. Brightness
            val brightnessCm = ColorMatrix().apply {
                val b = state.brightness * 255
                set(floatArrayOf(
                    1f, 0f, 0f, 0f, b,
                    0f, 1f, 0f, 0f, b,
                    0f, 0f, 1f, 0f, b,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            cm.postConcat(brightnessCm)

            // 2. Contrast
            val contrastCm = ColorMatrix().apply {
                val c = state.contrast
                val t = (1f - c) * 255f / 2f
                set(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            cm.postConcat(contrastCm)

            // 3. Saturation
            val saturationCm = ColorMatrix().apply { setSaturation(state.saturation) }
            cm.postConcat(saturationCm)

            // 4. Filters
            when (state.currentFilter) {
                "Grayscale" -> {
                    val grayCm = ColorMatrix().apply { setSaturation(0f) }
                    cm.postConcat(grayCm)
                }
                "Sepia" -> {
                    val sepiaCm = ColorMatrix().apply {
                        set(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    cm.postConcat(sepiaCm)
                }
                "Invert" -> {
                    val invertCm = ColorMatrix().apply {
                        set(floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    cm.postConcat(invertCm)
                }
            }

            val resultBitmap = Bitmap.createBitmap(original.width, original.height, original.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(original, 0f, 0f, paint)

            withContext(Dispatchers.Main) {
                _previewBitmap.value = resultBitmap
            }
        }
    }
}
