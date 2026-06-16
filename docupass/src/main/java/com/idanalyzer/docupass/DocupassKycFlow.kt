package com.idanalyzer.docupass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DocupassKycEvent {
    data object Loading : DocupassKycEvent

    data class PhoneVerification(
        val state: DocupassSessionState,
        val codeSent: Boolean,
        val currentNumber: String?
    ) : DocupassKycEvent

    data class CustomForm(
        val fields: List<DocupassCustomField>
    ) : DocupassKycEvent

    data class DocumentCountrySelection(
        val countries: List<KYCCountry>,
        val selectedCountry: KYCCountry?
    ) : DocupassKycEvent

    data class DocumentSelection(
        val country: KYCCountry,
        val documentTypes: List<KYCDocumentType>,
        val selectedDocumentType: KYCDocumentType?
    ) : DocupassKycEvent

    data class DocumentCapture(
        val country: KYCCountry?,
        val documentType: KYCDocumentType?,
        val documentSide: Int?,
        val allowFileUpload: Boolean
    ) : DocupassKycEvent

    data class FaceVerification(
        val actions: List<KYCAction>
    ) : DocupassKycEvent

    data class Contract(
        val state: DocupassSessionState,
        val html: String,
        val signatureFields: List<DocupassContractSignatureField>
    ) : DocupassKycEvent

    data object PartyPending : DocupassKycEvent

    data class Completed(
        val result: KYCResult
    ) : DocupassKycEvent

    data class Failed(
        val result: KYCResult,
        val error: DocupassNormalizedError?
    ) : DocupassKycEvent
}

sealed interface DocupassKycIntent {
    data object Start : DocupassKycIntent
    data object Refresh : DocupassKycIntent
    data object Back : DocupassKycIntent
    data object ClearError : DocupassKycIntent
    data object Restart : DocupassKycIntent

    data class SendPhoneCode(
        val number: String?,
        val type: String
    ) : DocupassKycIntent

    data class VerifyPhoneCode(
        val number: String?,
        val code: String
    ) : DocupassKycIntent

    data class SaveCustomForm(
        val answers: Map<String, String>
    ) : DocupassKycIntent

    data class SelectDocumentCountry(
        val countryCode: String
    ) : DocupassKycIntent

    data class SelectDocumentType(
        val documentTypeCode: String
    ) : DocupassKycIntent

    data class UploadDocument(
        val frontBase64: String,
        val backBase64: String?
    ) : DocupassKycIntent

    data class UploadFace(
        val faceBase64List: List<String>
    ) : DocupassKycIntent

    data class SubmitContract(
        val signatures: Map<String, String>
    ) : DocupassKycIntent
}

data class DocupassKycErrorEvent(
    val message: String,
    val normalized: DocupassNormalizedError?
)

data class DocupassKycUiState(
    val event: DocupassKycEvent = DocupassKycEvent.Loading,
    val result: KYCResult = KYCResult(),
    val isBusy: Boolean = false,
    val canGoBack: Boolean = false,
    val error: DocupassKycErrorEvent? = null
)

