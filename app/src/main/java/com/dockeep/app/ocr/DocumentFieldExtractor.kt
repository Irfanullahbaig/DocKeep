package com.dockeep.app.ocr

import com.dockeep.app.domain.DefaultCategory
import com.dockeep.app.domain.FieldProfile
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DocumentFieldExtractor {

    fun extract(lines: List<String>, fullText: String, categoryId: String): List<ExtractedField> {
        val cleaned = lines.map { normalizeLine(it) }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return emptyList()
        val category = DefaultCategory.fromId(categoryId)
        val labeled = mutableListOf<ExtractedField>()
        cleaned.forEachIndexed { index, line ->
            val next = cleaned.getOrNull(index + 1).orEmpty()
            matchLabeled(line, next, category)?.let(labeled::add)
        }
        val unlabeled = unlabeledCandidates(cleaned, fullText, category)
        return merge(labeled, unlabeled, category)
    }

    private fun matchLabeled(line: String, next: String, category: DefaultCategory): ExtractedField? {
        val (label, inlineValue) = splitLabel(line) ?: return null
        val value = inlineValue.ifBlank { next.takeUnless { splitLabel(it) != null }.orEmpty() }.trim()
        if (value.isBlank() || value.length > 80) return null
        return when {
            isNameLabel(label) && looksLikePersonName(value) ->
                ExtractedField(OcrFieldKey.NAME, value, confidence = OcrConfidence.HIGH)
            isFatherLabel(label) && looksLikePersonName(value) ->
                ExtractedField(OcrFieldKey.FATHER_NAME, value, confidence = OcrConfidence.HIGH)
            isDoctorLabel(label) && looksLikePersonName(value.removePrefix("Dr").trim(' ', '.')) ->
                ExtractedField(OcrFieldKey.DOCTOR_NAME, value, confidence = OcrConfidence.HIGH)
            isHospitalLabel(label) && value.length in 3..60 ->
                ExtractedField(OcrFieldKey.HOSPITAL, value, confidence = OcrConfidence.HIGH)
            isDobLabel(label) -> dateField(OcrFieldKey.DATE_OF_BIRTH, value, OcrConfidence.HIGH)
            isIssueLabel(label) -> dateField(OcrFieldKey.ISSUE_DATE, value, OcrConfidence.HIGH)
            isExpiryLabel(label) -> dateField(OcrFieldKey.EXPIRY_DATE, value, OcrConfidence.HIGH)
            isRegistrationDateLabel(label) -> dateField(OcrFieldKey.REGISTRATION_DATE, value, OcrConfidence.HIGH)
            isCnicLabel(label) || isDocumentNumberLabel(label, category) ->
                identifierField(value, OcrConfidence.HIGH)
            isRegistrationLabel(label) ->
                ExtractedField(OcrFieldKey.DOCUMENT_NUMBER, normalizeIdentifier(value), confidence = OcrConfidence.HIGH)
            isChassisLabel(label) && value.length in 6..24 ->
                ExtractedField(OcrFieldKey.CHASSIS_NUMBER, value.filterNot { it.isWhitespace() }, confidence = OcrConfidence.HIGH)
            isEngineLabel(label) && value.length in 4..24 ->
                ExtractedField(OcrFieldKey.ENGINE_NUMBER, value.filterNot { it.isWhitespace() }, confidence = OcrConfidence.HIGH)
            isPhoneLabel(label) -> phoneField(value, OcrConfidence.HIGH)
            else -> null
        }
    }

    private fun unlabeledCandidates(
        lines: List<String>,
        fullText: String,
        category: DefaultCategory
    ): List<ExtractedField> {
        val haystack = "$fullText\n${lines.joinToString("\n")}"
        val found = mutableListOf<ExtractedField>()
        CNIC.findAll(haystack).map { it.value }.distinct().forEach { raw ->
            found += ExtractedField(
                OcrFieldKey.DOCUMENT_NUMBER,
                normalizeCnic(raw),
                confidence = OcrConfidence.MEDIUM
            )
        }
        PHONE.findAll(haystack.replace('\n', ' ')).map { it.value }.distinct().forEach { raw ->
            normalizePhone(raw)?.let {
                found += ExtractedField(OcrFieldKey.PHONE_NUMBER, it, confidence = OcrConfidence.MEDIUM)
            }
        }
        extractDates(haystack).forEach { date ->
            found += ExtractedField(
                key = OcrFieldKey.ISSUE_DATE,
                dateMillis = date,
                confidence = OcrConfidence.LOW
            )
        }
        if (category.profile == FieldProfile.VEHICLE) {
            lines.map { it.uppercase(Locale.US) }.forEach { line ->
                PLATE.find(line)?.value?.let { plate ->
                    if (!line.contains("CHASSIS") && !line.contains("ENGINE")) {
                        found += ExtractedField(
                            OcrFieldKey.DOCUMENT_NUMBER,
                            plate.replace("  ", " ").trim(),
                            confidence = OcrConfidence.MEDIUM
                        )
                    }
                }
            }
        }
        return found
    }

    private fun merge(
        labeled: List<ExtractedField>,
        unlabeled: List<ExtractedField>,
        category: DefaultCategory
    ): List<ExtractedField> {
        val byKey = linkedMapOf<OcrFieldKey, ExtractedField>()
        fun accept(field: ExtractedField?) {
            if (field == null || !fieldAllowed(field.key, category)) return
            if (field.text.isBlank() && field.dateMillis == null) return
            val current = byKey[field.key]
            if (current == null || current.confidence.ordinal > field.confidence.ordinal) {
                byKey[field.key] = field
            }
        }
        labeled.forEach(::accept)
        unlabeled.forEach { candidate ->
            when (candidate.key) {
                OcrFieldKey.DOCUMENT_NUMBER -> if (byKey[OcrFieldKey.DOCUMENT_NUMBER] == null) accept(candidate)
                OcrFieldKey.PHONE_NUMBER -> if (byKey[OcrFieldKey.PHONE_NUMBER] == null) accept(candidate)
                else -> Unit
            }
        }
        if (byKey[OcrFieldKey.EXPIRY_DATE] == null || byKey[OcrFieldKey.ISSUE_DATE] == null ||
            byKey[OcrFieldKey.DATE_OF_BIRTH] == null
        ) {
            assignUnlabeledDates(unlabeled.mapNotNull { it.dateMillis }.distinct().sorted(), byKey, category)
        }
        return byKey.values.toList()
    }

    private fun assignUnlabeledDates(
        dates: List<Long>,
        into: MutableMap<OcrFieldKey, ExtractedField>,
        category: DefaultCategory
    ) {
        if (dates.isEmpty()) return
        val today = LocalDate.now(ZoneOffset.UTC)
        val asDates = dates.map { it to millisToDate(it) }
        if (category.showsExpiry && into[OcrFieldKey.EXPIRY_DATE] == null) {
            asDates.lastOrNull { it.second.year >= today.year }?.let { (millis, _) ->
                into[OcrFieldKey.EXPIRY_DATE] = ExtractedField(
                    OcrFieldKey.EXPIRY_DATE,
                    dateMillis = millis,
                    confidence = if (asDates.size == 1) OcrConfidence.MEDIUM else OcrConfidence.MEDIUM
                )
            }
        }
        if (category.showsDateOfBirth && into[OcrFieldKey.DATE_OF_BIRTH] == null) {
            asDates.firstOrNull { today.year - it.second.year in 12..100 }?.let { (millis, _) ->
                into[OcrFieldKey.DATE_OF_BIRTH] = ExtractedField(
                    OcrFieldKey.DATE_OF_BIRTH,
                    dateMillis = millis,
                    confidence = OcrConfidence.MEDIUM
                )
            }
        }
        if (category.showsIssueDate && into[OcrFieldKey.ISSUE_DATE] == null) {
            val used = setOfNotNull(into[OcrFieldKey.EXPIRY_DATE]?.dateMillis, into[OcrFieldKey.DATE_OF_BIRTH]?.dateMillis)
            asDates.firstOrNull { it.first !in used && !it.second.isAfter(today) }?.let { (millis, _) ->
                into[OcrFieldKey.ISSUE_DATE] = ExtractedField(
                    OcrFieldKey.ISSUE_DATE,
                    dateMillis = millis,
                    confidence = OcrConfidence.MEDIUM
                )
            }
        }
        if (category.profile == FieldProfile.VEHICLE) {
            if (into[OcrFieldKey.REGISTRATION_DATE] == null) {
                into[OcrFieldKey.ISSUE_DATE]?.let { into[OcrFieldKey.REGISTRATION_DATE] = it.copy(key = OcrFieldKey.REGISTRATION_DATE) }
            }
        }
    }

    private fun fieldAllowed(key: OcrFieldKey, category: DefaultCategory): Boolean = when (category.profile) {
        FieldProfile.IDENTITY_CARD -> key in setOf(
            OcrFieldKey.NAME, OcrFieldKey.FATHER_NAME, OcrFieldKey.DOCUMENT_NUMBER,
            OcrFieldKey.DATE_OF_BIRTH, OcrFieldKey.ISSUE_DATE, OcrFieldKey.EXPIRY_DATE, OcrFieldKey.PHONE_NUMBER
        )
        FieldProfile.IDENTITY -> key in setOf(
            OcrFieldKey.NAME, OcrFieldKey.DOCUMENT_NUMBER, OcrFieldKey.DATE_OF_BIRTH,
            OcrFieldKey.ISSUE_DATE, OcrFieldKey.EXPIRY_DATE, OcrFieldKey.PHONE_NUMBER
        )
        FieldProfile.VEHICLE -> key in setOf(
            OcrFieldKey.NAME, OcrFieldKey.DOCUMENT_NUMBER, OcrFieldKey.CHASSIS_NUMBER,
            OcrFieldKey.ENGINE_NUMBER, OcrFieldKey.ISSUE_DATE, OcrFieldKey.EXPIRY_DATE,
            OcrFieldKey.REGISTRATION_DATE, OcrFieldKey.PHONE_NUMBER
        )
        FieldProfile.MEDICAL -> key in setOf(
            OcrFieldKey.NAME, OcrFieldKey.DOCTOR_NAME, OcrFieldKey.HOSPITAL,
            OcrFieldKey.DOCUMENT_NUMBER, OcrFieldKey.ISSUE_DATE, OcrFieldKey.PHONE_NUMBER
        )
        FieldProfile.POLICY, FieldProfile.RECORD -> key in setOf(
            OcrFieldKey.NAME, OcrFieldKey.DOCUMENT_NUMBER, OcrFieldKey.ISSUE_DATE,
            OcrFieldKey.EXPIRY_DATE, OcrFieldKey.PHONE_NUMBER
        )
        FieldProfile.CONTACT -> key in setOf(
            OcrFieldKey.NAME, OcrFieldKey.PHONE_NUMBER, OcrFieldKey.DOCUMENT_NUMBER
        )
    }

    private fun identifierField(value: String, confidence: OcrConfidence): ExtractedField {
        val cnic = CNIC.find(value)?.value
        val text = when {
            cnic != null -> normalizeCnic(cnic)
            else -> normalizeIdentifier(value)
        }
        return ExtractedField(OcrFieldKey.DOCUMENT_NUMBER, text, confidence = confidence)
    }

    private fun dateField(key: OcrFieldKey, raw: String, confidence: OcrConfidence): ExtractedField? {
        val millis = parseDate(raw) ?: return null
        return ExtractedField(key, dateMillis = millis, confidence = confidence)
    }

    private fun phoneField(raw: String, confidence: OcrConfidence): ExtractedField? {
        val phone = normalizePhone(raw) ?: return null
        return ExtractedField(OcrFieldKey.PHONE_NUMBER, phone, confidence = confidence)
    }

    internal fun parseDate(raw: String): Long? {
        val trimmed = raw.trim().replace(',', ' ').replace(Regex("\\s+"), " ")
        DATE_FORMATS.forEach { formatter ->
            try {
                val date = LocalDate.parse(trimmed, formatter)
                if (date.year in 1920..2100) return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        val digits = Regex("""(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""").find(trimmed) ?: return null
        val d = digits.groupValues[1].toInt()
        val m = digits.groupValues[2].toInt()
        var y = digits.groupValues[3].toInt()
        if (y < 100) y += if (y >= 30) 1900 else 2000
        return runCatching {
            LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun extractDates(text: String): List<Long> {
        val found = mutableListOf<Long>()
        DATE_TOKEN.findAll(text).forEach { match ->
            parseDate(match.value)?.let(found::add)
        }
        return found.distinct()
    }

    private fun splitLabel(line: String): Pair<String, String>? {
        val idx = line.indexOf(':').takeIf { it in 1..40 }
            ?: line.indexOf('\u2013').takeIf { it in 1..40 }
            ?: line.indexOf('-').takeIf { it in 3..28 && line.substring(0, it).none { ch -> ch.isDigit() } }
        if (idx == null) {
            LABEL_PREFIX.find(line)?.let { match ->
                return match.value.trim().lowercase(Locale.US) to line.removePrefix(match.value).trim()
            }
            return null
        }
        val label = line.substring(0, idx).trim().lowercase(Locale.US)
        val value = line.substring(idx + 1).trim()
        if (label.length !in 2..40) return null
        return label to value
    }

    private fun looksLikePersonName(value: String): Boolean {
        val cleaned = value.replace(Regex("[^A-Za-z .']"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.length !in 3..48) return false
        val words = cleaned.split(' ').filter { it.length > 1 }
        if (words.size !in 2..5) return false
        if (words.any { it.uppercase(Locale.US) in NAME_STOPWORDS }) return false
        if (words.any { it.any(Char::isDigit) }) return false
        return words.all { token -> token.first().isLetter() }
    }

    private fun normalizeLine(line: String): String =
        line.replace('\u00A0', ' ').replace(Regex("[|]+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun normalizeIdentifier(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().trimEnd('.', ',')

    internal fun normalizeCnic(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        if (digits.length != 13) return normalizeIdentifier(raw)
        return "${digits.substring(0, 5)}-${digits.substring(5, 12)}-${digits.substring(12)}"
    }

    internal fun normalizePhone(raw: String): String? {
        val compact = raw.filter { it.isDigit() || it == '+' }
        val digits = compact.filter(Char::isDigit)
        if (digits.length !in 10..15) return null
        if (digits.startsWith("92") && digits.length >= 12) {
            return "+92 ${digits.drop(2).take(3)} ${digits.drop(5)}"
        }
        if (digits.startsWith("03") && digits.length == 11) return digits
        if (digits.length == 10 && digits.startsWith("3")) return "0$digits"
        return if (compact.startsWith("+")) compact else digits
    }

    private fun millisToDate(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    private fun isNameLabel(label: String) =
        label.matches(Regex("^(full\\s*)?name$")) ||
            label.contains("holder name") ||
            label.contains("owner name") ||
            label.contains("patient name") ||
            label.contains("applicant") ||
            label == "name"

    private fun isFatherLabel(label: String) =
        label.contains("father") || label == "s/o" || label == "so" || label.contains("parent")

    private fun isDoctorLabel(label: String) =
        label.contains("doctor") || label.contains("physician") || label == "dr"

    private fun isHospitalLabel(label: String) =
        label.contains("hospital") || label.contains("clinic") || label.contains("facility")

    private fun isDobLabel(label: String) =
        label.contains("birth") || label == "dob" || label.contains("d.o.b")

    private fun isIssueLabel(label: String) =
        (label.contains("issue") || label.contains("issued") || label.contains("date of issue")) &&
            !label.contains("expiry") && !label.contains("birth")

    private fun isExpiryLabel(label: String) =
        label.contains("expir") || label.contains("valid until") || label.contains("valid till") ||
            label.contains("valid up") || label.contains("validity")

    private fun isRegistrationDateLabel(label: String) =
        (label.contains("registration date") || label.contains("purchase date") || label.contains("date of registration"))

    private fun isCnicLabel(label: String) =
        label.contains("cnic") || label.contains("national id") || label.contains("nic") ||
            label.contains("id number") || label.contains("id no") || label.contains("identity")

    private fun isDocumentNumberLabel(label: String, category: DefaultCategory) =
        label.contains("document no") || label.contains("document number") ||
            label.contains("card number") || label.contains("card no") ||
            label.contains("passport") || label.contains("license") || label.contains("licence") ||
            label.contains("policy no") || label.contains("policy number") ||
            label.contains("prescription") || label.contains("patient id") ||
            (category.profile == FieldProfile.MEDICAL && (label.contains("mr no") || label.contains("mrn")))

    private fun isRegistrationLabel(label: String) =
        label.contains("registration no") || label.contains("registration number") ||
            label.contains("reg no") || label.contains("reg. no") || label.contains("regn") ||
            label.contains("vehicle no") || label.contains("vehicle number") ||
            label.contains("plate") 

    private fun isChassisLabel(label: String) =
        label.contains("chassis") || label == "vin"

    private fun isEngineLabel(label: String) =
        label.contains("engine no") || label.contains("engine number") || label == "engine"

    private fun isPhoneLabel(label: String) =
        label.contains("phone") || label.contains("mobile") || label.contains("contact") || label == "tel"

    private val CNIC = Regex("""\b\d{5}[-\s]?\d{7}[-\s]?\d\b""")
    private val PHONE = Regex("""(?:\+?92[\s-]?)?0?3\d{2}[\s-]?\d{7}""")
    private val PLATE = Regex("""\b[A-Z]{2,3}[-\s]?\d{2,4}\b""")
    private val DATE_TOKEN = Regex(
        """\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b|\b\d{4}-\d{1,2}-\d{1,2}\b|\b\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}\b"""
    )
    private val LABEL_PREFIX = Regex(
        """^(Name|Full Name|Holder Name|Owner Name|Patient Name|Applicant Name|Father Name|Father's Name|CNIC|Date of Birth|DOB|Issue Date|Expiry Date|Valid Until)\s+""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("uuuu-M-d", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d-M-uu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d/M/uu", Locale.ENGLISH)
    )
    private val NAME_STOPWORDS = setOf(
        "GOVERNMENT", "PAKISTAN", "NATIONAL", "IDENTITY", "CARD", "ISLAMIC", "REPUBLIC",
        "NADRA", "LICENSE", "LICENCE", "PASSPORT", "CERTIFICATE", "DOCUMENT", "VEHICLE",
        "REGISTRATION", "AUTHORITY", "FEDERAL", "PROVINCE", "PUNJAB", "SINDH", "HOSPITAL",
        "PRESCRIPTION", "INSURANCE", "POLICY", "DRIVER", "DRIVING", "MEDICAL", "ORIGINAL"
    )
}
