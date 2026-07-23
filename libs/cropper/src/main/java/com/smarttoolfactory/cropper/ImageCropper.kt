package com.smarttoolfactory.cropper

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.smarttoolfactory.cropper.crop.CropAgent
import com.smarttoolfactory.cropper.draw.DrawingOverlay
import com.smarttoolfactory.cropper.draw.ImageDrawCanvas
import com.smarttoolfactory.cropper.image.ImageWithConstraints
import com.smarttoolfactory.cropper.image.getScaledImageBitmap
import com.smarttoolfactory.cropper.model.CropOutline
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropProperties
import com.smarttoolfactory.cropper.settings.CropStyle
import com.smarttoolfactory.cropper.settings.CropType
import com.smarttoolfactory.cropper.state.DynamicCropState
import com.smarttoolfactory.cropper.state.rememberCropState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

@Composable
fun ImageCropper(
    modifier: Modifier = Modifier,
    imageBitmap: ImageBitmap,
    contentDescription: String? = null,
    cropStyle: CropStyle = CropDefaults.style(),
    cropProperties: CropProperties,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    crop: Boolean = false,
    cropEnabled: Boolean = true,
    onCropStart: () -> Unit,
    onCropSuccess: (ImageBitmap) -> Unit,
    onCropRect: ((android.graphics.RectF, Float, Float, Float) -> Unit)? = null,
    cropResetTrigger: Int = 0,
    initialZoom: Float = 1f,
    initialPanX: Float = 0f,
    initialPanY: Float = 0f,
    backgroundModifier: Modifier = Modifier,
    onHandleTouchChange: (Boolean) -> Unit = {},
    onGestureEnd: () -> Unit = {},
    imageRotation: Float = 0f,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {

    ImageWithConstraints(
        modifier = modifier,
        contentScale = cropProperties.contentScale,
        contentDescription = contentDescription,
        filterQuality = filterQuality,
        imageBitmap = imageBitmap,
        drawImage = false,
        contentPadding = contentPadding
    ) {

        // No crop operation is applied by ScalableImage so rect points to bounds of original
        // bitmap
        val scaledImageBitmap = getScaledImageBitmap(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rect = rect,
            bitmap = imageBitmap,
            contentScale = cropProperties.contentScale,
        )

        // Container Dimensions
        val containerWidthPx = constraints.maxWidth
        val containerHeightPx = constraints.maxHeight

        val containerWidth: Dp
        val containerHeight: Dp

        // Bitmap Dimensions
        val bitmapWidth = scaledImageBitmap.width
        val bitmapHeight = scaledImageBitmap.height

        // Dimensions of Composable that displays Bitmap
        val imageWidthPx: Int
        val imageHeightPx: Int

        with(LocalDensity.current) {
            imageWidthPx = imageWidth.roundToPx()
            imageHeightPx =
                imageHeight.roundToPx() - if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 0
                else WindowInsets.navigationBars.getBottom(LocalDensity.current)
            containerWidth = containerWidthPx.toDp()
            containerHeight = containerHeightPx.toDp()
        }

        val topPaddingPx = with(LocalDensity.current) { contentPadding.calculateTopPadding().roundToPx() }
        val bottomPaddingPx = with(LocalDensity.current) { contentPadding.calculateBottomPadding().roundToPx() }
        val safeBoxHeight = containerHeightPx - topPaddingPx - bottomPaddingPx
        val centerYSafe = topPaddingPx + safeBoxHeight / 2f
        val centerYFull = containerHeightPx / 2f
        val offsetY = (centerYSafe - centerYFull).toInt()

        val cropType = cropProperties.cropType
        val contentScale = cropProperties.contentScale
        val fixedAspectRatio = cropProperties.fixedAspectRatio
        val cropOutline = cropProperties.cropOutlineProperty.cropOutline

        // these keys are for resetting cropper when image width/height, contentScale or
        // overlay aspect ratio changes
        val resetKeys =
            getResetKeys(
                cropEnabled,
                scaledImageBitmap,
                imageWidthPx,
                imageHeightPx,
                containerWidthPx,
                containerHeightPx,
                contentScale,
                cropType,
                offsetY,
                cropResetTrigger
            )

        val cropState = rememberCropState(
            imageSize = IntSize(bitmapWidth, bitmapHeight),
            containerSize = IntSize(containerWidthPx, containerHeightPx),
            drawAreaSize = IntSize(imageWidthPx, imageHeightPx),
            cropProperties = cropProperties,
            initialOffsetY = offsetY,
            safeDrawAreaHeight = safeBoxHeight,
            keys = resetKeys
        )

        var isImageGestureInProgress by remember { mutableStateOf(false) }

        val isHandleTouched by remember(cropState) {
            derivedStateOf {
                cropState is DynamicCropState && handlesTouched(cropState.touchRegion)
            }
        }
        
        LaunchedEffect(isHandleTouched) {
            onHandleTouchChange(isHandleTouched)
        }

        val pressedStateColor = remember(cropStyle.backgroundColor) {
            cropStyle.backgroundColor
                .copy(cropStyle.backgroundColor.alpha * .7f)
        }

        val transparentColor by animateColorAsState(
            animationSpec = tween(300, easing = LinearEasing),
            targetValue = if (isHandleTouched || isImageGestureInProgress) pressedStateColor else cropStyle.backgroundColor,
            label = "transparentColor"
        )

        // Crops image when user invokes crop operation
        Crop(
            crop,
            scaledImageBitmap,
            cropState.cropRect,
            cropOutline,
            onCropStart,
            onCropSuccess,
            onCropRect = onCropRect?.let { callback ->
                { cropRect ->
                    // cropRect is in scaledImageBitmap pixel coords.
                    // scaledImageBitmap is a 1:1 pixel sub-region of imageBitmap at (rect.left, rect.top).
                    // Normalize to 0-1 relative to the full input imageBitmap.
                    val imgW = imageBitmap.width.toFloat()
                    val imgH = imageBitmap.height.toFloat()
                    val left = (rect.left + cropRect.left) / imgW
                    val top = (rect.top + cropRect.top) / imgH
                    val right = (rect.left + cropRect.right) / imgW
                    val bottom = (rect.top + cropRect.bottom) / imgH
                    callback(android.graphics.RectF(left, top, right, bottom), cropState.zoom, cropState.pan.x, cropState.pan.y)
                }
            },
            rotation = cropState.rotation
        )

        val imageModifier = Modifier
            .size(containerWidth, containerHeight)
            .crop(
                keys = resetKeys,
                cropState = cropState,
                onGestureStart = {
                    isImageGestureInProgress = true
                },
                onGestureEnd = {
                    isImageGestureInProgress = false
                    onGestureEnd()
                }
            )

        LaunchedEffect(key1 = cropProperties) {
            cropState.updateProperties(cropProperties)
        }
        
        LaunchedEffect(imageRotation) {
            if (cropState.rotation != imageRotation) {
                cropState.snapRotationTo(imageRotation)
            }
        }
        
        LaunchedEffect(initialZoom, initialPanX, initialPanY, cropResetTrigger) {
            if (initialZoom != 1f || initialPanX != 0f || initialPanY != 0f) {
                cropState.snapZoomTo(initialZoom)
                cropState.snapPanXto(initialPanX)
                cropState.snapPanYto(initialPanY)
            }
        }

        /// Create a MutableTransitionState<Boolean> for the AnimatedVisibility.
        var visible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(100)
            visible = true
        }

        ImageCropper(
            modifier = imageModifier,
            visible = visible,
            cropEnabled = cropEnabled,
            imageBitmap = imageBitmap,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            imageWidthPx = imageWidthPx,
            imageHeightPx = imageHeightPx,
            handleSize = cropProperties.handleSize,
            overlayRect = cropState.overlayRect,
            cropType = cropType,
            cropOutline = cropOutline,
            cropStyle = cropStyle,
            transparentColor = transparentColor,
            backgroundModifier = backgroundModifier,
            offsetY = offsetY
        )
    }
}

