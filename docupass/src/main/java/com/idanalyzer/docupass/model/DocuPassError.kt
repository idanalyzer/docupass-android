package com.idanalyzer.docupass.model

/**
 * Authoritative DocuPass error / terminal codes (from coreapi_go `response.go`).
 * These arrive in `error.code` of an envelope with `success:false` and HTTP 200 —
 * never branch on HTTP status for DocuPass logic, branch on these.
 */
object DocuPassErrorCode {
    // Terminal / completion (message often carries the redirect URL).
    const val COMPLETED = "DOCUPASS_COMPLETED"
    const val FAILED = "DOCUPASS_FAILED"
    const val ACCEPTED = "DOCUPASS_ACCEPTED"
    const val UNDER_REVIEW = "DOCUPASS_UNDER_REVIEW"
    const val REDIRECT = "DOCUPASS_REDIRECT"
    const val REVIEW_CONTRACT = "DOCUPASS_REVIEW_CONTRACT"
    const val SUCCESS_MESSAGE = "DOCUPASS_SUCCESS_MESSAGE"
    const val ERROR_MESSAGE = "DOCUPASS_ERROR_MESSAGE"
    const val ERROR_POPUP = "DOCUPASS_ERROR_POPUP"

    // Recoverable errors.
    const val DOCUMENT_REJECTED = "DOCUPASS_DOCUMENT_REJECTED"
    const val FACE_REJECTED = "DOCUPASS_FACE_REJECTED"
    const val GENERIC_ERROR = "DOCUPASS_GENERIC_ERROR"
    const val INVALID_ACTION = "DOCUPASS_INVALID_ACTION"

    // Fatal (unrecoverable — restart/abort).
    const val FATAL_ERROR = "DOCUPASS_FATAL_ERROR"

    private val terminalCodes = setOf(COMPLETED, FAILED, ACCEPTED, UNDER_REVIEW, REDIRECT)
    private val fatalCodes = setOf(FATAL_ERROR)

    fun isTerminal(code: String?): Boolean = code in terminalCodes
    fun isFatal(code: String?): Boolean = code in fatalCodes

    /** A successful end-state (vs. an outright failure/rejection). */
    fun isSuccessTerminal(code: String?): Boolean =
        code == COMPLETED || code == ACCEPTED || code == UNDER_REVIEW
}

/**
 * A structured DocuPass error. [code] is one of [DocuPassErrorCode]; [message]
 * carries the server's human text or, for completion/redirect codes, the
 * redirect URL.
 */
data class DocuPassError(
    val code: String?,
    val message: String?,
    val httpStatus: Int? = null,
    val rawBody: String? = null,
) {
    val isTerminal: Boolean get() = DocuPassErrorCode.isTerminal(code)
    val isFatal: Boolean get() = DocuPassErrorCode.isFatal(code)
}

/** Thrown for transport/parse failures and unrecoverable API errors. */
class DocuPassException(
    val error: DocuPassError,
    cause: Throwable? = null,
) : Exception(error.message ?: error.code ?: "DocuPass error", cause)
