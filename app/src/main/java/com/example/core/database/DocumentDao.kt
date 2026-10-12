package com.example.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllActiveDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY updatedAt DESC")
    fun getDocumentsInFolder(folderId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getRecycleBinDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun observeDocumentById(id: String): Flow<DocumentEntity?>

    @Query("""
        SELECT DISTINCT d.* FROM documents d 
        LEFT JOIN ocr_results o ON d.id = o.documentId 
        WHERE d.deletedAt IS NULL AND (
            d.title LIKE '%' || :query || '%' OR 
            d.tags LIKE '%' || :query || '%' OR 
            o.recognizedText LIKE '%' || :query || '%'
        )
        ORDER BY d.updatedAt DESC
    """)
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET deletedAt = :deletedAt WHERE id = :documentId")
    suspend fun moveToRecycleBin(documentId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET deletedAt = NULL WHERE id = :documentId")
    suspend fun restoreFromRecycleBin(documentId: String)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deletePermanently(documentId: String)

    @Query("DELETE FROM documents WHERE deletedAt IS NOT NULL")
    suspend fun emptyRecycleBin()

    @Query("UPDATE documents SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updateFavorite(documentId: String, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET title = :newTitle, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun renameDocument(documentId: String, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun moveToFolder(documentId: String, folderId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET pageCount = :pageCount, thumbnailPath = :thumbnailPath, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updatePageCountAndThumbnail(documentId: String, pageCount: Int, thumbnailPath: String?, updatedAt: Long = System.currentTimeMillis())
}
