package com.dockeep.app.ui.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dockeep.app.data.repository.DocumentRepository
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.domain.Category
import com.dockeep.app.domain.DefaultCategory
import com.dockeep.app.ocr.DocumentOcrScanner
import com.dockeep.app.ocr.OcrFieldKey
import com.dockeep.app.ocr.OcrScanResult
import com.dockeep.app.ocr.StructuredOcrResult
import com.dockeep.app.util.toDisplayDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class AddDocumentUiState(
    val isEdit: Boolean = false,
    val documentId: String? = null,
    val name: String = "",
    val categoryId: String = DefaultCategory.default.id,
    val documentNumber: String = "",
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val notes: String = "",
    val fatherName: String = "",
    val dateOfBirth: Long? = null,
    val phoneNumber: String = "",
    val chassisNumber: String = "",
    val engineNumber: String = "",
    val doctorName: String = "",
    val hospital: String = "",
    val pendingUri: Uri? = null,
    val pendingFile: File? = null,
    val pendingMime: String? = null,
    val hasFile: Boolean = false,
    val existingPath: String? = null,
    val existingMime: String? = null,
    val categories: List<Category> = emptyList(),
    val saving: Boolean = false,
    val ocrScanning: Boolean = false,
    val ocrStatus: String? = null,
    val autoFilled: Set<String> = emptySet(),
    val error: String? = null,
    val saved: Boolean = false
) {
    val isPdf: Boolean
        get() = (pendingMime ?: existingMime).orEmpty().contains("pdf")

    val canScan: Boolean get() = hasFile && !isPdf && !ocrScanning
}

