package com.smarttoolfactory.cropper.draw

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.smarttoolfactory.cropper.model.CropImageMask
import com.smarttoolfactory.cropper.model.CropOutline
import com.smarttoolfactory.cropper.model.CropPath
import com.smarttoolfactory.cropper.model.CropShape
import com.smarttoolfactory.cropper.util.drawGrid
import com.smarttoolfactory.cropper.util.drawWithLayer
import com.smarttoolfactory.cropper.util.scaleAndTranslatePath

/**
 * Draw overlay composed of 9 rectangles. When [drawHandles]
 * is set draw handles for changing drawing rectangle
 */
@Composable
internal fun DrawingOverlay(
    modifier: Modifier,
    drawOverlay: Boolean,
    rect: Rect,
    cropOutline: CropOutline,
    drawGrid: Boolean,
    transparentColor: Color,
    overlayColor: Color,
    handleColor: Color,
    strokeWidth: Dp,
    drawHandles: Boolean,
    handleSize: Float
) {
    val density = LocalDensity.current
    val layoutDirection: LayoutDirection = LocalLayoutDirection.current

    val strokeWidthPx = LocalDensity.current.run { strokeWidth.toPx() }

    val pathHandles = remember {
        Path()
    }

    when (cropOutline) {
        is CropShape -> {

            val outline = remember(rect, cropOutline) {
                cropOutline.shape.createOutline(rect.size, layoutDirection, density)
            }

            DrawingOverlayImpl(
                modifier = modifier,
                drawOverlay = drawOverlay,
                rect = rect,
                drawGrid = drawGrid,
                transparentColor = transparentColor,
                overlayColor = overlayColor,
                handleColor = handleColor,
                strokeWidth = strokeWidthPx,
                drawHandles = drawHandles,
                handleSize = handleSize,
                pathHandles = pathHandles,
                outline = outline
            )
        }

        is CropPath -> {
            val path = remember(rect, cropOutline) {
                Path().apply {
                    addPath(cropOutline.path)
                    scaleAndTranslatePath(rect.width, rect.height)
                }
            }


            DrawingOverlayImpl(
                modifier = modifier,
                drawOverlay = drawOverlay,
                rect = rect,
                drawGrid = drawGrid,
                transparentColor = transparentColor,
                overlayColor = overlayColor,
                handleColor = handleColor,
                strokeWidth = strokeWidthPx,
                drawHandles = drawHandles,
                handleSize = handleSize,
                pathHandles = pathHandles,
                path = path
            )
        }

        is CropImageMask -> {
            val imageBitmap = cropOutline.image

            DrawingOverlayImpl(
                modifier = modifier,
                drawOverlay = drawOverlay,
                rect = rect,
                drawGrid = drawGrid,
                transparentColor = transparentColor,
                overlayColor = overlayColor,
                handleColor = handleColor,
                strokeWidth = strokeWidthPx,
                drawHandles = drawHandles,
                handleSize = handleSize,
                pathHandles = pathHandles,
                image = imageBitmap
            )
        }
    }
}

