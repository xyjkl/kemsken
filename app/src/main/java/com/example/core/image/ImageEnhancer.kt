package com.example.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.example.domain.model.FilterType
import kotlin.math.max
import kotlin.math.min

object ImageEnhancer {

    /**
     * Applies the selected filter preset along with fine-tuned brightness, contrast,
     * saturation, and sharpness adjustments. Non-destructive, returns a new Bitmap.
     */
    fun enhance(
        source: Bitmap,
        filterType: FilterType,
        brightness: Float = 0.0f,
        contrast: Float = 1.0f,
        saturation: Float = 1.0f,
        sharpness: Float = 0.0f
    ): Bitmap {
        val width = source.width
        val height = source.height

        // For filters that benefit from pixel-level adaptive thresholding
        when (filterType) {
            FilterType.BLACK_AND_WHITE -> return applyAdaptiveThreshold(source, brightness, contrast)
            FilterType.RECEIPT -> return applyReceiptFilter(source)
            FilterType.WHITEBOARD -> return applyWhiteboardFilter(source)
            FilterType.SHARPEN -> return applySharpenKernel(source, sharpness.coerceAtLeast(0.6f))
            else -> { /* handled via color matrices and hardware canvas */ }
        }

        val colorMatrix = ColorMatrix()

        when (filterType) {
            FilterType.ORIGINAL -> {
                // Identity
            }
            FilterType.AUTO_ENHANCE -> {
                // Background brightening + contrast boost
                val cm = ColorMatrix(floatArrayOf(
                    1.25f, 0f, 0f, 0f, 15f,
                    0f, 1.25f, 0f, 0f, 15f,
                    0f, 0f, 1.25f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(cm)
            }
            FilterType.MAGIC_COLOR -> {
                // Boost saturation + high key white point
                val satMatrix = ColorMatrix().apply { setSaturation(1.35f) }
                val boostMatrix = ColorMatrix(floatArrayOf(
                    1.28f, 0f, 0f, 0f, 20f,
                    0f, 1.28f, 0f, 0f, 20f,
                    0f, 0f, 1.28f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(satMatrix)
                colorMatrix.postConcat(boostMatrix)
            }
            FilterType.GRAYSCALE -> {
                colorMatrix.setSaturation(0.0f)
                val boost = ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 10f,
                    0f, 1.15f, 0f, 0f, 10f,
                    0f, 0f, 1.15f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(boost)
            }
            FilterType.HIGH_CONTRAST -> {
                val scale = 1.6f
                val translate = (-0.5f * scale + 0.5f) * 255f
                val cm = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(cm)
            }
            FilterType.BRIGHTEN -> {
                val cm = ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 40f,
                    0f, 1.15f, 0f, 0f, 40f,
                    0f, 0f, 1.15f, 0f, 40f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(cm)
            }
            else -> {}
        }

        // Apply manual adjustments if non-default
        if (brightness != 0f) {
            val bOffset = brightness * 100f
            val bMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, bOffset,
                0f, 1f, 0f, 0f, bOffset,
                0f, 0f, 1f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(bMatrix)
        }

        if (contrast != 1f) {
            val scale = contrast
            val translate = (-0.5f * scale + 0.5f) * 255f
            val cMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(cMatrix)
        }

        if (saturation != 1f && filterType != FilterType.GRAYSCALE) {
            val sMatrix = ColorMatrix().apply { setSaturation(saturation) }
            colorMatrix.postConcat(sMatrix)
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        return if (sharpness > 0.1f) applySharpenKernel(output, sharpness) else output
    }

    /**
     * Adaptive thresholding (Bradley-Roth style local window) for crisp document binarization.
     */
    private fun applyAdaptiveThreshold(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Integral image for fast local average
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var sum = 0L
            for (x in 0 until width) {
                val idx = y * width + x
                sum += gray[idx]
                integral[idx] = if (y == 0) sum else integral[(y - 1) * width + x] + sum
            }
        }

        // Window size ~ 1/16th of width
        val s = max(16, width / 16)
        val sHalf = s / 2
        val t = 0.14f * (1f - brightness * 0.2f) // percentage factor

        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            val y1 = max(0, y - sHalf)
            val y2 = min(height - 1, y + sHalf)
            for (x in 0 until width) {
                val x1 = max(0, x - sHalf)
                val x2 = min(width - 1, x + sHalf)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val idx = y * width + x
                val a = integral[y2 * width + x2]
                val b = if (y1 > 0) integral[(y1 - 1) * width + x2] else 0L
                val c = if (x1 > 0) integral[y2 * width + (x1 - 1)] else 0L
                val d = if (x1 > 0 && y1 > 0) integral[(y1 - 1) * width + (x1 - 1)] else 0L
                val sum = a - b - c + d

                val localMean = sum / count
                val currentGray = gray[idx]

                // If pixel is darker than local average by t percent, it's text (black), else background (white)
                outPixels[idx] = if (currentGray.toLong() * 100 < localMean * (100 - (t * 100).toInt())) {
                    0xFF000000.toInt() // crisp black ink
                } else {
                    0xFFFFFFFF.toInt() // pure white background
                }
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Whiteboard filter: cleans marker smudges and glare while saturating colors.
     */
    private fun applyWhiteboardFilter(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            if (luminance > 185) {
                // Background glare -> clean pure white
                outPixels[i] = 0xFFFFFFFF.toInt()
            } else {
                // Saturate colored marker strokes
                val newR = min(255, (r * 1.3).toInt())
                val newG = min(255, (g * 1.3).toInt())
                val newB = min(255, (b * 1.3).toInt())
                outPixels[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Receipt filter: maximizes contrast on faint thermal paper.
     */
    private fun applyReceiptFilter(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            // Steep contrast curve
            val v = if (gray < 140) max(0, (gray * 0.7).toInt()) else 255
            outPixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Sharpening convolution filter to enhance fine document text.
     */
    private fun applySharpenKernel(source: Bitmap, amount: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val outPixels = IntArray(width * height)

        val centerWeight = 1f + 4f * amount
        val edgeWeight = -amount

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                val cCenter = pixels[idx]
                val cTop = pixels[idx - width]
                val cBottom = pixels[idx + width]
                val cLeft = pixels[idx - 1]
                val cRight = pixels[idx + 1]

                val r = ((((cCenter shr 16) and 0xFF) * centerWeight) +
                        (((cTop shr 16) and 0xFF) * edgeWeight) +
                        (((cBottom shr 16) and 0xFF) * edgeWeight) +
                        (((cLeft shr 16) and 0xFF) * edgeWeight) +
                        (((cRight shr 16) and 0xFF) * edgeWeight)).toInt().coerceIn(0, 255)

                val g = ((((cCenter shr 8) and 0xFF) * centerWeight) +
                        (((cTop shr 8) and 0xFF) * edgeWeight) +
                        (((cBottom shr 8) and 0xFF) * edgeWeight) +
                        (((cLeft shr 8) and 0xFF) * edgeWeight) +
                        (((cRight shr 8) and 0xFF) * edgeWeight)).toInt().coerceIn(0, 255)

                val b = (((cCenter and 0xFF) * centerWeight) +
                        ((cTop and 0xFF) * edgeWeight) +
                        ((cBottom and 0xFF) * edgeWeight) +
                        ((cLeft and 0xFF) * edgeWeight) +
                        ((cRight and 0xFF) * edgeWeight)).toInt().coerceIn(0, 255)

                outPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }
}
