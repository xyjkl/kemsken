package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.domain.model.DocumentCorners
import kotlin.math.hypot

@Composable
fun CropHandlesCanvas(
    bitmap: Bitmap,
    corners: DocumentCorners,
    onCornersChanged: (DocumentCorners) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(corners, bitmap) {
                    val touchRadius = 60f // touch hit threshold in screen px

                    detectDragGestures(
                        onDragStart = { offset ->
                            val scaleX = size.width.toFloat() / bitmap.width.toFloat()
                            val scaleY = size.height.toFloat() / bitmap.height.toFloat()
                            val scale = minOf(scaleX, scaleY)

                            val imgDisplayWidth = bitmap.width * scale
                            val imgDisplayHeight = bitmap.height * scale
                            val offsetX = (size.width - imgDisplayWidth) / 2f
                            val offsetY = (size.height - imgDisplayHeight) / 2f

                            val screenCorners = corners.toList().map { pt ->
                                Offset(offsetX + pt.x * scale, offsetY + pt.y * scale)
                            }

                            // Find nearest corner
                            var minDistance = Float.MAX_VALUE
                            var nearestIndex: Int? = null
                            for (i in screenCorners.indices) {
                                val dist = hypot(offset.x - screenCorners[i].x, offset.y - screenCorners[i].y)
                                if (dist < touchRadius && dist < minDistance) {
                                    minDistance = dist
                                    nearestIndex = i
                                }
                            }
                            activeCornerIndex = nearestIndex
                        },
                        onDragEnd = {
                            activeCornerIndex = null
                        },
                        onDragCancel = {
                            activeCornerIndex = null
                        },
                        onDrag = { change, dragAmount ->
                            val idx = activeCornerIndex ?: return@detectDragGestures
                            change.consume()

                            val scaleX = size.width.toFloat() / bitmap.width.toFloat()
                            val scaleY = size.height.toFloat() / bitmap.height.toFloat()
                            val scale = minOf(scaleX, scaleY)

                            val currentPoints = corners.toList().toMutableList()
                            val cur = currentPoints[idx]

                            val newX = (cur.x + dragAmount.x / scale).coerceIn(0f, bitmap.width.toFloat())
                            val newY = (cur.y + dragAmount.y / scale).coerceIn(0f, bitmap.height.toFloat())
                            currentPoints[idx] = PointF(newX, newY)

                            onCornersChanged(
                                DocumentCorners(
                                    topLeft = currentPoints[0],
                                    topRight = currentPoints[1],
                                    bottomRight = currentPoints[2],
                                    bottomLeft = currentPoints[3]
                                )
                            )
                        }
                    )
                }
        ) {
            val scaleX = size.width / bitmap.width.toFloat()
            val scaleY = size.height / bitmap.height.toFloat()
            val scale = minOf(scaleX, scaleY)

            val imgDisplayWidth = bitmap.width * scale
            val imgDisplayHeight = bitmap.height * scale
            val offsetX = (size.width - imgDisplayWidth) / 2f
            val offsetY = (size.height - imgDisplayHeight) / 2f

            // 1. Draw image centered
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(imgDisplayWidth.toInt(), imgDisplayHeight.toInt())
            )

            // 2. Transform bitmap coordinates to screen canvas coordinates
            val tl = Offset(offsetX + corners.topLeft.x * scale, offsetY + corners.topLeft.y * scale)
            val tr = Offset(offsetX + corners.topRight.x * scale, offsetY + corners.topRight.y * scale)
            val br = Offset(offsetX + corners.bottomRight.x * scale, offsetY + corners.bottomRight.y * scale)
            val bl = Offset(offsetX + corners.bottomLeft.x * scale, offsetY + corners.bottomLeft.y * scale)

            // 3. Draw crop polygon
            val path = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            // Shaded border outside document
            drawPath(
                path = path,
                color = Color(0xFF3CCBC5),
                style = Stroke(width = 4f)
            )

            // Inner dashed guide lines (rule of thirds)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            drawLine(
                color = Color(0x80FFFFFF),
                start = Offset((tl.x * 2 + bl.x) / 3, (tl.y * 2 + bl.y) / 3),
                end = Offset((tr.x * 2 + br.x) / 3, (tr.y * 2 + br.y) / 3),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )
            drawLine(
                color = Color(0x80FFFFFF),
                start = Offset((tl.x + bl.x * 2) / 3, (tl.y + bl.y * 2) / 3),
                end = Offset((tr.x + br.x * 2) / 3, (tr.y + br.y * 2) / 3),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )

            // 4. Draw interactive handles
            val handleRadius = 24f
            val cornerPoints = listOf(tl, tr, br, bl)

            for ((i, pt) in cornerPoints.withIndex()) {
                val isSelected = activeCornerIndex == i
                // Outer circle
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) handleRadius * 1.3f else handleRadius,
                    center = pt
                )
                // Inner teal circle
                drawCircle(
                    color = Color(0xFF087F8C),
                    radius = if (isSelected) handleRadius * 0.9f else handleRadius * 0.7f,
                    center = pt
                )
            }
        }
    }
}
