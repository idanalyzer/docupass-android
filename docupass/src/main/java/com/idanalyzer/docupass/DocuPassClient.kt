package com.idanalyzer.docupass

import com.idanalyzer.docupass.model.DocuPassSession
import com.idanalyzer.docupass.model.PhoneChannel
import com.idanalyzer.docupass.net.HttpTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Headless client for the DocuPass v3 mobile protocol (docupassappv3).
 *
 * One instance == one verification session. Every call returns the latest
 * [DocuPassSession] (the server returns the next state from each POST too, so
 * you usually don't need a separate [getAction] between steps). Terminal and
 * error states are thrown as [com.idanalyzer.docupass.model.DocuPassException];
 * inspect its `error.code` against
 * [com.idanalyzer.docupass.model.DocuPassErrorCode].
 *
 * This class performs no UI and no capture — pair it with the headless
 * [com.idanalyzer.docupass.session.DocuPassController] or the drop-in
 * [com.idanalyzer.docupass.ui.DocuPassView].
 */
class DocuPassClient(val config: DocuPassConfig) {

    private val transport = HttpTransport(
        baseUrl = config.baseUrl,
        reference = config.reference,
        partyId = config.partyId,
        connectTimeoutMs = config.connectTimeoutMs,
        readTimeoutMs = config.readTimeoutMs,
    )

    /** Set the GPS header (sent on subsequent requests) when the session asks for it. */
    fun setGeolocation(latitude: Double, longitude: Double, accuracy: Double) {
        transport.geolocation = "$latitude,$longitude,$accuracy"
    }

    /** GET get_action — the state machine. Drives every screen. */
    suspend fun getAction(): DocuPassSession = parse(transport.get("get_action"))

    /** POST save_document_selection — country (ISO-2) + 1-char document type. */
    suspend fun saveDocumentSelection(country: String, type: String): DocuPassSession =
        parse(transport.postJson("save_document_selection", buildJsonObject {
            put("country", country)
            put("type", type)
        }))

    /** POST upload_document — base64 JPEG(s), no data: prefix. Back omitted if front-only. */
    suspend fun uploadDocument(frontBase64: String, backBase64: String? = null): DocuPassSession =
        parse(transport.postJson("upload_document", buildJsonObject {
            put("document", frontBase64)
            if (!backBase64.isNullOrBlank()) put("documentBack", backBase64)
        }))

    /** POST save_form — answers keyed by the server's [CustomField.fieldId]. */
    suspend fun saveForm(answers: Map<String, String>): DocuPassSession =
        parse(transport.postJson("save_form", buildJsonObject {
            answers.forEach { (k, v) -> put(k, v) }
        }))

    /**
     * POST upload_face — one or more base64 frames (the best-neutral frame is
     * enough). [faceVideo] is an optional base64 video for video-capture mode.
     */
    suspend fun uploadFace(frames: List<String>, faceVideo: String? = null): DocuPassSession =
        parse(transport.postJson("upload_face", buildJsonObject {
            put("face", frames.joinToString(","))
            if (!faceVideo.isNullOrBlank()) put("faceVideo", faceVideo)
        }))

    /** POST submit_contract — signatures keyed by signature-field uid (base64 image). */
    suspend fun submitContract(signatures: Map<String, String>): DocuPassSession =
        parse(transport.postJson("submit_contract", buildJsonObject {
            signatures.forEach { (uid, img) -> put(uid, img) }
        }))

    /**
     * POST create_phone_verification. [number] is E.164-ish (`+<6..30 digits>`);
     * omit it when the session has a preset `userPhone`.
     */
    suspend fun createPhoneVerification(number: String?, channel: PhoneChannel): DocuPassSession =
        parse(transport.postJson("create_phone_verification", buildJsonObject {
            if (!number.isNullOrBlank()) put("number", number)
            put("type", channel.wire)
        }))

    /** POST check_phone_verification — 6-digit code. */
    suspend fun checkPhoneVerification(number: String?, code: String): DocuPassSession =
        parse(transport.postJson("check_phone_verification", buildJsonObject {
            if (!number.isNullOrBlank()) put("number", number)
            put("code", code)
        }))

    /** POST audit — best-effort telemetry; never throws, never blocks the flow. */
    suspend fun audit(action: String, data: List<String> = emptyList()) {
        transport.postJsonQuietly("audit", buildJsonObject {
            put("action", action)
            put("data", buildJsonArray { data.forEach { add(it) } })
        })
    }

    private fun parse(obj: JsonObject): DocuPassSession {
        val session = transport.parser.decodeFromJsonElement(DocuPassSession.serializer(), obj)
        session.sessionId?.takeIf { it.isNotBlank() }?.let { transport.sessionId = it }
        return session
    }
}
