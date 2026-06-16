package com.idanalyzer.docupass

enum class DocupassErrorAction {
    SHOW_COMPLETED,
    SHOW_FAILED,
    RESYNC_SESSION,
    REQUEST_LOCATION,
    RETRY,
    RETAKE_DOCUMENT,
    RETAKE_FACE,
    EDIT_INPUT,
    FIX_SIGNATURE,
    FATAL,
    CONTACT_SUPPORT
}

data class DocupassNormalizedError(
    val code: String?,
    val subCode: String?,
    val title: String,
    val detail: String,
    val suggestion: String,
    val action: DocupassErrorAction,
    val warningCodes: List<String> = emptyList(),
    val httpStatus: Int? = null,
    val rawMessage: String? = null,
    val rawBody: String? = null
) {
    fun toDisplayMessage(includeCode: Boolean = true): String {
        val codeLine = if (includeCode) {
            val parts = listOfNotNull(code, subCode).filter { it.isNotBlank() }
            if (parts.isNotEmpty()) "\nCode: ${parts.joinToString(" / ")}" else ""
        } else {
            ""
        }
        val warningLine = if (warningCodes.isNotEmpty()) {
            "\nWarnings: ${warningCodes.joinToString(", ")}"
        } else {
            ""
        }
        return "$title\n$detail\n$suggestion$warningLine$codeLine"
    }
}

private data class DocupassErrorTemplate(
    val title: String,
    val detail: String,
    val suggestion: String,
    val action: DocupassErrorAction
)

object DocupassErrorNormalizer {
    fun normalize(error: DocupassApiResult.Error): DocupassNormalizedError {
        return normalizeDocupassError(error)
    }
    fun normalize(
        code: String?,
        message: String?,
        httpStatus: Int? = null,
        rawBody: String? = null
    ): DocupassNormalizedError {
        return normalizeDocupassError(code, message, httpStatus, rawBody)
    }
}

fun normalizeDocupassError(error: DocupassApiResult.Error): DocupassNormalizedError {
    return normalizeDocupassError(
        code = error.code,
        message = error.message,
        httpStatus = error.httpStatus,
        rawBody = error.rawBody
    )
}

fun normalizeDocupassError(
    code: String?,
    message: String?,
    httpStatus: Int? = null,
    rawBody: String? = null
): DocupassNormalizedError {
    val normalizedCode = code.normalizedKeyOrNull()
    val rawMessage = message?.trim()

    return when (normalizedCode) {
        "DOCUPASS_COMPLETED" -> completed(rawMessage, httpStatus, rawBody)
        "DOCUPASS_FAILED" -> failed(rawMessage, httpStatus, rawBody)
        "DOCUPASS_INVALID_ACTION" -> fromTemplate(
            code = normalizedCode,
            subCode = null,
            template = mainCodeTemplates.getValue("DOCUPASS_INVALID_ACTION"),
            httpStatus = httpStatus,
            rawMessage = rawMessage,
            rawBody = rawBody
        )
        "DOCUPASS_FATAL_ERROR" -> fromSubCode(
            code = normalizedCode,
            message = rawMessage,
            templates = fatalSubCodeTemplates,
            fallback = DocupassErrorTemplate(
                title = "Fatal DocuPass session error",
                detail = "The session cannot continue because the server rejected the reference, session, or required context.",
                suggestion = "Restart from a valid DocuPass link. If this repeats, ask the link issuer to create a new link.",
                action = DocupassErrorAction.FATAL
            ),
            httpStatus = httpStatus,
            rawBody = rawBody
        )
        "DOCUPASS_GENERIC_ERROR" -> fromSubCode(
            code = normalizedCode,
            message = rawMessage,
            templates = genericSubCodeTemplates,
            fallback = DocupassErrorTemplate(
                title = "DocuPass input error",
                detail = "The server rejected the current input.",
                suggestion = "Review the entered data and try again.",
                action = DocupassErrorAction.EDIT_INPUT
            ),
            httpStatus = httpStatus,
            rawBody = rawBody
        )
        "DOCUPASS_DOCUMENT_REJECTED" -> rejected(
            code = normalizedCode,
            message = rawMessage,
            templates = documentWarningTemplates,
            fallbackTitle = "Document rejected",
            fallbackDetail = "The document verification was rejected by the server.",
            fallbackSuggestion = "Retake the document photo with the full document visible, focused, and free of glare.",
            action = DocupassErrorAction.RETAKE_DOCUMENT,
            httpStatus = httpStatus,
            rawBody = rawBody
        )
        "DOCUPASS_FACE_REJECTED" -> rejected(
            code = normalizedCode,
            message = rawMessage,
            templates = faceWarningTemplates,
            fallbackTitle = "Face verification failed",
            fallbackDetail = "The face verification was rejected by the server.",
            fallbackSuggestion = "Retake the selfie in good lighting and follow the liveness instructions.",
            action = DocupassErrorAction.RETAKE_FACE,
            httpStatus = httpStatus,
            rawBody = rawBody
        )
        "ERROR_INVALID_VALUE" -> invalidValue(rawMessage, httpStatus, rawBody)
        "ERROR_OPERATION_FAILED" -> operationFailed(rawMessage, httpStatus, rawBody)
        "ERROR_INTERNAL_ERROR" -> internalError(rawMessage, httpStatus, rawBody)
        else -> {
            val template = mainCodeTemplates[normalizedCode]
                ?: commonCodeTemplates[normalizedCode]
                ?: localCodeTemplates[normalizedCode]
            if (template != null) {
                fromTemplate(
                    code = normalizedCode,
                    subCode = null,
                    template = template,
                    httpStatus = httpStatus,
                    rawMessage = rawMessage,
                    rawBody = rawBody
                )
            } else {
                unknown(normalizedCode, rawMessage, httpStatus, rawBody)
            }
        }
    }
}

