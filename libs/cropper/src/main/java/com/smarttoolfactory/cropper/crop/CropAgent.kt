package com.smarttoolfactory.cropper.crop

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.smarttoolfactory.cropper.model.CropImageMask
import com.smarttoolfactory.cropper.model.CropOutline
import com.smarttoolfactory.cropper.model.CropPath
import com.smarttoolfactory.cropper.model.CropShape


/**
 * Crops imageBitmap based on path that is passed in [crop] function
 */
class CropAgent {

    private val imagePaint = Paint().apply {
        blendMode = BlendMode.SrcIn
    }

    private val paint = Paint()


    fun crop(
        imageBitmap: ImageBitmap,
        cropRect: Rect,
        cropOutline: CropOutline,
        layoutDirection: LayoutDirection,
        density: Density,
        rotation: Float = 0f
    ): ImageBitmap {
        return runCatching {
            val w = imageBitmap.width.toFloat()
            val h = imageBitmap.height.toFloat()

            // Calculate auto-zoom scale factor
            val rad = Math.toRadians(Math.abs(rotation).toDouble())
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            val s = if (w > 0 && h > 0) {
                maxOf(
                    (w * cos + h * sin) / w,
                    (w * sin + h * cos) / h
                )
            } else {
                1f
            }

            val croppedBitmap = Bitmap.createBitmap(
                cropRect.width.toInt(),
                cropRect.height.toInt(),
                Bitmap.Config.ARGB_8888
            )

            val canvas = android.graphics.Canvas(croppedBitmap)
            val matrix = android.graphics.Matrix()

            val pivotX = w / 2f
            val pivotY = h / 2f

            matrix.postScale(s, s, pivotX, pivotY)
            matrix.postRotate(rotation, pivotX, pivotY)
            matrix.postTranslate(-cropRect.left, -cropRect.top)

            val paint = android.graphics.Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
            }

            canvas.drawBitmap(imageBitmap.asAndroidBitmap(), matrix, paint)

            val imageToCrop = croppedBitmap
                .copy(Bitmap.Config.ARGB_8888, true)!!
                .asImageBitmap()

            drawCroppedImage(cropOutline, cropRect, layoutDirection, density, imageToCrop)

            imageToCrop
        }.getOrNull() ?: imageBitmap
    }

    private fun drawCroppedImage(
        cropOutline: CropOutline,
        cropRect: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
        imageToCrop: ImageBitmap,
    ) {

        when (cropOutline) {
            is CropShape -> {

                val path = Path().apply {
                    val outline =
                        cropOutline.shape.createOutline(cropRect.size, layoutDirection, density)
                    addOutline(outline)
                }

                Canvas(image = imageToCrop).run {
                    saveLayer(nativeCanvas.clipBounds.toComposeRect(), imagePaint)

                    // Destination
                    drawPath(path, paint)

                    // Source
                    drawImage(
                        image = imageToCrop,
                        topLeftOffset = Offset.Zero,
                        paint = imagePaint
                    )
                    restore()
                }
            }

            is CropPath -> {

                val path = Path().apply {

                    addPath(cropOutline.path)

                    val pathSize = getBounds().size
                    val rectSize = cropRect.size

                    val matrix = android.graphics.Matrix()
                    matrix.postScale(
                        rectSize.width / pathSize.width,
                        cropRect.height / pathSize.height
                    )
                    this.asAndroidPath().transform(matrix)

                    val left = getBounds().left
                    val top = getBounds().top

                    translate(Offset(-left, -top))
                }

                Canvas(image = imageToCrop).run {
                    saveLayer(nativeCanvas.clipBounds.toComposeRect(), imagePaint)

                    // Destination
                    drawPath(path, paint)

                    // Source
                    drawImage(image = imageToCrop, topLeftOffset = Offset.Zero, imagePaint)
                    restore()
                }
            }

            is CropImageMask -> {

                val imageMask = Bitmap.createScaledBitmap(
                    cropOutline.image.asAndroidBitmap(),
                    cropRect.width.toInt(),
                    cropRect.height.toInt(),
                    true
                ).asImageBitmap()

                Canvas(image = imageToCrop).run {
                    saveLayer(nativeCanvas.clipBounds.toComposeRect(), imagePaint)

                    // Destination
                    drawImage(imageMask, topLeftOffset = Offset.Zero, paint)

                    // Source
                    drawImage(image = imageToCrop, topLeftOffset = Offset.Zero, imagePaint)

                    restore()
                }
            }
        }
    }
}

