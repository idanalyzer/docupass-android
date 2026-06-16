package com.idanalyzer.docupass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class DocupassKycEventKind {
    LOADING,
    PHONE_VERIFICATION,
    CUSTOM_FORM,
    DOCUMENT_COUNTRY_SELECTION,
    DOCUMENT_SELECTION,
    DOCUMENT_CAPTURE,
    FACE_VERIFICATION,
    CONTRACT,
    PARTY_PENDING,
    COMPLETED,
    FAILED
}

data class DocupassPhoneVerificationPayload(
    val state: DocupassSessionState,
    val codeSent: Boolean,
    val currentNumber: String?
)

data class DocupassCustomFormPayload(
    val fields: List<DocupassCustomField>
)

data class DocupassDocumentCountrySelectionPayload(
    val countries: List<KYCCountry>,
    val selectedCountry: KYCCountry?
)

data class DocupassDocumentSelectionPayload(
    val country: KYCCountry,
    val documentTypes: List<KYCDocumentType>,
    val selectedDocumentType: KYCDocumentType?
)

data class DocupassDocumentCapturePayload(
    val country: KYCCountry?,
    val documentType: KYCDocumentType?,
    val documentSide: Int?,
    val allowFileUpload: Boolean
)

data class DocupassFaceVerificationPayload(
    val actions: List<KYCAction>
)

data class DocupassContractPayload(
    val state: DocupassSessionState,
    val html: String,
    val signatureFields: List<DocupassContractSignatureField>
)

data class DocupassCompletedPayload(
    val result: KYCResult
)

data class DocupassFailedPayload(
    val result: KYCResult,
    val error: DocupassNormalizedError?
)

data class DocupassKycNativeState(
    val event: DocupassKycEventKind,
    val isBusy: Boolean,
    val errorMessage: String?,
    val normalizedError: DocupassNormalizedError?,
    val result: KYCResult,
    val phone: DocupassPhoneVerificationPayload? = null,
    val customForm: DocupassCustomFormPayload? = null,
    val documentCountrySelection: DocupassDocumentCountrySelectionPayload? = null,
    val documentSelection: DocupassDocumentSelectionPayload? = null,
    val documentCapture: DocupassDocumentCapturePayload? = null,
    val face: DocupassFaceVerificationPayload? = null,
    val contract: DocupassContractPayload? = null,
    val completed: DocupassCompletedPayload? = null,
    val failed: DocupassFailedPayload? = null
)

interface DocupassKycListener {
    fun onStateChanged(state: DocupassKycNativeState)
}

class DocupassSubscription internal constructor(
    private val job: Job
) {
    fun close() {
        job.cancel()
    }
}

class DocupassKycSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controller: DocupassKycController

    constructor(config: DocupassApiConfig) {
        controller = DocupassKycController(config = config)
    }

    constructor(config: DocupassApiConfig, workflow: List<KYCStep>) {
        controller = DocupassKycController(config = config, workflow = workflow)
    }

    fun subscribe(listener: DocupassKycListener): DocupassSubscription {
        val job = scope.launch {
            controller.state.collectLatest { state ->
                listener.onStateChanged(state.toNativeState())
            }
        }
        return DocupassSubscription(job)
    }

    fun currentState(): DocupassKycNativeState {
        return controller.state.value.toNativeState()
    }

    fun start() {
        controller.emit(DocupassKycIntent.Start)
    }

    fun refresh() {
        controller.emit(DocupassKycIntent.Refresh)
    }

    fun clearError() {
        controller.emit(DocupassKycIntent.ClearError)
    }

    fun restart() {
        controller.emit(DocupassKycIntent.Restart)
    }

    fun sendPhoneCode(number: String?, type: String) {
        controller.emit(DocupassKycIntent.SendPhoneCode(number, type))
    }

    fun verifyPhoneCode(number: String?, code: String) {
        controller.emit(DocupassKycIntent.VerifyPhoneCode(number, code))
    }

    fun saveCustomForm(answers: Map<String, String>) {
        controller.emit(DocupassKycIntent.SaveCustomForm(answers))
    }

    fun selectDocumentCountry(countryCode: String) {
        controller.emit(DocupassKycIntent.SelectDocumentCountry(countryCode))
    }

    fun selectDocumentType(documentTypeCode: String) {
        controller.emit(DocupassKycIntent.SelectDocumentType(documentTypeCode))
    }

    fun uploadDocument(frontBase64: String, backBase64: String?) {
        controller.emit(DocupassKycIntent.UploadDocument(frontBase64, backBase64))
    }

    fun uploadFace(faceBase64List: List<String>) {
        controller.emit(DocupassKycIntent.UploadFace(faceBase64List))
    }

    fun submitContract(signatures: Map<String, String>) {
        controller.emit(DocupassKycIntent.SubmitContract(signatures))
    }

    fun close() {
        controller.close()
        scope.cancel()
    }
}