private fun fromSubCode(
    code: String,
    message: String?,
    templates: Map<String, DocupassErrorTemplate>,
    fallback: DocupassErrorTemplate,
    httpStatus: Int?,
    rawBody: String?
): DocupassNormalizedError {
    val subCode = message.normalizedKeyOrNull()
    val template = templates[subCode] ?: fallback
    return fromTemplate(
        code = code,
        subCode = subCode,
        template = template,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody
    )
}

private fun rejected(
    code: String,
    message: String?,
    templates: Map<String, DocupassErrorTemplate>,
    fallbackTitle: String,
    fallbackDetail: String,
    fallbackSuggestion: String,
    action: DocupassErrorAction,
    httpStatus: Int?,
    rawBody: String?
): DocupassNormalizedError {
    val warningCodes = message
        ?.split(",")
        ?.mapNotNull { it.normalizedKeyOrNull() }
        .orEmpty()
    val firstWarning = warningCodes.firstOrNull()
    val template = templates[firstWarning] ?: DocupassErrorTemplate(
        title = fallbackTitle,
        detail = fallbackDetail,
        suggestion = fallbackSuggestion,
        action = action
    )
    return fromTemplate(
        code = code,
        subCode = firstWarning,
        template = template,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody,
        warningCodes = warningCodes
    )
}

private fun completed(message: String?, httpStatus: Int?, rawBody: String?): DocupassNormalizedError {
    val hasRedirect = message.isLikelyUrl()
    return DocupassNormalizedError(
        code = "DOCUPASS_COMPLETED",
        subCode = null,
        title = "Verification completed",
        detail = "The DocuPass verification has already been completed successfully.",
        suggestion = if (hasRedirect) {
            "Show the completed state and continue to the returned redirect URL."
        } else {
            "Show the completed state and stop submitting more verification data."
        },
        action = DocupassErrorAction.SHOW_COMPLETED,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody
    )
}

private fun failed(message: String?, httpStatus: Int?, rawBody: String?): DocupassNormalizedError {
    val hasRedirect = message.isLikelyUrl()
    return DocupassNormalizedError(
        code = "DOCUPASS_FAILED",
        subCode = null,
        title = "Verification failed",
        detail = "The DocuPass verification has reached a failed or rejected final state.",
        suggestion = if (hasRedirect) {
            "Show the failed state and continue to the returned redirect URL."
        } else {
            "Show the failed state. Do not retry the same completed session."
        },
        action = DocupassErrorAction.SHOW_FAILED,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody
    )
}

private fun invalidValue(message: String?, httpStatus: Int?, rawBody: String?): DocupassNormalizedError {
    val field = message?.trim().orEmpty()
    val template = when (field) {
        "document" -> DocupassErrorTemplate(
            title = "Document image missing",
            detail = "The request did not include a valid front document image.",
            suggestion = "Retake or reselect the front document image before uploading.",
            action = DocupassErrorAction.RETAKE_DOCUMENT
        )
        "face" -> DocupassErrorTemplate(
            title = "Face image missing",
            detail = "The request did not include a valid face image or face video.",
            suggestion = "Restart face capture and submit at least one valid face frame.",
            action = DocupassErrorAction.RETAKE_FACE
        )
        "profile" -> DocupassErrorTemplate(
            title = "Invalid profile",
            detail = "The DocuPass link creation request is missing a valid profile id.",
            suggestion = "Use an existing profile id when creating the DocuPass link.",
            action = DocupassErrorAction.EDIT_INPUT
        )
        "profileOverride" -> DocupassErrorTemplate(
            title = "Invalid profile override",
            detail = "The profileOverride value is not valid JSON.",
            suggestion = "Fix the JSON body before creating the DocuPass link.",
            action = DocupassErrorAction.EDIT_INPUT
        )
        else -> DocupassErrorTemplate(
            title = "Invalid request value",
            detail = if (field.isNotBlank()) {
                "Parameter '$field' is missing or contains an invalid value."
            } else {
                "A required parameter is missing or invalid."
            },
            suggestion = "Fix the request payload and try again.",
            action = DocupassErrorAction.EDIT_INPUT
        )
    }
    return fromTemplate(
        code = "ERROR_INVALID_VALUE",
        subCode = field.takeIf { it.isNotBlank() },
        template = template,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody
    )
}

