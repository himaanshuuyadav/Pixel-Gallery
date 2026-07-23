package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments

import android.graphics.Bitmap
import android.graphics.RectF
import com.prantiux.pixelgallery.domain.model.editor.Adjustment

/**
 * Crop adjustment that stores a normalized crop rect (0-1 range) and rotation.
 * Also stores UI state variables (zoom, panX, panY, aspect ratio) so the crop
 * can be re-edited later if the user returns to the crop scrubber.
 *
 * [apply] crops [bitmap] using the normalized rect and rotation, producing a full-resolution result
 * regardless of the preview bitmap size used during editing.
 */
data class Crop(
    val normalizedRect: RectF,
    val rotation: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val aspectRatioValue: Float = -1f // -1f denotes AspectRatio.Original
): Adjustment {

    override fun apply(bitmap: Bitmap): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        if (rotation == 0f) {
            val x = (normalizedRect.left * w).toInt().coerceIn(0, bitmap.width)
            val y = (normalizedRect.top * h).toInt().coerceIn(0, bitmap.height)
            val cropW = ((normalizedRect.right - normalizedRect.left) * w).toInt()
                .coerceIn(1, bitmap.width - x)
            val cropH = ((normalizedRect.bottom - normalizedRect.top) * h).toInt()
                .coerceIn(1, bitmap.height - y)
            return Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
        }

        // 1. Calculate zoom scale S for rotation
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

        // 2. We have normalizedRect which gives us the unscaled, unrotated crop region!
        val cropRectL = normalizedRect.left * w
        val cropRectT = normalizedRect.top * h
        val cropRectR = normalizedRect.right * w
        val cropRectB = normalizedRect.bottom * h
        val cropW = (cropRectR - cropRectL).toInt()
        val cropH = (cropRectB - cropRectT).toInt()
        
        if (cropW <= 0 || cropH <= 0) return bitmap

        // 3. Create the destination bitmap
        val croppedBitmap = Bitmap.createBitmap(
            cropW,
            cropH,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(croppedBitmap)
        val matrix = android.graphics.Matrix()

        val pivotX = w / 2f
        val pivotY = h / 2f

        matrix.postScale(s, s, pivotX, pivotY)
        matrix.postRotate(rotation, pivotX, pivotY)
        matrix.postTranslate(-cropRectL, -cropRectT)

        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        canvas.drawBitmap(bitmap, matrix, paint)

        return croppedBitmap
    }

}
