package com.dockeep.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val folderName: String,
    val sortOrder: Int
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("categoryId"),
        Index("expiryDate"),
        Index("updatedAt"),
        Index("name")
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    val documentNumber: String?,
    val issueDate: Long?,
    val expiryDate: Long?,
    val notes: String?,
    val relativePath: String,
    val mimeType: String,
    val fileType: String,
    val createdAt: Long,
    val updatedAt: Long
)
