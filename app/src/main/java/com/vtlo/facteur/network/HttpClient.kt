package com.vtlo.facteur.network

import com.vtlo.facteur.model.HttpResult
import com.vtlo.facteur.model.METHODS_WITHOUT_BODY
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FacteurRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024

private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/**
 * Sends a single HTTP request built from the given components and blocks until a response
 * (or failure) is available. Must be called off the main thread.
 */
fun sendHttpRequest(
    method: String,
    url: String,
    headers: List<Pair<String, String>>,
    payload: String
): HttpResult {
    val requestBuilder = try {
        Request.Builder().url(url)
    } catch (e: IllegalArgumentException) {
        throw FacteurRequestException("Invalid URL: ${e.message}", e)
    }

    val contentType = headers.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second
    headers.forEach { (key, value) ->
        if (key.isNotBlank()) requestBuilder.addHeader(key, value)
    }

    val requestBody = if (method !in METHODS_WITHOUT_BODY && payload.isNotEmpty()) {
        payload.toRequestBody((contentType ?: "text/plain; charset=utf-8").toMediaTypeOrNull())
    } else null

    requestBuilder.method(method, requestBody)

    val startNanos = System.nanoTime()
    try {
        client.newCall(requestBuilder.build()).execute().use { response ->
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

            val body = response.body
            val bodyText: String
            val truncated: Boolean
            if (body != null) {
                val source = body.source()
                source.request(MAX_RESPONSE_BYTES + 1)
                val buffer = source.buffer
                truncated = buffer.size > MAX_RESPONSE_BYTES
                bodyText = buffer.readUtf8(minOf(buffer.size, MAX_RESPONSE_BYTES))
            } else {
                bodyText = ""
                truncated = false
            }

            return HttpResult(
                statusCode = response.code,
                statusMessage = response.message,
                protocol = response.protocol.toString(),
                headers = response.headers.map { it.first to it.second },
                body = bodyText,
                elapsedMs = elapsedMs,
                bodyTruncated = truncated
            )
        }
    } catch (e: FacteurRequestException) {
        throw e
    } catch (e: Exception) {
        throw FacteurRequestException(e.message ?: "Request failed", e)
    }
}
