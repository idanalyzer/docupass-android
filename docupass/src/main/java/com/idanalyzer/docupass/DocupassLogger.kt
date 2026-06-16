package com.idanalyzer.docupass

import android.util.Log

interface DocupassLogger {
    fun debug(message: String)
    fun warning(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}

object DocupassPlatformLogger : DocupassLogger {
    override fun debug(message: String) {
        Log.d(DOCUPASS_API_LOG_TAG, message)
    }

    override fun warning(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(DOCUPASS_API_LOG_TAG, message, throwable)
        } else {
            Log.w(DOCUPASS_API_LOG_TAG, message)
        }
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(DOCUPASS_API_LOG_TAG, message, throwable)
        } else {
            Log.e(DOCUPASS_API_LOG_TAG, message)
        }
    }
}

internal const val DOCUPASS_API_LOG_TAG = "DocuPassSdk"
internal const val DOCUPASS_API_ERROR_PREFIX = "[DOCUPASS_API_ERROR]"
internal const val DOCUPASS_API_RAW_BODY_LOG_LIMIT = 2_000

