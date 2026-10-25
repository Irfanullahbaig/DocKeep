package com.dockeep.app.data.backup

import android.content.Context
import android.net.Uri
import com.dockeep.app.data.local.AppDatabase
import com.dockeep.app.data.local.CategoryEntity
import com.dockeep.app.data.local.DocumentEntity
import com.dockeep.app.data.repository.DocumentRepository
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.data.storage.isInside
import com.dockeep.app.domain.DefaultCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val repository: DocumentRepository,
    private val storage: LocalFileStorage,
    private val database: AppDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            categories = database.categoryDao().getAll().map { it.toBackup() },
            documents = database.documentDao().getAll().map { it.toBackup() }
        )
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry("dockeep-backup.json"))
                zip.write(json.encodeToString(BackupPayload.serializer(), payload).toByteArray())
                zip.closeEntry()
                payload.documents.forEach { document ->
                    val file = storage.fileFor(document.relativePath)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("files/${document.relativePath}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Could not write the backup file.")
    }

    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        // Unpack to a staging folder first (streamed, so large backups don't run out of memory)
        // and only replace the current library once the backup has been read successfully.
        val staging = File(context.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            var payload: BackupPayload? = null
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            when {
                                entry.name.endsWith("dockeep-backup.json") -> {
                                    payload = json.decodeFromString(
                                        BackupPayload.serializer(),
                                        zip.readBytes().decodeToString()
                                    )
                                }
                                entry.name.startsWith("files/") -> {
                                    val dest = File(staging, entry.name.removePrefix("files/"))
                                    if (isInside(staging, dest)) {
                                        dest.parentFile?.mkdirs()
                                        dest.outputStream().use { zip.copyTo(it) }
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Could not read the backup file.")

            val backup = payload ?: error("This file is not a valid DocKeep backup.")
            val categories = (backup.categories.map { it.toEntity() } + AppDatabase.defaultCategories())
                .distinctBy { it.id }
            val categoryIds = categories.map { it.id }.toSet()
            val documents = backup.documents
                .filter { storage.isSafeRelativePath(it.relativePath) }
                .map { document ->
                    val entity = document.toEntity()
                    if (entity.categoryId in categoryIds) entity else entity.copy(categoryId = DefaultCategory.default.id)
                }

            storage.deleteAllDocuments()
            documents.forEach { document ->
                val source = File(staging, document.relativePath)
                if (!isInside(staging, source) || !source.exists()) return@forEach
                val dest = storage.fileFor(document.relativePath)
                dest.parentFile?.mkdirs()
                if (!source.renameTo(dest)) source.copyTo(dest, overwrite = true)
            }
            repository.restore(categories = categories, documents = documents)
            repository.seedIfNeeded()
            storage.ensureCategoryFolders()
        } finally {
            staging.deleteRecursively()
        }
    }
}

@Serializable
data class BackupPayload(
    val version: Int,
    val exportedAt: Long,
    val categories: List<CategoryBackup>,
    val documents: List<DocumentBackup>
)

@Serializable
data class CategoryBackup(
    val id: String,
    val name: String,
    val emoji: String,
    val folderName: String,
    val sortOrder: Int
)

@Serializable
data class DocumentBackup(
    val id: String,
    val name: String,
    val categoryId: String,
    val documentNumber: String? = null,
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val notes: String? = null,
    val relativePath: String,
    val mimeType: String,
    val fileType: String,
    val createdAt: Long,
    val updatedAt: Long
)

private fun CategoryEntity.toBackup() = CategoryBackup(id, name, emoji, folderName, sortOrder)
private fun DocumentEntity.toBackup() = DocumentBackup(
    id, name, categoryId, documentNumber, issueDate, expiryDate, notes, relativePath, mimeType, fileType, createdAt, updatedAt
)
private fun CategoryBackup.toEntity() = CategoryEntity(id, name, emoji, folderName, sortOrder)
private fun DocumentBackup.toEntity() = DocumentEntity(
    id, name, categoryId, documentNumber, issueDate, expiryDate, notes, relativePath, mimeType, fileType, createdAt, updatedAt
)
