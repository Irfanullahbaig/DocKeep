package com.dockeep.app.data.repository

import com.dockeep.app.data.local.AppDatabase
import com.dockeep.app.data.local.CategoryEntity
import com.dockeep.app.data.local.DocumentEntity
import com.dockeep.app.data.storage.ImportedFile
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.domain.Category
import com.dockeep.app.domain.DefaultCategory
import com.dockeep.app.domain.DocumentItem
import com.dockeep.app.domain.ExpiryStatus
import com.dockeep.app.domain.FileType
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class DocumentRepository(
    private val database: AppDatabase,
    private val storage: LocalFileStorage
) {
    private val categoryDao = database.categoryDao()
    private val documentDao = database.documentDao()

    suspend fun seedIfNeeded() {
        storage.ensureCategoryFolders()
        categoryDao.insertMissing(AppDatabase.defaultCategories())
        migrateLegacyCategories()
    }

    private suspend fun migrateLegacyCategories() {
        val legacy = mapOf(
            "car" to DefaultCategory.REGISTRATION.id,
            "bike" to DefaultCategory.REGISTRATION.id,
            "id" to DefaultCategory.NATIONAL_ID.id,
            "national_id" to DefaultCategory.NATIONAL_ID.id,
            "medical" to DefaultCategory.MEDICAL_DOCUMENT.id,
            "insurance" to DefaultCategory.VEHICLE_INSURANCE.id,
            "personal" to DefaultCategory.UTILITY.id
        )
        legacy.forEach { (fromId, toId) ->
            documentDao.getByCategory(fromId).forEach { document ->
                val path = storage.moveToCategory(document.relativePath, document.id, toId)
                documentDao.update(
                    document.copy(
                        categoryId = toId,
                        relativePath = path,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            categoryDao.deleteById(fromId)
        }
        listOf("Car", "Bike", "ID", "Medical", "Insurance", "Personal", "National ID")
            .forEach(storage::removeCategoryFolder)
    }

    val categories: Flow<List<Category>> = categoryDao.observeWithCounts().map { rows ->
        rows.map {
            val known = DefaultCategory.entries.firstOrNull { category -> category.id == it.category.id }
            Category(
                id = it.category.id,
                name = known?.displayName ?: it.category.name,
                emoji = known?.emoji ?: it.category.emoji,
                folderName = known?.folderName ?: it.category.folderName,
                sortOrder = it.category.sortOrder,
                documentCount = it.documentCount,
                groupId = known?.group?.id.orEmpty(),
                groupName = known?.group?.displayName.orEmpty()
            )
        }
    }

    val documents: Flow<List<DocumentItem>> = combine(
        documentDao.observeAll(),
        categoryDao.observeAll()
    ) { docs, cats -> docs.map { it.toItem(cats) } }

    fun observeDocument(id: String): Flow<DocumentItem?> = combine(
        documentDao.observeById(id),
        categoryDao.observeAll()
    ) { doc, cats -> doc?.toItem(cats) }

    val recentDocuments: Flow<List<DocumentItem>> = combine(
        documentDao.observeRecent(8),
        categoryDao.observeAll()
    ) { docs, cats -> docs.map { it.toItem(cats) } }

    val expiringSoon: Flow<List<DocumentItem>> = combine(
        documentDao.observeWithExpiry(),
        categoryDao.observeAll()
    ) { docs, cats ->
        docs.map { it.toItem(cats) }
            .filter { it.expiryStatus == ExpiryStatus.EXPIRING_SOON || it.expiryStatus == ExpiryStatus.EXPIRED }
            .sortedBy { it.expiryDate }
    }

    val totalCount: Flow<Int> = documentDao.observeCount()

    suspend fun getAllDocuments(): List<DocumentItem> {
        val cats = categoryDao.getAll()
        return documentDao.getAll().map { it.toItem(cats) }
    }

    suspend fun saveNew(
        id: String,
        name: String,
        categoryId: String,
        documentNumber: String?,
        issueDate: Long?,
        expiryDate: Long?,
        notes: String?,
        imported: ImportedFile
    ) {
        val now = System.currentTimeMillis()
        documentDao.insert(
            DocumentEntity(
                id = id,
                name = name.trim(),
                categoryId = categoryId,
                documentNumber = documentNumber?.trim()?.ifBlank { null },
                issueDate = issueDate,
                expiryDate = expiryDate,
                notes = notes?.trim()?.ifBlank { null },
                relativePath = imported.relativePath,
                mimeType = imported.mimeType,
                fileType = imported.fileType.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateMetadata(
        id: String,
        name: String,
        categoryId: String,
        documentNumber: String?,
        issueDate: Long?,
        expiryDate: Long?,
        notes: String?
    ) {
        val current = documentDao.getById(id) ?: return
        val path = if (current.categoryId != categoryId) {
            storage.moveToCategory(current.relativePath, id, categoryId)
        } else {
            current.relativePath
        }
        documentDao.update(
            current.copy(
                name = name.trim(),
                categoryId = categoryId,
                documentNumber = documentNumber?.trim()?.ifBlank { null },
                issueDate = issueDate,
                expiryDate = expiryDate,
                notes = notes?.trim()?.ifBlank { null },
                relativePath = path,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun replaceFile(id: String, imported: ImportedFile) {
        val current = documentDao.getById(id) ?: return
        if (current.relativePath != imported.relativePath) {
            storage.delete(current.relativePath)
        }
        documentDao.update(
            current.copy(
                relativePath = imported.relativePath,
                mimeType = imported.mimeType,
                fileType = imported.fileType.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(id: String) {
        val current = documentDao.getById(id) ?: return
        storage.delete(current.relativePath)
        documentDao.deleteById(id)
    }

    suspend fun deleteAll() {
        storage.deleteAllDocuments()
        documentDao.deleteAll()
    }

    suspend fun restore(
        categories: List<CategoryEntity>,
        documents: List<DocumentEntity>
    ) {
        // One transaction, so a bad backup can't leave the library half-wiped.
        database.withTransaction {
            categoryDao.insertAll(categories)
            documentDao.deleteAll()
            documentDao.insertAll(documents)
        }
    }

    fun storageBytes(): Long = storage.totalBytes()

    fun storedFileCount(): Int = storage.fileCount()

    private fun DocumentEntity.toItem(categories: List<CategoryEntity>): DocumentItem {
        val known = DefaultCategory.entries.firstOrNull { it.id == categoryId }
        val category = categories.firstOrNull { it.id == categoryId }
        return DocumentItem(
            id = id,
            name = name,
            categoryId = categoryId,
            categoryName = known?.displayName ?: category?.name ?: categoryId,
            categoryEmoji = known?.emoji ?: category?.emoji ?: "📄",
            groupName = known?.group?.displayName.orEmpty(),
            documentNumber = documentNumber,
            issueDate = issueDate,
            expiryDate = expiryDate,
            notes = notes,
            relativePath = relativePath,
            mimeType = mimeType,
            fileType = runCatching { FileType.valueOf(fileType) }.getOrDefault(FileType.IMAGE),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
