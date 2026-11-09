package com.dockeep.app.domain

import com.dockeep.app.util.toDisplayDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DocumentNotesTest {
    @Test
    fun splitsStructuredLinesFromFreeformNotes() {
        val dob = LocalDate.of(2000, 7, 4)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val parsed = parseDocumentNotes(
            """
            Father's Name: Muhammad Baig
            Date of Birth: ${dob.toDisplayDate()}
            Phone: 03001234567
            Chassis Number: ABC123
            Engine Number: ENG9
            Doctor: Dr Khan
            Hospital: City Clinic
            Keep this note
            """.trimIndent()
        )

        assertEquals("Muhammad Baig", parsed.fatherName)
        assertEquals(dob, parsed.dateOfBirth)
        assertEquals("03001234567", parsed.phoneNumber)
        assertEquals("ABC123", parsed.chassisNumber)
        assertEquals("ENG9", parsed.engineNumber)
        assertEquals("Dr Khan", parsed.doctorName)
        assertEquals("City Clinic", parsed.hospital)
        assertEquals("Keep this note", parsed.notes)
    }

    @Test
    fun keepsUnparseableDateInNotes() {
        val parsed = parseDocumentNotes("Date of Birth: not-a-date\nHello")
        assertNull(parsed.dateOfBirth)
        assertTrue(parsed.notes.contains("not-a-date"))
        assertTrue(parsed.notes.contains("Hello"))
    }
}
