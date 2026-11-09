package com.dockeep.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPromptPlanTest {
    @Test
    fun legacyApiDoesNotMixAuthenticatorCalls() {
        val plan = authPromptPlan(
            sdkAtLeastR = false,
            allowBiometric = true,
            canStrongAndDevice = true,
            canDevice = true,
            canStrong = true,
            canWeak = true
        )
        assertTrue(plan.useLegacyDeviceCredential)
        assertNull(plan.negativeButtonText)
        assertEquals(0, plan.authenticators)
    }

    @Test
    fun deviceCredentialPromptOmitsNegativeButton() {
        val plan = authPromptPlan(
            sdkAtLeastR = true,
            allowBiometric = true,
            canStrongAndDevice = true,
            canDevice = true,
            canStrong = true,
            canWeak = true
        )
        assertFalse(plan.useLegacyDeviceCredential)
        assertNull(plan.negativeButtonText)
        assertTrue(plan.authenticators and DEVICE_CREDENTIAL_BIT != 0)
    }

    @Test
    fun biometricOnlyPromptIncludesCancel() {
        val plan = authPromptPlan(
            sdkAtLeastR = true,
            allowBiometric = true,
            canStrongAndDevice = false,
            canDevice = false,
            canStrong = true,
            canWeak = false
        )
        assertEquals("Cancel", plan.negativeButtonText)
        assertEquals(0, plan.authenticators and DEVICE_CREDENTIAL_BIT)
    }

    private companion object {
        const val DEVICE_CREDENTIAL_BIT = 1 shl 15
    }
}
