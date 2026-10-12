package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val folderId: String? = null,
    val pageCount: Int = 1,
    val thumbnailPath: String? = null,
    val isFavorite: Boolean = false,
    val deletedAt: Long? = null, // null if active, timestamp if moved to recycle bin
    val tags: String = "",
    val processingStatus: String = "READY",
    val isEncrypted: Boolean = false
)
