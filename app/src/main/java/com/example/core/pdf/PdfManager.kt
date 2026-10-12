package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import com.example.core.database.OcrEntity
import com.example.core.database.PageEntity
import com.example.core.storage.FileManager
import com.example.domain.model.PdfPageSize
import com.example.domain.model.PdfQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class PdfManager(
    private val context: Context,
    private val fileManager: FileManager
) {

    /**
     * Exports a document's pages to a PDF file with configurable page size, quality,
     * and optional searchable OCR text layer.
     */
    suspend fun exportToPdf(
        pages: List<PageEntity>,
        ocrResults: Map<String, OcrEntity>,
        outputStream: OutputStream,
        pageSize: PdfPageSize = PdfPageSize.A4,
        quality: PdfQuality = PdfQuality.STANDARD,
        isSearchable: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        try {
            for ((index, page) in pages.withIndex()) {
                val bitmap = fileManager.loadBitmap(page.processedImagePath, quality.maxDimension)
                    ?: continue

                // Determine PDF page dimensions in points (72 points = 1 inch)
                val targetWidthPt: Int
                val targetHeightPt: Int

                if (pageSize == PdfPageSize.AUTO) {
                    targetWidthPt = (bitmap.width * 72f / 150f).toInt().coerceAtLeast(100)
                    targetHeightPt = (bitmap.height * 72f / 150f).toInt().coerceAtLeast(100)
                } else {
                    targetWidthPt = pageSize.widthPt
                    targetHeightPt = pageSize.heightPt
                }

                val pageInfo = PdfDocument.PageInfo.Builder(targetWidthPt, targetHeightPt, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // Fit and center bitmap within PDF page with margins
                val margin = if (pageSize == PdfPageSize.AUTO) 0f else 20f
                val availableWidth = targetWidthPt - (margin * 2)
                val availableHeight = targetHeightPt - (margin * 2)

                val scale = minOf(
                    availableWidth / bitmap.width.toFloat(),
                    availableHeight / bitmap.height.toFloat()
                )

                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                val left = margin + (availableWidth - scaledWidth) / 2f
                val top = margin + (availableHeight - scaledHeight) / 2f

                val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                val paint = Paint().apply {
                    isFilterBitmap = true
                    isAntiAlias = true
                    isDither = true
                }

                // Draw background white
                canvas.drawColor(Color.WHITE)

                // Draw page image
                canvas.drawBitmap(bitmap, null, destRect, paint)

                // If searchable PDF requested, draw invisible selectable text layer from OCR
                if (isSearchable) {
                    val ocr = ocrResults[page.id]
                    if (ocr != null && ocr.recognizedText.isNotBlank()) {
                        drawInvisibleTextLayer(canvas, ocr.recognizedText, destRect, bitmap.width, bitmap.height)
                    }
                }

                pdfDocument.finishPage(pdfPage)
                bitmap.recycle()
            }

            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Draws transparent selectable text on top of the document image for full PDF searchability.
     */
    private fun drawInvisibleTextLayer(
        canvas: Canvas,
        text: String,
        destRect: RectF,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        val textPaint = Paint().apply {
            color = Color.TRANSPARENT // invisible text layer
            textSize = 10f
            isAntiAlias = true
        }

        // Draw line by line evenly distributed or bounding boxes
        val lines = text.lines()
        if (lines.isEmpty()) return

        val lineHeight = destRect.height() / lines.size.coerceAtLeast(1)
        var yPos = destRect.top + lineHeight * 0.8f

        for (line in lines) {
            if (line.isNotBlank()) {
                canvas.drawText(line, destRect.left, yPos, textPaint)
            }
            yPos += lineHeight
        }
    }

    /**
     * Renders a preview bitmap from a generated PDF file using native PdfRenderer.
     */
    fun renderPdfPagePreview(pdfFile: File, pageIndex: Int = 0): Bitmap? {
        if (!pdfFile.exists()) return null
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return null
            }
            val page = renderer.openPage(pageIndex)
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Prints a PDF document using the Android Print Framework.
     */
    fun printDocument(pdfFile: File, documentTitle: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = PdfPrintDocumentAdapter(pdfFile, documentTitle)
        printManager.print(documentTitle, printAdapter, PrintAttributes.Builder().build())
    }
}
