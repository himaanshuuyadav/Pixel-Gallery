package com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider




import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.domain.model.editor.DrawMode
import com.prantiux.pixelgallery.domain.model.editor.DrawType
import com.prantiux.pixelgallery.domain.model.editor.EditorDestination
import com.prantiux.pixelgallery.domain.model.editor.EditorItems
import com.prantiux.pixelgallery.domain.model.editor.ImageFilter
import com.prantiux.pixelgallery.domain.model.editor.MarkupItems
import com.prantiux.pixelgallery.domain.model.editor.PathProperties
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.VariableFilterTypes
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.adjustment.AdjustScrubber
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.colour.ColourSection
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.colour.toVariableFilterType
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.effects.EffectsSection
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.effects.toVariableFilterType
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.filters.FiltersSelector
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.lighting.LightingSection
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.lighting.toVariableFilterType
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.markup.MarkupSelector
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.markup.MarkupToolSelector
import com.prantiux.pixelgallery.domain.model.editor.TextAnnotation
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EditorNavigator(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    appliedAdjustments: List<Adjustment>,
    targetImage: Bitmap?,
    targetUri: Uri?,
    onAdjustItemLongClick: (VariableFilterTypes) -> Unit = {},
    onAdjustmentChange: (Adjustment) -> Unit = {},
    onAdjustmentPreview: (Adjustment) -> Unit = {},
    onToggleFilter: (ImageFilter) -> Unit = {},
    drawMode: DrawMode,
    setDrawMode: (DrawMode) -> Unit,
    drawType: DrawType,
    setDrawType: (DrawType) -> Unit,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    filterIntensity: Float = 1f,
    onFilterIntensityChange: (Float) -> Unit = {},
    activeFilterName: String? = null,
    isSupportingPanel: Boolean = false,
    onRequestTextInput: () -> Unit = {},
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1
) {

    SharedTransitionLayout {
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = EditorDestination.Lighting,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            composable<EditorDestination.Editor> {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalAnimatedVisibilityScope provides this@composable
                ) {
                    if (isSupportingPanel) {
                        EditorSelector(
                    isSupportingPanel = true,
                    onItemClick = { editorItem ->
                        val dest = when (editorItem) {
                            EditorItems.Crop -> EditorDestination.Crop
                            EditorItems.Lighting -> EditorDestination.Lighting
                            EditorItems.Filters -> EditorDestination.Filters
                            EditorItems.Markup -> EditorDestination.Markup
                            EditorItems.Colour -> EditorDestination.Colour
                            EditorItems.Effects -> EditorDestination.Effects
                            EditorItems.More -> EditorDestination.More
                        }
                        navController.navigate(dest)
                    }
                )
            }
            // Phone layout: tab bar is outside NavHost, so Editor destination is empty
        }
        }

        // Crop tab
        composable<EditorDestination.Crop> {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this@composable
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().height(0.dp)
                )
            }
        }

        // Lighting tab
        composable<EditorDestination.Lighting> {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this@composable
            ) {
                LightingSection(
                appliedAdjustments = appliedAdjustments,
                isSupportingPanel = isSupportingPanel,
                onItemClick = { tool ->
                    navController.navigate(
                        EditorDestination.AdjustDetail(tool.toVariableFilterType())
                    )
                },
                onLongItemClick = { tool ->
                    onAdjustItemLongClick(tool.toVariableFilterType())
                }
            )
            }
        }

        // Colour tab
        composable<EditorDestination.Colour> {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this@composable
            ) {
                ColourSection(
                appliedAdjustments = appliedAdjustments,
                isSupportingPanel = isSupportingPanel,
                onItemClick = { tool ->
                    navController.navigate(
                        EditorDestination.AdjustDetail(tool.toVariableFilterType())
                    )
                },
                onLongItemClick = { tool ->
                    onAdjustItemLongClick(tool.toVariableFilterType())
                }
            )
            }
        }

        // Effects tab
        composable<EditorDestination.Effects> {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this@composable
            ) {
                EffectsSection(
                appliedAdjustments = appliedAdjustments,
                isSupportingPanel = isSupportingPanel,
                onItemClick = { tool ->
                    navController.navigate(
                        EditorDestination.AdjustDetail(tool.toVariableFilterType())
                    )
                },
                onLongItemClick = { tool ->
                    onAdjustItemLongClick(tool.toVariableFilterType())
                }
            )
            }
        }

        // More tab → directly show external editors
        composable<EditorDestination.More> {
            ExternalEditor(
                currentUri = targetUri,
                isSupportingPanel = isSupportingPanel
            )
        }

        // Shared detail scrubber for all variable filters
        composable<EditorDestination.AdjustDetail> {
            val params = it.toRoute<EditorDestination.AdjustDetail>()
            val isRotate = params.adjustment == VariableFilterTypes.Rotate
            val isHue = params.adjustment == VariableFilterTypes.Hue

            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this@composable
            ) {
                AdjustScrubber(
                modifier = Modifier.padding(bottom = 16.dp),
                adjustment = params.adjustment,
                displayValue = { value ->
                    when {
                        isRotate -> "${value.roundToInt()}°"
                        isHue -> "${(value * 180f).roundToInt()}°"
                        else -> (value * 100f).roundToInt().toString()
                    }
                },
                onAdjustmentChange = onAdjustmentChange,
                onAdjustmentPreview = onAdjustmentPreview,
                onCancel = { navController.popBackStack() },
                onDone = {
                    onAdjustmentChange(it)
                    navController.popBackStack()
                },
                appliedAdjustments = appliedAdjustments,
                isSupportingPanel = isSupportingPanel
            )
            }
        }

        composable<EditorDestination.CropDetail> {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this@composable
            ) {
                CropScrubber(
                    modifier = Modifier.padding(bottom = 16.dp),
                    isSupportingPanel = isSupportingPanel,
                    currentValue = 0f,
                    onValueChanged = {},
                    onCancel = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
        }

        composable<EditorDestination.Filters> {
            FiltersSelector(
                bitmap = targetImage!!,
                onClick = onToggleFilter,
                appliedAdjustments = appliedAdjustments,
                activeFilterName = activeFilterName,
                isSupportingPanel = isSupportingPanel,
                filterIntensity = filterIntensity,
                onFilterIntensityChange = onFilterIntensityChange
            )
        }

        composable<EditorDestination.Markup> {
            MarkupToolSelector(
                isSupportingPanel = isSupportingPanel,
                onToolClick = { item ->
                    when (item) {
                        MarkupItems.Stylus -> {
                            setDrawMode(DrawMode.Draw)
                            setDrawType(DrawType.Stylus)
                        }
                        MarkupItems.Highlighter -> {
                            setDrawMode(DrawMode.Draw)
                            setDrawType(DrawType.Highlighter)
                        }
                        MarkupItems.Marker -> {
                            setDrawMode(DrawMode.Draw)
                            setDrawType(DrawType.Marker)
                        }
                        MarkupItems.Text -> {
                            setDrawMode(DrawMode.Text)
                            onRequestTextInput()
                        }
                        MarkupItems.Eraser -> {
                            setDrawMode(DrawMode.Erase)
                        }
                        MarkupItems.Pan -> {
                            setDrawMode(DrawMode.Touch)
                        }
                    }
                    navController.navigate(EditorDestination.MarkupDraw) {
                        popUpTo(EditorDestination.Markup) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<EditorDestination.MarkupDraw> {
            MarkupSelector(
                drawMode = drawMode,
                setDrawMode = setDrawMode,
                drawType = drawType,
                setDrawType = setDrawType,
                isSupportingPanel = isSupportingPanel,
                currentPathProperty = currentPathProperty,
                setCurrentPathProperty = setCurrentPathProperty,
                onRequestTextInput = onRequestTextInput,
                textAnnotations = textAnnotations,
                onTextAnnotationsChange = onTextAnnotationsChange,
                selectedTextIndex = selectedTextIndex
            )
        }

    }
    }
}
