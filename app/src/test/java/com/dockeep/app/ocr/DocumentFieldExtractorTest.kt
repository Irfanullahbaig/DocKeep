package com.dockeep.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentFieldExtractorTest {
    @Test
    fun extractsStructuredIdCardFieldsWithoutAddress() {
        val lines = listOf(
            "Government of Pakistan",
            "National Identity Card",
            "Name: Irfan Ullah Baig",
            "Father Name: Muhammad Baig",
            "CNIC: 35202-1234567-1",
            "Date of Birth: 04-07-2000",
            "Address: XYZ Street Lahore",
            "Valid Until: 04-07-2030"
        )
        val fields = DocumentFieldExtractor.extract(lines, lines.joinToString("\n"), "identity_national_id")
        val byKey = fields.associateBy { it.key }

        assertEquals("Irfan Ullah Baig", byKey[OcrFieldKey.NAME]?.text)
        assertEquals("Muhammad Baig", byKey[OcrFieldKey.FATHER_NAME]?.text)
        assertEquals("35202-1234567-1", byKey[OcrFieldKey.DOCUMENT_NUMBER]?.text)
        assertNotNull(byKey[OcrFieldKey.DATE_OF_BIRTH]?.dateMillis)
        assertNotNull(byKey[OcrFieldKey.EXPIRY_DATE]?.dateMillis)
        assertTrue(fields.none { it.text.contains("XYZ", ignoreCase = true) })
        assertTrue(fields.none { it.text.contains("Government", ignoreCase = true) })
        assertTrue(byKey.values.all { it.usable })
    }

    @Test
    fun parsesCommonDateAndCnicFormats() {
        assertNotNull(DocumentFieldExtractor.parseDate("04/07/2000"))
        assertNotNull(DocumentFieldExtractor.parseDate("04 July 2000"))
        assertNotNull(DocumentFieldExtractor.parseDate("2000-07-04"))
        assertEquals("35202-1234567-1", DocumentFieldExtractor.normalizeCnic("35202 1234567 1"))
        assertEquals("03001234567", DocumentFieldExtractor.normalizePhone("03001234567"))
    }
}
