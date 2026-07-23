package com.smarttoolfactory.cropper.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import com.smarttoolfactory.cropper.TouchRegion
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.model.CropData
import com.smarttoolfactory.cropper.settings.CropProperties

val CropState.cropData: CropData
    get() = CropData(
        zoom = animatableZoom.targetValue,
        pan = Offset(animatablePanX.targetValue, animatablePanY.targetValue),
        rotation = animatableRotation.targetValue,
        overlayRect = overlayRect,
        cropRect = cropRect
    )

/**
 * Base class for crop operations. Any class that extends this class gets access to pan, zoom,
 * rotation values and animations via [TransformState], fling and moving back to bounds animations.
 * @param imageSize size of the **Bitmap**
 * @param containerSize size of the Composable that draws **Bitmap**. This is full size
 * of the Composable. [drawAreaSize] can be smaller than [containerSize] initially based
 * on content scale of Image composable
 * @param drawAreaSize size of the area that **Bitmap** is drawn
 * @param maxZoom maximum zoom value
 * @param fling when set to true dragging pointer builds up velocity. When last
 * pointer leaves Composable a movement invoked against friction till velocity drops below
 * to threshold
 * @param zoomable when set to true zoom is enabled
 * @param pannable when set to true pan is enabled
 * @param rotatable when set to true rotation is enabled
 * @param limitPan limits pan to bounds of parent Composable. Using this flag prevents creating
 * empty space on sides or edges of parent
 */
