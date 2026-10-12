package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

data class PathPoint(val offset: Offset, val isStart: Boolean = false)

@Composable
fun SignaturePadView(
    paths: MutableList<List<Offset>>,
    strokeWidth: Float = 6f,
    strokeColor: Color = Color(0xFF17252A),
    modifier: Modifier = Modifier
) {
    var currentPath = remember { mutableStateListOf<Offset>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPath.clear()
                        currentPath.add(offset)
                    },
                    onDragEnd = {
                        if (currentPath.isNotEmpty()) {
                            paths.add(currentPath.toList())
                            currentPath.clear()
                        }
                    },
                    onDragCancel = {
                        currentPath.clear()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPath.add(change.position)
                    }
                )
            }
    ) {
        // Draw completed strokes
        for (stroke in paths) {
            if (stroke.size > 1) {
                for (i in 0 until stroke.size - 1) {
                    drawLine(
                        color = strokeColor,
                        start = stroke[i],
                        end = stroke[i + 1],
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            } else if (stroke.size == 1) {
                drawCircle(
                    color = strokeColor,
                    radius = strokeWidth / 2f,
                    center = stroke[0]
                )
            }
        }

        // Draw current in-progress stroke
        if (currentPath.size > 1) {
            for (i in 0 until currentPath.size - 1) {
                drawLine(
                    color = strokeColor,
                    start = currentPath[i],
                    end = currentPath[i + 1],
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

fun renderSignatureToBitmap(paths: List<List<Offset>>, width: Int, height: Int, strokeWidth: Float = 6f): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(100), height.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint().apply {
        color = android.graphics.Color.BLACK
        this.strokeWidth = strokeWidth
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        isAntiAlias = true
    }

    for (stroke in paths) {
        if (stroke.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(stroke[0].x, stroke[0].y)
            for (i in 1 until stroke.size) {
                path.lineTo(stroke[i].x, stroke[i].y)
            }
            canvas.drawPath(path, paint)
        }
    }

    return bitmap
}