fun DocupassKycUiState.toNativeState(): DocupassKycNativeState {
    val currentError = error
    return when (val current = event) {
        DocupassKycEvent.Loading -> DocupassKycNativeState(
            event = DocupassKycEventKind.LOADING,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result
        )

        is DocupassKycEvent.PhoneVerification -> DocupassKycNativeState(
            event = DocupassKycEventKind.PHONE_VERIFICATION,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            phone = DocupassPhoneVerificationPayload(
                state = current.state,
                codeSent = current.codeSent,
                currentNumber = current.currentNumber
            )
        )

        is DocupassKycEvent.CustomForm -> DocupassKycNativeState(
            event = DocupassKycEventKind.CUSTOM_FORM,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            customForm = DocupassCustomFormPayload(current.fields)
        )

        is DocupassKycEvent.DocumentCountrySelection -> DocupassKycNativeState(
            event = DocupassKycEventKind.DOCUMENT_COUNTRY_SELECTION,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            documentCountrySelection = DocupassDocumentCountrySelectionPayload(
                countries = current.countries,
                selectedCountry = current.selectedCountry
            )
        )

        is DocupassKycEvent.DocumentSelection -> DocupassKycNativeState(
            event = DocupassKycEventKind.DOCUMENT_SELECTION,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            documentSelection = DocupassDocumentSelectionPayload(
                country = current.country,
                documentTypes = current.documentTypes,
                selectedDocumentType = current.selectedDocumentType
            )
        )

        is DocupassKycEvent.DocumentCapture -> DocupassKycNativeState(
            event = DocupassKycEventKind.DOCUMENT_CAPTURE,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            documentCapture = DocupassDocumentCapturePayload(
                country = current.country,
                documentType = current.documentType,
                documentSide = current.documentSide,
                allowFileUpload = current.allowFileUpload
            )
        )

        is DocupassKycEvent.FaceVerification -> DocupassKycNativeState(
            event = DocupassKycEventKind.FACE_VERIFICATION,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            face = DocupassFaceVerificationPayload(current.actions)
        )

        is DocupassKycEvent.Contract -> DocupassKycNativeState(
            event = DocupassKycEventKind.CONTRACT,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            contract = DocupassContractPayload(
                state = current.state,
                html = current.html,
                signatureFields = current.signatureFields
            )
        )

        DocupassKycEvent.PartyPending -> DocupassKycNativeState(
            event = DocupassKycEventKind.PARTY_PENDING,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result
        )

        is DocupassKycEvent.Completed -> DocupassKycNativeState(
            event = DocupassKycEventKind.COMPLETED,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            completed = DocupassCompletedPayload(current.result)
        )

        is DocupassKycEvent.Failed -> DocupassKycNativeState(
            event = DocupassKycEventKind.FAILED,
            isBusy = isBusy,
            errorMessage = currentError?.message,
            normalizedError = currentError?.normalized,
            result = result,
            failed = DocupassFailedPayload(current.result, current.error)
        )
    }
}

