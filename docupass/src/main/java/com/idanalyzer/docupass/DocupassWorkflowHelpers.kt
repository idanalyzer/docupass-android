package com.idanalyzer.docupass

internal fun normalizeWorkflow(input: List<KYCStep>): List<KYCStep> {
    if (input.any { it is KYCStep.CaptureDocument }) return input

    val normalized = mutableListOf<KYCStep>()
    input.forEach { step ->
        normalized += step
        if (step is KYCStep.SelectDocument) {
            normalized += KYCStep.CaptureDocument
        }
    }
    return normalized
}

internal fun List<KYCStep>.firstFaceActions(): List<KYCAction> {
    return firstOrNull { it is KYCStep.FaceVerification }
        ?.let { (it as KYCStep.FaceVerification).actions }
        ?.takeIf { it.isNotEmpty() }
        ?: KYCAction.entries.toList()
}

internal fun List<KYCAction>.randomizedFaceActions(minCount: Int = 2): List<KYCAction> {
    val uniqueCandidates = distinct().ifEmpty { KYCAction.entries.toList() }
    val desiredCount = maxOf(minCount, 1).coerceAtMost(KYCAction.entries.size)
    val selected = uniqueCandidates.shuffled().toMutableList()

    if (selected.size < desiredCount) {
        selected += KYCAction.entries
            .filterNot { it in selected }
            .shuffled()
    }

    return selected.take(desiredCount)
}

internal fun countriesForFilter(filterCodes: List<String>?): List<KYCCountry> {
    if (filterCodes.isNullOrEmpty()) return ALL_COUNTRIES
    val known = ALL_COUNTRIES.associateBy { it.code.uppercase() }
    return filterCodes
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .map { code -> known[code] ?: KYCCountry(code = code, name = code) }
        .sortedBy { it.name }
}

internal fun countryFromCode(code: String): KYCCountry {
    val normalized = code.trim().uppercase()
    return ALL_COUNTRIES.firstOrNull { it.code.equals(normalized, ignoreCase = true) }
        ?: KYCCountry(code = normalized, name = normalized)
}

internal fun documentTypeFromCode(code: String): KYCDocumentType? {
    return KYCDocumentType.entries.firstOrNull { it.apiTypeCode.equals(code.trim(), ignoreCase = true) }
}

internal fun documentTypesForFilter(acceptedTypes: List<String>?): List<KYCDocumentType> {
    val accepted = acceptedTypes?.map { it.uppercase() }?.toSet().orEmpty()
    return if (accepted.isEmpty()) {
        KYCDocumentType.entries
    } else {
        KYCDocumentType.entries.filter { accepted.contains(it.apiTypeCode) }
    }
}

internal fun extractContractSignatureFields(contractSource: String): List<DocupassContractSignatureField> {
    val tagRegex = Regex("""<(?:img|div)\b[^>]*data-signature[^>]*>""", RegexOption.IGNORE_CASE)
    return tagRegex.findAll(contractSource)
        .mapNotNull { match ->
            val tag = match.value
            val uid = tag.htmlAttribute("data-uid")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DocupassContractSignatureField(
                uid = uid,
                label = tag.htmlAttribute("data-label").orEmpty().ifBlank { "Signature" },
                party = tag.htmlAttribute("data-party")
            )
        }
        .distinctBy { it.uid }
        .toList()
}

private fun String.htmlAttribute(name: String): String? {
    val regex = Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    return regex.find(this)?.groupValues?.getOrNull(1)?.htmlUnescape()
}

private fun String.htmlUnescape(): String {
    return replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

internal fun formatApiErrorMessage(error: DocupassApiResult.Error): String {
    val rawMessage = error.message.trim()
    if (rawMessage.isNotBlank() && !rawMessage.isDiagnosticTokenList()) {
        return rawMessage
    }

    val normalized = normalizeDocupassError(error)
    return normalized.detail.ifBlank { normalized.title }
}

internal fun String.isDiagnosticTokenList(): Boolean {
    val parts = split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return false
    return parts.all { part ->
        part.matches(Regex("""[A-Z][A-Z0-9_ -]{2,}"""))
    }
}

