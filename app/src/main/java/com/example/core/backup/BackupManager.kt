package com.example.core.backup

import android.content.Context
import com.example.core.database.DocumentEntity
import com.example.core.database.FolderEntity
import com.example.core.database.KemSkenDatabase
import com.example.core.database.OcrEntity
import com.example.core.database.PageEntity
import com.example.core.storage.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val database: KemSkenDatabase,
    private val fileManager: FileManager
) {

    suspend fun createBackup(outputZipFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val documents = database.documentDao().getAllActiveDocuments()
            // We need a snapshot of documents
            val docList = ArrayList<DocumentEntity>()
            // Query all documents
            val pages = ArrayList<PageEntity>()
            val ocrList = ArrayList<OcrEntity>()

            val manifest = JSONObject().apply {
                put("version", 1)
                put("app", "KemSken")
                put("timestamp", System.currentTimeMillis())
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifest.toString().toByteArray())
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
