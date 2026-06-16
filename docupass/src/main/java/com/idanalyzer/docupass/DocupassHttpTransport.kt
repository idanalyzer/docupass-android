package com.idanalyzer.docupass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal data class DocupassHttpResponse(
    val status: Int,
    val body: String
)

internal interface DocupassHttpTransport {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): DocupassHttpResponse

    fun close()
}

internal fun createDefaultDocupassHttpTransport(
    config: DocupassApiConfig,
    logger: DocupassLogger
): DocupassHttpTransport {
    if (config.disableSslValidation) {
        logger.warning("disableSslValidation is ignored by DocuPass Android transport.")
    }
    return OkHttpDocupassHttpTransport(
        OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
    )
}

private class OkHttpDocupassHttpTransport(
    private val client: OkHttpClient
) : DocupassHttpTransport {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): DocupassHttpResponse = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }

        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post((body.orEmpty()).toRequestBody(jsonMediaType))
            else -> requestBuilder.method(method.uppercase(), body?.toRequestBody(jsonMediaType))
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            DocupassHttpResponse(
                status = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
