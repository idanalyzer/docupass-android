package com.idanalyzer.docupass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DocupassApiClient(
    private val config: DocupassApiConfig,
    private val logger: DocupassLogger = DocupassPlatformLogger
) {
    private val transport: DocupassHttpTransport = createDefaultDocupassHttpTransport(config, logger)
    private var runtimeSessionId: String? = config.sessionId

    suspend fun getAction(): DocupassApiResult<DocupassSessionState> {
        return requestSession("GET", "get_action")
    }

    suspend fun saveDocumentSelection(
        countryCode: String,
        documentType: String
    ): DocupassApiResult<DocupassSessionState> {
        val body = buildJsonObject {
            put("country", countryCode)
            put("type", documentType)
        }
        return requestSession("POST", "save_document_selection", body)
    }

    suspend fun uploadDocument(
        frontDocumentBase64: String,
        backDocumentBase64: String?
    ): DocupassApiResult<DocupassSessionState> {
        if (frontDocumentBase64.isBlank()) {
            return DocupassApiResult.Error(
                message = "Front document image is required.",
                code = "LOCAL_VALIDATION"
            )
        }

        val body = buildJsonObject {
            put("document", frontDocumentBase64)
            if (!backDocumentBase64.isNullOrBlank()) {
                put("documentBack", backDocumentBase64)
            }
        }
        return requestSession("POST", "upload_document", body)
    }

    suspend fun uploadFace(faceBase64List: List<String>): DocupassApiResult<DocupassSessionState> {
        val nonEmptyFaces = faceBase64List.map { it.trim() }.filter { it.isNotEmpty() }
        if (nonEmptyFaces.isEmpty()) {
            return DocupassApiResult.Error(
                message = "At least one face image is required.",
                code = "LOCAL_VALIDATION"
            )
        }

        val body = buildJsonObject {
            put("face", nonEmptyFaces.joinToString(","))
        }
        return requestSession("POST", "upload_face", body)
    }

    suspend fun createPhoneVerification(
        number: String?,
        type: String
    ): DocupassApiResult<Unit> {
        val body = buildJsonObject {
            put("type", type)
            if (number.isNullOrBlank()) {
                put("number", JsonNull)
            } else {
                put("number", number.trim())
            }
        }
        return requestUnit("POST", "create_phone_verification", body)
    }

    suspend fun checkPhoneVerification(
        number: String?,
        code: String
    ): DocupassApiResult<DocupassSessionState> {
        val body = buildJsonObject {
            put("code", code.trim())
            if (number.isNullOrBlank()) {
                put("number", JsonNull)
            } else {
                put("number", number.trim())
            }
        }
        return requestSession("POST", "check_phone_verification", body)
    }

    suspend fun saveForm(answers: Map<String, String>): DocupassApiResult<DocupassSessionState> {
        val body = buildJsonObject {
            answers.forEach { (fieldId, answer) ->
                put(fieldId, answer)
            }
        }
        return requestSession("POST", "save_form", body)
    }

    suspend fun submitContract(signatures: Map<String, String>): DocupassApiResult<DocupassSessionState> {
        val body = buildJsonObject {
            signatures.forEach { (uid, signatureImage) ->
                put(uid, signatureImage)
            }
        }
        return requestSession("POST", "submit_contract", body)
    }

    suspend fun logAuditData(action: String, data: List<String>): DocupassApiResult<Unit> {
        val body = buildJsonObject {
            put("action", action)
            put(
                "data",
                buildJsonArray {
                    data.forEach { add(JsonPrimitive(it)) }
                }
            )
        }
        return requestUnit("POST", "audit", body)
    }

    fun close() {
        transport.close()
    }

    private suspend fun requestSession(
        method: String,
        path: String,
        body: JsonObject? = null
    ): DocupassApiResult<DocupassSessionState> {
        return when (val result = requestJson(method, path, body)) {
            is DocupassApiResult.Success -> {
                val state = result.data.toSessionState()
                if (!state.sessionId.isNullOrBlank()) {
                    runtimeSessionId = state.sessionId
                }
                DocupassApiResult.Success(state)
            }

            is DocupassApiResult.Error -> result
        }
    }

    private suspend fun requestUnit(
        method: String,
        path: String,
        body: JsonObject? = null
    ): DocupassApiResult<Unit> {
        return when (val result = requestJson(method, path, body)) {
            is DocupassApiResult.Success -> DocupassApiResult.Success(Unit)
            is DocupassApiResult.Error -> result
        }
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JsonObject? = null
    ): DocupassApiResult<JsonObject> {
        val endpoint = buildUrl(path)
        val headers = buildHeaders()
        val bodyText = if (method == "POST") (body ?: buildJsonObject { }).toString() else null

        return try {
            val response = transport.request(method, endpoint, headers, bodyText)
            val parsedJson = parseJsonObject(response.body)

            if (response.status !in 200..299 || parsedJson.hasApiError()) {
                val error = parsedJson.toApiError(
                    fallbackMessage = if (response.status !in 200..299) {
                        "HTTP ${response.status}"
                    } else {
                        "Docupass API returned error"
                    },
                    httpStatus = response.status,
                    rawBody = response.body
                )
                logApiError(endpoint, method, path, error)
                error
            } else {
                DocupassApiResult.Success(parsedJson)
            }
        } catch (e: Exception) {
            val error = DocupassApiResult.Error(
                message = e.message ?: "Network error",
                code = "NETWORK_ERROR"
            )
            logApiError(endpoint, method, path, error, e)
            error
        }
    }

    private fun buildUrl(path: String): String {
        val base = resolveBaseUrl().trim().trimEnd('/')
        val endpoint = path.trim().removePrefix("/")
        return "$base/$endpoint"
    }

    private fun resolveBaseUrl(): String {
        val reference = config.reference?.trim().takeIf { !it.isNullOrBlank() }
        return when {
            !config.baseUrl.isNullOrBlank() -> config.baseUrl.trim()
            reference != null -> resolveDocupassEndpoint(reference)
            else -> DOCUPASS_API_ENDPOINT_US
        }
    }

    private fun buildHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )
        resolveAuthorizationHeader()?.let { headers["Authorization"] = it }
        if (!config.geolocation.isNullOrBlank()) {
            headers["Geolocation"] = config.geolocation
        }
        return headers
    }

    internal fun resolveAuthorizationHeader(): String? {
        if (!config.authorization.isNullOrBlank()) {
            return config.authorization.trim()
        }

        val session = runtimeSessionId?.trim().takeIf { !it.isNullOrBlank() }
        if (session != null) {
            return "DOCUPASS_SESSION $session"
        }

        val reference = config.reference?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        val partyId = config.partyId?.trim().takeIf { !it.isNullOrBlank() }
        return if (partyId != null) {
            "DOCUPASS $reference $partyId"
        } else {
            "DOCUPASS $reference"
        }
    }

    private fun logApiError(
        endpoint: String,
        method: String,
        path: String,
        error: DocupassApiResult.Error,
        throwable: Throwable? = null
    ) {
        val rawBody = error.rawBody
            ?.replace("\n", "\\n")
            ?.replace("\r", "\\r")
            ?.take(DOCUPASS_API_RAW_BODY_LOG_LIMIT)
        val message = buildString {
            append(DOCUPASS_API_ERROR_PREFIX)
            append(" endpoint=").append(endpoint)
            append(" method=").append(method)
            append(" path=").append(path)
            error.httpStatus?.let { append(" httpStatus=").append(it) }
            error.code?.let { append(" code=").append(it) }
            append(" message=").append(error.message)
            if (!rawBody.isNullOrBlank()) {
                append(" rawBody=").append(rawBody)
            }
        }
        logger.error(message, throwable)
    }
}

