package com.example.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

class FileManager(private val context: Context) {

    private val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    private val signaturesDir = File(context.filesDir, "signatures").apply { mkdirs() }

    fun getDocumentDirectory(documentId: String): File {
        return File(documentsDir, documentId).apply { mkdirs() }
    }

    fun getPagesDirectory(documentId: String): File {
        return File(getDocumentDirectory(documentId), "pages").apply { mkdirs() }
    }

    fun savePageBitmap(
        documentId: String,
        pageId: String,
        bitmap: Bitmap,
        isOriginal: Boolean,
        quality: Int = 90
    ): File {
        val dir = getPagesDirectory(documentId)
        val suffix = if (isOriginal) "_orig.jpg" else "_proc.jpg"
        val targetFile = File(dir, "$pageId$suffix")
        val tempFile = File(dir, "$pageId$suffix.tmp")

        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.flush()
        }
        if (targetFile.exists()) targetFile.delete()
        tempFile.renameTo(targetFile)
        return targetFile
    }

    fun saveThumbnail(documentId: String, bitmap: Bitmap): File {
        val dir = getDocumentDirectory(documentId)
        val targetFile = File(dir, "thumb.jpg")
        val thumb = createThumbnailBitmap(bitmap, 320, 320)
        FileOutputStream(targetFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.flush()
        }
        if (thumb != bitmap) thumb.recycle()
        return targetFile
    }

    fun loadBitmap(path: String, maxDimension: Int = 2048): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        val srcWidth = options.outWidth
        val srcHeight = options.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        val maxSrc = max(srcWidth, srcHeight)
        while (maxSrc / sampleSize > maxDimension * 1.5) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }

    fun loadBitmapFromUri(uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var sampleSize = 1
            val maxSrc = max(srcWidth, srcHeight)
            while (maxSrc / sampleSize > maxDimension * 1.5) {
                sampleSize *= 2
            }

            val decodeStream = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val result = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream?.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveSignature(bitmap: Bitmap): File {
        val file = File(signaturesDir, "sig_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return file
    }

    fun getSignatures(): List<File> {
        return signaturesDir.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun createThumbnailBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val width = source.width
        val height = source.height
        val scale = minOf(targetWidth.toFloat() / width, targetHeight.toFloat() / height)
        val matrix = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
    }

    fun getExportFile(filename: String, extension: String): File {
        val safeName = filename.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        return File(exportsDir, "$safeName.$extension")
    }

    fun getShareableUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun deleteDocumentFiles(documentId: String) {
        val dir = File(documentsDir, documentId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun clearCache(): Long {
        var freed = 0L
        exportsDir.listFiles()?.forEach {
            freed += it.length()
            it.delete()
        }
        return freed
    }

    fun calculateStorageUsage(): StorageInfo {
        var docSize = 0L
        documentsDir.walkTopDown().forEach { docSize += it.length() }

        var cacheSize = 0L
        exportsDir.walkTopDown().forEach { cacheSize += it.length() }

        return StorageInfo(
            documentsSizeBytes = docSize,
            cacheSizeBytes = cacheSize,
            totalSizeBytes = docSize + cacheSize
        )
    }

    data class StorageInfo(
        val documentsSizeBytes: Long,
        val cacheSizeBytes: Long,
        val totalSizeBytes: Long
    )
}
