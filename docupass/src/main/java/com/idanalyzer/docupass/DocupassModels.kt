package com.idanalyzer.docupass

data class DocupassApiConfig(
    val enabled: Boolean = true,
    val baseUrl: String? = null,
    val reference: String? = null,
    val partyId: String? = null,
    val sessionId: String? = null,
    val authorization: String? = null,
    val geolocation: String? = null,
    val disableSslValidation: Boolean = false,
    val connectTimeoutMs: Int = 20_000,
    val readTimeoutMs: Int = 20_000
)

object DocupassConfigFactory {
    fun fromReference(
        reference: String,
        partyId: String? = null,
        geolocation: String? = null,
        enabled: Boolean = true
    ): DocupassApiConfig {
        return DocupassApiConfig(
            enabled = enabled,
            baseUrl = resolveDocupassEndpoint(reference),
            reference = reference,
            partyId = partyId,
            geolocation = geolocation
        )
    }
}

const val DOCUPASS_API_ENDPOINT_US = "https://api2.idanalyzer.com/docupassappv3"
const val DOCUPASS_API_ENDPOINT_EU = "https://api2-eu.idanalyzer.com/docupassappv3"

fun resolveDocupassEndpoint(reference: String?): String {
    val ref = reference?.trim().orEmpty()
    return if (ref.startsWith("EU", ignoreCase = true)) {
        DOCUPASS_API_ENDPOINT_EU
    } else {
        DOCUPASS_API_ENDPOINT_US
    }
}

fun docupassConfigFromReference(
    reference: String,
    partyId: String? = null,
    geolocation: String? = null,
    enabled: Boolean = true
): DocupassApiConfig {
    return DocupassConfigFactory.fromReference(reference, partyId, geolocation, enabled)
}

data class DocupassCustomField(
    val fieldId: String,
    val fieldLabel: String,
    val fieldDescription: String,
    val fieldType: Int,
    val fieldData: String
)

data class DocupassPhoneCountryCode(
    val name: String,
    val dialCode: String,
    val code: String
)

data class DocupassSessionState(
    val success: Boolean,
    val sessionId: String?,
    val task: String?,
    val reference: String?,
    val acceptedDocumentCountry: String?,
    val acceptedDocumentType: String?,
    val selectedDocumentCountry: String?,
    val selectedDocumentType: String?,
    val allowFileUpload: Boolean,
    val documentSide: Int,
    val gps: Boolean,
    val reviewData: Boolean,
    val logoUrl: String?,
    val companyName: String?,
    val welcomeMessage: String?,
    val language: String?,
    val userPhone: String?,
    val hasFaceFile: Boolean,
    val hasDocumentFile: Boolean,
    val verifyDocumentNo: String?,
    val verifyName: String?,
    val verifyDob: String?,
    val verifyAge: String?,
    val verifyAddress: String?,
    val verifyPostcode: String?,
    val preloadFaceLib: Boolean,
    val contractSource: String?,
    val customFields: List<DocupassCustomField>,
    val phoneCountryCodes: List<DocupassPhoneCountryCode>,
    val rawJson: String
) {
    fun acceptedDocumentCountryCodes(): List<String> {
        return acceptedDocumentCountry.commaSeparatedValues()
    }

    fun acceptedDocumentTypeCodes(): List<String> {
        return acceptedDocumentType.documentTypeCodeValues()
    }
}

typealias DocupassGetActionResponse = DocupassSessionState

sealed class DocupassApiResult<out T> {
    data class Success<T>(val data: T) : DocupassApiResult<T>()

    data class Error(
        val message: String,
        val code: String? = null,
        val httpStatus: Int? = null,
        val rawBody: String? = null
    ) : DocupassApiResult<Nothing>()
}

