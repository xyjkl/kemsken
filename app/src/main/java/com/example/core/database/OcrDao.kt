package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrDao {

    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId LIMIT 1")
    suspend fun getOcrForPage(pageId: String): OcrEntity?

    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId LIMIT 1")
    fun observeOcrForPage(pageId: String): Flow<OcrEntity?>

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId ORDER BY lastProcessedAt ASC")
    suspend fun getAllOcrForDocument(documentId: String): List<OcrEntity>

    @Query("SELECT * FROM ocr_results WHERE documentId = :documentId ORDER BY lastProcessedAt ASC")
    fun observeAllOcrForDocument(documentId: String): Flow<List<OcrEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcr(ocr: OcrEntity)

    @Query("DELETE FROM ocr_results WHERE pageId = :pageId")
    suspend fun deleteOcrForPage(pageId: String)
}
