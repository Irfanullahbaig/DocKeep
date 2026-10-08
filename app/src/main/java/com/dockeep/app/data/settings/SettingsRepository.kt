package com.dockeep.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.dockeep.app.domain.AppSettings
import com.dockeep.app.domain.AutoLockTimeout
import com.dockeep.app.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "dockeep_settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        AppSettings(
            appLockEnabled = prefs[Keys.APP_LOCK] ?: true,
            biometricEnabled = prefs[Keys.BIOMETRIC] ?: true,
            autoLockTimeout = AutoLockTimeout.fromMillis(prefs[Keys.AUTO_LOCK] ?: AutoLockTimeout.ONE_MINUTE.millis),
            expiryNotificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            permissionsOnboardingCompleted = prefs[Keys.PERMISSIONS_ONBOARDING] ?: false
        )
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC] = enabled }
    }

    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        dataStore.edit { it[Keys.AUTO_LOCK] = timeout.millis }
    }

    suspend fun setExpiryNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setPermissionsOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.PERMISSIONS_ONBOARDING] = completed }
    }

    private object Keys {
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK = longPreferencesKey("auto_lock_timeout")
        val NOTIFICATIONS = booleanPreferencesKey("expiry_notifications")
        val THEME = stringPreferencesKey("theme_mode")
        val PERMISSIONS_ONBOARDING = booleanPreferencesKey("permissions_onboarding_completed")
    }
}