@Composable
private fun DrawingOverlayImpl(
    modifier: Modifier,
    drawOverlay: Boolean, // this means draw the bright outline border
    rect: Rect,
    drawGrid: Boolean,
    transparentColor: Color,
    overlayColor: Color,
    handleColor: Color,
    strokeWidth: Float,
    drawHandles: Boolean,
    handleSize: Float,
    pathHandles: Path,
    outline: Outline,
) {
    Canvas(modifier = modifier) {
        // Construct the massive rect path
        val massivePath = Path().apply {
            addRect(Rect(-10000f, -10000f, 20000f, 20000f))
        }

        // Construct the hole path based on outline
        val holePath = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        
        // Translate the hole to the crop rect position
        holePath.translate(Offset(rect.left, rect.top))

        // Subtract the hole from the massive rect to create a bleeding overlay
        val overlayPath = androidx.compose.ui.graphics.Path.combine(
            operation = androidx.compose.ui.graphics.PathOperation.Difference,
            path1 = massivePath,
            path2 = holePath
        )

        // Draw the darkness WITHOUT saveLayer, allowing it to bleed indefinitely
        drawPath(
            path = overlayPath,
            color = transparentColor
        )

        if (drawGrid) {
            drawGrid(
                rect = rect,
                strokeWidth = strokeWidth,
                color = overlayColor
            )
        }

        if (drawOverlay) {
            // SHADOW for rect
            drawRect(
                topLeft = rect.topLeft,
                size = rect.size,
                color = Color(0x66000000), // Shadow
                style = Stroke(width = strokeWidth * 2 + 4f)
            )
            // ACTUAL rect
            drawRect(
                topLeft = rect.topLeft,
                size = rect.size,
                color = overlayColor,
                style = Stroke(width = strokeWidth * 2)
            )

            if (drawHandles) {
                pathHandles.apply {
                    reset()
                    updateHandlePath(rect, handleSize)
                }

                // SHADOW for handles
                drawPath(
                    path = pathHandles,
                    color = Color(0x66000000), // Shadow
                    style = Stroke(
                        width = strokeWidth * 3 + 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // ACTUAL handles
                drawPath(
                    path = pathHandles,
                    color = handleColor,
                    style = Stroke(
                        width = strokeWidth * 3,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun DrawingOverlayImpl(
    modifier: Modifier,
    drawOverlay: Boolean,
    rect: Rect,
    drawGrid: Boolean,
    transparentColor: Color,
    overlayColor: Color,
    handleColor: Color,
    strokeWidth: Float,
    drawHandles: Boolean,
    handleSize: Float,
    pathHandles: Path,
    path: Path,
) {
    Canvas(modifier = modifier) {
        val massivePath = Path().apply {
            addRect(Rect(-10000f, -10000f, 20000f, 20000f))
        }

        val holePath = Path().apply {
            addPath(path)
            translate(Offset(rect.left, rect.top))
        }

        val overlayPath = androidx.compose.ui.graphics.Path.combine(
            operation = androidx.compose.ui.graphics.PathOperation.Difference,
            path1 = massivePath,
            path2 = holePath
        )

        drawPath(
            path = overlayPath,
            color = transparentColor
        )

        if (drawGrid) {
            drawGrid(
                rect = rect,
                strokeWidth = strokeWidth,
                color = overlayColor
            )
        }

        if (drawOverlay) {
            // SHADOW for rect
            drawRect(
                topLeft = rect.topLeft,
                size = rect.size,
                color = Color(0x66000000), // Shadow
                style = Stroke(width = strokeWidth * 2 + 4f)
            )
            // ACTUAL rect
            drawRect(
                topLeft = rect.topLeft,
                size = rect.size,
                color = overlayColor,
                style = Stroke(width = strokeWidth * 2)
            )

            if (drawHandles) {
                pathHandles.apply {
                    reset()
                    updateHandlePath(rect, handleSize)
                }

                // SHADOW for handles
                drawPath(
                    path = pathHandles,
                    color = Color(0x66000000), // Shadow
                    style = Stroke(
                        width = strokeWidth * 3 + 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // ACTUAL handles
                drawPath(
                    path = pathHandles,
                    color = handleColor,
                    style = Stroke(
                        width = strokeWidth * 3,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun DrawingOverlayImpl(
    modifier: Modifier,
    drawOverlay: Boolean,
    rect: Rect,
    drawGrid: Boolean,
    transparentColor: Color,
    overlayColor: Color,
    handleColor: Color,
    strokeWidth: Float,
    drawHandles: Boolean,
    handleSize: Float,
    pathHandles: Path,
    image: ImageBitmap,
) {
    Canvas(modifier = modifier) {
        drawOverlay(
            drawOverlay,
            rect,
            drawGrid,
            transparentColor,
            overlayColor,
            handleColor,
            strokeWidth,
            drawHandles,
            handleSize,
            pathHandles
        ) {
            drawCropImage(rect, image)
        }
    }
}

private fun DrawScope.drawOverlay(
    drawOverlay: Boolean,
    rect: Rect,
    drawGrid: Boolean,
    transparentColor: Color,
    overlayColor: Color,
    handleColor: Color,
    strokeWidth: Float,
    drawHandles: Boolean,
    handleSize: Float,
    pathHandles: Path,
    drawBlock: DrawScope.() -> Unit
) {
    drawWithLayer {

        // Destination
        // Draw a massive rect to cover the entire edge-to-edge canvas space
        drawRect(
            color = transparentColor,
            topLeft = androidx.compose.ui.geometry.Offset(-10000f, -10000f),
            size = androidx.compose.ui.geometry.Size(20000f, 20000f)
        )

        // Source
        translate(left = rect.left, top = rect.top) {
            drawBlock()
        }

        if (drawGrid) {
            drawGrid(
                rect = rect,
                strokeWidth = strokeWidth,
                color = overlayColor
            )
        }
    }

    if (drawOverlay) {
        // SHADOW for rect
        drawRect(
            topLeft = rect.topLeft,
            size = rect.size,
            color = Color(0x66000000), // Shadow
            style = Stroke(width = strokeWidth * 2 + 4f)
        )
        // ACTUAL rect
        drawRect(
            topLeft = rect.topLeft,
            size = rect.size,
            color = overlayColor,
            style = Stroke(width = strokeWidth * 2)
        )

        if (drawHandles) {
            pathHandles.apply {
                reset()
                updateHandlePath(rect, handleSize)
            }

            // SHADOW for handles
            drawPath(
                path = pathHandles,
                color = Color(0x66000000), // Shadow
                style = Stroke(
                    width = strokeWidth * 3 + 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            // ACTUAL handles
            drawPath(
                path = pathHandles,
                color = handleColor,
                style = Stroke(
                    width = strokeWidth * 3,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun DrawScope.drawCropImage(
    rect: Rect,
    imageBitmap: ImageBitmap,
    blendMode: BlendMode = BlendMode.DstOut
) {
    drawImage(
        image = imageBitmap,
        dstSize = IntSize(rect.size.width.toInt(), rect.size.height.toInt()),
        blendMode = blendMode
    )
}

private fun DrawScope.drawCropOutline(
    outline: Outline,
    blendMode: BlendMode = BlendMode.SrcOut
) {
    drawOutline(
        outline = outline,
        color = Color.Transparent,
        blendMode = blendMode
    )
}

private fun DrawScope.drawCropPath(
    path: Path,
    blendMode: BlendMode = BlendMode.SrcOut
) {
    drawPath(
        path = path,
        color = Color.Transparent,
        blendMode = blendMode
    )
}

private fun Path.updateHandlePath(
    rect: Rect,
    handleSize: Float
) {
    if (rect != Rect.Zero) {
        // Top left lines
        moveTo(rect.topLeft.x, rect.topLeft.y + handleSize)
        lineTo(rect.topLeft.x, rect.topLeft.y)
        lineTo(rect.topLeft.x + handleSize, rect.topLeft.y)

        // Top right lines
        moveTo(rect.topRight.x - handleSize, rect.topRight.y)
        lineTo(rect.topRight.x, rect.topRight.y)
        lineTo(rect.topRight.x, rect.topRight.y + handleSize)

        // Bottom right lines
        moveTo(rect.bottomRight.x, rect.bottomRight.y - handleSize)
        lineTo(rect.bottomRight.x, rect.bottomRight.y)
        lineTo(rect.bottomRight.x - handleSize, rect.bottomRight.y)


        // Top edge line
        moveTo(rect.center.x - handleSize, rect.top)
        lineTo(rect.center.x + handleSize, rect.top)

        // Bottom edge line
        moveTo(rect.center.x - handleSize, rect.bottom)
        lineTo(rect.center.x + handleSize, rect.bottom)

        // Left edge line
        moveTo(rect.left, rect.center.y - handleSize)
        lineTo(rect.left, rect.center.y + handleSize)

        // Right edge line
        moveTo(rect.right, rect.center.y - handleSize)
        lineTo(rect.right, rect.center.y + handleSize)

        // Bottom left lines
        moveTo(rect.bottomLeft.x + handleSize, rect.bottomLeft.y)
        lineTo(rect.bottomLeft.x, rect.bottomLeft.y)
        lineTo(rect.bottomLeft.x, rect.bottomLeft.y - handleSize)
    }
}
