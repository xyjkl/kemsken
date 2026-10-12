package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"]), Index(value = ["documentId"])]
)
data class OcrEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val pageId: String,
    val documentId: String,
    val recognizedText: String,
    val language: String = "und",
    val recognizedBlocksJson: String = "",
    val lastProcessedAt: Long = System.currentTimeMillis()
)
