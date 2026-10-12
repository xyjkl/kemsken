package com.example.core.image

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.domain.model.DocumentCorners
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class DocumentDetector {

    // Smoothed corners across frames for steady tracking
    private var lastValidCorners: DocumentCorners? = null
    private var consecutiveDetections = 0

    /**
     * Detects document boundaries in a bitmap (typically downsampled from camera preview).
     * Returns DocumentCorners in the coordinate system of the provided bitmap.
     */
    fun detect(bitmap: Bitmap): DocumentCorners? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 50 || height < 50) return null

        // Downsample to fast analysis grid (max 320 on long edge)
        val scale = min(1f, 320f / max(width, height).toFloat())
        val scaledWidth = (width * scale).toInt().coerceAtLeast(40)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(40)

        val scaledBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }

        val pixels = IntArray(scaledWidth * scaledHeight)
        scaledBitmap.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        // 1. Grayscale luminance array
        val gray = IntArray(scaledWidth * scaledHeight)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // 2. Sobel gradient magnitude
        val edges = ArrayList<PointF>()
        val threshold = 35 // gradient threshold for document border contrast

        for (y in 2 until scaledHeight - 2 step 2) {
            for (x in 2 until scaledWidth - 2 step 2) {
                val idx = y * scaledWidth + x

                // Sobel 3x3 kernels
                val gx = (-gray[idx - scaledWidth - 1] + gray[idx - scaledWidth + 1]
                        - 2 * gray[idx - 1] + 2 * gray[idx + 1]
                        - gray[idx + scaledWidth - 1] + gray[idx + scaledWidth + 1])

                val gy = (-gray[idx - scaledWidth - 1] - 2 * gray[idx - scaledWidth] - gray[idx - scaledWidth + 1]
                        + gray[idx + scaledWidth - 1] + 2 * gray[idx + scaledWidth] + gray[idx + scaledWidth + 1])

                val mag = hypot(gx.toDouble(), gy.toDouble()).toInt()
                if (mag > threshold) {
                    // Map back to original coordinate system
                    edges.add(PointF(x / scale, y / scale))
                }
            }
        }

        // If not enough edge points found, return smoothed or default
        if (edges.size < 40) {
            consecutiveDetections = max(0, consecutiveDetections - 1)
            return lastValidCorners ?: DocumentCorners.defaultInset(width.toFloat(), height.toFloat())
        }

        // 3. Compute 4 corner candidates using extreme projections (TL: min(x+y), TR: max(x-y), BR: max(x+y), BL: min(x-y))
        var minSum = Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE

        var tl = PointF(width * 0.08f, height * 0.08f)
        var tr = PointF(width * 0.92f, height * 0.08f)
        var br = PointF(width * 0.92f, height * 0.92f)
        var bl = PointF(width * 0.08f, height * 0.92f)

        for (pt in edges) {
            val sum = pt.x + pt.y
            val diff = pt.x - pt.y

            if (sum < minSum) {
                minSum = sum
                tl = pt
            }
            if (sum > maxSum) {
                maxSum = sum
                br = pt
            }
            if (diff > maxDiff) {
                maxDiff = diff
                tr = pt
            }
            if (diff < minDiff) {
                minDiff = diff
                bl = pt
            }
        }

        // 4. Validate geometry: check area ratio and reasonable distances
        val docArea = calculatePolygonArea(listOf(tl, tr, br, bl))
        val totalArea = width * height.toFloat()
        val areaRatio = docArea / totalArea

        if (areaRatio < 0.12f || areaRatio > 0.98f) {
            consecutiveDetections = max(0, consecutiveDetections - 1)
            return lastValidCorners ?: DocumentCorners.defaultInset(width.toFloat(), height.toFloat())
        }

        // Clamp corners strictly inside bitmap bounds
        val clampedCorners = DocumentCorners(
            topLeft = PointF(tl.x.coerceIn(0f, width.toFloat()), tl.y.coerceIn(0f, height.toFloat())),
            topRight = PointF(tr.x.coerceIn(0f, width.toFloat()), tr.y.coerceIn(0f, height.toFloat())),
            bottomRight = PointF(br.x.coerceIn(0f, width.toFloat()), br.y.coerceIn(0f, height.toFloat())),
            bottomLeft = PointF(bl.x.coerceIn(0f, width.toFloat()), bl.y.coerceIn(0f, height.toFloat()))
        )

        // 5. Exponential smoothing with previous frame to eliminate flicker
        val prev = lastValidCorners
        val smoothed = if (prev != null) {
            val alpha = 0.35f // 35% new, 65% previous
            DocumentCorners(
                topLeft = PointF(lerp(prev.topLeft.x, clampedCorners.topLeft.x, alpha), lerp(prev.topLeft.y, clampedCorners.topLeft.y, alpha)),
                topRight = PointF(lerp(prev.topRight.x, clampedCorners.topRight.x, alpha), lerp(prev.topRight.y, clampedCorners.topRight.y, alpha)),
                bottomRight = PointF(lerp(prev.bottomRight.x, clampedCorners.bottomRight.x, alpha), lerp(prev.bottomRight.y, clampedCorners.bottomRight.y, alpha)),
                bottomLeft = PointF(lerp(prev.bottomLeft.x, clampedCorners.bottomLeft.x, alpha), lerp(prev.bottomLeft.y, clampedCorners.bottomLeft.y, alpha))
            )
        } else {
            clampedCorners
        }

        lastValidCorners = smoothed
        consecutiveDetections++
        return smoothed
    }

    fun isStableForAutoCapture(): Boolean = consecutiveDetections >= 5

    fun reset() {
        lastValidCorners = null
        consecutiveDetections = 0
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun calculatePolygonArea(pts: List<PointF>): Float {
        var area = 0f
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return Math.abs(area) / 2f
    }
}
