package com.idanalyzer.docupass.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The flat `get_action` response.
 * Also returned in the body of every successful POST step, so the client always
 * has the next state to render. Unknown JSON keys are ignored (forward-compat).
 *
 * Field names mirror the server's exact JSON tags.
 */
@Serializable
data class DocuPassSession(
    val success: Boolean = false,
    val task: String? = null,
    val sessionId: String? = null,
    val reference: String? = null,

    // Branding / behaviour
    val companyName: String = "",
    val welcomeMessage: String = "",
    val logoURL: String = "",
    val language: String = "",
    val gps: Boolean = false,
    val allowFileUpload: Boolean = false,
    val reviewData: Boolean = false,
    val preloadFaceLib: Boolean = false,

    // Document
    val documentSide: Int = 0,
    val acceptedDocumentCountry: String = "",
    val acceptedDocumentType: String = "",
    val selectedDocumentCountry: String = "",
    val selectedDocumentType: String = "",
    val hasDocumentFile: Boolean = false,
    val hasFaceFile: Boolean = false,
    val verifyDocumentNo: String = "",
    val verifyName: String = "",
    val verifyDob: String = "",
    val verifyAge: String = "",
    val verifyAddress: String = "",
    @SerialName("verifyPostcode") val verifyPostCode: String = "",

    // Phone
    val userPhone: String = "",
    val phoneCountryCode: List<PhoneCode> = emptyList(),

    // Custom form
    val customField: List<CustomField> = emptyList(),

    // Contract
    val contractSource: String = "",
) {
    /** Parsed convenience accessors. */
    val parsedTask: DocuPassTask get() = DocuPassTask.fromWire(task)
    val parsedDocumentSide: DocumentSide get() = DocumentSide.fromCode(documentSide)

    /** Front-only when the session says so or the selected doc is a passport ("P"). */
    val isFrontOnly: Boolean
        get() = parsedDocumentSide == DocumentSide.FRONT_ONLY ||
            selectedDocumentType.equals("P", ignoreCase = true)

    /** Accepted ISO-2 country codes, or empty = no restriction. */
    val acceptedCountries: List<String>
        get() = acceptedDocumentCountry.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Accepted 1-char document-type codes, or empty = no restriction. */
    val acceptedTypes: List<String>
        get() = acceptedDocumentType.map { it.toString() }.filter { it.isNotBlank() }
}

/** Phone dialing code entry for the phone step. */
@Serializable
data class PhoneCode(
    @SerialName("dial_code") val dialCode: String = "",
    val code: String = "",
    val name: String = "",
)

/**
 * A custom-form field. `fieldId` is a server-computed hash of `fieldLabel` and
 * MUST be used verbatim as the save_form key.
 */
@Serializable
data class CustomField(
    val fieldLabel: String = "",
    val fieldDescription: String = "",
    val fieldId: String = "",
    val fieldType: Int = 0,
    /** Dropdown options as "Display\tvalue|Display\tvalue". Empty otherwise. */
    val fieldData: String = "",
) {
    val parsedType: CustomFieldType get() = CustomFieldType.fromCode(fieldType)

    /** Parsed dropdown options (display -> value); empty for non-dropdown fields. */
    val dropdownOptions: List<DropdownOption>
        get() = if (fieldData.isBlank()) emptyList() else fieldData.split("|").mapNotNull { raw ->
            val parts = raw.split("\t")
            when (parts.size) {
                2 -> DropdownOption(parts[0], parts[1])
                1 -> DropdownOption(parts[0], parts[0])
                else -> null
            }
        }
}

data class DropdownOption(val display: String, val value: String)
