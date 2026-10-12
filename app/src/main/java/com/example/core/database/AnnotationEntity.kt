package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"])]
)
data class AnnotationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val pageId: String,
    val annotationType: String, // "SIGNATURE", "WATERMARK", "DRAWING", "TEXT"
    val annotationData: String, // coordinates, signature file path, text, color, stroke
    val zIndex: Int = 0
)
