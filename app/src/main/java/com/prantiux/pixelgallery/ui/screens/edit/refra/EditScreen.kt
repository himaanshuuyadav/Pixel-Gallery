package com.prantiux.pixelgallery.ui.screens.edit.refra

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import androidx.compose.ui.unit.sp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AdaptStrategy
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.HorizontalScrubber
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.rememberNavController
import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.Crop
import com.prantiux.pixelgallery.domain.model.editor.CropState
import com.prantiux.pixelgallery.domain.model.editor.DrawMode
import com.prantiux.pixelgallery.domain.model.editor.DrawType
import com.prantiux.pixelgallery.domain.model.editor.EditorDestination
import com.prantiux.pixelgallery.domain.model.editor.EditorItems
import com.prantiux.pixelgallery.domain.model.editor.ImageFilter
import com.prantiux.pixelgallery.domain.model.editor.PathProperties
import com.prantiux.pixelgallery.domain.model.editor.TextAnnotation
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.VariableFilterTypes
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor.EditorNavigator
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor.EditorSelector
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor.ImageViewer
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.markup.TextMarkupOverlay
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.LocalHazeState
import com.smarttoolfactory.cropper.model.AspectRatio
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun EditScreen2(
    hasOriginalBackup: Boolean = false,
    isReverting: Boolean = false,
    canOverride: Boolean = false,
    canSave: Boolean = false,
    isChanged: Boolean = false,
    isSaving: Boolean = false,
    isProcessing: Boolean = false,
    currentImage: Bitmap?,
    targetImage: Bitmap?,
    targetUri: Uri?,
    previewMatrix: ColorMatrix? = null,
    previewRotation: Float = 0f,
    appliedAdjustments: List<Adjustment> = emptyList(),
    currentPosition: Offset,
    paths: List<Pair<Path, PathProperties>>,
    pathsUndone: List<Pair<Path, PathProperties>>,
    previousPosition: Offset,
    drawMode: DrawMode,
    drawType: DrawType,
    currentPathProperty: PathProperties,
    currentPath: Path,
    onClose: () -> Unit,
    onOverride: () -> Unit,
    onSaveCopy: () -> Unit,
    onAdjustItemLongClick: (VariableFilterTypes) -> Unit,
    onAdjustmentChange: (Adjustment) -> Unit,
    onAdjustmentPreview: (Adjustment) -> Unit,
    onToggleFilter: (ImageFilter) -> Unit,
    commitFilter: () -> Unit = {},
    removeLast: () -> Unit,
    onCropRect: (RectF, Float, Float, Float, Float, Float) -> Unit,
    extractLastCrop: () -> Crop?,
    addPath: (Path, PathProperties) -> Unit,
    clearPathsUndone: () -> Unit,
    setCurrentPosition: (Offset) -> Unit,
    setPreviousPosition: (Offset) -> Unit,
    setDrawMode: (DrawMode) -> Unit,
    setDrawType: (DrawType) -> Unit,
    setCurrentPath: (Path) -> Unit,
    setCurrentPathProperty: (PathProperties) -> Unit,
    applyDrawing: (Bitmap, () -> Unit) -> Unit,
    undoLastPath: () -> Unit,
    redoLastPath: () -> Unit,
    clearDrawing: () -> Unit = {},
    onRevertToOriginal: () -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onRedo: () -> Unit = {},
    filterIntensity: Float = 1f,
    onFilterIntensityChange: (Float) -> Unit = {},
    activeFilterName: String? = null,
    vignetteIntensity: Float = 0f,
    blurRadius: Float = 0f,
    sharpnessValue: Float = 0f,
    previewRotation90: Float = 0f,
    previewFlipH: Boolean = false,
    onRotate90: () -> Unit = {},
    onFlipH: () -> Unit = {}
) = com.prantiux.pixelgallery.ui.theme.PixelGalleryTheme(darkTheme = true) {
    val context = LocalContext.current
    val enterAnimation = remember { 
        androidx.compose.animation.slideInVertically(initialOffsetY = { it / 2 }) + 
        androidx.compose.animation.expandVertically() + 
        androidx.compose.animation.fadeIn() 
    }
    val exitAnimation = remember { 
        androidx.compose.animation.slideOutVertically(targetOffsetY = { it / 2 }) + 
        androidx.compose.animation.shrinkVertically() + 
        androidx.compose.animation.fadeOut() 
    }

    val topEnterAnimation = remember { slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.expandVertically(expandFrom = androidx.compose.ui.Alignment.Top) + fadeIn() }
    val topExitAnimation = remember { slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top) + fadeOut() }
    val navigator = rememberSupportingPaneScaffoldNavigator(
        adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(
            supportingPaneAdaptStrategy = AdaptStrategy.Hide
        )
    )
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Track if we're in actual drawing mode (MarkupDraw), not the Markup tool-picker tab
    val isMarkupDrawing by rememberedDerivedState {
        navBackStackEntry?.destination?.hasRoute<EditorDestination.MarkupDraw>() == true
    }

    // Track if we're in any detail mode (adjust scrubber, markup draw, crop detail)
    val isInDetailMode by rememberedDerivedState {
        isMarkupDrawing ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.AdjustDetail>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.CropDetail>() == true
    }

    var requestMarkupApply by remember { mutableStateOf(false) }

    // Auto-apply markup when leaving drawing mode
    var wasDrawing by remember { mutableStateOf(false) }
    LaunchedEffect(isMarkupDrawing) {
        if (wasDrawing && !isMarkupDrawing && paths.isNotEmpty()) {
            requestMarkupApply = true
        }
        wasDrawing = isMarkupDrawing
    }

    var showRevertDialog by remember { mutableStateOf(false) }

    // Track which tab is currently selected for the tab bar highlight
    var selectedTab by remember { mutableStateOf<EditorItems?>(EditorItems.Lighting) }
    val showingEditorScreen by rememberedDerivedState {
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Editor>() == true
    }

    // Determine if we're on a top-level tab (not in a detail view)
    val isOnTopLevelTab by rememberedDerivedState {
        showingEditorScreen ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Markup>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Lighting>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Colour>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Effects>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.More>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Filters>() == true
    }

    var cropState by rememberSaveable { mutableStateOf(CropState(showCropper = true)) }
    var isFraming by remember { mutableStateOf(false) }
    
    val scrubberInteraction = remember { mutableStateOf(false) }
    val isImmersiveMode by rememberedDerivedState(scrubberInteraction.value) {
        scrubberInteraction.value
    }
    
    var isHandleTouched by remember { mutableStateOf(false) }
    var hasEverTouchedHandle by remember { mutableStateOf(false) }
    var isCropScrubbingMode by remember { mutableStateOf(false) }
    var extractedCrop by remember { mutableStateOf<Crop?>(null) }
    var initialZoom by remember { mutableFloatStateOf(1f) }
    var requestedSaveType by remember { mutableIntStateOf(0) }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var initialPanX by remember { mutableFloatStateOf(0f) }
    var initialPanY by remember { mutableFloatStateOf(0f) }

    var imageRotation by remember { mutableFloatStateOf(0f) }
    
    val immersiveAlpha by animateFloatAsState(
        targetValue = if (isImmersiveMode) 0f else 1f,
        label = "immersiveAlpha"
    )

    // Grid overlay state
    var showGridOverlay by remember { mutableStateOf(false) }

    var topToolbarHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var bottomToolbarHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Pre-enable cropper: visible on all top-level tabs, hidden during detail/adjust/markup-draw modes
    val shouldShowCropper by rememberedDerivedState {
        isOnTopLevelTab
    }
    LaunchedEffect(shouldShowCropper) {
        cropState = cropState.copy(showCropper = shouldShowCropper)
    }

    val animatedBlurRadius by animateDpAsState(
        if (isSaving || isReverting || cropState.isCropping || requestMarkupApply) 50.dp else 0.dp,
        label = "animatedBlurRadius"
    )

    // 3-dot menu state
    var showMenu by remember { mutableStateOf(false) }

    // Aspect ratio state for crop
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.Original) }
    var showAspectMenu by remember { mutableStateOf(false) }
    var cropResetTrigger by remember { mutableIntStateOf(0) }

    // Text annotation state
    var textAnnotations by remember { mutableStateOf<List<TextAnnotation>>(emptyList()) }
    var showTextOverlay by remember { mutableStateOf(false) }
    var selectedTextIndex by remember { mutableIntStateOf(-1) }

    val onRequestTextInput: () -> Unit = { showTextOverlay = true }

    val hasUnsavedChanges = canUndo || isChanged || extractedCrop != null || imageRotation != 0f || paths.isNotEmpty() || textAnnotations.isNotEmpty() || activeFilterName != null
    val handleClose: () -> Unit = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            if (showingEditorScreen || isOnTopLevelTab) onClose()
            else navController.popBackStack()
        }
    }

    // Apply (or cancel) markup on a back press while still in drawing mode, so the
    // MarkupPainter is still composed to consume the request. A raw back gesture
    // otherwise disposes the painter first, leaving requestMarkupApply with nothing
    // to handle it and the blur/loading overlay stuck forever (#955).
    BackHandler(enabled = isMarkupDrawing) {
        if (paths.isNotEmpty() || textAnnotations.isNotEmpty()) {
            requestMarkupApply = true
        } else {
            clearDrawing()
            navController.popBackStack()
        }
    }

    BackHandler(enabled = !isMarkupDrawing && hasUnsavedChanges && !isCropScrubbingMode) {
        handleClose()
    }

    // Safety net: if an apply was requested but we're no longer in drawing mode
    // (the painter is already gone and can't handle it), clear the flag so the
    // loading/blur overlay can never hang indefinitely (#955).
    LaunchedEffect(requestMarkupApply, isMarkupDrawing) {
        if (requestMarkupApply && !isMarkupDrawing) {
            requestMarkupApply = false
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.LocalScrubberInteraction provides scrubberInteraction
    ) {
    Box(
        modifier = Modifier
            .hazeSource(LocalHazeState.current)
            .fillMaxSize()
            .then(if (isSaving || isReverting || cropState.isCropping || requestMarkupApply) Modifier.blur(animatedBlurRadius) else Modifier)
            .background(Color.Black)
    ) {
        // Top Bars
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(1f) // Ensure it floats above the Image Area
                .onSizeChanged { topToolbarHeight = it.height }
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        ) {
            // TOP BAR
            AnimatedVisibility(
                visible = !isInDetailMode && !isCropScrubbingMode,
                enter = topEnterAnimation,
                exit = topExitAnimation
            ) {
                Row(
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth()
                        .alpha(immersiveAlpha)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left cluster: Close + Undo + Redo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = handleClose,
                            modifier = Modifier.size(40.dp)
                        ) {
                            FontIcon(
                                unicode = FontIcons.Close,
                                size = 24.sp,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = canUndo || canRedo,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Undo split pill left
                                val undoInteraction = remember { MutableInteractionSource() }
                                val isUndoPressed by undoInteraction.collectIsPressedAsState()
                                val undoScale by animateFloatAsState(
                                    targetValue = if (isUndoPressed && canUndo) 0.85f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "undoScale"
                                )
                                
                                Surface(
                                    onClick = { if (canUndo) removeLast() },
                                    interactionSource = undoInteraction,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 36.dp)
                                        .graphicsLayer {
                                            scaleX = undoScale
                                            scaleY = undoScale
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FontIcon(
                                            unicode = FontIcons.Undo,
                                            contentDescription = "Undo",
                                            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                                            size = 20.sp
                                        )
                                    }
                                }

                                // Redo split pill right
                                val redoInteraction = remember { MutableInteractionSource() }
                                val isRedoPressed by redoInteraction.collectIsPressedAsState()
                                val redoScale by animateFloatAsState(
                                    targetValue = if (isRedoPressed && canRedo) 0.85f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "redoScale"
                                )
                                
                                Surface(
                                    onClick = { if (canRedo) onRedo() },
                                    interactionSource = redoInteraction,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 36.dp)
                                        .graphicsLayer {
                                            scaleX = redoScale
                                            scaleY = redoScale
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FontIcon(
                                            unicode = FontIcons.Redo,
                                            contentDescription = "Redo",
                                            tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.3f),
                                            size = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right cluster: Save pill + 3-dot menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        var showMenu by remember { mutableStateOf(false) }
                        
                        if (canOverride) {
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            SplitButtonLayout(
                                leadingButton = {
                                    Button(
                                        onClick = {
                                            onSaveCopy()
                                        },
                                        enabled = canSave && !isProcessing,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = androidx.compose.foundation.shape.CornerSize(50), bottomStart = androidx.compose.foundation.shape.CornerSize(50), topEnd = androidx.compose.foundation.shape.CornerSize(2.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(2.dp)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                                    ) {
                                        if (isProcessing) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text(
                                                text = "Save as copy",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                },
                                trailingButton = {
                                    Box {
                                        val rotationAngle by androidx.compose.animation.core.animateFloatAsState(
                                            targetValue = if (showMenu) 180f else 0f,
                                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                                        )
                                        val startCorner by androidx.compose.animation.core.animateDpAsState(
                                            targetValue = if (showMenu) 24.dp else 2.dp,
                                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                                        )
                                        val buttonInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                        val isPressed by buttonInteraction.collectIsPressedAsState()
                                        val buttonScale by androidx.compose.animation.core.animateFloatAsState(
                                            targetValue = if (isPressed) 0.8f else 1f,
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                            )
                                        )
                                        Button(
                                            onClick = { showMenu = true },
                                            interactionSource = buttonInteraction,
                                            enabled = canSave && !isProcessing,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = androidx.compose.foundation.shape.CornerSize(startCorner), bottomStart = androidx.compose.foundation.shape.CornerSize(startCorner), topEnd = androidx.compose.foundation.shape.CornerSize(50), bottomEnd = androidx.compose.foundation.shape.CornerSize(50)),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                            modifier = Modifier
                                                .width(36.dp)
                                                .graphicsLayer {
                                                    scaleX = buttonScale
                                                    scaleY = buttonScale
                                                }
                                        ) {
                                            FontIcon(
                                                unicode = FontIcons.KeyboardArrowDown,
                                                size = 20.sp,
                                                contentDescription = "Save options dropdown",
                                                modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }
                                            )
                                        }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text("Save")
                                                        Text("Replace current image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    onOverride()
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        } else {
                            Button(
                                onClick = {
                                    onSaveCopy()
                                },
                                enabled = canSave && !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text(
                                        text = "Save as copy",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                        val hasMenuActions = hasOriginalBackup
                        AnimatedVisibility(
                            visible = hasMenuActions,
                            enter = enterAnimation,
                            exit = exitAnimation
                        ) {
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    enabled = !isProcessing,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    FontIcon(
                                        unicode = FontIcons.MoreVert,
                                        size = 24.sp,
                                        contentDescription = "More options",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    if (hasOriginalBackup) {
                                        DropdownMenuItem(
                                            text = { Text("Revert to original") },
                                            onClick = {
                                                showMenu = false
                                                showRevertDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } // end top bar
            
            // CROP TOOLBAR
            AnimatedVisibility(
                visible = !isInDetailMode && cropState.showCropper,
                enter = topEnterAnimation,
                exit = topExitAnimation
            ) {
                Row(
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Grid + Straighten
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showGridOverlay = !showGridOverlay },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showGridOverlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                        ) {
                            FontIcon(
                                unicode = FontIcons.GridOn,
                                size = 24.sp,
                                contentDescription = "Show Grid",
                                tint = if (showGridOverlay) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                            )
                        }
                    }

                    // Right cluster: Aspect ratio, Flip, Rotate, Crop Button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { showAspectMenu = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = Color.White
                                ),
                                shape = CircleShape,
                            ) {
                                FontIcon(
                                    unicode = FontIcons.AspectRatio,
                                    size = 24.sp,
                                    contentDescription = "Aspect ratio",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showAspectMenu,
                                onDismissRequest = { showAspectMenu = false }
                            ) {
                                data class RatioOption(val label: String, val ratio: AspectRatio)
                                val options = listOf(
                                    RatioOption("Freeform", AspectRatio.Original),
                                    RatioOption("Square", AspectRatio(1f)),
                                    RatioOption("5:4", AspectRatio(5f / 4f)),
                                    RatioOption("4:3", AspectRatio(4f / 3f)),
                                    RatioOption("3:2", AspectRatio(3f / 2f)),
                                    RatioOption("16:9", AspectRatio(16f / 9f)),
                                    RatioOption("4:5", AspectRatio(4f / 5f)),
                                    RatioOption("3:4", AspectRatio(3f / 4f)),
                                    RatioOption("2:3", AspectRatio(2f / 3f)),
                                    RatioOption("9:16", AspectRatio(9f / 16f))
                                )
                                options.forEach { option ->
                                    val isSelected = selectedAspectRatio == option.ratio
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(option.label)
                                                if (isSelected) {
                                                    FontIcon(
                                                        unicode = FontIcons.Check,
                                                        size = 16.sp,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedAspectRatio = option.ratio
                                            showAspectMenu = false
                                            isCropScrubbingMode = true
                                        }
                                    )
                                }
                            }
                        }

                        // Restore previous crop if available when first entering crop mode
                        LaunchedEffect(isCropScrubbingMode) {
                            if (isCropScrubbingMode) {
                                val lastCrop = extractLastCrop()
                                if (lastCrop != null) {
                                    extractedCrop = lastCrop
                                    imageRotation = lastCrop.rotation
                                    if (lastCrop.aspectRatioValue != -1f) {
                                        selectedAspectRatio = AspectRatio(lastCrop.aspectRatioValue)
                                    } else {
                                        selectedAspectRatio = AspectRatio.Original
                                    }
                                    initialZoom = lastCrop.zoom
                                    initialPanX = lastCrop.panX
                                    initialPanY = lastCrop.panY
                                    cropResetTrigger++
                                }
                            }
                        }

                        IconButton(
                            onClick = onFlipH,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                        ) {
                            FontIcon(
                                unicode = FontIcons.Flip,
                                size = 24.sp,
                                contentDescription = "Mirror image",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onRotate90,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                        ) {
                            FontIcon(
                                unicode = FontIcons.RotateRight, // rotate 90
                                size = 24.sp,
                                contentDescription = "Rotate 90 degrees",
                                tint = Color.White
                            )
                        }
                    }
                }
            } // end crop toolbar
        } // end top bars Column

        val density = LocalDensity.current
        val currentTopPadding = with(density) { topToolbarHeight.toDp() } + 16.dp
        val currentBottomPadding = with(density) { bottomToolbarHeight.toDp() } + 24.dp // Safe buffer for labels and dynamic expansions

        var frozenTopPadding by remember { mutableStateOf(-1.dp) }
        var frozenBottomPadding by remember { mutableStateOf(-1.dp) }

        val shouldFreezePadding = isCropScrubbingMode
        if (shouldFreezePadding) {
            if (frozenTopPadding < 0.dp) {
                frozenTopPadding = currentTopPadding
            }
            if (frozenBottomPadding < 0.dp) {
                frozenBottomPadding = currentBottomPadding
            }
        } else {
            frozenTopPadding = -1.dp
            frozenBottomPadding = -1.dp
        }

        val effectiveTopPadding = if (frozenTopPadding >= 0.dp) frozenTopPadding else currentTopPadding
        val effectiveBottomPadding = if (frozenBottomPadding >= 0.dp) frozenBottomPadding else currentBottomPadding

        LaunchedEffect(
            isCropScrubbingMode,
            isHandleTouched,
            currentTopPadding,
            frozenTopPadding,
            effectiveTopPadding
        ) {
            android.util.Log.d("CROP_DEBUG", 
                "isScrubbing=$isCropScrubbingMode " +
                "isTouch=$isHandleTouched " +
                "top[curr=$currentTopPadding, froz=$frozenTopPadding, eff=$effectiveTopPadding] " +
                "bot[curr=$currentBottomPadding, froz=$frozenBottomPadding, eff=$effectiveBottomPadding]"
            )
        }

        // IMAGE AREA
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
                SupportingPaneScaffold(
                    directive = navigator.scaffoldDirective,
                    value = navigator.scaffoldValue,
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxSize(),
                    mainPane = {
                        ImageViewer(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentPadding = PaddingValues(top = effectiveTopPadding, bottom = effectiveBottomPadding),
                            currentImage = currentImage,
                            previewMatrix = previewMatrix,
                            previewRotation = previewRotation + imageRotation,
                            cropState = cropState,
                            cropAspectRatio = selectedAspectRatio,
                            cropResetTrigger = cropResetTrigger,
                            initialZoom = initialZoom,
                            initialPanX = initialPanX,
                            initialPanY = initialPanY,
                            initialCropRect = extractedCrop?.normalizedRect,
                            showGridOverlay = showGridOverlay,
                            showMarkup = isMarkupDrawing,
                            paths = paths,
                            currentPosition = currentPosition,
                            previousPosition = previousPosition,
                            drawMode = drawMode,
                            currentPath = currentPath,
                            currentPathProperty = currentPathProperty,
                            isSupportingPanel = navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded,
                            onHandleTouchChange = { 
                                android.util.Log.d("CROP_DEBUG", "onHandleTouchChange: $it")
                                isHandleTouched = it
                                if (it) {
                                    hasEverTouchedHandle = true
                                } else if (hasEverTouchedHandle) {
                                    cropState = cropState.copy(hasCropChanged = true)
                                    isCropScrubbingMode = true
                                    hasEverTouchedHandle = false
                                }
                            },
                            onGestureEnd = {
                                cropState = cropState.copy(hasCropChanged = true)
                                isCropScrubbingMode = true
                            },
                            onCropRect = { rect, zoom, panX, panY ->
                                onCropRect(
                                    rect, 
                                    imageRotation, 
                                    zoom, 
                                    panX, 
                                    panY, 
                                    selectedAspectRatio.value
                                )
                                imageRotation = 0f
                                val shouldSave = cropState.requestSave
                                val saveType = requestedSaveType
                                requestedSaveType = 0
                                cropState = cropState.copy(isCropping = false, requestSave = false, hasCropChanged = false)
                                isCropScrubbingMode = false
                                cropResetTrigger++
                                extractedCrop = null
                                if (shouldSave) {
                                    if (saveType == 2) onOverride() else onSaveCopy()
                                }
                            },
                            addPath = addPath,
                            clearPathsUndone = clearPathsUndone,
                            setCurrentPosition = setCurrentPosition,
                            setPreviousPosition = setPreviousPosition,
                            setCurrentPath = setCurrentPath,
                            setCurrentPathProperty = setCurrentPathProperty,
                            applyDrawing = applyDrawing,
                            onNavigateBack = { navController.popBackStack() },
                            requestApply = requestMarkupApply,
                            onApplyHandled = { requestMarkupApply = false },
                            textAnnotations = textAnnotations,
                            onTextAnnotationsChange = { textAnnotations = it },
                            selectedTextIndex = selectedTextIndex,
                            onSelectedTextIndexChange = { selectedTextIndex = it },
                            vignetteIntensity = vignetteIntensity,
                            blurRadius = blurRadius,
                            sharpnessValue = sharpnessValue,
                            previewRotation90 = previewRotation90,
                            previewFlipH = previewFlipH
                        )
                    },
                    supportingPane = {
                        AnimatedPane(modifier = Modifier) {
                            EditorNavigator(
                                modifier = Modifier.animateContentSize(),
                                navController = navController,
                                appliedAdjustments = appliedAdjustments,
                                targetImage = targetImage,
                                targetUri = targetUri,
                                onAdjustItemLongClick = onAdjustItemLongClick,
                                onAdjustmentChange = onAdjustmentChange,
                                onAdjustmentPreview = onAdjustmentPreview,
                                onToggleFilter = onToggleFilter,
                                drawMode = drawMode,
                                setDrawMode = setDrawMode,
                                drawType = drawType,
                                setDrawType = setDrawType,
                                currentPathProperty = currentPathProperty,
                                setCurrentPathProperty = setCurrentPathProperty,
                                filterIntensity = filterIntensity,
                                onFilterIntensityChange = onFilterIntensityChange,
                                activeFilterName = activeFilterName,
                                isSupportingPanel = true,
                                onRequestTextInput = onRequestTextInput,
                                textAnnotations = textAnnotations,
                                onTextAnnotationsChange = { textAnnotations = it },
                                selectedTextIndex = selectedTextIndex
                            )
                        }
                    }
                )
            } // end image area box
            
        // Bottom Bars
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(1f) // Ensure it floats above the Image Area
                .onSizeChanged { bottomToolbarHeight = it.height }
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
        ) {

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessHigh,
                            visibilityThreshold = IntSize.VisibilityThreshold
                        )
                    )
            ) {
                // Tool content area
                AnimatedVisibility(
                    visible = navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Hidden
                            && !showingEditorScreen && !isCropScrubbingMode,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    EditorNavigator(
                        modifier = Modifier
                            .animateContentSize()
                            .fillMaxWidth(),
                        navController = navController,
                        appliedAdjustments = appliedAdjustments,
                        targetImage = targetImage,
                        targetUri = targetUri,
                        onAdjustItemLongClick = onAdjustItemLongClick,
                        onAdjustmentChange = onAdjustmentChange,
                        onAdjustmentPreview = onAdjustmentPreview,
                        onToggleFilter = onToggleFilter,
                        drawMode = drawMode,
                        setDrawMode = setDrawMode,
                        drawType = drawType,
                        setDrawType = setDrawType,
                        currentPathProperty = currentPathProperty,
                        setCurrentPathProperty = setCurrentPathProperty,
                        filterIntensity = filterIntensity,
                        onFilterIntensityChange = onFilterIntensityChange,
                        activeFilterName = activeFilterName,
                        isSupportingPanel = false,
                        onRequestTextInput = onRequestTextInput,
                        textAnnotations = textAnnotations,
                        onTextAnnotationsChange = { textAnnotations = it },
                        selectedTextIndex = selectedTextIndex
                    )
                }

                androidx.compose.animation.AnimatedContent(
                    targetState = when {
                        isMarkupDrawing -> 0
                        navBackStackEntry?.destination?.hasRoute<EditorDestination.AdjustDetail>() == true -> 1
                        isCropScrubbingMode -> 2
                        else -> 3
                    },
                    label = "BottomBarTransition"
                ) { targetState ->
                    when (targetState) {
                        0 -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(immersiveAlpha)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            IconButton(
                                onClick = {
                                    clearDrawing()
                                    navController.popBackStack()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = CircleShape
                                    )
                            ) {
                                FontIcon(
                                    unicode = FontIcons.Close,
                                    size = 24.sp,
                                    contentDescription = "Cancel markup",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val undoEnabled by rememberedDerivedState(paths) { paths.isNotEmpty() }
                                IconButton(
                                    onClick = undoLastPath,
                                    enabled = undoEnabled
                                ) {
                                    FontIcon(
                                        unicode = FontIcons.Undo,
                                        size = 24.sp,
                                        contentDescription = "Undo",
                                        tint = if (undoEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (drawMode == DrawMode.Text) {
                                    TextButton(onClick = onRequestTextInput) {
                                        FontIcon(
                                            unicode = FontIcons.Add,
                                            size = 24.sp,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Add text",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Markup",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White
                                    )
                                }

                                val redoEnabled by rememberedDerivedState(pathsUndone) { pathsUndone.isNotEmpty() }
                                IconButton(
                                    onClick = redoLastPath,
                                    enabled = redoEnabled
                                ) {
                                    FontIcon(
                                        unicode = FontIcons.Redo,
                                        size = 24.sp,
                                        contentDescription = "Redo",
                                        tint = if (redoEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { requestMarkupApply = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                            ) {
                                FontIcon(
                                    unicode = FontIcons.Check,
                                    size = 24.sp,
                                    contentDescription = "Apply markup",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                        1 -> {
                            // Empty branch to hide bottom tabs while in detail view
                        }
                        2 -> {
                        // Show the CropScrubber (with Save/Cancel buttons)
                        val density = LocalDensity.current
                        val scrubberHeight = remember { bottomToolbarHeight }
                        val heightDp = with(density) { scrubberHeight.toDp() }
                        val initialRotation = remember { imageRotation }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (heightDp > 0.dp) heightDp else 120.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor.CropScrubber(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                isSupportingPanel = false,
                                currentValue = imageRotation,
                                onValueChanged = { imageRotation = it },
                                onCancel = { 
                                    val crop = extractedCrop
                                    if (crop != null) {
                                        onCropRect(
                                            crop.normalizedRect, 
                                            crop.rotation, 
                                            crop.zoom, 
                                            crop.panX, 
                                            crop.panY, 
                                            crop.aspectRatioValue
                                        )
                                        extractedCrop = null
                                    }
                                    imageRotation = initialRotation
                                    selectedAspectRatio = AspectRatio.Original
                                    cropResetTrigger++
                                    isCropScrubbingMode = false 
                                },
                                onDone = {
                                    cropState = cropState.copy(isCropping = true, requestSave = false)
                                    // The scrubber mode will be closed automatically when crop is successful
                                }
                            )
                        }
                    }
                    3 -> {
                        LaunchedEffect(navBackStackEntry) {
                            val dest = navBackStackEntry?.destination
                            val tab = when {
                                dest?.hasRoute<EditorDestination.Lighting>() == true -> EditorItems.Lighting
                                dest?.hasRoute<EditorDestination.Filters>() == true -> EditorItems.Filters
                                dest?.hasRoute<EditorDestination.Markup>() == true -> EditorItems.Markup
                                dest?.hasRoute<EditorDestination.Colour>() == true -> EditorItems.Colour
                                dest?.hasRoute<EditorDestination.Effects>() == true -> EditorItems.Effects
                                dest?.hasRoute<EditorDestination.More>() == true -> EditorItems.More
                                else -> null
                            }
                            if (tab != null) selectedTab = tab
                        }
                        EditorSelector(
                            modifier = Modifier.fillMaxWidth().alpha(immersiveAlpha),
                            selectedItem = selectedTab,
                            isSupportingPanel = false,
                            onItemClick = { editorItem ->
                                if (selectedTab == EditorItems.Filters && editorItem != EditorItems.Filters) {
                                    commitFilter()
                                }
                                selectedTab = editorItem
                                val dest = when (editorItem) {
                                    EditorItems.Lighting -> EditorDestination.Lighting
                                    EditorItems.Filters -> EditorDestination.Filters
                                    EditorItems.Markup -> EditorDestination.Markup
                                    EditorItems.Colour -> EditorDestination.Colour
                                    EditorItems.Effects -> EditorDestination.Effects
                                    EditorItems.More -> EditorDestination.More
                                }
                                navController.navigate(dest) {
                                    popUpTo(EditorDestination.Editor) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Loading overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isSaving || isReverting || requestMarkupApply || isProcessing,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .background(color = Color.Black.copy(alpha = 0.4f))
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                com.prantiux.pixelgallery.ui.components.ContainedExpressiveLoadingIndicator()
            }
        }

        // Text markup overlay
        if (showTextOverlay) {
            BackHandler { showTextOverlay = false }
            TextMarkupOverlay(
                onDone = { text, color ->
                    textAnnotations = textAnnotations + TextAnnotation(
                        text = text,
                        color = color,
                        position = Offset(0.1f, 0.45f)
                    )
                    showTextOverlay = false
                },
                onRemove = {
                    showTextOverlay = false
                }
            )
        }

        // Revert dialog
        if (showRevertDialog) {
            AlertDialog(
                onDismissRequest = { showRevertDialog = false },
                title = { Text("Revert to original") },
                text = { Text("Are you sure you want to revert to the original? All edits will be lost.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showRevertDialog = false
                            onRevertToOriginal()
                        }
                    ) {
                        Text("Revert")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRevertDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard edits?") },
                text = { Text("If you go back now, you will lose all the changes you've made.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDiscardDialog = false
                            if (showingEditorScreen || isOnTopLevelTab) onClose()
                            else navController.popBackStack()
                        }
                    ) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Keep editing")
                    }
                }
            )
        }

        // Image loading overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = currentImage == null,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                com.prantiux.pixelgallery.ui.components.ContainedExpressiveLoadingIndicator()
            }
        }
    }
    } } // End of CompositionLocalProvider
@Composable
inline fun <T> rememberedDerivedState(
    vararg inputs: Any?,
    crossinline producer: () -> T
): androidx.compose.runtime.State<T> {
    return androidx.compose.runtime.remember(inputs) {
        androidx.compose.runtime.derivedStateOf { producer() }
    }
}