abstract class CropState internal constructor(
    imageSize: IntSize,
    containerSize: IntSize,
    drawAreaSize: IntSize,
    maxZoom: Float,
    internal var fling: Boolean = true,
    internal var aspectRatio: AspectRatio,
    internal var overlayRatio: Float,
    zoomable: Boolean = true,
    pannable: Boolean = true,
    rotatable: Boolean = false,
    limitPan: Boolean = false,
    initialOffsetY: Int = 0,
    val safeDrawAreaHeight: Int = 0,
    initialZoom: Float = 1f,
    initialPanX: Float = 0f,
    initialPanY: Float = 0f,
    initialRotation: Float = 0f
) : TransformState(
    imageSize = imageSize,
    containerSize = containerSize,
    drawAreaSize = drawAreaSize,
    initialZoom = initialZoom,
    initialPanX = initialPanX,
    initialPanY = initialPanY,
    initialRotation = initialRotation,
    maxZoom = maxZoom,
    zoomable = zoomable,
    pannable = pannable,
    rotatable = rotatable,
    limitPan = limitPan,
    initialOffsetY = initialOffsetY
) {

    private val animatableRectOverlay = Animatable(
        getOverlayFromAspectRatio(
            containerSize.width.toFloat(),
            containerSize.height.toFloat(),
            drawAreaSize.width.toFloat(),
            aspectRatio,
            overlayRatio,
            initialOffsetY.toFloat()
        ),
        Rect.VectorConverter
    )

    val overlayRect: Rect
        get() = animatableRectOverlay.value

    var cropRect: Rect = Rect.Zero
        get() = getCropRectangle(
            imageSize.width,
            imageSize.height,
            drawAreaRect,
            animatableRectOverlay.targetValue
        )
        private set


    internal var initialized: Boolean = false

    /**
     * Region of touch inside, corners of or outside of overlay rectangle
     */
    var touchRegion by mutableStateOf(TouchRegion.None)

    internal val hasInitialCrop = initialZoom != 1f || initialPanX != 0f || initialPanY != 0f || initialRotation != 0f

    internal suspend fun init() {
        if (!initialized) {
            animatableRectOverlay.snapTo(
                getOverlayFromAspectRatio(
                    containerSize.width.toFloat(),
                    containerSize.height.toFloat(),
                    drawAreaSize.width.toFloat(),
                    aspectRatio,
                    overlayRatio,
                    initialOffsetY.toFloat()
                )
            )
            
            if (hasInitialCrop) {
                // If we have an initial crop, just update the draw area without animating/resetting
                drawAreaRect = updateImageDrawRectFromTransformation()
            } else {
                // When initial aspect ratio doesn't match drawable area
                // overlay gets updated so updates draw area as well
                animateTransformationToOverlayBounds(overlayRect, animate = true)
            }
            initialized = true
        }
    }

    /**
     * Update properties of [CropState] and animate to valid intervals if required
     */
    internal open suspend fun updateProperties(
        cropProperties: CropProperties,
        forceUpdate: Boolean = false
    ) {

        if (!initialized) return

        fling = cropProperties.fling
        pannable = cropProperties.pannable
        zoomable = cropProperties.zoomable
        rotatable = cropProperties.rotatable

        val maxZoom = cropProperties.maxZoom

        // Update overlay rectangle
        val aspectRatio = cropProperties.aspectRatio

        // Ratio of overlay to screen
        val overlayRatio = cropProperties.overlayRatio

        if (
            this.aspectRatio.value != aspectRatio.value ||
            maxZoom != zoomMax ||
            this.overlayRatio != overlayRatio ||
            forceUpdate
        ) {
            val oldAspectRatio = this.aspectRatio
            this.aspectRatio = aspectRatio
            this.overlayRatio = overlayRatio

            zoomMax = maxZoom
            animatableZoom.updateBounds(zoomMin, zoomMax)

            val currentZoom = if (zoom > zoomMax) zoomMax else zoom
            snapZoomTo(currentZoom)

            drawAreaRect = updateImageDrawRectFromTransformation()

            if (oldAspectRatio == AspectRatio.Original && aspectRatio != AspectRatio.Original) {
                if (overlayRect.size != androidx.compose.ui.geometry.Size.Zero && overlayRect.width > 0f) {
                    val currentWidth = overlayRect.width
                    val currentHeight = overlayRect.height
                    val newRatioValue = aspectRatio.value

                    var newWidth = currentWidth
                    var newHeight = currentWidth / newRatioValue

                    if (newHeight > currentHeight) {
                        newHeight = currentHeight
                        newWidth = newHeight * newRatioValue
                    }

                    val offsetX = overlayRect.left + (currentWidth - newWidth) / 2f
                    val offsetY = overlayRect.top + (currentHeight - newHeight) / 2f
                    
                    animateOverlayRectTo(androidx.compose.ui.geometry.Rect(offset = androidx.compose.ui.geometry.Offset(offsetX, offsetY), size = androidx.compose.ui.geometry.Size(newWidth, newHeight)))
                    animateTransformationToOverlayBounds(overlayRect, animate = true)
                } else {
                    animateOverlayRectTo(
                        getOverlayFromAspectRatio(
                            containerSize.width.toFloat(),
                            containerSize.height.toFloat(),
                            drawAreaSize.width.toFloat(),
                            aspectRatio,
                            overlayRatio,
                            initialOffsetY.toFloat()
                        )
                    )
                    animateTransformationToOverlayBounds(overlayRect, animate = true)
                }
            } else if (oldAspectRatio != AspectRatio.Original && aspectRatio == AspectRatio.Original) {
                // Perfect ratio -> Freeform: keep frame where it is.
                // We do nothing to overlayRect, zoom, or pan.
                // Just ensure it stays within bounds.
                animateTransformationToOverlayBounds(overlayRect, animate = true)
            } else if (oldAspectRatio != AspectRatio.Original && aspectRatio != AspectRatio.Original) {
                snapZoomTo(1f)
                snapPanXto(0f)
                snapPanYto(0f)
                
                drawAreaRect = updateImageDrawRectFromTransformation()

                animateOverlayRectTo(
                    getOverlayFromAspectRatio(
                        containerSize.width.toFloat(),
                        containerSize.height.toFloat(),
                        drawAreaSize.width.toFloat(),
                        aspectRatio,
                        overlayRatio,
                        initialOffsetY.toFloat()
                    )
                )
                animateTransformationToOverlayBounds(overlayRect, animate = true)
            } else {
                animateOverlayRectTo(
                    getOverlayFromAspectRatio(
                        containerSize.width.toFloat(),
                        containerSize.height.toFloat(),
                        drawAreaSize.width.toFloat(),
                        aspectRatio,
                        overlayRatio,
                        initialOffsetY.toFloat()
                    )
                )
                animateTransformationToOverlayBounds(overlayRect, animate = true)
            }
        }
    }

    /**
     * Animate overlay rectangle to target value
     */
    internal suspend fun animateOverlayRectTo(
        rect: Rect,
        animationSpec: AnimationSpec<Rect> = tween(400)
    ) {
        animatableRectOverlay.animateTo(
            targetValue = rect,
            animationSpec = animationSpec
        )
    }

    /**
     * Snap overlay rectangle to target value
     */
    internal suspend fun snapOverlayRectTo(rect: Rect) {
        animatableRectOverlay.snapTo(rect)
    }

    /*
        Touch gestures
     */
    internal abstract suspend fun onDown(change: PointerInputChange)

    internal abstract suspend fun onMove(changes: List<PointerInputChange>)

    internal abstract suspend fun onUp(change: PointerInputChange)

    /*
        Transform gestures
     */
    internal abstract suspend fun onGesture(
        centroid: Offset,
        panChange: Offset,
        zoomChange: Float,
        rotationChange: Float,
        mainPointer: PointerInputChange,
        changes: List<PointerInputChange>
    )

    internal abstract suspend fun onGestureStart()

    internal abstract suspend fun onGestureEnd(onBoundsCalculated: () -> Unit)

    // Double Tap
    internal abstract suspend fun onDoubleTap(
        offset: Offset,
        zoom: Float = 1f,
        onAnimationEnd: () -> Unit
    )

    /**
     * Check if area that image is drawn covers [overlayRect]
     */
    internal fun isOverlayInImageDrawBounds(): Boolean {
        if (this.rotation == 0f) {
            return drawAreaRect.left <= overlayRect.left &&
                    drawAreaRect.top <= overlayRect.top &&
                    drawAreaRect.right >= overlayRect.right &&
                    drawAreaRect.bottom >= overlayRect.bottom
        }

        val theta = this.rotation
        val rad = Math.toRadians(Math.abs(theta).toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()

        val wOrig = drawAreaSize.width.toFloat()
        val hOrig = drawAreaSize.height.toFloat()

        val s = if (wOrig > 0 && hOrig > 0) {
            maxOf(
                (wOrig * cos + hOrig * sin) / wOrig,
                (wOrig * sin + hOrig * cos) / hOrig
            )
        } else {
            1f
        }

        val currentZoom = animatableZoom.targetValue.coerceAtLeast(1f)
        val c0x = containerSize.width / 2f
        val c0y = containerSize.height / 2f + initialOffsetY * currentZoom

        val u1x = overlayRect.left;   val u1y = overlayRect.top
        val u2x = overlayRect.right;  val u2y = overlayRect.top
        val u3x = overlayRect.right;  val u3y = overlayRect.bottom
        val u4x = overlayRect.left;   val u4y = overlayRect.bottom

        val d1x = u1x - c0x; val d1y = u1y - c0y
        val d2x = u2x - c0x; val d2y = u2y - c0y
        val d3x = u3x - c0x; val d3y = u3y - c0y
        val d4x = u4x - c0x; val d4y = u4y - c0y

        val radNeg = Math.toRadians(-theta.toDouble())
        val cosNeg = Math.cos(radNeg).toFloat()
        val sinNeg = Math.sin(radNeg).toFloat()

        fun rotX(x: Float, y: Float) = x * cosNeg - y * sinNeg
        fun rotY(x: Float, y: Float) = x * sinNeg + y * cosNeg

        val l1x = rotX(d1x, d1y); val l1y = rotY(d1x, d1y)
        val l2x = rotX(d2x, d2y); val l2y = rotY(d2x, d2y)
        val l3x = rotX(d3x, d3y); val l3y = rotY(d3x, d3y)
        val l4x = rotX(d4x, d4y); val l4y = rotY(d4x, d4y)

        val xMax = maxOf(l1x, l2x, l3x, l4x)
        val xMin = minOf(l1x, l2x, l3x, l4x)
        val yMax = maxOf(l1y, l2y, l3y, l4y)
        val yMin = minOf(l1y, l2y, l3y, l4y)

        val wi = s * wOrig * currentZoom
        val hi = s * hOrig * currentZoom

        val px = animatablePanX.targetValue
        val py = animatablePanY.targetValue

        val pLocalX = rotX(px, py)
        val pLocalY = rotY(px, py)

        val minPx = xMax - wi / 2f
        val maxPx = xMin + wi / 2f
        val minPy = yMax - hi / 2f
        val maxPy = yMin + hi / 2f

        val eps = 0.5f
        return pLocalX >= minPx - eps && pLocalX <= maxPx + eps && pLocalY >= minPy - eps && pLocalY <= maxPy + eps
    }
    internal fun isRectInContainerBounds(rect: Rect): Boolean {
        return rect.left >= 0 &&
                rect.right <= containerSize.width &&
                rect.top >= 0 &&
                rect.bottom <= containerSize.height
    }

    /**
     * Update rectangle for area that image is drawn. This rect changes when zoom and
     * pan changes and position of image changes on screen as result of transformation.
     *
     * This function is called
     *
     * * when [onGesture] is called to update rect when zoom or pan changes
     *  and if [fling] is true just after **fling** gesture starts with target
     *  value in  [StaticCropState].
     *
     *  * when [updateProperties] is called in [CropState]
     *
     *  * when [onUp] is called in [DynamicCropState] to match [overlayRect] that could be
     *  changed and animated if it's out of [containerSize] bounds or its grow
     *  bigger than previous size
     */
    internal fun updateImageDrawRectFromTransformation(): Rect {
        val containerWidth = containerSize.width
        val containerHeight = containerSize.height

        val originalDrawWidth = drawAreaSize.width
        val originalDrawHeight = drawAreaSize.height

        val panX = animatablePanX.targetValue
        val panY = animatablePanY.targetValue

        val left = (containerWidth - originalDrawWidth) / 2f
        val top = (containerHeight - originalDrawHeight) / 2f

        val zoom = animatableZoom.targetValue

        val newWidth = originalDrawWidth * zoom
        val newHeight = originalDrawHeight * zoom

        return Rect(
            offset = Offset(
                left - (newWidth - originalDrawWidth) / 2 + panX,
                top - (newHeight - originalDrawHeight) / 2 + initialOffsetY * zoom + panY,
            ),
            size = Size(newWidth, newHeight)
        )
    }

    // TODO Add resetting back to bounds for rotated state as well
    /**
     * Resets to bounds with animation and resets tracking for fling animation.
     * Changes pan, zoom and rotation to valid bounds based on [drawAreaRect] and [overlayRect]
     */
    internal suspend fun animateTransformationToOverlayBounds(
        overlayRect: Rect,
        animate: Boolean,
        animationSpec: AnimationSpec<Float> = tween(400)
    ) {
        val currentZoom = zoom.coerceAtLeast(1f)
        
        if (this.rotation == 0f) {
            val newDrawAreaRect = calculateValidImageDrawRect(overlayRect, drawAreaRect)
            val newZoom = calculateNewZoom(oldRect = drawAreaRect, newRect = newDrawAreaRect, zoom = currentZoom)
            val newPanX = newDrawAreaRect.center.x - containerSize.width / 2f
            val newPanY = newDrawAreaRect.center.y - containerSize.height / 2f - initialOffsetY * newZoom
            
            drawAreaRect = newDrawAreaRect
            if (animate) {
                resetWithAnimation(pan = Offset(newPanX, newPanY), zoom = newZoom, animationSpec = animationSpec)
            } else {
                snapPanXto(newPanX)
                snapPanYto(newPanY)
                snapZoomTo(newZoom)
            }
            resetTracking()
            return
        }

        val theta = this.rotation
        val rad = Math.toRadians(Math.abs(theta).toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        
        val wOrig = drawAreaSize.width.toFloat()
        val hOrig = drawAreaSize.height.toFloat()
        
        val s = if (wOrig > 0 && hOrig > 0) {
            maxOf(
                (wOrig * cos + hOrig * sin) / wOrig,
                (wOrig * sin + hOrig * cos) / hOrig
            )
        } else {
            1f
        }
        
        val c0x = containerSize.width / 2f
        val c0y = containerSize.height / 2f + initialOffsetY * currentZoom
        
        val u1x = overlayRect.left;   val u1y = overlayRect.top
        val u2x = overlayRect.right;  val u2y = overlayRect.top
        val u3x = overlayRect.right;  val u3y = overlayRect.bottom
        val u4x = overlayRect.left;   val u4y = overlayRect.bottom

        val d1x = u1x - c0x; val d1y = u1y - c0y
        val d2x = u2x - c0x; val d2y = u2y - c0y
        val d3x = u3x - c0x; val d3y = u3y - c0y
        val d4x = u4x - c0x; val d4y = u4y - c0y

        val radNeg = Math.toRadians(-theta.toDouble())
        val cosNeg = Math.cos(radNeg).toFloat()
        val sinNeg = Math.sin(radNeg).toFloat()

        fun rotX(x: Float, y: Float) = x * cosNeg - y * sinNeg
        fun rotY(x: Float, y: Float) = x * sinNeg + y * cosNeg

        val l1x = rotX(d1x, d1y); val l1y = rotY(d1x, d1y)
        val l2x = rotX(d2x, d2y); val l2y = rotY(d2x, d2y)
        val l3x = rotX(d3x, d3y); val l3y = rotY(d3x, d3y)
        val l4x = rotX(d4x, d4y); val l4y = rotY(d4x, d4y)

        val xMax = maxOf(l1x, l2x, l3x, l4x)
        val xMin = minOf(l1x, l2x, l3x, l4x)
        val yMax = maxOf(l1y, l2y, l3y, l4y)
        val yMin = minOf(l1y, l2y, l3y, l4y)
        
        val zoomReqX = if (s * wOrig > 0) (xMax - xMin) / (s * wOrig) else currentZoom
        val zoomReqY = if (s * hOrig > 0) (yMax - yMin) / (s * hOrig) else currentZoom
        val newZoom = maxOf(currentZoom, zoomReqX, zoomReqY)
        
        // Recalculate with newZoom
        val c0y_new = containerSize.height / 2f + initialOffsetY * newZoom
        
        val d1x_new = u1x - c0x; val d1y_new = u1y - c0y_new
        val d2x_new = u2x - c0x; val d2y_new = u2y - c0y_new
        val d3x_new = u3x - c0x; val d3y_new = u3y - c0y_new
        val d4x_new = u4x - c0x; val d4y_new = u4y - c0y_new
        
        val l1x_new = rotX(d1x_new, d1y_new); val l1y_new = rotY(d1x_new, d1y_new)
        val l2x_new = rotX(d2x_new, d2y_new); val l2y_new = rotY(d2x_new, d2y_new)
        val l3x_new = rotX(d3x_new, d3y_new); val l3y_new = rotY(d3x_new, d3y_new)
        val l4x_new = rotX(d4x_new, d4y_new); val l4y_new = rotY(d4x_new, d4y_new)
        
        val xMax_new = maxOf(l1x_new, l2x_new, l3x_new, l4x_new)
        val xMin_new = minOf(l1x_new, l2x_new, l3x_new, l4x_new)
        val yMax_new = maxOf(l1y_new, l2y_new, l3y_new, l4y_new)
        val yMin_new = minOf(l1y_new, l2y_new, l3y_new, l4y_new)
        
        val wi = s * wOrig * newZoom
        val hi = s * hOrig * newZoom
        
        val minPx = xMax_new - wi / 2f
        val maxPx = xMin_new + wi / 2f
        val minPy = yMax_new - hi / 2f
        val maxPy = yMin_new + hi / 2f
        
        val px = animatablePanX.targetValue
        // Find current image center, offset relative to new c0!
        // P_relative = C_curr - C_0_new
        val py_relative = animatablePanY.targetValue + initialOffsetY * (currentZoom - newZoom)
        
        val pLocalX = rotX(px, py_relative)
        val pLocalY = rotY(px, py_relative)
        
        val safeMinPx = minOf(minPx, maxPx)
        val safeMaxPx = maxOf(minPx, maxPx)
        val clampedPLocalX = pLocalX.coerceIn(safeMinPx, safeMaxPx)
        
        val safeMinPy = minOf(minPy, maxPy)
        val safeMaxPy = maxOf(minPy, maxPy)
        val clampedPLocalY = pLocalY.coerceIn(safeMinPy, safeMaxPy)
        
        val radPos = Math.toRadians(theta.toDouble())
        val cosPos = Math.cos(radPos).toFloat()
        val sinPos = Math.sin(radPos).toFloat()
        
        val newPanX = clampedPLocalX * cosPos - clampedPLocalY * sinPos
        val newPanY = clampedPLocalX * sinPos + clampedPLocalY * cosPos
        
        if (animate) {
            resetWithAnimation(
                pan = Offset(newPanX, newPanY),
                zoom = newZoom,
                animationSpec = animationSpec
            )
        } else {
            snapPanXto(newPanX)
            snapPanYto(newPanY)
            snapZoomTo(newZoom)
        }
        
        drawAreaRect = updateImageDrawRectFromTransformation()
        resetTracking()
    }

    /**
     * If new overlay is bigger, when crop type is dynamic, we need to increase zoom at least
     * size of bigger dimension for image draw area([drawAreaRect]) to cover overlay([overlayRect])
     */
    private fun calculateNewZoom(oldRect: Rect, newRect: Rect, zoom: Float): Float {

        if (oldRect.size == androidx.compose.ui.geometry.Size.Zero || newRect.size == androidx.compose.ui.geometry.Size.Zero) return zoom

        val widthChange = (newRect.width / oldRect.width)
            .coerceAtLeast(1f)
        val heightChange = (newRect.height / oldRect.height)
            .coerceAtLeast(1f)

        return widthChange.coerceAtLeast(heightChange) * zoom
    }

    /**
     * Calculate valid position for image draw rectangle when pointer is up. Overlay rectangle
     * should fit inside draw image rectangle to have valid bounds when calculation is completed.
     *
     * @param rectOverlay rectangle of overlay that is used for cropping
     * @param rectDrawArea rectangle of image that is being drawn
     */
    private fun calculateValidImageDrawRect(rectOverlay: Rect, rectDrawArea: Rect): Rect {

        var width = rectDrawArea.width
        var height = rectDrawArea.height

        if (width < rectOverlay.width) {
            width = rectOverlay.width
        }

        if (height < rectOverlay.height) {
            height = rectOverlay.height
        }

        var rectImageArea = Rect(offset = rectDrawArea.topLeft, size = Size(width, height))

        if (rectImageArea.left > rectOverlay.left) {
            rectImageArea = rectImageArea.translate(rectOverlay.left - rectImageArea.left, 0f)
        }

        if (rectImageArea.right < rectOverlay.right) {
            rectImageArea = rectImageArea.translate(rectOverlay.right - rectImageArea.right, 0f)
        }

        if (rectImageArea.top > rectOverlay.top) {
            rectImageArea = rectImageArea.translate(0f, rectOverlay.top - rectImageArea.top)
        }

        if (rectImageArea.bottom < rectOverlay.bottom) {
            rectImageArea = rectImageArea.translate(0f, rectOverlay.bottom - rectImageArea.bottom)
        }

        return rectImageArea
    }

    /**
     * Create [Rect] to draw overlay based on selected aspect ratio
     */
    internal fun getOverlayFromAspectRatio(
        containerWidth: Float,
        containerHeight: Float,
        drawAreaWidth: Float,
        aspectRatio: AspectRatio,
        coefficient: Float,
        offsetYPx: Float = 0f
    ): Rect {

        val maxAvailableHeight = if (safeDrawAreaHeight > 0) safeDrawAreaHeight.toFloat() else containerHeight

        if (aspectRatio == AspectRatio.Original) {
            val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()

            // Maximum width and height overlay rectangle can be measured with
            val overlayWidthMax = drawAreaWidth.coerceAtMost(containerWidth * coefficient)
            val overlayHeightMax =
                (overlayWidthMax / imageAspectRatio).coerceAtMost(maxAvailableHeight * coefficient)

            val offsetX = (containerWidth - overlayWidthMax) / 2f
            val offsetY = (containerHeight - overlayHeightMax) / 2f + offsetYPx

            return Rect(
                offset = Offset(offsetX, offsetY),
                size = Size(overlayWidthMax, overlayHeightMax)
            )
        }

        val overlayWidthMax = containerWidth * coefficient
        val overlayHeightMax = maxAvailableHeight * coefficient

        val aspectRatioValue = aspectRatio.value

        var width = overlayWidthMax
        var height = overlayWidthMax / aspectRatioValue

        if (height > overlayHeightMax) {
            height = overlayHeightMax
            width = height * aspectRatioValue
        }

        val offsetX = (containerWidth - width) / 2f
        val offsetY = (containerHeight - height) / 2f + offsetYPx

        return Rect(offset = Offset(offsetX, offsetY), size = Size(width, height))
    }

    /**
     * Get crop rectangle
     */
    private fun getCropRectangle(
        bitmapWidth: Int,
        bitmapHeight: Int,
        drawAreaRect: Rect,
        overlayRect: Rect
    ): Rect {

        if (drawAreaRect == Rect.Zero || overlayRect == Rect.Zero) return Rect(
            offset = Offset.Zero,
            Size(bitmapWidth.toFloat(), bitmapHeight.toFloat())
        )

        // Calculate latest image draw area based on overlay position
        // This is valid rectangle that contains crop area inside overlay
        val newRect = calculateValidImageDrawRect(overlayRect, drawAreaRect)

        val overlayWidth = overlayRect.width
        val overlayHeight = overlayRect.height

        val drawAreaWidth = newRect.width
        val drawAreaHeight = newRect.height

        val widthRatio = overlayWidth / drawAreaWidth
        val heightRatio = overlayHeight / drawAreaHeight

        val diffLeft = overlayRect.left - newRect.left
        val diffTop = overlayRect.top - newRect.top

        val croppedBitmapLeft = (diffLeft * (bitmapWidth / drawAreaWidth))
        val croppedBitmapTop = (diffTop * (bitmapHeight / drawAreaHeight))

        val croppedBitmapWidth = bitmapWidth * widthRatio
        val croppedBitmapHeight = bitmapHeight * heightRatio

        return Rect(
            offset = Offset(croppedBitmapLeft, croppedBitmapTop),
            size = Size(croppedBitmapWidth, croppedBitmapHeight)
        )
    }
}
