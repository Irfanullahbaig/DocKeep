package com.dockeep.app.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dockeep.app.domain.AppSettings
import com.dockeep.app.domain.AutoLockTimeout

class BiometricAuthenticator(private val activity: FragmentActivity) {
    private val manager = BiometricManager.from(activity)

    fun canAuthenticate(allowBiometric: Boolean = true): Boolean {
        val authenticators = authenticators(allowBiometric)
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enrollmentStatus(allowBiometric: Boolean = true): AuthAvailability {
        return when (manager.canAuthenticate(authenticators(allowBiometric))) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AuthAvailability.UNAVAILABLE
            else -> AuthAvailability.UNAVAILABLE
        }
    }

    fun authenticate(
        title: String,
        subtitle: String,
        allowBiometric: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("Unable to authenticate right now.")
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onFailed()
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                onFailed()
            }
        }
        try {
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            prompt.authenticate(promptInfo(title, subtitle, allowBiometric))
        } catch (error: RuntimeException) {
            onError(error.message ?: "Authentication isn’t available on this device.")
        }
    }

    private fun promptInfo(
        title: String,
        subtitle: String,
        allowBiometric: Boolean
    ): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = authenticators(allowBiometric)
            builder.setAllowedAuthenticators(authenticators)
            if (authenticators and DEVICE_CREDENTIAL == 0) {
                builder.setNegativeButtonText("Cancel")
            }
        } else {
            if (allowBiometric) {
                builder.setAllowedAuthenticators(BIOMETRIC_WEAK)
            }
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        return builder.build()
    }

    private fun authenticators(allowBiometric: Boolean): Int {
        if (!allowBiometric) return DEVICE_CREDENTIAL
        val strongAndDevice = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        return when {
            manager.canAuthenticate(strongAndDevice) == BiometricManager.BIOMETRIC_SUCCESS ->
                strongAndDevice
            manager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS ->
                DEVICE_CREDENTIAL
            manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS ->
                BIOMETRIC_STRONG
            manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS ->
                BIOMETRIC_WEAK
            else -> DEVICE_CREDENTIAL
        }
    }
}

enum class AuthAvailability {
    READY,
    NONE_ENROLLED,
    UNAVAILABLE
}

class AppLockController {
    @Volatile
    var unlockedInSession: Boolean = false
        private set

    @Volatile
    private var lastBackgroundAt: Long? = null

    @Volatile
    private var externalActivityStartedAt: Long? = null

    fun markUnlocked() {
        unlockedInSession = true
        lastBackgroundAt = null
    }

    fun lockNow() {
        unlockedInSession = false
        lastBackgroundAt = System.currentTimeMillis()
    }

    /**
     * Call right before DocKeep opens the camera, a file picker, the share sheet or system
     * settings. Leaving for one of those doesn't count as backgrounding, so the app doesn't
     * lock (and throw away the screen waiting for the result) while the user takes a photo.
     */
    fun expectExternalActivity() {
        externalActivityStartedAt = System.currentTimeMillis()
    }

    fun cancelExternalActivity() {
        externalActivityStartedAt = null
    }

    fun onBackground() {
        lastBackgroundAt = System.currentTimeMillis()
    }

    fun shouldLock(settings: AppSettings): Boolean {
        if (!settings.appLockEnabled) return false
        if (!unlockedInSession) return true
        val external = externalActivityStartedAt
        if (external != null) {
            externalActivityStartedAt = null
            if (System.currentTimeMillis() - external < ExternalActivityGraceMillis) {
                lastBackgroundAt = null
                return false
            }
        }
        return when (settings.autoLockTimeout) {
            AutoLockTimeout.NEVER -> false
            AutoLockTimeout.IMMEDIATE -> lastBackgroundAt != null
            else -> {
                val backgrounded = lastBackgroundAt ?: return false
                System.currentTimeMillis() - backgrounded >= settings.autoLockTimeout.millis
            }
        }
    }
}

private const val ExternalActivityGraceMillis = 10 * 60_000L
