package com.dockeep.app.data.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.dockeep.app.domain.DefaultCategory
import com.dockeep.app.domain.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LocalFileStorage(private val context: Context) {

    private val documentsRoot: File
        get() = File(context.filesDir, "Documents").apply { mkdirs() }

    fun ensureCategoryFolders() {
        DefaultCategory.entries.forEach { category ->
            File(documentsRoot, category.folderName).mkdirs()
        }
        File(context.cacheDir, "captures").mkdirs()
        File(context.cacheDir, "imports").mkdirs()
        File(context.cacheDir, "edits").mkdirs()
        File(context.cacheDir, "ocr").mkdirs()
    }

    fun ocrTempDir(): File = File(context.cacheDir, "ocr").apply { mkdirs() }

    fun clearOcrTemps() {
        ocrTempDir().listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun categoryDir(categoryId: String): File =
        File(documentsRoot, DefaultCategory.fromId(categoryId).folderName).apply { mkdirs() }

    fun fileFor(relativePath: String): File = File(documentsRoot, relativePath)

    /** True when the path stays inside DocKeep's documents folder (guards against "../" in backups). */
    fun isSafeRelativePath(relativePath: String): Boolean =
        relativePath.isNotBlank() && isInside(documentsRoot, File(documentsRoot, relativePath))

    /** Deletes leftover camera shots and crop edits older than a day; they're copies of documents. */
    fun clearStaleTemps(maxAgeMillis: Long = 24 * 60 * 60_000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        listOf("captures", "edits", "imports", "restore").forEach { name ->
            File(context.cacheDir, name).listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) runCatching { file.deleteRecursively() }
            }
        }
    }

    fun createCaptureFile(): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(dir, "capture_${UUID.randomUUID()}.jpg").apply { createNewFile() }
    }

    fun createEditFile(): File {
        val dir = File(context.cacheDir, "edits").apply { mkdirs() }
        return File(dir, "edit_${UUID.randomUUID()}.jpg").apply { createNewFile() }
    }

    suspend fun importFromUri(
        uri: Uri,
        documentId: String,
        categoryId: String,
        declaredMime: String?
    ): ImportedFile = withContext(Dispatchers.IO) {
        val mimeType = resolveMimeType(uri, declaredMime)
        val extension = extensionFor(mimeType, uri)
        val fileType = if (mimeType == "application/pdf") FileType.PDF else FileType.IMAGE
        val dest = File(categoryDir(categoryId), "$documentId.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read the selected file.")
        ImportedFile(
            relativePath = "${DefaultCategory.fromId(categoryId).folderName}/${dest.name}",
            mimeType = mimeType,
            fileType = fileType
        )
    }

    suspend fun importFromFile(
        source: File,
        documentId: String,
        categoryId: String,
        mimeType: String
    ): ImportedFile = withContext(Dispatchers.IO) {
        val extension = extensionFor(mimeType, null)
        val dest = File(categoryDir(categoryId), "$documentId.$extension")
        source.copyTo(dest, overwrite = true)
        ImportedFile(
            relativePath = "${DefaultCategory.fromId(categoryId).folderName}/${dest.name}",
            mimeType = mimeType,
            fileType = if (mimeType == "application/pdf") FileType.PDF else FileType.IMAGE
        )
    }

    suspend fun moveToCategory(
        currentRelativePath: String,
        documentId: String,
        newCategoryId: String
    ): String = withContext(Dispatchers.IO) {
        val current = fileFor(currentRelativePath)
        if (!current.exists()) return@withContext currentRelativePath
        val dest = File(categoryDir(newCategoryId), "$documentId.${current.extension}")
        if (current.canonicalPath == dest.canonicalPath) return@withContext currentRelativePath
        current.copyTo(dest, overwrite = true)
        if (current.absolutePath != dest.absolutePath) current.delete()
        "${DefaultCategory.fromId(newCategoryId).folderName}/${dest.name}"
    }

    fun removeCategoryFolder(folderName: String) {
        val dir = File(documentsRoot, folderName)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun delete(relativePath: String) {
        val file = fileFor(relativePath)
        if (file.exists()) file.delete()
    }

    fun deleteAllDocuments() {
        documentsRoot.listFiles()?.forEach { it.deleteRecursively() }
        ensureCategoryFolders()
    }

    fun totalBytes(): Long = documentsRoot.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }

    fun fileCount(): Int = documentsRoot.walkTopDown()
        .count { it.isFile }

    private fun resolveMimeType(uri: Uri, declared: String?): String {
        declared?.let { if (it.isNotBlank()) return it }
        context.contentResolver.getType(uri)?.let { return it }
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext?.lowercase())
            ?: "application/octet-stream"
    }

    private fun extensionFor(mimeType: String, uri: Uri?): String {
        if (mimeType == "application/pdf") return "pdf"
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { return it }
        val fromUri = uri?.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        if (!fromUri.isNullOrBlank() && fromUri.length <= 4) return fromUri
        return "jpg"
    }
}

data class ImportedFile(
    val relativePath: String,
    val mimeType: String,
    val fileType: FileType
)

internal fun isInside(root: File, candidate: File): Boolean {
    val rootPath = root.canonicalPath + File.separator
    return candidate.canonicalPath.startsWith(rootPath)
}
