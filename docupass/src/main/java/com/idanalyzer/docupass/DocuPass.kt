package com.idanalyzer.docupass

import com.idanalyzer.docupass.model.DocuPassError

/** Library version. */
const val DOCUPASS_SDK_VERSION = "0.1.0"

/**
 * Outcome of a DocuPass verification. The actual verification *result/data* lives
 * server-side — fetch it with the ID Analyzer v2 server SDK `GET /docupass/{ref}`
 * using your API key. The device only ever sees this high-level outcome.
 */
sealed interface DocuPassResult {
    val reference: String

    /** Flow finished successfully (accepted / under-review). */
    data class Completed(
        override val reference: String,
        /** Server-configured redirect URL, if any. */
        val redirectUrl: String? = null,
        val code: String? = null,
    ) : DocuPassResult

    /** Flow finished with a rejection/failure decision. */
    data class Failed(
        override val reference: String,
        val code: String? = null,
        val message: String? = null,
        val redirectUrl: String? = null,
    ) : DocuPassResult

    /** User dismissed/aborted before completion. */
    data class Cancelled(override val reference: String) : DocuPassResult

    /** Unrecoverable error (network, fatal session error, etc.). */
    data class Error(
        override val reference: String,
        val error: DocuPassError,
    ) : DocuPassResult
}
