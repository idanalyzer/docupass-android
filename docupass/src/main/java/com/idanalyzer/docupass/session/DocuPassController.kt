package com.idanalyzer.docupass.session

import com.idanalyzer.docupass.DocuPassClient
import com.idanalyzer.docupass.DocuPassConfig
import com.idanalyzer.docupass.DocuPassResult
import com.idanalyzer.docupass.model.DocuPassError
import com.idanalyzer.docupass.model.DocuPassErrorCode
import com.idanalyzer.docupass.model.DocuPassException
import com.idanalyzer.docupass.model.DocuPassSession
import com.idanalyzer.docupass.model.PhoneChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable state of the verification flow. */
sealed interface DocuPassState {
    data object Idle : DocuPassState
    data object Loading : DocuPassState
    /** Server wants the user to perform `session.parsedTask`. */
    data class Step(val session: DocuPassSession) : DocuPassState
    data class Finished(val result: DocuPassResult) : DocuPassState
}

/**
 * Headless driver for a DocuPass session. Owns the protocol state machine and
 * exposes it as a [StateFlow]; the UI (the bundled [com.idanalyzer.docupass.ui.DocuPassView]
 * or your own) observes [state] and calls the submit* methods with captured data.
 * Capture (camera/liveness) is intentionally NOT here — keep it in the UI layer.
 *
 * All submit* methods are suspend and safe to call from the main thread (IO is
 * dispatched internally). Recoverable rejections surface on [transientErrors]
 * and the flow auto-resyncs; terminal/fatal states move [state] to [Finished].
 */
class DocuPassController(val config: DocuPassConfig) {

    val client = DocuPassClient(config)

    private val _state = MutableStateFlow<DocuPassState>(DocuPassState.Idle)
    val state: StateFlow<DocuPassState> = _state.asStateFlow()

    private val _transientErrors = MutableSharedFlow<DocuPassError>(extraBufferCapacity = 8)
    val transientErrors: SharedFlow<DocuPassError> = _transientErrors.asSharedFlow()

    /** Provide GPS before [start] if the profile may require it. */
    fun setGeolocation(latitude: Double, longitude: Double, accuracy: Double) =
        client.setGeolocation(latitude, longitude, accuracy)

    /** Begin (or restart) the flow. */
    suspend fun start() = run { client.getAction() }

    suspend fun refresh() = run { client.getAction() }

    suspend fun submitDocumentSelection(country: String, type: String) =
        run { client.saveDocumentSelection(country, type) }

    suspend fun submitDocument(frontBase64: String, backBase64: String? = null) =
        run { client.uploadDocument(frontBase64, backBase64) }

    suspend fun submitForm(answers: Map<String, String>) =
        run { client.saveForm(answers) }

    suspend fun submitFace(frames: List<String>, faceVideo: String? = null) =
        run { client.uploadFace(frames, faceVideo) }

    suspend fun submitContract(signatures: Map<String, String>) =
        run { client.submitContract(signatures) }

    /** Request an OTP. Stays on the phone step; does not advance. */
    suspend fun sendPhoneCode(number: String?, channel: PhoneChannel) {
        try {
            client.createPhoneVerification(number, channel)
        } catch (e: DocuPassException) {
            handle(e)
        }
    }

    /** Verify the OTP, then advance to the next task. */
    suspend fun verifyPhoneCode(number: String?, code: String) {
        try {
            client.checkPhoneVerification(number, code)
            run { client.getAction() }
        } catch (e: DocuPassException) {
            handle(e)
        }
    }

    /** Abort the flow (user dismissed). */
    fun cancel() {
        _state.value = DocuPassState.Finished(DocuPassResult.Cancelled(config.reference))
    }

    /** Run a protocol call and fold the result/exception into [state]. */
    private suspend fun run(call: suspend () -> DocuPassSession) {
        if (_state.value !is DocuPassState.Step) _state.value = DocuPassState.Loading
        try {
            val session = call()
            _state.value = DocuPassState.Step(session)
        } catch (e: DocuPassException) {
            handle(e)
        }
    }

    private suspend fun handle(e: DocuPassException) {
        val err = e.error
        when {
            err.isTerminal -> _state.value = DocuPassState.Finished(mapTerminal(err))
            err.isFatal -> _state.value =
                DocuPassState.Finished(DocuPassResult.Error(config.reference, err))
            err.code == null -> // transport (network/parse) — unrecoverable for this attempt
                _state.value = DocuPassState.Finished(DocuPassResult.Error(config.reference, err))
            else -> {
                // Recoverable (DOCUMENT_REJECTED / FACE_REJECTED / GENERIC_ERROR /
                // INVALID_ACTION): surface and resync to the authoritative state.
                _transientErrors.tryEmit(err)
                runCatching { _state.value = DocuPassState.Step(client.getAction()) }
                    .onFailure { if (it is DocuPassException) handle(it) }
            }
        }
    }

    private fun mapTerminal(err: DocuPassError): DocuPassResult {
        val redirect = err.message?.takeIf { it.isNotBlank() }
        return if (err.code == DocuPassErrorCode.FAILED) {
            DocuPassResult.Failed(config.reference, err.code, err.message, redirect)
        } else {
            // COMPLETED / ACCEPTED / UNDER_REVIEW / REDIRECT
            DocuPassResult.Completed(config.reference, redirect, err.code)
        }
    }
}
