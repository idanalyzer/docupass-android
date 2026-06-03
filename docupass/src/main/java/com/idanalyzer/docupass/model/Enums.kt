package com.idanalyzer.docupass.model

/**
 * API region. The DocuPass v3 mobile endpoints live in two regions; the correct
 * one is derived from the reference prefix (see [fromReference]).
 *
 * - US -> https://api2.idanalyzer.com/docupassappv3
 * - EU -> https://api2-eu.idanalyzer.com/docupassappv3
 */
enum class DocuPassRegion(val baseUrl: String) {
    US("https://api2.idanalyzer.com/docupassappv3"),
    EU("https://api2-eu.idanalyzer.com/docupassappv3");

    companion object {
        /**
         * A v3 reference is `US`/`EU` + 24 chars. A reference that starts with
         * `EU` (case-insensitive) is an EU session; everything else is US.
         */
        fun fromReference(reference: String): DocuPassRegion =
            if (reference.trim().startsWith("EU", ignoreCase = true)) EU else US
    }
}

/**
 * The live step the server is currently asking the client to perform. Returned
 * in the `task` field of a successful get_action / POST response.
 *
 * Terminal/completion states are NOT tasks — they arrive as an error envelope
 * (see [com.idanalyzer.docupass.model.DocuPassErrorCode]).
 */
enum class DocuPassTask(val wire: String) {
    PHONE("phone"),
    CUSTOM_FORM("customform"),
    DOCUMENT("document"),
    FACE("face"),
    CONTRACT("contract"),
    PARTY_PENDING("party_pending"),
    UNKNOWN("");

    companion object {
        fun fromWire(value: String?): DocuPassTask =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** Which document sides the session requires. */
enum class DocumentSide(val code: Int) {
    AUTO(0),
    FRONT_ONLY(1),
    FRONT_AND_BACK(2);

    companion object {
        fun fromCode(code: Int): DocumentSide =
            entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/** Custom-form field input type. `DROPDOWN` carries options in `fieldData`. */
enum class CustomFieldType(val code: Int) {
    TEXT(0),
    MULTILINE(1),
    DROPDOWN(2);

    companion object {
        fun fromCode(code: Int): CustomFieldType =
            entries.firstOrNull { it.code == code } ?: TEXT
    }
}

/** Phone-verification delivery channel. */
enum class PhoneChannel(val wire: String) {
    SMS("sms"),
    CALL("call");
}
