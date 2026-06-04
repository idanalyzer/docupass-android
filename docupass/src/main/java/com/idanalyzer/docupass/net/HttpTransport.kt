package com.idanalyzer.docupass.net

import com.idanalyzer.docupass.model.DocuPassError
import com.idanalyzer.docupass.model.DocuPassException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP layer for the docupassappv3 protocol: builds requests against a base
 * URL, applies the evolving DocuPass `Authorization` header, optionally the
 * `Geolocation` header, and parses the `{success,error{code,message}}` envelope.
 *
 * It is auth-state aware: once the server returns a `sessionId`, all subsequent
 * requests switch from `DOCUPASS <ref>[ <partyId>]` to `DOCUPASS_SESSION <id>`.
 */
internal class HttpTransport(
    private val baseUrl: String,
    private val reference: String,
    private val partyId: String?,
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // The server marshals empty/absent slices and optional values as explicit
        // JSON `null` (e.g. "customField": null when no custom form is configured).
        // Coerce those nulls to each property's default instead of throwing.
        coerceInputValues = true
    }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    /** Set after the first get_action; switches auth to the session form. */
    @Volatile var sessionId: String? = null
    /** "lat,lng,accuracy" — sent as the Geolocation header when present. */
    @Volatile var geolocation: String? = null

    val parser: Json get() = json

    private fun authHeader(): String {
        sessionId?.takeIf { it.isNotBlank() }?.let { return "DOCUPASS_SESSION $it" }
        return if (!partyId.isNullOrBlank()) "DOCUPASS $reference $partyId" else "DOCUPASS $reference"
    }

    private fun url(path: String): String = "$baseUrl/${path.removePrefix("/")}"

    private fun newRequest(path: String): Request.Builder {
        val b = Request.Builder()
            .url(url(path))
            .header("Authorization", authHeader())
            .header("Accept", "application/json")
        geolocation?.takeIf { it.isNotBlank() }?.let { b.header("Geolocation", it) }
        return b
    }

    /** GET returning the parsed success-envelope body as a [JsonObject]. */
    suspend fun get(path: String): JsonObject = execute(newRequest(path).get().build())

    /** POST a JSON body, returning the parsed success-envelope body. */
    suspend fun postJson(path: String, body: JsonObject): JsonObject {
        val req = newRequest(path)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return execute(req)
    }

    /** Fire-and-forget POST (used by audit); never throws. */
    suspend fun postJsonQuietly(path: String, body: JsonObject) {
        runCatching {
            withContext(Dispatchers.IO) {
                client.newCall(
                    newRequest(path).post(body.toString().toRequestBody(jsonMedia)).build()
                ).execute().use { /* ignore */ }
            }
        }
    }

    private suspend fun execute(request: Request): JsonObject = withContext(Dispatchers.IO) {
        val raw: String
        val status: Int
        try {
            client.newCall(request).execute().use { resp ->
                status = resp.code
                raw = resp.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw DocuPassException(DocuPassError(code = "NETWORK_ERROR", message = e.message), e)
        }

        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw DocuPassException(
                DocuPassError(code = "PARSE_ERROR", message = "Non-JSON response", httpStatus = status, rawBody = raw)
            )

        val success = obj["success"]?.let { runCatching { it.jsonPrimitive.boolean }.getOrDefault(false) } ?: false
        val errorObj = obj["error"]?.let { runCatching { it.jsonObject }.getOrNull() }

        if (!success || errorObj != null || status !in 200..299) {
            val code = errorObj?.get("code")?.jsonPrimitive?.contentOrNull
            val message = errorObj?.get("message")?.jsonPrimitive?.contentOrNull
                ?: obj["message"]?.jsonPrimitive?.contentOrNull
            throw DocuPassException(DocuPassError(code, message, status, raw))
        }
        obj
    }
}
