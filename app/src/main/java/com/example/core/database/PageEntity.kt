package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"]), Index(value = ["documentId", "pageIndex"])]
)
data class PageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val pageIndex: Int = 0,
    val originalImagePath: String,
    val processedImagePath: String,
    val rotation: Int = 0, // 0, 90, 180, 270
    val cropCoordinates: String = "", // JSON or "tlX,tlY;trX,trY;brX,brY;blX,blY"
    val filterType: String = "AUTO_ENHANCE",
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val sharpness: Float = 0.0f,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