class DocupassKycController(
    private val config: DocupassApiConfig,
    workflow: List<KYCStep> = DocupassWorkflow.defaultWorkflow(),
    private val apiClient: DocupassApiClient = DocupassApiClient(config),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val workflow = normalizeWorkflow(workflow)
    private val faceActionCandidates = this.workflow.firstFaceActions()
    private val mutableState = MutableStateFlow(DocupassKycUiState())

    private var currentStepIdx = 0
    private var result = KYCResult()
    private var phoneCodeSent = false
    private var currentPhoneNumber: String? = null
    private val eventBackStack = mutableListOf<DocupassKycEvent>()

    val state: StateFlow<DocupassKycUiState> = mutableState.asStateFlow()

    fun emit(intent: DocupassKycIntent) {
        when (intent) {
            DocupassKycIntent.ClearError -> {
                updateState { it.copy(error = null) }
            }

            DocupassKycIntent.Start -> {
                scope.launch { runStart() }
            }

            DocupassKycIntent.Refresh -> {
                scope.launch { refresh() }
            }

            DocupassKycIntent.Back -> {
                goBack()
            }

            DocupassKycIntent.Restart -> {
                scope.launch {
                    resetLocalState()
                    runStart()
                }
            }

            is DocupassKycIntent.SendPhoneCode -> {
                scope.launch { sendPhoneCode(intent.number, intent.type) }
            }

            is DocupassKycIntent.VerifyPhoneCode -> {
                scope.launch { verifyPhoneCode(intent.number, intent.code) }
            }

            is DocupassKycIntent.SaveCustomForm -> {
                scope.launch { saveCustomForm(intent.answers) }
            }

            is DocupassKycIntent.SelectDocumentCountry -> {
                selectDocumentCountry(intent.countryCode)
            }

            is DocupassKycIntent.SelectDocumentType -> {
                scope.launch { selectDocumentType(intent.documentTypeCode) }
            }

            is DocupassKycIntent.UploadDocument -> {
                scope.launch { uploadDocument(intent.frontBase64, intent.backBase64) }
            }

            is DocupassKycIntent.UploadFace -> {
                scope.launch { uploadFace(intent.faceBase64List) }
            }

            is DocupassKycIntent.SubmitContract -> {
                scope.launch { submitContract(intent.signatures) }
            }
        }
    }

    fun start() {
        emit(DocupassKycIntent.Start)
    }

    fun refreshAsync() {
        emit(DocupassKycIntent.Refresh)
    }

    fun close() {
        apiClient.close()
        scope.cancel()
    }

    private suspend fun runStart() {
        updateState {
            it.copy(event = DocupassKycEvent.Loading, isBusy = config.enabled, error = null)
        }
        if (!config.enabled) {
            publishLocalStep()
            return
        }
        refresh()
    }

    private suspend fun refresh() {
        if (!config.enabled) {
            publishLocalStep()
            return
        }

        setBusy(true)
        try {
            when (val response = apiClient.getAction()) {
                is DocupassApiResult.Success -> applySessionState(response.data)
                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private suspend fun sendPhoneCode(number: String?, type: String) {
        setBusy(true)
        clearError()
        try {
            when (val response = apiClient.createPhoneVerification(number, type)) {
                is DocupassApiResult.Success -> {
                    phoneCodeSent = true
                    currentPhoneNumber = number
                    republishPhoneEvent()
                }

                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private suspend fun verifyPhoneCode(number: String?, code: String) {
        setBusy(true)
        clearError()
        try {
            when (val response = apiClient.checkPhoneVerification(number, code)) {
                is DocupassApiResult.Success -> applySessionState(response.data)
                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private suspend fun saveCustomForm(answers: Map<String, String>) {
        setBusy(true)
        clearError()
        try {
            when (val response = apiClient.saveForm(answers)) {
                is DocupassApiResult.Success -> applySessionState(response.data)
                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private fun selectDocumentCountry(countryCode: String) {
        val country = countryFromCode(countryCode)
        result = result.copy(country = country)
        val documentTypes = documentTypesForFilter(result.sessionState?.acceptedDocumentTypeCodes())
        updateState(recordHistory = true) {
            it.copy(
                event = DocupassKycEvent.DocumentSelection(
                    country = country,
                    documentTypes = documentTypes,
                    selectedDocumentType = result.documentType
                ),
                result = result,
                error = null
            )
        }
    }

    private suspend fun selectDocumentType(documentTypeCode: String) {
        val country = result.country
        if (country == null) {
            showLocalError("Please select country first.")
            return
        }

        val documentType = documentTypeFromCode(documentTypeCode)
        if (documentType == null) {
            showLocalError("Unsupported document type.")
            return
        }

        result = result.copy(documentType = documentType)
        updateState { it.copy(result = result, error = null) }

        if (!config.enabled) {
            publishEventForStep(KYCStep.CaptureDocument)
            return
        }

        setBusy(true)
        try {
            when (
                val response = apiClient.saveDocumentSelection(
                    countryCode = country.code,
                    documentType = documentType.apiTypeCode
                )
            ) {
                is DocupassApiResult.Success -> applySessionState(response.data)
                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private suspend fun uploadDocument(frontBase64: String, backBase64: String?) {
        result = result.copy(documentFrontBase64 = frontBase64, documentBackBase64 = backBase64)

        if (!config.enabled) {
            currentStepIdx = nextWorkflowIndexAfter<KYCStep.CaptureDocument>()
            publishLocalStep()
            return
        }

        setBusy(true)
        clearError()
        try {
            when (
                val response = apiClient.uploadDocument(
                    frontDocumentBase64 = frontBase64,
                    backDocumentBase64 = backBase64
                )
            ) {
                is DocupassApiResult.Success -> applySessionState(response.data)
                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private suspend fun uploadFace(faceBase64List: List<String>) {
        result = result.copy(faceBase64List = faceBase64List, isFaceVerified = true)

        if (!config.enabled) {
            currentStepIdx = nextWorkflowIndexAfter<KYCStep.FaceVerification>()
            publishLocalStep()
            return
        }

        setBusy(true)
        clearError()
        try {
            when (val response = apiClient.uploadFace(faceBase64List)) {
                is DocupassApiResult.Success -> applySessionState(response.data)
                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private suspend fun submitContract(signatures: Map<String, String>) {
        setBusy(true)
        clearError()
        try {
            when (val response = apiClient.submitContract(signatures)) {
                is DocupassApiResult.Success -> applySessionState(response.data)
                is DocupassApiResult.Error -> handleApiError(response)
            }
        } finally {
            setBusy(false)
        }
    }

    private fun applySessionState(session: DocupassSessionState) {
        val selectedCountry = session.selectedDocumentCountry?.let { countryFromCode(it) }
        val selectedDocumentType = session.selectedDocumentType?.let { documentTypeFromCode(it) }
        result = result.copy(
            country = selectedCountry ?: result.country,
            documentType = selectedDocumentType ?: result.documentType,
            serverTask = session.task,
            sessionId = session.sessionId,
            sessionState = session,
            terminalError = null
        )
        phoneCodeSent = false
        currentPhoneNumber = null
        updateState(recordHistory = true) {
            it.copy(
                event = eventForSessionState(session),
                result = result,
                error = null
            )
        }
    }

    private suspend fun handleApiError(error: DocupassApiResult.Error) {
        val normalized = normalizeDocupassError(error)
        result = result.copy(terminalError = normalized)
        when (normalized.action) {
            DocupassErrorAction.SHOW_COMPLETED -> {
                updateState(recordHistory = true) {
                    it.copy(
                        event = DocupassKycEvent.Completed(result),
                        result = result,
                        error = null
                    )
                }
            }

            DocupassErrorAction.SHOW_FAILED -> {
                updateState(recordHistory = true) {
                    it.copy(
                        event = DocupassKycEvent.Failed(result, normalized),
                        result = result,
                        error = null
                    )
                }
            }

            DocupassErrorAction.RESYNC_SESSION -> {
                refresh()
            }

            else -> {
                updateState {
                    it.copy(
                        result = result,
                        error = DocupassKycErrorEvent(
                            message = formatApiErrorMessage(error),
                            normalized = normalized
                        )
                    )
                }
            }
        }
    }

    private fun eventForSessionState(session: DocupassSessionState): DocupassKycEvent {
        return when (session.task?.trim()?.lowercase()) {
            "phone" -> DocupassKycEvent.PhoneVerification(
                state = session,
                codeSent = phoneCodeSent,
                currentNumber = currentPhoneNumber
            )

            "customform" -> DocupassKycEvent.CustomForm(session.customFields)
            "document" -> eventForDocumentSession(session)
            "face" -> DocupassKycEvent.FaceVerification(faceActionCandidates.randomizedFaceActions())
            "contract" -> DocupassKycEvent.Contract(
                state = session,
                html = session.contractSource.orEmpty(),
                signatureFields = extractContractSignatureFields(session.contractSource.orEmpty())
            )

            "party_pending" -> DocupassKycEvent.PartyPending
            else -> DocupassKycEvent.Completed(result)
        }
    }

    private fun eventForDocumentSession(session: DocupassSessionState): DocupassKycEvent {
        val selectedCountry = session.selectedDocumentCountry ?: result.country?.code
        val selectedType = session.selectedDocumentType ?: result.documentType?.apiTypeCode
        return when {
            selectedCountry.isNullOrBlank() -> DocupassKycEvent.DocumentCountrySelection(
                countries = countriesForFilter(session.acceptedDocumentCountryCodes().takeIf { it.isNotEmpty() }),
                selectedCountry = result.country
            )

            selectedType.isNullOrBlank() -> {
                val country = countryFromCode(selectedCountry)
                result = result.copy(country = country)
                DocupassKycEvent.DocumentSelection(
                    country = country,
                    documentTypes = documentTypesForFilter(session.acceptedDocumentTypeCodes()),
                    selectedDocumentType = result.documentType
                )
            }

            else -> DocupassKycEvent.DocumentCapture(
                country = result.country,
                documentType = result.documentType,
                documentSide = session.documentSide,
                allowFileUpload = session.allowFileUpload
            )
        }
    }

    private fun publishLocalStep() {
        val step = workflow.getOrNull(currentStepIdx) ?: KYCStep.Success
        publishEventForStep(step)
    }

    private fun publishEventForStep(step: KYCStep) {
        val event = when (step) {
            is KYCStep.PhoneVerification -> {
                DocupassKycEvent.PhoneVerification(step.state, phoneCodeSent, currentPhoneNumber)
            }

            is KYCStep.CustomForm -> DocupassKycEvent.CustomForm(step.fields)
            is KYCStep.SelectCountry -> DocupassKycEvent.DocumentCountrySelection(
                countries = countriesForFilter(step.filterCodes),
                selectedCountry = result.country
            )

            KYCStep.SelectDocument -> {
                val country = result.country
                if (country == null) {
                    DocupassKycEvent.DocumentCountrySelection(countriesForFilter(null), null)
                } else {
                    DocupassKycEvent.DocumentSelection(country, documentTypesForFilter(null), result.documentType)
                }
            }

            KYCStep.CaptureDocument -> DocupassKycEvent.DocumentCapture(
                country = result.country,
                documentType = result.documentType,
                documentSide = null,
                allowFileUpload = false
            )

            is KYCStep.FaceVerification -> DocupassKycEvent.FaceVerification(step.actions.randomizedFaceActions())
            is KYCStep.Contract -> DocupassKycEvent.Contract(
                state = step.state,
                html = step.state.contractSource.orEmpty(),
                signatureFields = extractContractSignatureFields(step.state.contractSource.orEmpty())
            )

            KYCStep.PartyPending -> DocupassKycEvent.PartyPending
            KYCStep.Success -> DocupassKycEvent.Completed(result)
            is KYCStep.Failed -> DocupassKycEvent.Failed(result, step.error)
        }
        updateState(recordHistory = true) {
            it.copy(event = event, result = result, isBusy = false, error = null)
        }
    }

    private fun republishPhoneEvent() {
        val current = mutableState.value.event
        if (current is DocupassKycEvent.PhoneVerification) {
            updateState {
                it.copy(
                    event = current.copy(
                        codeSent = phoneCodeSent,
                        currentNumber = currentPhoneNumber
                    ),
                    result = result,
                    error = null
                )
            }
        }
    }

    private inline fun <reified T : KYCStep> nextWorkflowIndexAfter(): Int {
        val current = workflow.indexOfFirst { it is T }.takeIf { it >= 0 } ?: currentStepIdx
        return (current + 1).coerceAtMost(workflow.size)
    }

    private fun resetLocalState() {
        currentStepIdx = 0
        result = KYCResult()
        phoneCodeSent = false
        currentPhoneNumber = null
        eventBackStack.clear()
        mutableState.value = DocupassKycUiState()
    }

    private fun goBack() {
        val current = mutableState.value
        if (current.isBusy) return

        val previous = eventBackStack.removeLastOrNull() ?: return
        updateState {
            it.copy(
                event = previous,
                error = null
            )
        }
    }

    private fun setBusy(isBusy: Boolean) {
        updateState { it.copy(isBusy = isBusy) }
    }

    private fun clearError() {
        updateState { it.copy(error = null) }
    }

    private fun showLocalError(message: String) {
        updateState {
            it.copy(
                error = DocupassKycErrorEvent(
                    message = message,
                    normalized = null
                )
            )
        }
    }

    private fun updateState(
        recordHistory: Boolean = false,
        block: (DocupassKycUiState) -> DocupassKycUiState
    ) {
        val previous = mutableState.value
        val next = block(previous)

        if (
            recordHistory &&
            previous.event != next.event &&
            previous.event !is DocupassKycEvent.Loading &&
            !previous.event.isResultScreen()
        ) {
            eventBackStack += previous.event
        }

        if (next.event.isResultScreen()) {
            eventBackStack.clear()
        }

        mutableState.value = next.copy(canGoBack = eventBackStack.isNotEmpty() && !next.event.isResultScreen())
    }
}

private fun DocupassKycEvent.isResultScreen(): Boolean {
    return this is DocupassKycEvent.Completed || this is DocupassKycEvent.Failed
}

