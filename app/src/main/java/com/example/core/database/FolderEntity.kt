package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val colorHex: String = "#087F8C",
    val createdAt: Long = System.currentTimeMillis()
)
