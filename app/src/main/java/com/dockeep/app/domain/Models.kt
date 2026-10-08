package com.dockeep.app.domain

enum class DocumentGroup(
    val id: String,
    val displayName: String,
    val emoji: String,
    val sortOrder: Int
) {
    IDENTITY("identity", "Identity", "🪪", 0),
    VEHICLE("vehicle", "Vehicle", "🚗", 1),
    HEALTH("health", "Health", "🏥", 2),
    TRAVEL("travel", "Travel", "✈️", 3),
    HOME("home", "Home", "🏠", 4);

    companion object {
        fun fromId(id: String): DocumentGroup? = entries.firstOrNull { it.id == id }
    }
}

enum class FieldProfile {
    IDENTITY_CARD,
    IDENTITY,
    VEHICLE,
    POLICY,
    MEDICAL,
    RECORD,
    CONTACT
}

enum class DefaultCategory(
    val id: String,
    val displayName: String,
    val folderName: String,
    val sortOrder: Int,
    val group: DocumentGroup,
    val profile: FieldProfile,
    val numberLabel: String,
    val nameLabel: String = "Document name",
    val issueLabel: String = "Issue date (optional)",
    val expiryLabel: String = "Expiry date (optional)"
) {
    PASSPORT(
        "identity_passport", "Passport", "Identity/Passport", 0,
        DocumentGroup.IDENTITY, FieldProfile.IDENTITY, "Passport number", "Full name"
    ),
    NATIONAL_ID(
        "identity_national_id", "National ID", "Identity/National ID", 1,
        DocumentGroup.IDENTITY, FieldProfile.IDENTITY_CARD, "ID / CNIC number", "Full name"
    ),
    DRIVING_LICENCE(
        "identity_driving_licence", "Driving licence", "Identity/Driving licence", 2,
        DocumentGroup.IDENTITY, FieldProfile.IDENTITY, "Licence number", "Full name"
    ),
    RESIDENCE_PERMIT(
        "identity_residence_permit", "Residence permit", "Identity/Residence permit", 3,
        DocumentGroup.IDENTITY, FieldProfile.IDENTITY, "Permit number", "Full name"
    ),

    REGISTRATION(
        "vehicle_registration", "Registration", "Vehicle/Registration", 0,
        DocumentGroup.VEHICLE, FieldProfile.VEHICLE, "Registration number", "Owner name",
        "Registration date (optional)"
    ),
    VEHICLE_INSURANCE(
        "vehicle_insurance", "Insurance", "Vehicle/Insurance", 1,
        DocumentGroup.VEHICLE, FieldProfile.POLICY, "Policy number", "Holder name"
    ),
    INSPECTION(
        "vehicle_inspection", "Inspection", "Vehicle/Inspection", 2,
        DocumentGroup.VEHICLE, FieldProfile.RECORD, "Certificate number"
    ),
    SERVICE(
        "vehicle_service", "Service documents", "Vehicle/Service", 3,
        DocumentGroup.VEHICLE, FieldProfile.RECORD, "Reference number",
        issueLabel = "Service date (optional)", expiryLabel = "Next service (optional)"
    ),

    HEALTH_INSURANCE(
        "health_insurance", "Insurance card", "Health/Insurance card", 0,
        DocumentGroup.HEALTH, FieldProfile.POLICY, "Member number", "Holder name"
    ),
    PRESCRIPTION(
        "health_prescription", "Prescriptions", "Health/Prescriptions", 1,
        DocumentGroup.HEALTH, FieldProfile.MEDICAL, "Prescription number", "Patient name"
    ),
    VACCINATION(
        "health_vaccination", "Vaccination records", "Health/Vaccination", 2,
        DocumentGroup.HEALTH, FieldProfile.RECORD, "Record number", "Patient name",
        "Vaccination date (optional)", "Next due (optional)"
    ),
    MEDICAL_DOCUMENT(
        "health_medical", "Medical documents", "Health/Medical", 3,
        DocumentGroup.HEALTH, FieldProfile.MEDICAL, "Reference number", "Patient name"
    ),

    TRAVEL_PASSPORT(
        "travel_passport", "Passport", "Travel/Passport", 0,
        DocumentGroup.TRAVEL, FieldProfile.IDENTITY, "Passport number", "Full name"
    ),
    VISA(
        "travel_visa", "Visa", "Travel/Visa", 1,
        DocumentGroup.TRAVEL, FieldProfile.RECORD, "Visa number", "Full name"
    ),
    TRAVEL_INSURANCE(
        "travel_insurance", "Travel insurance", "Travel/Insurance", 2,
        DocumentGroup.TRAVEL, FieldProfile.POLICY, "Policy number", "Holder name"
    ),
    BOOKING(
        "travel_booking", "Booking documents", "Travel/Bookings", 3,
        DocumentGroup.TRAVEL, FieldProfile.RECORD, "Booking reference",
        issueLabel = "Start date (optional)", expiryLabel = "End date (optional)"
    ),
    EMERGENCY_CONTACT(
        "travel_emergency", "Emergency contacts", "Travel/Emergency contacts", 4,
        DocumentGroup.TRAVEL, FieldProfile.CONTACT, "Reference (optional)", "Contact name"
    ),

    RENTAL(
        "home_rental", "Rental agreement", "Home/Rental agreement", 0,
        DocumentGroup.HOME, FieldProfile.RECORD, "Agreement number",
        issueLabel = "Start date (optional)", expiryLabel = "End date (optional)"
    ),
    HOME_INSURANCE(
        "home_insurance", "Home insurance", "Home/Insurance", 1,
        DocumentGroup.HOME, FieldProfile.POLICY, "Policy number", "Holder name"
    ),
    UTILITY(
        "home_utility", "Utility documents", "Home/Utilities", 2,
        DocumentGroup.HOME, FieldProfile.RECORD, "Account number"
    ),
    WARRANTY(
        "home_warranty", "Appliance warranties", "Home/Warranties", 3,
        DocumentGroup.HOME, FieldProfile.RECORD, "Warranty number",
        issueLabel = "Purchase date (optional)"
    );

    val emoji: String get() = group.emoji

    val showsFatherName: Boolean get() = profile == FieldProfile.IDENTITY_CARD

    val showsDateOfBirth: Boolean
        get() = profile == FieldProfile.IDENTITY_CARD || profile == FieldProfile.IDENTITY

    val showsIssueDate: Boolean get() = profile != FieldProfile.CONTACT

    val showsExpiry: Boolean
        get() = this != PRESCRIPTION && profile != FieldProfile.CONTACT

    val showsVehicleIds: Boolean get() = profile == FieldProfile.VEHICLE

    val showsClinical: Boolean get() = profile == FieldProfile.MEDICAL

    val showsPhone: Boolean
        get() = profile == FieldProfile.IDENTITY_CARD ||
            profile == FieldProfile.IDENTITY ||
            profile == FieldProfile.MEDICAL ||
            profile == FieldProfile.CONTACT

    companion object {
        val default: DefaultCategory = NATIONAL_ID

        fun fromId(id: String): DefaultCategory =
            entries.firstOrNull { it.id == id } ?: default

        fun resolve(id: String?): DefaultCategory {
            if (id.isNullOrBlank()) return default
            DocumentGroup.fromId(id)?.let { group ->
                return entries.first { it.group == group }
            }
            return fromId(id)
        }

        fun inGroup(group: DocumentGroup): List<DefaultCategory> =
            entries.filter { it.group == group }.sortedBy { it.sortOrder }
    }
}

