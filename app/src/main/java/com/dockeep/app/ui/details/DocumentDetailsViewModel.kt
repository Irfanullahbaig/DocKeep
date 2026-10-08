package com.dockeep.app.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dockeep.app.data.repository.DocumentRepository
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.domain.DefaultCategory
import com.dockeep.app.domain.DocumentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.net.Uri
import java.io.File

data class DetailsUiState(
    val document: DocumentItem? = null,
    val replacing: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
)

class DocumentDetailsViewModel(
    private val repository: DocumentRepository,
    private val storage: LocalFileStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    val document: StateFlow<DocumentItem?> = repository.observeDocument(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    val deleted: StateFlow<Boolean> = _uiState.map { it.deleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun fileFor(document: DocumentItem): File = storage.fileFor(document.relativePath)

    fun replaceFile(uri: Uri, mime: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(replacing = true, error = null) }
            runCatching {
                val imported = storage.importFromUri(
                    uri,
                    documentId,
                    document.value?.categoryId ?: DefaultCategory.default.id,
                    mime
                )
                repository.replaceFile(documentId, imported)
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Could not replace the file.") }
            }
            _uiState.update { it.copy(replacing = false) }
        }
    }

    fun replaceCapture(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(replacing = true, error = null) }
            runCatching {
                val imported = storage.importFromFile(
                    source = file,
                    documentId = documentId,
                    categoryId = document.value?.categoryId ?: DefaultCategory.default.id,
                    mimeType = "image/jpeg"
                )
                repository.replaceFile(documentId, imported)
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Could not replace the file.") }
            }
            _uiState.update { it.copy(replacing = false) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { repository.delete(documentId) }
                .onSuccess { _uiState.update { it.copy(deleted = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Could not delete the document.") }
                }
        }
    }
}
