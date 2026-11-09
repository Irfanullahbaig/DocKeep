package com.dockeep.app.domain

import com.dockeep.app.util.parseDisplayDate

data class ParsedDocumentNotes(
    val fatherName: String = "",
    val dateOfBirth: Long? = null,
    val phoneNumber: String = "",
    val chassisNumber: String = "",
    val engineNumber: String = "",
    val doctorName: String = "",
    val hospital: String = "",
    val notes: String = ""
)

fun parseDocumentNotes(raw: String?): ParsedDocumentNotes {
    if (raw.isNullOrBlank()) return ParsedDocumentNotes()
    var parsed = ParsedDocumentNotes()
    val remaining = mutableListOf<String>()
    raw.lines().forEach { line ->
        val match = knownFields.firstOrNull { field ->
            line.trimStart().startsWith(field.prefix, ignoreCase = true)
        }
        if (match == null) {
            remaining += line
        } else {
            val value = line.trim().substringAfter(":").trim()
            val updated = if (value.isBlank()) null else match.apply(parsed, value)
            if (updated == null) remaining += line else parsed = updated
        }
    }
    return parsed.copy(notes = remaining.joinToString("\n").trim())
}

private data class NoteField(
    val prefix: String,
    val apply: (ParsedDocumentNotes, String) -> ParsedDocumentNotes?
)

private val knownFields = listOf(
    NoteField("Father's Name:") { parsed, value -> parsed.copy(fatherName = value) },
    NoteField("Father Name:") { parsed, value -> parsed.copy(fatherName = value) },
    NoteField("Date of Birth:") { parsed, value ->
        parseDisplayDate(value)?.let { parsed.copy(dateOfBirth = it) }
    },
    NoteField("Phone:") { parsed, value -> parsed.copy(phoneNumber = value) },
    NoteField("Chassis Number:") { parsed, value -> parsed.copy(chassisNumber = value) },
    NoteField("Engine Number:") { parsed, value -> parsed.copy(engineNumber = value) },
    NoteField("Doctor:") { parsed, value -> parsed.copy(doctorName = value) },
    NoteField("Hospital:") { parsed, value -> parsed.copy(hospital = value) }
)