@Composable
private fun ImageCropper(
    modifier: Modifier,
    visible: Boolean,
    cropEnabled: Boolean,
    imageBitmap: ImageBitmap,
    containerWidth: Dp,
    containerHeight: Dp,
    imageWidthPx: Int,
    imageHeightPx: Int,
    handleSize: Float,
    cropType: CropType,
    cropOutline: CropOutline,
    cropStyle: CropStyle,
    overlayRect: Rect,
    transparentColor: Color,
    backgroundModifier: Modifier,
    offsetY: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500))
        ) {
            ImageCropperImpl(
                modifier = modifier,
                cropEnabled = cropEnabled,
                imageBitmap = imageBitmap,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                imageWidthPx = imageWidthPx,
                imageHeightPx = imageHeightPx,
                cropType = cropType,
                cropOutline = cropOutline,
                handleSize = handleSize,
                cropStyle = cropStyle,
                rectOverlay = overlayRect,
                transparentColor = transparentColor,
                offsetY = offsetY
            )
        }
    }
}

@Composable
private fun ImageCropperImpl(
    modifier: Modifier,
    imageBitmap: ImageBitmap,
    cropEnabled: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    imageWidthPx: Int,
    imageHeightPx: Int,
    cropType: CropType,
    cropOutline: CropOutline,
    handleSize: Float,
    cropStyle: CropStyle,
    transparentColor: Color,
    rectOverlay: Rect,
    offsetY: Int = 0
) {

    Box(contentAlignment = Alignment.Center) {

        // Draw Image
        ImageDrawCanvas(
            modifier = modifier,
            imageBitmap = imageBitmap,
            imageWidth = imageWidthPx,
            imageHeight = imageHeightPx,
            offsetY = offsetY
        )

        val drawOverlay = cropStyle.drawOverlay

        val drawGrid = cropStyle.drawGrid
        val overlayColor = cropStyle.overlayColor
        val handleColor = cropStyle.handleColor
        val drawHandles = cropType == CropType.Dynamic
        val strokeWidth = cropStyle.strokeWidth

        AnimatedVisibility(
            visible = cropEnabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DrawingOverlay(
                modifier = Modifier.size(containerWidth, containerHeight),
                drawOverlay = drawOverlay,
                rect = rectOverlay,
                cropOutline = cropOutline,
                drawGrid = drawGrid,
                overlayColor = overlayColor,
                handleColor = handleColor,
                strokeWidth = strokeWidth,
                drawHandles = drawHandles,
                handleSize = handleSize,
                transparentColor = transparentColor,
            )
        }
    }
}