private fun operationFailed(message: String?, httpStatus: Int?, rawBody: String?): DocupassNormalizedError {
    val key = message?.trim().orEmpty()
    val template = operationFailedTemplates[key] ?: DocupassErrorTemplate(
        title = "Operation failed",
        detail = if (key.isNotBlank()) key else "The server rejected the requested operation.",
        suggestion = "Review the request settings and try again.",
        action = DocupassErrorAction.EDIT_INPUT
    )
    return fromTemplate(
        code = "ERROR_OPERATION_FAILED",
        subCode = key.takeIf { it.isNotBlank() },
        template = template,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody
    )
}

private fun internalError(message: String?, httpStatus: Int?, rawBody: String?): DocupassNormalizedError {
    val detail = if (!message.isNullOrBlank() && message != "Internal server error.") {
        "The server returned an internal error: ${message.trim()}"
    } else {
        "The server hit an internal error while processing the request."
    }
    return DocupassNormalizedError(
        code = "ERROR_INTERNAL_ERROR",
        subCode = null,
        title = "Technical error",
        detail = detail,
        suggestion = "Retry once. If the same error repeats, contact support with the reference and request step.",
        action = DocupassErrorAction.CONTACT_SUPPORT,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody
    )
}

private fun unknown(
    code: String?,
    message: String?,
    httpStatus: Int?,
    rawBody: String?
): DocupassNormalizedError {
    return DocupassNormalizedError(
        code = code,
        subCode = null,
        title = "Unexpected DocuPass error",
        detail = message?.takeIf { it.isNotBlank() } ?: "The server returned an unmapped error.",
        suggestion = "Show this message and keep the raw error for debugging.",
        action = DocupassErrorAction.CONTACT_SUPPORT,
        httpStatus = httpStatus,
        rawMessage = message,
        rawBody = rawBody
    )
}

private fun fromTemplate(
    code: String?,
    subCode: String?,
    template: DocupassErrorTemplate,
    httpStatus: Int?,
    rawMessage: String?,
    rawBody: String?,
    warningCodes: List<String> = emptyList()
): DocupassNormalizedError {
    return DocupassNormalizedError(
        code = code,
        subCode = subCode,
        title = template.title,
        detail = template.detail,
        suggestion = template.suggestion,
        action = template.action,
        warningCodes = warningCodes,
        httpStatus = httpStatus,
        rawMessage = rawMessage,
        rawBody = rawBody
    )
}

private fun String?.normalizedKeyOrNull(): String? {
    val normalized = this?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    return normalized
}

private fun String?.isLikelyUrl(): Boolean {
    val value = this?.trim().orEmpty()
    return value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
}

private val mainCodeTemplates = mapOf(
    "DOCUPASS_INVALID_ACTION" to DocupassErrorTemplate(
        title = "Session is out of sync",
        detail = "The action no longer matches the current server task. This can happen if the task was completed on another device or the profile changed.",
        suggestion = "Call get_action again and route the user to the latest returned task.",
        action = DocupassErrorAction.RESYNC_SESSION
    ),
    "DOCUPASS_REDIRECT" to DocupassErrorTemplate(
        title = "Redirect required",
        detail = "The server returned a DocuPass redirect state.",
        suggestion = "If the message is a URL, continue to it or return it through the SDK callback.",
        action = DocupassErrorAction.SHOW_COMPLETED
    ),
    "DOCUPASS_ACCEPTED" to DocupassErrorTemplate(
        title = "Verification accepted",
        detail = "The DocuPass verification has been accepted.",
        suggestion = "Show the completed state and stop submitting more data.",
        action = DocupassErrorAction.SHOW_COMPLETED
    ),
    "DOCUPASS_UNDER_REVIEW" to DocupassErrorTemplate(
        title = "Verification under review",
        detail = "The DocuPass verification has completed and is waiting for review.",
        suggestion = "Show a completed or review-pending state.",
        action = DocupassErrorAction.SHOW_COMPLETED
    ),
    "DOCUPASS_CUSTOM_URL_ERROR" to DocupassErrorTemplate(
        title = "Custom DocuPass URL unavailable",
        detail = "The link requested a custom DocuPass URL that is not allowed by the current plan or account settings.",
        suggestion = "Remove the custom URL setting or ask the account administrator to enable the required plan.",
        action = DocupassErrorAction.EDIT_INPUT
    )
)