enum class FileType { IMAGE, PDF }

enum class ExpiryStatus {
    NONE,
    VALID,
    EXPIRING_SOON,
    EXPIRED;

    companion object {
        fun from(expiryDateMillis: Long?): ExpiryStatus {
            if (expiryDateMillis == null) return NONE
            val expiryDate = java.time.Instant.ofEpochMilli(expiryDateMillis)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
            val today = java.time.LocalDate.now()
            return when {
                expiryDate.isBefore(today) -> EXPIRED
                !expiryDate.isAfter(today.plusDays(30)) -> EXPIRING_SOON
                else -> VALID
            }
        }
    }
}

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val folderName: String,
    val sortOrder: Int,
    val documentCount: Int = 0,
    val groupId: String = "",
    val groupName: String = ""
)

fun List<Category>.asGroups(): List<Category> {
    val counts = groupBy { it.groupId }
    return DocumentGroup.entries.map { group ->
        Category(
            id = group.id,
            name = group.displayName,
            emoji = group.emoji,
            folderName = group.displayName,
            sortOrder = group.sortOrder,
            documentCount = counts[group.id].orEmpty().sumOf { it.documentCount },
            groupId = group.id,
            groupName = group.displayName
        )
    }
}

data class DocumentItem(
    val id: String,
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val categoryEmoji: String,
    val groupName: String = "",
    val documentNumber: String?,
    val issueDate: Long?,
    val expiryDate: Long?,
    val notes: String?,
    val relativePath: String,
    val mimeType: String,
    val fileType: FileType,
    val createdAt: Long,
    val updatedAt: Long
) {
    val expiryStatus: ExpiryStatus get() = ExpiryStatus.from(expiryDate)

    val categoryLabel: String
        get() = if (groupName.isBlank()) {
            "$categoryEmoji  $categoryName"
        } else {
            "$categoryEmoji  $groupName · $categoryName"
        }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AutoLockTimeout(val millis: Long, val label: String) {
    IMMEDIATE(0L, "Immediately"),
    ONE_MINUTE(60_000L, "After 1 minute"),
    FIVE_MINUTES(5 * 60_000L, "After 5 minutes"),
    FIFTEEN_MINUTES(15 * 60_000L, "After 15 minutes"),
    NEVER(-1L, "Never");

    companion object {
        fun fromMillis(value: Long): AutoLockTimeout =
            entries.firstOrNull { it.millis == value } ?: ONE_MINUTE
    }
}

data class AppSettings(
    val appLockEnabled: Boolean = true,
    val biometricEnabled: Boolean = true,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val expiryNotificationsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val permissionsOnboardingCompleted: Boolean = false
)