private val docupassJson = Json {
    ignoreUnknownKeys = true
}

private fun parseJsonObject(rawBody: String): JsonObject {
    if (rawBody.isBlank()) return buildJsonObject { }
    return try {
        docupassJson.parseToJsonElement(rawBody) as? JsonObject ?: buildJsonObject {
            put("raw", rawBody)
        }
    } catch (_: Exception) {
        buildJsonObject {
            put("raw", rawBody)
        }
    }
}

private fun JsonObject.hasApiError(): Boolean {
    if (this["error"] is JsonObject) return true
    return optBoolean("success", true) == false
}

private fun JsonObject.toSessionState(): DocupassSessionState {
    return DocupassSessionState(
        success = optBoolean("success", false),
        sessionId = optNullableString("sessionId"),
        task = optNullableString("task"),
        reference = optNullableString("reference"),
        acceptedDocumentCountry = optNullableString("acceptedDocumentCountry"),
        acceptedDocumentType = optNullableString("acceptedDocumentType"),
        selectedDocumentCountry = optNullableString("selectedDocumentCountry"),
        selectedDocumentType = optNullableString("selectedDocumentType"),
        allowFileUpload = optBoolean("allowFileUpload", false),
        documentSide = optInt("documentSide", 0),
        gps = optBoolean("gps", false),
        reviewData = optBoolean("reviewData", false),
        logoUrl = optNullableString("logoURL"),
        companyName = optNullableString("companyName"),
        welcomeMessage = optNullableString("welcomeMessage"),
        language = optNullableString("language"),
        userPhone = optNullableString("userPhone"),
        hasFaceFile = optBoolean("hasFaceFile", false),
        hasDocumentFile = optBoolean("hasDocumentFile", false),
        verifyDocumentNo = optNullableString("verifyDocumentNo"),
        verifyName = optNullableString("verifyName"),
        verifyDob = optNullableString("verifyDob"),
        verifyAge = optNullableString("verifyAge"),
        verifyAddress = optNullableString("verifyAddress"),
        verifyPostcode = optNullableString("verifyPostcode"),
        preloadFaceLib = optBoolean("preloadFaceLib", false),
        contractSource = optNullableString("contractSource"),
        customFields = optArray("customField").toCustomFields(),
        phoneCountryCodes = optArray("phoneCountryCode").toPhoneCountryCodes(),
        rawJson = toString()
    )
}

