package com.dockeep.app.ocr

enum class OcrConfidence {
    HIGH,
    MEDIUM,
    LOW
}

enum class OcrFieldKey {
    NAME,
    FATHER_NAME,
    DOCUMENT_NUMBER,
    DATE_OF_BIRTH,
    ISSUE_DATE,
    EXPIRY_DATE,
    REGISTRATION_DATE,
    PHONE_NUMBER,
    CHASSIS_NUMBER,
    ENGINE_NUMBER,
    DOCTOR_NAME,
    HOSPITAL
}

data class ExtractedField(
    val key: OcrFieldKey,
    val text: String = "",
    val dateMillis: Long? = null,
    val confidence: OcrConfidence
) {
    val usable: Boolean get() = confidence != OcrConfidence.LOW
}

data class StructuredOcrResult(
    val fields: List<ExtractedField>,
    val qualityWarning: String? = null
) {
    fun usable(key: OcrFieldKey): ExtractedField? =
        fields.firstOrNull { it.key == key && it.usable }
}

sealed interface OcrScanResult {
    data class Success(val data: StructuredOcrResult) : OcrScanResult
    data class Recoverable(val message: String) : OcrScanResult
}
