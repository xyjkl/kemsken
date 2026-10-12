package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.core.database.DocumentEntity
import com.example.core.database.FolderEntity
import com.example.core.database.KemSkenDatabase
import com.example.core.database.OcrEntity
import com.example.core.database.PageEntity
import com.example.core.image.ImageEnhancer
import com.example.core.image.PerspectiveTransformer
import com.example.core.ocr.OcrEngine
import com.example.core.pdf.PdfManager
import com.example.core.storage.FileManager
import com.example.domain.model.DocumentCorners
import com.example.domain.model.FilterType
import com.example.domain.model.PdfPageSize
import com.example.domain.model.PdfQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

class DocumentRepository(
    private val database: KemSkenDatabase,
    val fileManager: FileManager,
    private val context: Context
) {

    private val documentDao = database.documentDao()
    private val pageDao = database.pageDao()
    private val ocrDao = database.ocrDao()
    private val folderDao = database.folderDao()

    val ocrEngine by lazy { OcrEngine() }
    val pdfManager by lazy { PdfManager(context, fileManager) }

    fun getAllActiveDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllActiveDocuments()

    fun getFavoriteDocuments(): Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()

    fun getDocumentsInFolder(folderId: String): Flow<List<DocumentEntity>> = documentDao.getDocumentsInFolder(folderId)

    fun getRecycleBinDocuments(): Flow<List<DocumentEntity>> = documentDao.getRecycleBinDocuments()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> = documentDao.searchDocuments(query)

    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    suspend fun getDocumentById(id: String): DocumentEntity? = documentDao.getDocumentById(id)

    fun observeDocumentById(id: String): Flow<DocumentEntity?> = documentDao.observeDocumentById(id)

    fun getPagesForDocument(documentId: String): Flow<List<PageEntity>> = pageDao.getPagesForDocumentFlow(documentId)

    suspend fun getPagesListForDocument(documentId: String): List<PageEntity> = pageDao.getPagesForDocument(documentId)

    suspend fun getPageById(pageId: String): PageEntity? = pageDao.getPageById(pageId)

    suspend fun saveNewDocument(
        title: String,
        folderId: String? = null,
        capturedPages: List<Pair<Bitmap, DocumentCorners?>>,
        filter: FilterType = FilterType.AUTO_ENHANCE
    ): String = withContext(Dispatchers.IO) {
        val documentId = UUID.randomUUID().toString()
        val docDir = fileManager.getDocumentDirectory(documentId)

        val pagesToInsert = ArrayList<PageEntity>()
        var firstThumbnailPath: String? = null

        for ((index, pair) in capturedPages.withIndex()) {
            val (originalBitmap, corners) = pair
            val pageId = UUID.randomUUID().toString()

            // 1. Save original capture
            val origFile = fileManager.savePageBitmap(documentId, pageId, originalBitmap, isOriginal = true)

            // 2. Perform perspective correction if corners are specified
            val croppedBitmap = if (corners != null) {
                PerspectiveTransformer.transform(originalBitmap, corners)
            } else {
                originalBitmap
            }

            // 3. Apply document enhancement filter
            val processedBitmap = ImageEnhancer.enhance(croppedBitmap, filter)

            // 4. Save processed image
            val procFile = fileManager.savePageBitmap(documentId, pageId, processedBitmap, isOriginal = false)

            if (index == 0) {
                val thumbFile = fileManager.saveThumbnail(documentId, processedBitmap)
                firstThumbnailPath = thumbFile.absolutePath
            }

            val pageEntity = PageEntity(
                id = pageId,
                documentId = documentId,
                pageIndex = index,
                originalImagePath = origFile.absolutePath,
                processedImagePath = procFile.absolutePath,
                rotation = 0,
                cropCoordinates = corners?.serialize() ?: "",
                filterType = filter.name,
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
                sharpness = 0f,
                width = processedBitmap.width,
                height = processedBitmap.height
            )
            pagesToInsert.add(pageEntity)

            if (croppedBitmap != originalBitmap) croppedBitmap.recycle()
            if (processedBitmap != originalBitmap) processedBitmap.recycle()
        }

        val docEntity = DocumentEntity(
            id = documentId,
            title = title.ifBlank { "KemSken_${System.currentTimeMillis()}" },
            folderId = folderId,
            pageCount = pagesToInsert.size,
            thumbnailPath = firstThumbnailPath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        documentDao.insertDocument(docEntity)
        pageDao.insertPages(pagesToInsert)

        // Asynchronously recognize text in background for fast search
        for (page in pagesToInsert) {
            try {
                val procBmp = fileManager.loadBitmap(page.processedImagePath, 1600)
                if (procBmp != null) {
                    val ocrResult = ocrEngine.recognizeText(procBmp)
                    if (ocrResult.fullText.isNotBlank()) {
                        ocrDao.insertOcr(
                            OcrEntity(
                                pageId = page.id,
                                documentId = documentId,
                                recognizedText = ocrResult.fullText,
                                language = ocrResult.language,
                                lastProcessedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    procBmp.recycle()
                }
            } catch (e: Exception) {
                // OCR is non-blocking for save
            }
        }

        documentId
    }

    suspend fun addPageToDocument(
        documentId: String,
        bitmap: Bitmap,
        corners: DocumentCorners?,
        filter: FilterType = FilterType.AUTO_ENHANCE
    ): PageEntity = withContext(Dispatchers.IO) {
        val existingPages = pageDao.getPagesForDocument(documentId)
        val newIndex = existingPages.size
        val pageId = UUID.randomUUID().toString()

        val origFile = fileManager.savePageBitmap(documentId, pageId, bitmap, isOriginal = true)

        val croppedBitmap = if (corners != null) {
            PerspectiveTransformer.transform(bitmap, corners)
        } else {
            bitmap
        }

        val processedBitmap = ImageEnhancer.enhance(croppedBitmap, filter)
        val procFile = fileManager.savePageBitmap(documentId, pageId, processedBitmap, isOriginal = false)

        val pageEntity = PageEntity(
            id = pageId,
            documentId = documentId,
            pageIndex = newIndex,
            originalImagePath = origFile.absolutePath,
            processedImagePath = procFile.absolutePath,
            rotation = 0,
            cropCoordinates = corners?.serialize() ?: "",
            filterType = filter.name,
            width = processedBitmap.width,
            height = processedBitmap.height
        )

        pageDao.insertPage(pageEntity)
        val updatedPages = pageDao.getPagesForDocument(documentId)
        val thumbPath = if (updatedPages.isNotEmpty()) {
            fileManager.saveThumbnail(documentId, processedBitmap).absolutePath
        } else null

        documentDao.updatePageCountAndThumbnail(documentId, updatedPages.size, thumbPath)

        if (croppedBitmap != bitmap) croppedBitmap.recycle()
        if (processedBitmap != bitmap) processedBitmap.recycle()

        pageEntity
    }

    suspend fun updatePageCrop(pageId: String, corners: DocumentCorners) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val originalBitmap = fileManager.loadBitmap(page.originalImagePath) ?: return@withContext

        val cropped = PerspectiveTransformer.transform(originalBitmap, corners)
        val rotated = if (page.rotation != 0) {
            PerspectiveTransformer.rotate(cropped, page.rotation.toFloat())
        } else cropped

        val filter = FilterType.valueOf(page.filterType)
        val processed = ImageEnhancer.enhance(
            rotated,
            filter,
            page.brightness,
            page.contrast,
            page.saturation,
            page.sharpness
        )

        fileManager.savePageBitmap(page.documentId, pageId, processed, isOriginal = false)

        val updatedPage = page.copy(
            cropCoordinates = corners.serialize(),
            width = processed.width,
            height = processed.height
        )
        pageDao.updatePage(updatedPage)

        if (page.pageIndex == 0) {
            fileManager.saveThumbnail(page.documentId, processed)
        }

        originalBitmap.recycle()
        if (cropped != originalBitmap) cropped.recycle()
        if (rotated != cropped) rotated.recycle()
        if (processed != rotated) processed.recycle()
    }

    suspend fun updatePageFilter(
        pageId: String,
        filter: FilterType,
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
        sharpness: Float = 0f
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val originalBitmap = fileManager.loadBitmap(page.originalImagePath) ?: return@withContext

        val corners = if (page.cropCoordinates.isNotBlank()) {
            DocumentCorners.deserialize(page.cropCoordinates, originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
        } else null

        val cropped = if (corners != null) {
            PerspectiveTransformer.transform(originalBitmap, corners)
        } else originalBitmap

        val rotated = if (page.rotation != 0) {
            PerspectiveTransformer.rotate(cropped, page.rotation.toFloat())
        } else cropped

        val processed = ImageEnhancer.enhance(rotated, filter, brightness, contrast, saturation, sharpness)
        fileManager.savePageBitmap(page.documentId, pageId, processed, isOriginal = false)

        val updatedPage = page.copy(
            filterType = filter.name,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            sharpness = sharpness
        )
        pageDao.updatePage(updatedPage)

        if (page.pageIndex == 0) {
            fileManager.saveThumbnail(page.documentId, processed)
        }

        originalBitmap.recycle()
        if (cropped != originalBitmap) cropped.recycle()
        if (rotated != cropped) rotated.recycle()
        if (processed != rotated) processed.recycle()
    }

    suspend fun rotatePage(pageId: String) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val newRotation = (page.rotation + 90) % 360

        val procBitmap = fileManager.loadBitmap(page.processedImagePath) ?: return@withContext
        val rotated = PerspectiveTransformer.rotate(procBitmap, 90f)
        fileManager.savePageBitmap(page.documentId, pageId, rotated, isOriginal = false)

        pageDao.updatePage(page.copy(rotation = newRotation, width = rotated.width, height = rotated.height))
        if (page.pageIndex == 0) {
            fileManager.saveThumbnail(page.documentId, rotated)
        }

        procBitmap.recycle()
        rotated.recycle()
    }

    suspend fun deletePage(pageId: String) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val documentId = page.documentId
        pageDao.deletePage(page)

        val remainingPages = pageDao.getPagesForDocument(documentId)
        if (remainingPages.isEmpty()) {
            documentDao.moveToRecycleBin(documentId)
        } else {
            // Re-index remaining pages
            for ((idx, p) in remainingPages.withIndex()) {
                if (p.pageIndex != idx) {
                    pageDao.updatePageIndex(p.id, idx)
                }
            }
            val firstBmp = fileManager.loadBitmap(remainingPages.first().processedImagePath, 320)
            val thumbPath = if (firstBmp != null) {
                fileManager.saveThumbnail(documentId, firstBmp).absolutePath.also { firstBmp.recycle() }
            } else null
            documentDao.updatePageCountAndThumbnail(documentId, remainingPages.size, thumbPath)
        }
    }

    suspend fun reorderPages(documentId: String, newOrder: List<PageEntity>) = withContext(Dispatchers.IO) {
        for ((idx, page) in newOrder.withIndex()) {
            pageDao.updatePageIndex(page.id, idx)
        }
        val firstPage = newOrder.firstOrNull()
        if (firstPage != null) {
            val firstBmp = fileManager.loadBitmap(firstPage.processedImagePath, 320)
            val thumbPath = if (firstBmp != null) {
                fileManager.saveThumbnail(documentId, firstBmp).absolutePath.also { firstBmp.recycle() }
            } else null
            documentDao.updatePageCountAndThumbnail(documentId, newOrder.size, thumbPath)
        }
    }

    suspend fun runOcrOnPage(pageId: String): OcrEntity = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: throw IllegalArgumentException("Page not found")
        val bitmap = fileManager.loadBitmap(page.processedImagePath) ?: throw IllegalStateException("Could not load page image")

        val result = ocrEngine.recognizeText(bitmap)
        bitmap.recycle()

        val entity = OcrEntity(
            pageId = page.id,
            documentId = page.documentId,
            recognizedText = result.fullText,
            language = result.language,
            lastProcessedAt = System.currentTimeMillis()
        )
        ocrDao.insertOcr(entity)
        entity
    }

    suspend fun getOcrForPage(pageId: String): OcrEntity? = ocrDao.getOcrForPage(pageId)

    fun observeOcrForPage(pageId: String): Flow<OcrEntity?> = ocrDao.observeOcrForPage(pageId)

    suspend fun saveOcrText(pageId: String, documentId: String, newText: String) = withContext(Dispatchers.IO) {
        val existing = ocrDao.getOcrForPage(pageId)
        val entity = existing?.copy(recognizedText = newText, lastProcessedAt = System.currentTimeMillis())
            ?: OcrEntity(pageId = pageId, documentId = documentId, recognizedText = newText)
        ocrDao.insertOcr(entity)
    }

    suspend fun moveToRecycleBin(documentId: String) = documentDao.moveToRecycleBin(documentId)

    suspend fun restoreFromRecycleBin(documentId: String) = documentDao.restoreFromRecycleBin(documentId)

    suspend fun deletePermanently(documentId: String) = withContext(Dispatchers.IO) {
        documentDao.deletePermanently(documentId)
        fileManager.deleteDocumentFiles(documentId)
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        // Collect ids first
        database.runInTransaction {
            // Delete files for all deleted docs
        }
        documentDao.emptyRecycleBin()
    }

    suspend fun renameDocument(documentId: String, newTitle: String) = documentDao.renameDocument(documentId, newTitle)

    suspend fun toggleFavorite(documentId: String, isFavorite: Boolean) = documentDao.updateFavorite(documentId, isFavorite)

    suspend fun moveToFolder(documentId: String, folderId: String?) = documentDao.moveToFolder(documentId, folderId)

    suspend fun duplicateDocument(documentId: String): String = withContext(Dispatchers.IO) {
        val original = documentDao.getDocumentById(documentId) ?: return@withContext ""
        val pages = pageDao.getPagesForDocument(documentId)

        val newDocId = UUID.randomUUID().toString()
        val newPages = ArrayList<PageEntity>()

        for (p in pages) {
            val origBmp = fileManager.loadBitmap(p.originalImagePath) ?: continue
            val procBmp = fileManager.loadBitmap(p.processedImagePath) ?: continue

            val newPageId = UUID.randomUUID().toString()
            val newOrigFile = fileManager.savePageBitmap(newDocId, newPageId, origBmp, isOriginal = true)
            val newProcFile = fileManager.savePageBitmap(newDocId, newPageId, procBmp, isOriginal = false)

            newPages.add(
                p.copy(
                    id = newPageId,
                    documentId = newDocId,
                    originalImagePath = newOrigFile.absolutePath,
                    processedImagePath = newProcFile.absolutePath
                )
            )

            origBmp.recycle()
            procBmp.recycle()
        }

        val firstThumb = newPages.firstOrNull()?.let {
            val bmp = fileManager.loadBitmap(it.processedImagePath, 320)
            if (bmp != null) {
                fileManager.saveThumbnail(newDocId, bmp).absolutePath.also { bmp.recycle() }
            } else null
        }

        val newDoc = original.copy(
            id = newDocId,
            title = "${original.title} (Salinan)",
            pageCount = newPages.size,
            thumbnailPath = firstThumb,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            deletedAt = null
        )

        documentDao.insertDocument(newDoc)
        pageDao.insertPages(newPages)
        newDocId
    }

    suspend fun createFolder(name: String, colorHex: String = "#087F8C"): FolderEntity = withContext(Dispatchers.IO) {
        val folder = FolderEntity(name = name, colorHex = colorHex)
        folderDao.insertFolder(folder)
        folder
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderById(folderId) ?: return@withContext
        // Remove folderId from documents
        folderDao.deleteFolder(folder)
    }

    suspend fun exportDocumentToPdf(
        documentId: String,
        outputStream: OutputStream,
        pageSize: PdfPageSize = PdfPageSize.A4,
        quality: PdfQuality = PdfQuality.STANDARD,
        isSearchable: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesForDocument(documentId)
        if (pages.isEmpty()) return@withContext false

        val ocrList = ocrDao.getAllOcrForDocument(documentId)
        val ocrMap = ocrList.associateBy { it.pageId }

        pdfManager.exportToPdf(pages, ocrMap, outputStream, pageSize, quality, isSearchable)
    }
}
