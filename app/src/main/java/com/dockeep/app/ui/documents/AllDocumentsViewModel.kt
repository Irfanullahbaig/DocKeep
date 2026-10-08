package com.dockeep.app.ui.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dockeep.app.data.repository.DocumentRepository
import com.dockeep.app.domain.Category
import com.dockeep.app.domain.DefaultCategory
import com.dockeep.app.domain.DocumentGroup
import com.dockeep.app.domain.DocumentItem
import com.dockeep.app.domain.ExpiryStatus
import com.dockeep.app.domain.asGroups
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExpiryChip { ALL, EXPIRING, EXPIRED }

data class AllDocumentsUiState(
    val query: String = "",
    val title: String = "All documents",
    val selectedGroupId: String? = null,
    val selectedTypeId: String? = null,
    val expiryChip: ExpiryChip = ExpiryChip.ALL,
    val groups: List<Category> = emptyList(),
    val types: List<Category> = emptyList(),
    val visible: List<DocumentItem> = emptyList()
)

class AllDocumentsViewModel(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedGroupId: MutableStateFlow<String?>
    private val selectedTypeId: MutableStateFlow<String?>
    private val expiryChip = MutableStateFlow(
        when (savedStateHandle.get<String>("expiry")) {
            "soon" -> ExpiryChip.EXPIRING
            "expired" -> ExpiryChip.EXPIRED
            else -> ExpiryChip.ALL
        }
    )

    init {
        val requested = savedStateHandle.get<String>("categoryId")?.ifBlank { null }
        val group = requested?.let(DocumentGroup::fromId)
        val type = DefaultCategory.entries.firstOrNull { it.id == requested }
        selectedGroupId = MutableStateFlow(group?.id ?: type?.group?.id)
        selectedTypeId = MutableStateFlow(type?.id)
    }

    val uiState: StateFlow<AllDocumentsUiState> = combine(
        repository.documents,
        repository.categories,
        query,
        combine(selectedGroupId, selectedTypeId, expiryChip) { groupId, typeId, expiry ->
            Triple(groupId, typeId, expiry)
        }
    ) { documents, categories, q, selection ->
        val (groupId, typeId, expiry) = selection
        val filtered = documents.filter { doc ->
            val known = DefaultCategory.entries.firstOrNull { it.id == doc.categoryId }
            val matchesQuery = q.isBlank() ||
                doc.name.contains(q, ignoreCase = true) ||
                doc.documentNumber?.contains(q, ignoreCase = true) == true ||
                doc.notes?.contains(q, ignoreCase = true) == true ||
                doc.categoryName.contains(q, ignoreCase = true) ||
                doc.groupName.contains(q, ignoreCase = true)
            val matchesGroup = groupId == null || known?.group?.id == groupId
            val matchesType = typeId == null || doc.categoryId == typeId
            val matchesExpiry = when (expiry) {
                ExpiryChip.ALL -> true
                ExpiryChip.EXPIRING -> doc.expiryStatus == ExpiryStatus.EXPIRING_SOON
                ExpiryChip.EXPIRED -> doc.expiryStatus == ExpiryStatus.EXPIRED
            }
            matchesQuery && matchesGroup && matchesType && matchesExpiry
        }
        val group = groupId?.let(DocumentGroup::fromId)
        AllDocumentsUiState(
            query = q,
            title = group?.displayName ?: "All documents",
            selectedGroupId = groupId,
            selectedTypeId = typeId,
            expiryChip = expiry,
            groups = categories.asGroups(),
            types = categories.filter { it.groupId == groupId },
            visible = filtered
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllDocumentsUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onGroupSelected(id: String?) {
        selectedGroupId.value = id
        selectedTypeId.value = null
    }

    fun onTypeSelected(id: String?) {
        selectedTypeId.value = id
    }

    fun onExpiryChip(chip: ExpiryChip) {
        expiryChip.value = chip
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
