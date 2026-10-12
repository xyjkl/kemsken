package com.example.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.example.domain.model.DocumentCorners
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PerspectiveTransformer {

    /**
     * Warps the four corners of a document into a flat rectangular bitmap.
     * Uses hardware-accelerated projective transformation (Matrix.setPolyToPoly).
     */
    fun transform(source: Bitmap, corners: DocumentCorners): Bitmap {
        val tl = corners.topLeft
        val tr = corners.topRight
        val br = corners.bottomRight
        val bl = corners.bottomLeft

        // Compute prospective width and height
        val topWidth = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
        val bottomWidth = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).toFloat()
        val targetWidth = max(topWidth, bottomWidth).roundToInt().coerceAtLeast(100)

        val leftHeight = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
        val rightHeight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble()).toFloat()
        val targetHeight = max(leftHeight, rightHeight).roundToInt().coerceAtLeast(100)

        // Source points in order: TL, TR, BR, BL
        val srcPts = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        // Target rectangular points in order: TL, TR, BR, BL
        val dstPts = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint().apply {
            isFilterBitmap = true
            isDither = true
            isAntiAlias = true
        }

        if (success) {
            canvas.drawBitmap(source, matrix, paint)
        } else {
            // Fallback: draw directly if degenerate polygon
            canvas.drawBitmap(source, 0f, 0f, paint)
        }

        return outputBitmap
    }

    /**
     * Rotates a bitmap by a specified angle (90, 180, 270 degrees).
     */
    fun rotate(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
