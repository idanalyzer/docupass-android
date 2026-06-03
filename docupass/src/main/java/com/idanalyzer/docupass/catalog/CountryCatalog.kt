package com.idanalyzer.docupass.catalog

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A country entry from the bundled `country.json` (ported from the web flow). */
@Serializable
data class Country(
    val iso: String = "",
    val name_en: String = "",
    /** String of 1-char document-type codes available for this country. */
    val licencetypes: String = "",
) {
    val documentTypeCodes: List<String> get() = licencetypes.map { it.toString() }
}

/**
 * Loads and filters the bundled country + document-type catalog. The DocuPass
 * session only sends *accepted* country/type restrictions; this resolves them to
 * a user-pickable list. Loaded once and cached.
 */
class CountryCatalog private constructor(private val all: List<Country>) {

    /** Countries to offer, honoring the session's accepted-country restriction. */
    fun countries(acceptedIso: List<String>): List<Country> =
        if (acceptedIso.isEmpty()) all
        else all.filter { it.iso in acceptedIso }

    fun country(iso: String): Country? = all.firstOrNull { it.iso.equals(iso, ignoreCase = true) }

    /** Document types for a country, honoring the session's accepted-type restriction. */
    fun documentTypes(iso: String, acceptedTypes: List<String>): List<DocumentTypeOption> {
        val codes = country(iso)?.documentTypeCodes.orEmpty()
        return codes.filter { acceptedTypes.isEmpty() || it in acceptedTypes }
            .map { DocumentTypeOption(it, documentTypeLabel(it)) }
    }

    companion object {
        @Volatile private var cached: CountryCatalog? = null
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun load(context: Context): CountryCatalog {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: run {
                    val text = context.assets.open("country.json")
                        .bufferedReader().use { it.readText() }
                    val list = json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(Country.serializer()), text
                    )
                    CountryCatalog(list).also { cached = it }
                }
            }
        }

        /** Human label for a 1-char document-type code. */
        fun documentTypeLabel(code: String): String = when (code.uppercase()) {
            "P" -> "Passport"
            "D" -> "Driver License"
            "I" -> "Identity Card"
            "R" -> "Residence Permit"
            "V" -> "Visa"
            "H" -> "Health Card"
            "T" -> "Travel Document"
            else -> "Document ($code)"
        }
    }
}

data class DocumentTypeOption(val code: String, val label: String)
