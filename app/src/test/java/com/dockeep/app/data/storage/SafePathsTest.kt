package com.dockeep.app.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafePathsTest {
    @Test
    fun acceptsNestedDocumentPaths() {
        assertEquals(
            "Identity/Passport/doc.jpg",
            normalizeSafeRelativePath("Identity/Passport/doc.jpg")
        )
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertNull(normalizeSafeRelativePath("../secret.txt"))
        assertNull(normalizeSafeRelativePath("files/../../secret.txt"))
        assertNull(normalizeSafeRelativePath("/etc/passwd"))
        assertNull(normalizeSafeRelativePath("..\\secret.txt"))
        assertNull(normalizeSafeRelativePath(""))
        assertNull(normalizeSafeRelativePath("folder/"))
    }
}
