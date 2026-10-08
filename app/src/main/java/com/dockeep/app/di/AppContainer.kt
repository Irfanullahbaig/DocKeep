package com.dockeep.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.dockeep.app.data.backup.BackupManager
import com.dockeep.app.data.local.AppDatabase
import com.dockeep.app.data.repository.DocumentRepository
import com.dockeep.app.data.settings.SettingsRepository
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.ocr.DocumentOcrScanner
import com.dockeep.app.security.AppLockController
import com.dockeep.app.ui.add.AddDocumentViewModel
import com.dockeep.app.ui.details.DocumentDetailsViewModel
import com.dockeep.app.ui.documents.AllDocumentsViewModel
import com.dockeep.app.ui.home.HomeViewModel
import com.dockeep.app.ui.settings.SettingsViewModel

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.create(appContext)
    val fileStorage: LocalFileStorage = LocalFileStorage(appContext)
    val documentRepository: DocumentRepository = DocumentRepository(database, fileStorage)
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val backupManager: BackupManager = BackupManager(
        context = appContext,
        repository = documentRepository,
        storage = fileStorage,
        database = database
    )
    val appLockController: AppLockController = AppLockController()

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val handle = extras.createSavedStateHandle()
            return when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                    HomeViewModel(documentRepository) as T
                modelClass.isAssignableFrom(AllDocumentsViewModel::class.java) ->
                    AllDocumentsViewModel(documentRepository, handle) as T
                modelClass.isAssignableFrom(AddDocumentViewModel::class.java) ->
                    AddDocumentViewModel(
                        documentRepository,
                        fileStorage,
                        handle,
                        DocumentOcrScanner(appContext)
                    ) as T
                modelClass.isAssignableFrom(DocumentDetailsViewModel::class.java) ->
                    DocumentDetailsViewModel(documentRepository, fileStorage, handle) as T
                modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                    SettingsViewModel(settingsRepository, documentRepository, backupManager, appLockController) as T
                else -> error("Unknown ViewModel: ${modelClass.name}")
            }
        }
    }
}