sealed class KYCStep {
    data class PhoneVerification(val state: DocupassSessionState) : KYCStep()
    data class CustomForm(val fields: List<DocupassCustomField>) : KYCStep()
    data class SelectCountry(val filterCodes: List<String>? = null) : KYCStep()
    data object SelectDocument : KYCStep()
    data object CaptureDocument : KYCStep()
    data class FaceVerification(val actions: List<KYCAction>) : KYCStep()
    data class Contract(val state: DocupassSessionState) : KYCStep()
    data object PartyPending : KYCStep()
    data object Success : KYCStep()
    data class Failed(val error: DocupassNormalizedError? = null) : KYCStep()
}

object Verify {
    fun Phone(state: DocupassSessionState) = KYCStep.PhoneVerification(state)
    fun CustomForm(fields: List<DocupassCustomField>) = KYCStep.CustomForm(fields)
    val Country = KYCStep.SelectCountry()
    fun Country(only: List<String>) = KYCStep.SelectCountry(only)
    val Document = KYCStep.SelectDocument
    val DocumentCapture = KYCStep.CaptureDocument
    fun Biometric(actions: List<KYCAction>) = KYCStep.FaceVerification(actions)
    fun Contract(state: DocupassSessionState) = KYCStep.Contract(state)
    val PartyPending = KYCStep.PartyPending
}

object DocupassWorkflow {
    fun defaultWorkflow(): List<KYCStep> {
        return listOf(
            Verify.Country,
            Verify.Document,
            Verify.DocumentCapture,
            Verify.Biometric(KYCAction.entries.toList())
        )
    }
}

data class KYCResult(
    val country: KYCCountry? = null,
    val documentType: KYCDocumentType? = null,
    val documentFrontBase64: String? = null,
    val documentBackBase64: String? = null,
    val faceBase64List: List<String> = emptyList(),
    val isFaceVerified: Boolean = false,
    val serverTask: String? = null,
    val sessionId: String? = null,
    val sessionState: DocupassSessionState? = null,
    val terminalError: DocupassNormalizedError? = null
)

enum class KYCDocumentType(
    val label: String,
    val apiTypeCode: String,
    val requiresBackSide: Boolean
) {
    PASSPORT("Passport", "P", false),
    DRIVER_LICENSE("Driver License", "D", true),
    IDENTITY_CARD("Identity Card", "I", true)
}

data class KYCCountry(
    val code: String,
    val name: String,
    val flag: String = ""
)

data class DocupassContractSignatureField(
    val uid: String,
    val label: String,
    val party: String?
)

enum class KYCAction(val instruction: String) {
    TURN_LEFT("TURN HEAD LEFT"),
    TURN_RIGHT("TURN HEAD RIGHT"),
    TURN_UP("TURN HEAD UP"),
    MOUTH_OPEN("OPEN MOUTH O-SHAPE")
}

val ALL_COUNTRIES = listOf(
    KYCCountry("TW", "Taiwan"),
    KYCCountry("US", "United States"),
    KYCCountry("JP", "Japan"),
    KYCCountry("KR", "South Korea"),
    KYCCountry("HK", "Hong Kong"),
    KYCCountry("SG", "Singapore"),
    KYCCountry("GB", "United Kingdom"),
    KYCCountry("AU", "Australia"),
    KYCCountry("CA", "Canada"),
    KYCCountry("DE", "Germany"),
    KYCCountry("FR", "France"),
    KYCCountry("TH", "Thailand")
).sortedBy { it.name }

internal fun String?.commaSeparatedValues(): List<String> {
    return this
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
}

internal fun String?.documentTypeCodeValues(): List<String> {
    val knownCodes = KYCDocumentType.entries.map { it.apiTypeCode.uppercase() }.toSet()
    return commaSeparatedValues()
        .flatMap { value ->
            val normalized = value.uppercase()
            when {
                normalized in knownCodes -> listOf(normalized)
                normalized.length > 1 && normalized.all { it.toString() in knownCodes } ->
                    normalized.map { it.toString() }
                else -> listOf(normalized)
            }
        }
        .distinct()
}
