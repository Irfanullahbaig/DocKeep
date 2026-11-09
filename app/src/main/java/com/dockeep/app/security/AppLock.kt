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
import java.util.concurrent.atomic.AtomicBoolean

class BiometricAuthenticator(private val activity: FragmentActivity) {
    private val prompting = AtomicBoolean(false)

    fun canAuthenticate(allowBiometric: Boolean = true): Boolean {
        return enrollmentStatus(allowBiometric) == AuthAvailability.READY
    }

    fun enrollmentStatus(allowBiometric: Boolean = true): AuthAvailability {
        return try {
            val manager = BiometricManager.from(activity)
            when (manager.canAuthenticate(authenticators(manager, allowBiometric))) {
                BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.READY
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthAvailability.NONE_ENROLLED
                else -> AuthAvailability.UNAVAILABLE
            }
        } catch (_: RuntimeException) {
            AuthAvailability.UNAVAILABLE
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
        if (!prompting.compareAndSet(false, true)) return
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting.set(false)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting.set(false)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onFailed()
                } else {
                    onError(errString.toString().ifBlank { "Authentication isn’t available on this device." })
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
            prompting.set(false)
            onError(error.message ?: "Authentication isn’t available on this device.")
        }
    }

    private fun promptInfo(
        title: String,
        subtitle: String,
        allowBiometric: Boolean
    ): BiometricPrompt.PromptInfo {
        val manager = BiometricManager.from(activity)
        val plan = authPromptPlan(
            sdkAtLeastR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            allowBiometric = allowBiometric,
            canStrongAndDevice = can(manager, BIOMETRIC_STRONG or DEVICE_CREDENTIAL),
            canDevice = can(manager, DEVICE_CREDENTIAL),
            canStrong = can(manager, BIOMETRIC_STRONG),
            canWeak = can(manager, BIOMETRIC_WEAK)
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (plan.useLegacyDeviceCredential) {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        } else {
            builder.setAllowedAuthenticators(plan.authenticators)
            plan.negativeButtonText?.let(builder::setNegativeButtonText)
        }
        return builder.build()
    }

    private fun authenticators(manager: BiometricManager, allowBiometric: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return BIOMETRIC_WEAK
        return authPromptPlan(
            sdkAtLeastR = true,
            allowBiometric = allowBiometric,
            canStrongAndDevice = can(manager, BIOMETRIC_STRONG or DEVICE_CREDENTIAL),
            canDevice = can(manager, DEVICE_CREDENTIAL),
            canStrong = can(manager, BIOMETRIC_STRONG),
            canWeak = can(manager, BIOMETRIC_WEAK)
        ).authenticators
    }

    private fun can(manager: BiometricManager, authenticators: Int): Boolean {
        return runCatching {
            manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }
}

internal data class AuthPromptPlan(
    val authenticators: Int,
    val useLegacyDeviceCredential: Boolean,
    val negativeButtonText: String?
)

internal fun authPromptPlan(
    sdkAtLeastR: Boolean,
    allowBiometric: Boolean,
    canStrongAndDevice: Boolean,
    canDevice: Boolean,
    canStrong: Boolean,
    canWeak: Boolean
): AuthPromptPlan {
    if (!sdkAtLeastR) {
        return AuthPromptPlan(
            authenticators = 0,
            useLegacyDeviceCredential = true,
            negativeButtonText = null
        )
    }
    val authenticators = when {
        !allowBiometric -> DEVICE_CREDENTIAL
        canStrongAndDevice -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        canDevice -> DEVICE_CREDENTIAL
        canStrong -> BIOMETRIC_STRONG
        canWeak -> BIOMETRIC_WEAK
        else -> DEVICE_CREDENTIAL
    }
    val negative = if (authenticators and DEVICE_CREDENTIAL == 0) "Cancel" else null
    return AuthPromptPlan(
        authenticators = authenticators,
        useLegacyDeviceCredential = false,
        negativeButtonText = negative
    )
}

enum class AuthAvailability {
    READY,
    NONE_ENROLLED,
    UNAVAILABLE
}

class AppLockController(
    private val now: () -> Long = { System.currentTimeMillis() }
) {
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
        lastBackgroundAt = now()
    }

    /**
     * Call right before DocKeep opens the camera, a file picker, the share sheet or system
     * settings. Leaving for one of those doesn't count as backgrounding, so the app doesn't
     * lock (and throw away the screen waiting for the result) while the user takes a photo.
     */
    fun expectExternalActivity() {
        externalActivityStartedAt = now()
    }

    fun cancelExternalActivity() {
        externalActivityStartedAt = null
    }

    fun onBackground() {
        lastBackgroundAt = now()
    }

    fun shouldLock(settings: AppSettings): Boolean {
        if (!settings.appLockEnabled) return false
        if (!unlockedInSession) return true
        val external = externalActivityStartedAt
        if (external != null) {
            externalActivityStartedAt = null
            if (now() - external < ExternalActivityGraceMillis) {
                lastBackgroundAt = null
                return false
            }
        }
        return when (settings.autoLockTimeout) {
            AutoLockTimeout.NEVER -> false
            AutoLockTimeout.IMMEDIATE -> lastBackgroundAt != null
            else -> {
                val backgrounded = lastBackgroundAt ?: return false
                now() - backgrounded >= settings.autoLockTimeout.millis
            }
        }
    }
}

private const val ExternalActivityGraceMillis = 10 * 60_000L
