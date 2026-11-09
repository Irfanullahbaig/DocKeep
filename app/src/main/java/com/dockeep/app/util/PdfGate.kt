package com.dockeep.app.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PdfGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: () -> T): T = mutex.withLock { block() }
}