package com.dockeep.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dockeep.app.data.backup.BackupManager
import com.dockeep.app.data.repository.DocumentRepository
import com.dockeep.app.data.settings.SettingsRepository
import com.dockeep.app.domain.AppSettings
import com.dockeep.app.domain.AutoLockTimeout
import com.dockeep.app.domain.ThemeMode
import com.dockeep.app.security.AppLockController
import com.dockeep.app.util.formatFileSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val storageLabel: String = "0 B",
    val fileCount: Int = 0,
    val documentCount: Int = 0,
    val message: String? = null,
    val busy: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository,
    private val backupManager: BackupManager,
    private val appLockController: AppLockController
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val bytes = documentRepository.storageBytes()
            val files = documentRepository.storedFileCount()
            val docs = documentRepository.getAllDocuments().size
            _uiState.update {
                it.copy(
                    storageLabel = formatFileSize(bytes),
                    fileCount = files,
                    documentCount = docs
                )
            }
        }
    }

    fun setAppLock(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAppLockEnabled(enabled)
        if (enabled) appLockController.lockNow() else appLockController.markUnlocked()
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBiometricEnabled(enabled)
    }

    fun setAutoLock(timeout: AutoLockTimeout) = viewModelScope.launch {
        settingsRepository.setAutoLockTimeout(timeout)
    }

    fun setNotifications(enabled: Boolean, schedule: (Boolean) -> Unit) = viewModelScope.launch {
        settingsRepository.setExpiryNotificationsEnabled(enabled)
        schedule(enabled)
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { _uiState.update { it.copy(busy = false, message = "Backup saved on this device.") } }
                .onFailure { error ->
                    _uiState.update { it.copy(busy = false, message = error.message ?: "Backup failed.") }
                }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching { backupManager.restoreFrom(uri) }
                .onSuccess {
                    refreshStorage()
                    _uiState.update { it.copy(busy = false, message = "Backup restored. Everything stayed on this phone.") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(busy = false, message = error.message ?: "Restore failed.") }
                }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching { documentRepository.deleteAll() }
                .onSuccess {
                    refreshStorage()
                    _uiState.update { it.copy(busy = false, message = "All documents were deleted from this device.") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(busy = false, message = error.message ?: "Could not delete documents.") }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