private fun List<JsonObject>.toCustomFields(): List<DocupassCustomField> {
    return map { obj ->
        DocupassCustomField(
            fieldId = obj.optNullableString("fieldId").orEmpty(),
            fieldLabel = obj.optNullableString("fieldLabel").orEmpty(),
            fieldDescription = obj.optNullableString("fieldDescription").orEmpty(),
            fieldType = obj.optInt("fieldType", 0),
            fieldData = obj.optNullableString("fieldData").orEmpty()
        )
    }
}

private fun List<JsonObject>.toPhoneCountryCodes(): List<DocupassPhoneCountryCode> {
    return mapNotNull { obj ->
        val dialCode = obj.optNullableString("dial_code").orEmpty()
        if (dialCode.isBlank()) return@mapNotNull null
        DocupassPhoneCountryCode(
            name = obj.optNullableString("name").orEmpty(),
            dialCode = dialCode,
            code = obj.optNullableString("code").orEmpty()
        )
    }
}

private fun JsonObject.toApiError(
    fallbackMessage: String,
    httpStatus: Int? = null,
    rawBody: String? = null
): DocupassApiResult.Error {
    val errorObject = this["error"] as? JsonObject ?: this
    val code = errorObject.optNullableString("code")
    val message = errorObject.optNullableString("message")
        ?: optNullableString("message")
        ?: fallbackMessage

    return DocupassApiResult.Error(
        message = message,
        code = code,
        httpStatus = httpStatus,
        rawBody = rawBody
    )
}

private fun JsonObject.optNullableString(key: String): String? {
    return (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}

private fun JsonObject.optBoolean(key: String, default: Boolean): Boolean {
    return (this[key] as? JsonPrimitive)?.booleanOrNull ?: default
}

private fun JsonObject.optInt(key: String, default: Int): Int {
    return (this[key] as? JsonPrimitive)?.intOrNull ?: default
}

private fun JsonObject.optArray(key: String): List<JsonObject> {
    val array = this[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { it as? JsonObject }
}

