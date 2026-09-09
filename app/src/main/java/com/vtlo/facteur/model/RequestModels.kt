package com.vtlo.facteur.model

/** One editable header row in the request builder UI. */
data class HeaderEntry(
    val id: Long,
    val key: String = "",
    val value: String = ""
)

data class HttpResult(
    val statusCode: Int,
    val statusMessage: String,
    val protocol: String,
    val headers: List<Pair<String, String>>,
    val body: String,
    val elapsedMs: Long,
    val bodyTruncated: Boolean
)

val HTTP_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

val METHODS_WITHOUT_BODY = setOf("GET", "HEAD")