private val fatalSubCodeTemplates = mapOf(
    "REFERENCE_NOT_FOUND" to DocupassErrorTemplate(
        title = "DocuPass link not found",
        detail = "The reference in the Authorization header does not exist or the link is broken.",
        suggestion = "Stop the flow and ask the issuer to provide a new DocuPass link.",
        action = DocupassErrorAction.FATAL
    ),
    "SESSION_NOT_FOUND" to DocupassErrorTemplate(
        title = "Session not found",
        detail = "The DOCUPASS_SESSION token is invalid, expired, or no longer available.",
        suggestion = "If the original reference is available, restart from the reference. Otherwise ask for a new link.",
        action = DocupassErrorAction.FATAL
    ),
    "LOCATION_HEADER_MISSING" to DocupassErrorTemplate(
        title = "Location permission required",
        detail = "This profile requires GPS tracking, but the Geolocation header was missing or invalid.",
        suggestion = "Request device location permission, then retry get_action with Geolocation: lat,lng,accuracy.",
        action = DocupassErrorAction.REQUEST_LOCATION
    )
)

private val genericSubCodeTemplates = mapOf(
    "INVALID_PHONE_NUMBER" to DocupassErrorTemplate(
        title = "Invalid phone number",
        detail = "The phone number could not be parsed by the server.",
        suggestion = "Enter the number in E.164 format, for example +886912345678.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "PHONE NUMBER NOT IN ACCEPTED COUNTRY" to DocupassErrorTemplate(
        title = "Phone country not accepted",
        detail = "The phone number country code is outside the profile's accepted country list.",
        suggestion = "Select one of the allowed country codes and enter a matching phone number.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "SMS_LIMIT_REACHED" to DocupassErrorTemplate(
        title = "SMS resend limit reached",
        detail = "An SMS verification was requested too recently from this IP address.",
        suggestion = "Wait at least 60 seconds before sending another SMS.",
        action = DocupassErrorAction.RETRY
    ),
    "CALL_LIMIT_REACHED" to DocupassErrorTemplate(
        title = "Call retry limit reached",
        detail = "A call verification was requested too recently from this IP address.",
        suggestion = "Wait at least 5 minutes before requesting another call.",
        action = DocupassErrorAction.RETRY
    ),
    "PHONE_VERIFICATION_LIMIT_REACHED" to DocupassErrorTemplate(
        title = "Phone verification limit reached",
        detail = "Too many phone verification attempts were made for this DocuPass reference.",
        suggestion = "Stop retrying for now. Try again later or ask the issuer for help.",
        action = DocupassErrorAction.FATAL
    ),
    "NUMBER_NOT_SUPPORTED" to DocupassErrorTemplate(
        title = "Phone number not supported",
        detail = "The phone verification provider rejected this phone number or channel.",
        suggestion = "Try another number or switch between SMS and call.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "INVALID_PHONE_VERIFICATION_CODE" to DocupassErrorTemplate(
        title = "Invalid verification code",
        detail = "The phone verification code is not valid or was rejected.",
        suggestion = "Clear the code field and ask the user to enter the latest 6 digit code.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "PHONE_VERIFICATION_EXPIRED" to DocupassErrorTemplate(
        title = "Verification code expired",
        detail = "The server could not find a matching active phone verification record.",
        suggestion = "Send a new SMS or call verification code.",
        action = DocupassErrorAction.RETRY
    ),
    "CUSTOM_FIELD_EMPTY" to DocupassErrorTemplate(
        title = "Required answer missing",
        detail = "A required custom form field is empty.",
        suggestion = "Highlight the missing field and ask the user to answer it.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "INVALID_SIGNATURE_IMAGE" to DocupassErrorTemplate(
        title = "Invalid signature image",
        detail = "The signature value is not a valid image.",
        suggestion = "Open the signature pad again and collect a new signature image.",
        action = DocupassErrorAction.FIX_SIGNATURE
    ),
    "SIGNATURE_MISSING" to DocupassErrorTemplate(
        title = "Signature missing",
        detail = "At least one required signature field is missing.",
        suggestion = "Highlight the unsigned fields and require all signatures before submitting.",
        action = DocupassErrorAction.FIX_SIGNATURE
    )
)

