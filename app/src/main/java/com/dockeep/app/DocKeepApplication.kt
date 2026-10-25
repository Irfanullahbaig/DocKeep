package com.dockeep.app

import android.app.Application
import com.dockeep.app.di.AppContainer
import com.dockeep.app.notifications.ExpiryNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DocKeepApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runCatching { ExpiryNotifications.ensureChannel(this) }
        applicationScope.launch {
            runCatching { container.documentRepository.seedIfNeeded() }
            runCatching { container.fileStorage.clearStaleTemps() }
            runCatching {
                val settings = container.settingsRepository.settings.first()
                if (settings.expiryNotificationsEnabled) {
                    ExpiryNotifications.schedule(this@DocKeepApplication)
                }
            }
        }
    }
}