@Composable
private fun Crop(
    crop: Boolean,
    scaledImageBitmap: ImageBitmap,
    cropRect: Rect,
    cropOutline: CropOutline,
    onCropStart: () -> Unit,
    onCropSuccess: (ImageBitmap) -> Unit,
    onCropRect: ((Rect) -> Unit)? = null,
    rotation: Float = 0f
) {

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // Crop Agent is responsible for cropping image
    val cropAgent = remember { CropAgent() }

    LaunchedEffect(crop) {
        if (crop) {
            flow {
                emit(
                    cropAgent.crop(
                        scaledImageBitmap,
                        cropRect,
                        cropOutline,
                        layoutDirection,
                        density,
                        rotation
                    )
                )
            }
                .flowOn(Dispatchers.Default)
                .onStart {
                    onCropStart()
                    onCropRect?.invoke(cropRect)
                    delay(400)
                }
                .onEach {
                    onCropSuccess(it)
                }
                .launchIn(this)
        }
    }
}

@Composable
private fun getResetKeys(
    cropEnabled: Boolean,
    scaledImageBitmap: ImageBitmap,
    imageWidthPx: Int,
    imageHeightPx: Int,
    containerWidthPx: Int,
    containerHeightPx: Int,
    contentScale: ContentScale,
    cropType: CropType,
    offsetY: Int,
    cropResetTrigger: Int
) = remember(
    cropEnabled,
    scaledImageBitmap,
    imageWidthPx,
    imageHeightPx,
    containerWidthPx,
    containerHeightPx,
    contentScale,
    cropType,
    offsetY,
    cropResetTrigger
) {
    arrayOf(
        cropEnabled,
        scaledImageBitmap,
        imageWidthPx,
        imageHeightPx,
        containerWidthPx,
        containerHeightPx,
        contentScale,
        cropType,
        offsetY,
        cropResetTrigger
    )
}