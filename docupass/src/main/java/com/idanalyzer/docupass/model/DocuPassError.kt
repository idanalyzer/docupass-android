package com.idanalyzer.docupass.model

/**
 * Authoritative DocuPass error / terminal codes (from the DocuPass API).
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

    // Terminal success end-states. SUCCESS_MESSAGE shows a success notice; the
    // server delivers REVIEW_CONTRACT (with the contract review HTML) once a party
    // has signed — for the drop-in flow that's a stable "signed / under review"
    // end-state (full inline review parity is tracked separately).
    private val successTerminalCodes =
        setOf(COMPLETED, ACCEPTED, UNDER_REVIEW, REDIRECT, SUCCESS_MESSAGE, REVIEW_CONTRACT)

    // Terminal failure end-states. ERROR_MESSAGE is a hard stop (e.g. session
    // expired) — it must NOT be retried/resynced or the flow loops forever.
    private val failureTerminalCodes = setOf(FAILED, ERROR_MESSAGE)

    private val terminalCodes = successTerminalCodes + failureTerminalCodes

    // Display-only: show the message to the user but stay on the current step
    // (no resync, no terminal). ERROR_POPUP = recoverable phone-step alerts
    // ("incorrect format", "limit reached", …) where the user simply retries.
    private val displayCodes = setOf(ERROR_POPUP)

    private val fatalCodes = setOf(FATAL_ERROR)

    fun isTerminal(code: String?): Boolean = code in terminalCodes
    fun isFatal(code: String?): Boolean = code in fatalCodes

    /** Show the message but keep the user on the current step (do not resync/end). */
    fun isDisplay(code: String?): Boolean = code in displayCodes

    /** A successful end-state (vs. an outright failure/rejection). */
    fun isSuccessTerminal(code: String?): Boolean = code in successTerminalCodes

    /** Codes whose `message` is a redirect URL (vs. human-readable display text). */
    fun carriesRedirectUrl(code: String?): Boolean =
        code == COMPLETED || code == ACCEPTED || code == UNDER_REVIEW ||
            code == REDIRECT || code == FAILED
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
    val isDisplay: Boolean get() = DocuPassErrorCode.isDisplay(code)
}

/** Thrown for transport/parse failures and unrecoverable API errors. */
class DocuPassException(
    val error: DocuPassError,
    cause: Throwable? = null,
) : Exception(error.message ?: error.code ?: "DocuPass error", cause)
