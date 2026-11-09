package com.dockeep.app.security

import com.dockeep.app.domain.AppSettings
import com.dockeep.app.domain.AutoLockTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {
    @Test
    fun locksUntilUnlockedAndAfterTimeout() {
        var time = 1_000_000L
        val lock = AppLockController { time }
        val settings = AppSettings(
            appLockEnabled = true,
            autoLockTimeout = AutoLockTimeout.ONE_MINUTE
        )

        assertTrue(lock.shouldLock(settings))
        lock.markUnlocked()
        assertFalse(lock.shouldLock(settings))

        lock.onBackground()
        time += 59_000
        assertFalse(lock.shouldLock(settings))
        time += 1_000
        assertTrue(lock.shouldLock(settings))
    }

    @Test
    fun immediateAndNeverFollowTheirTimeout() {
        var time = 5_000L
        val lock = AppLockController { time }
        lock.markUnlocked()

        val immediate = AppSettings(appLockEnabled = true, autoLockTimeout = AutoLockTimeout.IMMEDIATE)
        assertFalse(lock.shouldLock(immediate))
        lock.onBackground()
        assertTrue(lock.shouldLock(immediate))

        lock.markUnlocked()
        val never = AppSettings(appLockEnabled = true, autoLockTimeout = AutoLockTimeout.NEVER)
        lock.onBackground()
        time += 60 * 60_000
        assertFalse(lock.shouldLock(never))
    }

    @Test
    fun disabledLockNeverLocks() {
        val lock = AppLockController()
        assertFalse(lock.shouldLock(AppSettings(appLockEnabled = false)))
    }
}