private val documentWarningTemplates = mapOf(
    "UNRECOGNIZED_DOCUMENT" to DocupassErrorTemplate(
        title = "Document not recognized",
        detail = "The server could not recognize the front document image as a supported official document.",
        suggestion = "Retake the full document with clear focus and no cropping.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "UNRECOGNIZED_BACK_DOCUMENT" to DocupassErrorTemplate(
        title = "Back document not recognized",
        detail = "The back side image could not be recognized.",
        suggestion = "Retake the back side clearly, including the full card.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "UNRECOGNIZED_BACK_BARCODE" to DocupassErrorTemplate(
        title = "Back barcode unreadable",
        detail = "The server could not read the barcode from the back side.",
        suggestion = "Retake the back side with the barcode in focus and without glare.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "INVALID_BACK_DOCUMENT" to DocupassErrorTemplate(
        title = "Invalid back document",
        detail = "The uploaded back image is not a valid back side for this document.",
        suggestion = "Upload the correct reverse side of the same document.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUPASS_BACK_DOCUMENT_NOT_UPLOADED" to DocupassErrorTemplate(
        title = "Back document required",
        detail = "This document type requires a back side image, but it was not uploaded.",
        suggestion = "Ask the user to capture the reverse side.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUPASS_BACK_DOCUMENT_MISMATCH" to DocupassErrorTemplate(
        title = "Back document mismatch",
        detail = "The uploaded back side does not match the expected document back side.",
        suggestion = "Retake the correct back side of the selected document.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUPASS_DOCUMENT_MISSING_FACE" to DocupassErrorTemplate(
        title = "Document photo missing",
        detail = "The uploaded document does not contain a detectable face photo required for face verification.",
        suggestion = "Upload a document page or side that includes the holder's portrait.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUMENT_FACE_NOT_FOUND" to DocupassErrorTemplate(
        title = "Document face not found",
        detail = "The server could not detect the face photo on the document.",
        suggestion = "Retake the document with the portrait area clear and in focus.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUMENT_FACE_LANDMARK_ERR" to DocupassErrorTemplate(
        title = "Document face too unclear",
        detail = "The document portrait was too blurry or unclear for face landmark detection.",
        suggestion = "Retake the document with better focus and lighting.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUPASS_DOCUMENT_TYPE_MISMATCH" to DocupassErrorTemplate(
        title = "Document type mismatch",
        detail = "The selected document type does not match the uploaded document.",
        suggestion = "Let the user reselect the document type or upload the matching document.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUPASS_DOCUMENT_COUNTRY_MISMATCH" to DocupassErrorTemplate(
        title = "Document country mismatch",
        detail = "The selected issuing country does not match the uploaded document.",
        suggestion = "Let the user reselect the issuing country or upload the matching document.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "DOCUPASS_NOT_FROM_CAMERA" to DocupassErrorTemplate(
        title = "Document not taken from camera",
        detail = "The document image does not appear to be a live camera capture.",
        suggestion = "Use the phone camera to capture the physical document directly.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "TYPE_NOT_ACCEPTED" to DocupassErrorTemplate(
        title = "Document type not accepted",
        detail = "The uploaded document type is not allowed by the profile.",
        suggestion = "Choose one of the allowed document types.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "COUNTRY_NOT_ACCEPTED" to DocupassErrorTemplate(
        title = "Document country not accepted",
        detail = "The document issuing country is not allowed by the profile.",
        suggestion = "Choose a document from one of the allowed countries.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "STATE_NOT_ACCEPTED" to DocupassErrorTemplate(
        title = "Document state not accepted",
        detail = "The document issuing state is not allowed by the profile.",
        suggestion = "Use a document from an accepted state or region.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "UNDER_18" to ageTemplate("18"),
    "UNDER_19" to ageTemplate("19"),
    "UNDER_20" to ageTemplate("20"),
    "UNDER_21" to ageTemplate("21"),
    "NAME_VERIFICATION_FAILED" to infoMismatchTemplate("name"),
    "DOB_VERIFICATION_FAILED" to infoMismatchTemplate("date of birth"),
    "AGE_VERIFICATION_FAILED" to infoMismatchTemplate("age"),
    "ID_NUMBER_VERIFICATION_FAILED" to infoMismatchTemplate("document number"),
    "ADDRESS_VERIFICATION_FAILED" to infoMismatchTemplate("address"),
    "POSTCODE_VERIFICATION_FAILED" to infoMismatchTemplate("postcode"),
    "LOW_TEXT_CONFIDENCE" to blurryTemplate("The OCR confidence is too low."),
    "MISSING_EXPIRY_DATE" to blurryTemplate("The expiry date is missing or unreadable."),
    "MISSING_ISSUE_DATE" to blurryTemplate("The issue date is missing or unreadable."),
    "MISSING_BIRTH_DATE" to blurryTemplate("The date of birth is missing or unreadable."),
    "MISSING_DOCUMENT_NUMBER" to blurryTemplate("The document number is missing or unreadable."),
    "MISSING_PERSONAL_NUMBER" to blurryTemplate("The personal number is missing or unreadable."),
    "MISSING_ADDRESS" to blurryTemplate("The address is missing or unreadable."),
    "MISSING_POSTCODE" to blurryTemplate("The postcode is missing or unreadable."),
    "MISSING_NAME" to blurryTemplate("The name is missing or unreadable."),
    "MISSING_LOCAL_NAME" to blurryTemplate("The local name is missing or unreadable."),
    "MISSING_GENDER" to blurryTemplate("The gender field is missing or unreadable."),
    "MISSING_HEIGHT" to blurryTemplate("The height field is missing or unreadable."),
    "MISSING_WEIGHT" to blurryTemplate("The weight field is missing or unreadable."),
    "MISSING_HAIR_COLOR" to blurryTemplate("The hair color field is missing or unreadable."),
    "MISSING_EYE_COLOR" to blurryTemplate("The eye color field is missing or unreadable."),
    "MISSING_RESTRICTIONS" to blurryTemplate("The restrictions field is missing or unreadable."),
    "IMAGE_TOO_SMALL" to DocupassErrorTemplate(
        title = "Document image too small",
        detail = "The image resolution is too low for reliable verification.",
        suggestion = "Retake the document at a higher resolution.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "IMAGE_TOO_BLURRY" to blurryTemplate("The document image is too blurry."),
    "GLARE_DETECTED" to DocupassErrorTemplate(
        title = "Glare detected",
        detail = "The document image contains glare or reflection.",
        suggestion = "Change the angle or lighting and retake the document.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "BLACK_WHITE_DOCUMENT" to screenOrCopyTemplate("The document appears to be a black and white copy."),
    "RECAPTURED_DOCUMENT" to screenOrCopyTemplate("The document may have been recaptured from another screen or print."),
    "SCREEN_DETECTED" to screenOrCopyTemplate("A screen or monitor was detected in the document image."),
    "DOCUMENT_EXPIRED" to DocupassErrorTemplate(
        title = "Document expired",
        detail = "The uploaded document is no longer valid.",
        suggestion = "Use a valid, non-expired document.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "IMAGE_FORGERY" to securityRejectTemplate("The document image may contain forged elements."),
    "IMAGE_EDITED" to securityRejectTemplate("The document image metadata suggests editing."),
    "TEXT_FORGERY" to securityRejectTemplate("The document text may have been artificially modified."),
    "FEATURE_VERIFICATION_FAILED" to securityRejectTemplate("The document security features do not match the expected template."),
    "FAKE_ID" to securityRejectTemplate("The document matches a known fake or sample document."),
    "ARTIFICIAL_IMAGE" to securityRejectTemplate("The document image appears artificially generated."),
    "ARTIFICIAL_TEXT" to securityRejectTemplate("The document text appears artificially generated."),
    "DOCUPASS_TOO_MANY_ATTEMPTS" to DocupassErrorTemplate(
        title = "Too many attempts",
        detail = "The user has failed document or face verification too many times.",
        suggestion = "Stop the current session and ask the issuer for a new link if appropriate.",
        action = DocupassErrorAction.SHOW_FAILED
    ),
    "DOCUPASS_EXPIRED" to DocupassErrorTemplate(
        title = "DocuPass link expired",
        detail = "The link expired before all required tasks were completed.",
        suggestion = "Ask the issuer to create a new DocuPass link.",
        action = DocupassErrorAction.SHOW_FAILED
    )
)

private val faceWarningTemplates = mapOf(
    "SELFIE_FACE_NOT_FOUND" to DocupassErrorTemplate(
        title = "Face not found",
        detail = "The selfie image does not contain a detectable face.",
        suggestion = "Center the face in the frame and retake the selfie.",
        action = DocupassErrorAction.RETAKE_FACE
    ),
    "SELFIE_MULTIPLE_FACES" to DocupassErrorTemplate(
        title = "Multiple faces detected",
        detail = "The selfie image contains more than one face.",
        suggestion = "Make sure only the user is visible in the camera frame.",
        action = DocupassErrorAction.RETAKE_FACE
    ),
    "SELFIE_FACE_LANDMARK_ERR" to DocupassErrorTemplate(
        title = "Face image unclear",
        detail = "The selfie is too blurry or unclear for face landmark detection.",
        suggestion = "Improve lighting, keep still, and retake the selfie.",
        action = DocupassErrorAction.RETAKE_FACE
    ),
    "FACE_MISMATCH" to DocupassErrorTemplate(
        title = "Face mismatch",
        detail = "The selfie does not match the face on the document.",
        suggestion = "Retake the selfie with the document holder. If it still fails, verification should fail.",
        action = DocupassErrorAction.RETAKE_FACE
    ),
    "FACE_IDENTICAL" to DocupassErrorTemplate(
        title = "Selfie appears identical",
        detail = "The selfie appears to be the same image as the document portrait.",
        suggestion = "Use a live camera selfie, not a document photo or uploaded portrait.",
        action = DocupassErrorAction.RETAKE_FACE
    ),
    "FACE_LIVENESS_ERR" to DocupassErrorTemplate(
        title = "Liveness failed",
        detail = "The selfie failed liveness verification.",
        suggestion = "Retake the face capture and follow the liveness instructions carefully.",
        action = DocupassErrorAction.RETAKE_FACE
    ),
    "RECAPTURED_FACE" to DocupassErrorTemplate(
        title = "Recaptured face detected",
        detail = "The selfie may have been captured from a screen or photo.",
        suggestion = "Use the live front camera and keep the actual user in frame.",
        action = DocupassErrorAction.RETAKE_FACE
    ),
    "DOCUPASS_TOO_MANY_ATTEMPTS" to DocupassErrorTemplate(
        title = "Too many attempts",
        detail = "The user has failed document or face verification too many times.",
        suggestion = "Stop the current session and ask the issuer for a new link if appropriate.",
        action = DocupassErrorAction.SHOW_FAILED
    )
)

private val commonCodeTemplates = mapOf(
    "ERROR_INVALID_LICENSE" to DocupassErrorTemplate(
        title = "Service license unavailable",
        detail = "The server license is invalid, expired, or over quota.",
        suggestion = "Stop the flow and ask the issuer or administrator to check the service license.",
        action = DocupassErrorAction.CONTACT_SUPPORT
    ),
    "SERVICE_UNAVAILABLE" to DocupassErrorTemplate(
        title = "Service busy",
        detail = "The server is busy or has reached its concurrent processing limit.",
        suggestion = "Wait briefly and retry with backoff.",
        action = DocupassErrorAction.RETRY
    ),
    "ERROR_MAX_EXECUTION_TIME_EXCEEDED" to DocupassErrorTemplate(
        title = "Request timed out",
        detail = "The document or face verification took longer than the server limit.",
        suggestion = "Retry the request. If it repeats, reduce image size or try again later.",
        action = DocupassErrorAction.RETRY
    ),
    "ERROR_REQUEST_TOO_LARGE" to DocupassErrorTemplate(
        title = "Upload too large",
        detail = "The request body exceeded the server upload size limit.",
        suggestion = "Compress the image or lower the camera resolution before uploading.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "ERROR_INVALID_JSON" to DocupassErrorTemplate(
        title = "Invalid request JSON",
        detail = "The POST body is not valid JSON.",
        suggestion = "Fix the SDK request builder before retrying.",
        action = DocupassErrorAction.CONTACT_SUPPORT
    ),
    "ERROR_UNAUTHORIZED" to DocupassErrorTemplate(
        title = "Unauthorized",
        detail = "The server could not authenticate the DocuPass user or credentials.",
        suggestion = "Stop the flow and ask the issuer for a valid link.",
        action = DocupassErrorAction.FATAL
    ),
    "ERROR_USER_BANNED" to DocupassErrorTemplate(
        title = "User account unavailable",
        detail = "The DocuPass link owner account is banned or disabled.",
        suggestion = "Stop the flow and ask the issuer to contact support.",
        action = DocupassErrorAction.FATAL
    ),
    "ERROR_QUOTA_EXCEEDED" to DocupassErrorTemplate(
        title = "Quota exceeded",
        detail = "The DocuPass link owner does not have enough quota for this operation.",
        suggestion = "Stop the flow and ask the issuer to add quota.",
        action = DocupassErrorAction.FATAL
    ),
    "ERROR_EXECUTION_CANCEL" to DocupassErrorTemplate(
        title = "Execution cancelled",
        detail = "The server cancelled the verification request.",
        suggestion = "Retry the current step.",
        action = DocupassErrorAction.RETRY
    ),
    "ERROR_INVALID_ENCODING" to DocupassErrorTemplate(
        title = "Invalid base64 image",
        detail = "The uploaded image could not be decoded from base64.",
        suggestion = "Regenerate the image base64 and retry the upload.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    ),
    "ERROR_REMOTE_IMAGE_FAILED" to DocupassErrorTemplate(
        title = "Remote image failed",
        detail = "The server could not load the image from a URL or cached reference.",
        suggestion = "Upload the image directly instead of using a remote URL, or retry with a valid reference.",
        action = DocupassErrorAction.RETRY
    ),
    "ERROR_IMAGE_CORRUPTED" to DocupassErrorTemplate(
        title = "Image unsupported or corrupted",
        detail = "The image format is unsupported or the file is corrupted.",
        suggestion = "Retake or reselect a clear JPG, PNG, or supported PDF file.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    )
)

private val localCodeTemplates = mapOf(
    "LOCAL_VALIDATION" to DocupassErrorTemplate(
        title = "Missing local input",
        detail = "The SDK blocked the request before sending it because required local data is missing.",
        suggestion = "Complete the current capture step before submitting.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "NETWORK_ERROR" to DocupassErrorTemplate(
        title = "Network error",
        detail = "The SDK could not reach the DocuPass API.",
        suggestion = "Check connectivity and retry.",
        action = DocupassErrorAction.RETRY
    ),
    "UNEXPECTED_ERROR" to DocupassErrorTemplate(
        title = "Unexpected SDK error",
        detail = "The SDK hit an unexpected error while handling the request.",
        suggestion = "Retry once. If it repeats, collect logs and contact support.",
        action = DocupassErrorAction.CONTACT_SUPPORT
    )
)

private val operationFailedTemplates = mapOf(
    "Invalid DocuPass mode." to DocupassErrorTemplate(
        title = "Invalid DocuPass mode",
        detail = "The requested DocuPass mode is outside the supported range.",
        suggestion = "Use mode 0, 1, 2, or 3 when creating the link.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Cannot set both reference document and reference face." to DocupassErrorTemplate(
        title = "Conflicting reference images",
        detail = "The link creation request includes both reference document and reference face.",
        suggestion = "Provide only one reference source for this mode.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Cannot set reference face for document only verification." to DocupassErrorTemplate(
        title = "Reference face not allowed",
        detail = "Document-only mode cannot use a reference face.",
        suggestion = "Remove referenceFace or use a different mode.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Cannot set reference document for document only verification." to DocupassErrorTemplate(
        title = "Reference document not allowed",
        detail = "Document-only mode does not accept a reference document in this server implementation.",
        suggestion = "Remove referenceDocument from the link creation request.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Cannot set reference document for face only verification." to DocupassErrorTemplate(
        title = "Reference document not allowed",
        detail = "Face-only mode requires a reference face, not a reference document.",
        suggestion = "Provide referenceFace instead of referenceDocument.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Reference face image missing for face verification." to DocupassErrorTemplate(
        title = "Reference face missing",
        detail = "Face-only verification needs a reference face image.",
        suggestion = "Provide referenceFace when creating a face-only link.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Cannot set reference face for e-Signature mode." to DocupassErrorTemplate(
        title = "Reference face not allowed",
        detail = "e-Signature mode cannot use a reference face.",
        suggestion = "Remove referenceFace from the request.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Cannot set reference document for e-Signature mode." to DocupassErrorTemplate(
        title = "Reference document not allowed",
        detail = "e-Signature mode cannot use a reference document.",
        suggestion = "Remove referenceDocument from the request.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "contractSign template id value required when using e-Signature mode." to DocupassErrorTemplate(
        title = "Contract template missing",
        detail = "e-Signature mode requires a contractSign template id.",
        suggestion = "Set contractSign to a valid template id.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Reusable link is deprecated in DocuPass v3" to DocupassErrorTemplate(
        title = "Reusable link not supported",
        detail = "DocuPass v3 no longer supports reusable links.",
        suggestion = "Create a one-time v3 link instead.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "contractSign contains invalid template ID." to DocupassErrorTemplate(
        title = "Invalid contract template",
        detail = "The contractSign template id does not exist or is not accessible.",
        suggestion = "Use a valid contract template id.",
        action = DocupassErrorAction.EDIT_INPUT
    ),
    "Template signatures contain multiple parties which is only supported in e-Signature only mode." to DocupassErrorTemplate(
        title = "Multiparty signature not allowed",
        detail = "The selected template has multiple parties, but the link is not e-Signature-only mode.",
        suggestion = "Use mode 3 or choose a single-party template.",
        action = DocupassErrorAction.EDIT_INPUT
    )
)

private fun ageTemplate(age: String): DocupassErrorTemplate {
    return DocupassErrorTemplate(
        title = "Age requirement failed",
        detail = "The document holder is under $age.",
        suggestion = "Show the age restriction failure and stop or return to the issuer flow.",
        action = DocupassErrorAction.SHOW_FAILED
    )
}

private fun infoMismatchTemplate(field: String): DocupassErrorTemplate {
    return DocupassErrorTemplate(
        title = "Information mismatch",
        detail = "The document $field does not match the expected verification data.",
        suggestion = "Show the mismatch result. If the user selected the wrong document, allow a retry.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    )
}

private fun blurryTemplate(detail: String): DocupassErrorTemplate {
    return DocupassErrorTemplate(
        title = "Document image unclear",
        detail = detail,
        suggestion = "Retake a clear, focused image with all text visible.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    )
}

private fun screenOrCopyTemplate(detail: String): DocupassErrorTemplate {
    return DocupassErrorTemplate(
        title = "Physical document required",
        detail = detail,
        suggestion = "Capture the original physical document directly with the camera.",
        action = DocupassErrorAction.RETAKE_DOCUMENT
    )
}

private fun securityRejectTemplate(detail: String): DocupassErrorTemplate {
    return DocupassErrorTemplate(
        title = "Document security check failed",
        detail = detail,
        suggestion = "Do not continue automatically. Show verification failed or route to manual review if your product supports it.",
        action = DocupassErrorAction.SHOW_FAILED
    )
}

