package com.prantiux.pixelgallery.ui.screens.edit.refra

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.LocalHazeState

import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.Crop
import com.prantiux.pixelgallery.ui.screens.edit.refra.EditScreen2
// Removed GalleryTheme
// Removed Hilt
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditActivity : ComponentActivity() {

    override fun finish() {
        super.finish()
        overridePendingTransition(com.prantiux.pixelgallery.R.anim.gallery_enter, com.prantiux.pixelgallery.R.anim.editor_exit)
    }

    private lateinit var intentSenderLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>

    @OptIn(ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        intentSenderLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val viewModel: EditViewModel = androidx.lifecycle.ViewModelProvider(this)[EditViewModel::class.java]
                viewModel.saveOverride(
                    context = this,
                    onSuccess = {
                        Toast.makeText(this, "Image replaced successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onFail = {
                        Toast.makeText(this, "Failed to replace image", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Toast.makeText(this, "Permission to modify image denied", Toast.LENGTH_SHORT).show()
                val viewModel: EditViewModel = androidx.lifecycle.ViewModelProvider(this)[EditViewModel::class.java]
                viewModel.cancelSaving()
            }
        }
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            com.prantiux.pixelgallery.ui.theme.PixelGalleryTheme(darkTheme = true) {
                val hazeState = rememberHazeState()
                CompositionLocalProvider(
                    LocalHazeState provides hazeState,
                    LocalHazeStyle provides dev.chrisbanes.haze.HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        tints = emptyList(),
                        blurRadius = 30.dp,
                        noiseFactor = 0.15f
                    )
                ) {
                    LaunchedEffect(Unit) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                            ) { true },
                            navigationBarStyle = SystemBarStyle.auto(
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                            ) { true }
                        )
                    }
                    val viewModel: EditViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

                    LaunchedEffect(intent.data) {
                        intent.data?.let {
                            viewModel.setSourceData(this@EditActivity, it)
                        }
                    }
                    val currentImage by viewModel.currentBitmap.collectAsStateWithLifecycle()
                    val targetImage by viewModel.targetBitmap.collectAsStateWithLifecycle()
                    val uri by viewModel.uri.collectAsStateWithLifecycle()
                    val canOverride by viewModel.canOverride.collectAsStateWithLifecycle()
                    val hasOriginalBackup by viewModel.hasOriginalBackup.collectAsStateWithLifecycle()
                    val isReverting by viewModel.isReverting.collectAsStateWithLifecycle()
                    val appliedAdjustments by viewModel.appliedAdjustments.collectAsStateWithLifecycle()
                    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
                    val previewMatrix by viewModel.previewMatrix.collectAsStateWithLifecycle()
                    val previewRotation by viewModel.previewRotation.collectAsStateWithLifecycle()

                    // Markup states
                    val paths by viewModel.paths.collectAsStateWithLifecycle()
                    val pathsUndone by viewModel.pathsUndone.collectAsStateWithLifecycle()
                    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
                    val previousPosition by viewModel.previousPosition.collectAsStateWithLifecycle()
                    val drawMode by viewModel.drawMode.collectAsStateWithLifecycle()
                    val drawType by viewModel.drawType.collectAsStateWithLifecycle()
                    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
                    val currentPathProperty by viewModel.currentPathProperty.collectAsStateWithLifecycle()
                    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
                    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
                    val filterIntensity by viewModel.filterIntensity.collectAsStateWithLifecycle()
                    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
                    val vignetteIntensity by viewModel.previewVignette.collectAsStateWithLifecycle()
                    val blurRadius by viewModel.previewBlur.collectAsStateWithLifecycle()
                    val sharpnessValue by viewModel.previewSharpness.collectAsStateWithLifecycle()
                    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
                    val previewRotation90 by viewModel.previewRotation90.collectAsStateWithLifecycle()
                    val previewFlipH by viewModel.previewFlipH.collectAsStateWithLifecycle()

                    val scope = rememberCoroutineScope { Dispatchers.IO }

                    val doOverride: () -> Unit = {
                        viewModel.saveOverride(
                            context = this@EditActivity,
                            onSuccess = {
                                Toast.makeText(this@EditActivity, "Image replaced successfully", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onFail = {
                                Toast.makeText(this@EditActivity, "Failed to replace image", Toast.LENGTH_SHORT).show()
                            },
                            onRequestPermission = { request ->
                                intentSenderLauncher.launch(request)
                            }
                        )
                    }

                    val doSaveCopy: () -> Unit = {
                        viewModel.saveCopy(
                            context = this@EditActivity,
                            onSuccess = {
                                Toast.makeText(this@EditActivity, "Image saved as copy", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onFail = {
                                Toast.makeText(this@EditActivity, "Failed to save copy", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }


                    val doRevert: () -> Unit = {
                        viewModel.revertToOriginal(
                            onSuccess = {
                                finish()
                            },
                            onFail = {
                                println("Failed to revert to original")
                            }
                        )
                    }



                    EditScreen2(
                        hasOriginalBackup = hasOriginalBackup,
                        isReverting = isReverting,
                        canOverride = canOverride,
                        canSave = appliedAdjustments.isNotEmpty(),
                        isChanged = appliedAdjustments.isNotEmpty(),
                        isSaving = isSaving,
                        isProcessing = isProcessing,
                        currentImage = currentImage,
                        targetImage = targetImage ?: currentImage,
                        targetUri = uri,
                        previewMatrix = previewMatrix,
                        previewRotation = previewRotation,
                        appliedAdjustments = appliedAdjustments,
                        currentPosition = currentPosition,
                        paths = paths,
                        pathsUndone = pathsUndone,
                        previousPosition = previousPosition,
                        drawMode = drawMode,
                        drawType = drawType,
                        currentPathProperty = currentPathProperty,
                        currentPath = currentPath,
                        onClose = {
                            finish()
                        },
                        onOverride = {
                            scope.launch {
                                uri?.let { uri ->
                                    doOverride()
                                }
                            }
                        },
                        onSaveCopy = {
                            scope.launch {
                                uri?.let { uri ->
                                    doSaveCopy()
                                }
                            }
                        },
                        onAdjustItemLongClick = viewModel::removeKind,
                        onAdjustmentChange = viewModel::applyAdjustment,
                        onAdjustmentPreview = viewModel::previewAdjustment,
                        onToggleFilter = viewModel::toggleFilter,
                        commitFilter = viewModel::commitFilter,
                        removeLast = viewModel::removeLast,
                        onCropRect = { normalizedRect, rotation, zoom, panX, panY, aspectRatioValue ->
                            viewModel.applyAdjustment(Crop(normalizedRect, rotation, zoom, panX, panY, aspectRatioValue))
                        },
                        extractLastCrop = viewModel::extractAndRemoveLastCrop,
                        addPath = viewModel::addPath,
                        clearPathsUndone = viewModel::clearPathsUndone,
                        setCurrentPosition = viewModel::setCurrentPosition,
                        setPreviousPosition = viewModel::setPreviousPosition,
                        setDrawMode = viewModel::setDrawMode,
                        setDrawType = viewModel::setDrawType,
                        setCurrentPath = viewModel::setCurrentPath,
                        setCurrentPathProperty = viewModel::setCurrentPathProperty,
                        applyDrawing = viewModel::applyDrawing,
                        undoLastPath = viewModel::undoLastPath,
                        redoLastPath = viewModel::redoLastPath,
                        clearDrawing = viewModel::clearDrawingBoard,
                        onRevertToOriginal = {
                            scope.launch {
                                uri?.let { uri ->
                                    doRevert()
                                }
                            }
                        },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onRedo = viewModel::redoLast,
                        filterIntensity = filterIntensity,
                        onFilterIntensityChange = viewModel::setFilterIntensity,
                        activeFilterName = activeFilter?.name,
                        vignetteIntensity = vignetteIntensity,
                        blurRadius = blurRadius,
                        sharpnessValue = sharpnessValue,
                        previewRotation90 = previewRotation90,
                        previewFlipH = previewFlipH,
                        onRotate90 = viewModel::applyRotate90,
                        onFlipH = viewModel::applyFlipH
                    )
                }
            }
        }
    }

    companion object {

        fun launchEditor(context: Context, uri: Uri) {
            context.startActivity(Intent(context, EditActivity::class.java).apply { data = uri })
        }
    }

}
