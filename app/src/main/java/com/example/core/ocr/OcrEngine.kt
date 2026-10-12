package com.example.core.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<String>
)

data class OcrScanResult(
    val fullText: String,
    val language: String,
    val blocks: List<OcrBlock>,
    val lineCount: Int
)

class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): OcrScanResult =
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText: Text ->
                    val blocks = visionText.textBlocks.map { block ->
                        OcrBlock(
                            text = block.text,
                            boundingBox = block.boundingBox,
                            lines = block.lines.map { it.text }
                        )
                    }

                    val detectedLang = visionText.textBlocks.firstOrNull()?.recognizedLanguage ?: "id"
                    val lineCount = visionText.textBlocks.sumOf { it.lines.size }

                    continuation.resume(
                        OcrScanResult(
                            fullText = visionText.text,
                            language = detectedLang,
                            blocks = blocks,
                            lineCount = lineCount
                        )
                    )
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

    fun close() {
        recognizer.close()
    }
}
