package com.idanalyzer.docupass

import com.idanalyzer.docupass.model.DocuPassRegion

/**
 * Configuration for a DocuPass verification session.
 *
 * The only required value is [reference] (created server-side via the ID Analyzer
 * v2 API / SDK `POST /docupass`). The device never holds your API key — only this
 * reference. Fetch the verification result afterwards server-side with
 * `GET /docupass/{reference}`.
 *
 * @property reference  The DocuPass reference (e.g. "US…"/"EU…", 26 chars).
 * @property partyId    Optional party sign-token for multi-party contract flows.
 * @property baseUrlOverride  Optional full base URL override (on-prem ID Fort).
 *   When null, the region is derived from the [reference] prefix.
 * @property liveness   Active-liveness tuning (defaults match the web flow).
 * @property connectTimeoutMs / @property readTimeoutMs  Network timeouts.
 */
data class DocuPassConfig(
    val reference: String,
    val partyId: String? = null,
    val baseUrlOverride: String? = null,
    val liveness: LivenessConfig = LivenessConfig(),
    val connectTimeoutMs: Long = 20_000,
    val readTimeoutMs: Long = 30_000,
) {
    val region: DocuPassRegion get() = DocuPassRegion.fromReference(reference)

    val baseUrl: String
        get() = baseUrlOverride?.trimEnd('/') ?: region.baseUrl

    init {
        require(reference.isNotBlank()) { "DocuPass reference must not be blank" }
    }
}

/**
 * Active-liveness parameters. Defaults are ported 1:1 from the DocuPass v3 web
 * client (DOCUPASS_PROTOCOL_SPEC §8) — change only with care, they are tuned
 * against the same `face_landmarker.task` model.
 */
data class LivenessConfig(
    /** Max |head-pose| (percent) still counted as "facing front". */
    val thresholdOffsetPercent: Float = 10f,
    /** Min |head-pose| (percent) required to count as a left/right turn. */
    val thresholdTurnPercent: Float = 40f,
    val frontStayMs: Long = 3_000,
    val leftStayMs: Long = 3_000,
    val rightStayMs: Long = 3_000,
    val successStayMs: Long = 2_000,
    /** Longest side of the uploaded capture, in px. */
    val maxImageSize: Int = 800,
    /** JPEG quality for the uploaded capture. */
    val jpegQuality: Int = 90,
)