class AddDocumentViewModel(
    private val repository: DocumentRepository,
    private val storage: LocalFileStorage,
    savedStateHandle: SavedStateHandle,
    private val ocrScanner: DocumentOcrScanner
) : ViewModel() {
    private val editId: String? = savedStateHandle.get<String>("documentId")?.ifBlank { null }
    private val presetCategory: String? = savedStateHandle.get<String>("categoryId")?.ifBlank { null }

    private val _uiState = MutableStateFlow(
        AddDocumentUiState(
            isEdit = editId != null,
            documentId = editId,
            categoryId = DefaultCategory.resolve(presetCategory).id
        )
    )
    val uiState: StateFlow<AddDocumentUiState> = _uiState.asStateFlow()

    val categories = repository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    init {
        viewModelScope.launch {
            repository.categories.collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
        if (editId != null) {
            viewModelScope.launch {
                repository.observeDocument(editId).collect { doc ->
                    if (doc != null) {
                        _uiState.update {
                            it.copy(
                                name = doc.name,
                                categoryId = doc.categoryId,
                                documentNumber = doc.documentNumber.orEmpty(),
                                issueDate = doc.issueDate,
                                expiryDate = doc.expiryDate,
                                notes = doc.notes.orEmpty(),
                                hasFile = true,
                                existingPath = doc.relativePath,
                                existingMime = doc.mimeType
                            )
                        }
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, error = null).withoutAuto("name") }
    fun onGroupChange(groupId: String) = _uiState.update { state ->
        val current = DefaultCategory.fromId(state.categoryId)
        val next = if (current.group.id == groupId) current else DefaultCategory.resolve(groupId)
        state.copy(categoryId = next.id)
    }

    fun onCategoryChange(value: String) = _uiState.update { it.copy(categoryId = value) }
    fun onNumberChange(value: String) = _uiState.update { it.copy(documentNumber = value).withoutAuto("number") }
    fun onIssueDate(value: Long?) = _uiState.update { it.copy(issueDate = value).withoutAuto("issue") }
    fun onExpiryDate(value: Long?) = _uiState.update { it.copy(expiryDate = value).withoutAuto("expiry") }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }
    fun onFatherNameChange(value: String) = _uiState.update { it.copy(fatherName = value).withoutAuto("father") }
    fun onDateOfBirth(value: Long?) = _uiState.update { it.copy(dateOfBirth = value).withoutAuto("dob") }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phoneNumber = value).withoutAuto("phone") }
    fun onChassisChange(value: String) = _uiState.update { it.copy(chassisNumber = value).withoutAuto("chassis") }
    fun onEngineChange(value: String) = _uiState.update { it.copy(engineNumber = value).withoutAuto("engine") }
    fun onDoctorChange(value: String) = _uiState.update { it.copy(doctorName = value).withoutAuto("doctor") }
    fun onHospitalChange(value: String) = _uiState.update { it.copy(hospital = value).withoutAuto("hospital") }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun showError(message: String) = _uiState.update { it.copy(error = message) }

    fun onFilePicked(uri: Uri, mime: String?) {
        val guessedName = _uiState.value.name.ifBlank {
            uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.replace('_', ' ')
                .orEmpty()
        }
        _uiState.update {
            it.copy(
                pendingUri = uri,
                pendingFile = null,
                pendingMime = mime,
                hasFile = true,
                name = guessedName.ifBlank { it.name },
                error = null,
                ocrStatus = null,
                autoFilled = emptySet()
            )
        }
    }

    fun onCaptureFile(file: File) {
        val guessedName = _uiState.value.name.ifBlank { "Scanned document" }
        _uiState.update {
            it.copy(
                pendingUri = null,
                pendingFile = file,
                pendingMime = "image/jpeg",
                hasFile = true,
                name = guessedName,
                error = null,
                ocrStatus = null,
                autoFilled = emptySet()
            )
        }
    }

    fun scanAndAutoFill() {
        val state = _uiState.value
        if (state.ocrScanning) return
        if (!state.hasFile) {
            _uiState.update { it.copy(error = "Choose a photo first.") }
            return
        }
        if (state.isPdf) {
            _uiState.update { it.copy(error = "Scan & Auto-Fill works with photos. Enter PDF details manually.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(ocrScanning = true, error = null, ocrStatus = null) }
            val result = runCatching {
                ocrScanner.scan(
                    file = state.pendingFile ?: state.existingPath?.let(storage::fileFor),
                    uri = state.pendingUri,
                    categoryId = state.categoryId
                )
            }.getOrElse {
                OcrScanResult.Recoverable("Could not scan this document. You can enter the details manually.")
            }
            storage.clearOcrTemps()
            when (result) {
                is OcrScanResult.Success -> applyOcr(result.data)
                is OcrScanResult.Recoverable -> _uiState.update {
                    it.copy(ocrScanning = false, ocrStatus = result.message)
                }
            }
        }
    }

    private fun applyOcr(data: StructuredOcrResult) {
        _uiState.update { current ->
            var next = current.copy(ocrScanning = false)
            val filled = current.autoFilled.toMutableSet()
            data.usable(OcrFieldKey.NAME)?.text?.let { value ->
                if (current.name.isBlank() || current.name == "Scanned document") {
                    next = next.copy(name = value)
                    filled += "name"
                }
            }
            data.usable(OcrFieldKey.DOCUMENT_NUMBER)?.text?.let { value ->
                if (current.documentNumber.isBlank()) {
                    next = next.copy(documentNumber = value)
                    filled += "number"
                }
            }
            val issue = data.usable(OcrFieldKey.ISSUE_DATE)?.dateMillis
                ?: data.usable(OcrFieldKey.REGISTRATION_DATE)?.dateMillis
            if (current.issueDate == null && issue != null) {
                next = next.copy(issueDate = issue)
                filled += "issue"
            }
            data.usable(OcrFieldKey.EXPIRY_DATE)?.dateMillis?.let { value ->
                if (current.expiryDate == null) {
                    next = next.copy(expiryDate = value)
                    filled += "expiry"
                }
            }
            data.usable(OcrFieldKey.FATHER_NAME)?.text?.let { value ->
                if (current.fatherName.isBlank()) {
                    next = next.copy(fatherName = value)
                    filled += "father"
                }
            }
            data.usable(OcrFieldKey.DATE_OF_BIRTH)?.dateMillis?.let { value ->
                if (current.dateOfBirth == null) {
                    next = next.copy(dateOfBirth = value)
                    filled += "dob"
                }
            }
            data.usable(OcrFieldKey.PHONE_NUMBER)?.text?.let { value ->
                if (current.phoneNumber.isBlank()) {
                    next = next.copy(phoneNumber = value)
                    filled += "phone"
                }
            }
            data.usable(OcrFieldKey.CHASSIS_NUMBER)?.text?.let { value ->
                if (current.chassisNumber.isBlank()) {
                    next = next.copy(chassisNumber = value)
                    filled += "chassis"
                }
            }
            data.usable(OcrFieldKey.ENGINE_NUMBER)?.text?.let { value ->
                if (current.engineNumber.isBlank()) {
                    next = next.copy(engineNumber = value)
                    filled += "engine"
                }
            }
            data.usable(OcrFieldKey.DOCTOR_NAME)?.text?.let { value ->
                if (current.doctorName.isBlank()) {
                    next = next.copy(doctorName = value)
                    filled += "doctor"
                }
            }
            data.usable(OcrFieldKey.HOSPITAL)?.text?.let { value ->
                if (current.hospital.isBlank()) {
                    next = next.copy(hospital = value)
                    filled += "hospital"
                }
            }
            val status = buildString {
                append("Scan complete")
                if (filled.isNotEmpty()) append(" · review the auto-filled fields before saving")
                data.qualityWarning?.let { warning ->
                    append('\n')
                    append(warning)
                }
            }
            next.copy(autoFilled = filled, ocrStatus = status)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Give this document a name.") }
            return
        }
        if (!state.isEdit && !state.hasFile) {
            _uiState.update { it.copy(error = "Choose a file first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching {
                val notes = mergedNotes(state)
                if (state.isEdit && state.documentId != null) {
                    repository.updateMetadata(
                        id = state.documentId,
                        name = state.name,
                        categoryId = state.categoryId,
                        documentNumber = state.documentNumber,
                        issueDate = state.issueDate,
                        expiryDate = state.expiryDate,
                        notes = notes
                    )
                    val replacement = when {
                        state.pendingFile != null -> storage.importFromFile(
                            source = state.pendingFile,
                            documentId = state.documentId,
                            categoryId = state.categoryId,
                            mimeType = state.pendingMime ?: "image/jpeg"
                        )
                        state.pendingUri != null -> storage.importFromUri(
                            uri = state.pendingUri,
                            documentId = state.documentId,
                            categoryId = state.categoryId,
                            declaredMime = state.pendingMime
                        )
                        else -> null
                    }
                    if (replacement != null) {
                        repository.replaceFile(state.documentId, replacement)
                    }
                } else {
                    val id = UUID.randomUUID().toString()
                    val imported = when {
                        state.pendingFile != null -> storage.importFromFile(
                            source = state.pendingFile,
                            documentId = id,
                            categoryId = state.categoryId,
                            mimeType = state.pendingMime ?: "image/jpeg"
                        )
                        state.pendingUri != null -> storage.importFromUri(
                            uri = state.pendingUri,
                            documentId = id,
                            categoryId = state.categoryId,
                            declaredMime = state.pendingMime
                        )
                        else -> error("No file selected.")
                    }
                    repository.saveNew(
                        id = id,
                        name = state.name,
                        categoryId = state.categoryId,
                        documentNumber = state.documentNumber,
                        issueDate = state.issueDate,
                        expiryDate = state.expiryDate,
                        notes = notes,
                        imported = imported
                    )
                }
            }.onSuccess {
                // The edited photo is now copied into private storage; drop the temp copy.
                state.pendingFile?.let { runCatching { it.delete() } }
                _uiState.update { it.copy(saving = false, saved = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(saving = false, error = error.message ?: "Could not save the document.") }
            }
        }
    }

    override fun onCleared() {
        ocrScanner.close()
        storage.clearOcrTemps()
        super.onCleared()
    }

    private fun mergedNotes(state: AddDocumentUiState): String {
        val extras = buildList {
            if (state.fatherName.isNotBlank()) add("Father's Name: ${state.fatherName}")
            state.dateOfBirth?.let { add("Date of Birth: ${it.toDisplayDate()}") }
            if (state.phoneNumber.isNotBlank()) add("Phone: ${state.phoneNumber}")
            if (state.chassisNumber.isNotBlank()) add("Chassis Number: ${state.chassisNumber}")
            if (state.engineNumber.isNotBlank()) add("Engine Number: ${state.engineNumber}")
            if (state.doctorName.isNotBlank()) add("Doctor: ${state.doctorName}")
            if (state.hospital.isNotBlank()) add("Hospital: ${state.hospital}")
        }
        val user = state.notes.trim()
        if (extras.isEmpty()) return user
        if (user.isBlank()) return extras.joinToString("\n")
        val missing = extras.filter { line -> user.lines().none { it.equals(line, ignoreCase = true) } }
        return if (missing.isEmpty()) user else (user + "\n" + missing.joinToString("\n")).trim()
    }
}

private fun AddDocumentUiState.withoutAuto(field: String): AddDocumentUiState =
    copy(autoFilled = autoFilled - field)
