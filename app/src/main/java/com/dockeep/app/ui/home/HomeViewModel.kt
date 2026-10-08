package com.dockeep.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dockeep.app.data.repository.DocumentRepository
import com.dockeep.app.domain.Category
import com.dockeep.app.domain.DocumentItem
import com.dockeep.app.domain.ExpiryStatus
import com.dockeep.app.domain.asGroups
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val totalDocuments: Int = 0,
    val expiringSoonCount: Int = 0,
    val expiredCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val expiringSoon: List<DocumentItem> = emptyList(),
    val recent: List<DocumentItem> = emptyList()
)

class HomeViewModel(
    private val repository: DocumentRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        repository.categories,
        repository.totalCount,
        repository.expiringSoon,
        repository.recentDocuments
    ) { categories, total, expiring, recent ->
        HomeUiState(
            totalDocuments = total,
            expiringSoonCount = expiring.count { it.expiryStatus == ExpiryStatus.EXPIRING_SOON },
            expiredCount = expiring.count { it.expiryStatus == ExpiryStatus.EXPIRED },
            categories = categories.asGroups(),
            expiringSoon = expiring.take(6),
            recent = recent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
