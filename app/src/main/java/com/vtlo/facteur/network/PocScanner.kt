package com.vtlo.facteur.network

import com.vtlo.facteur.model.PocResult
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * PoC checks ported from the mythos-web-attacker script, for use during
 * authorized security testing (pentest engagements, bug bounty, CTF,
 * or your own infrastructure) only.
 */

private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36"

/** Max IDs scanned in one IDOR run, so this stays a targeted PoC rather than a mass enumerator. */
const val IDOR_MAX_RANGE = 50

private val redirectFollowingClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private val noRedirectClient = redirectFollowingClient.newBuilder()
    .followRedirects(false)
    .build()

private fun get(url: String, followRedirects: Boolean = true) =
    (if (followRedirects) redirectFollowingClient else noRedirectClient)
        .newCall(Request.Builder().url(url).header("User-Agent", UA).build())
        .execute()

fun scanSsrLeak(url: String): PocResult {
    val log = mutableListOf("Target: $url")
    return try {
        get(url).use { resp ->
            val body = resp.body?.string().orEmpty()
            log += "HTTP ${resp.code} (${body.length} bytes)"

            val internalUrls = Regex("\"url\"\\s*:\\s*\"(http://[^\"]+)\"")
                .findAll(body).map { it.groupValues[1] }.toSet()
            val hostnames = Regex("http://([a-z][a-z0-9-]+):(\\d+)")
                .findAll(body).map { "${it.groupValues[1]}:${it.groupValues[2]}" }.toSet()
            val backendHeaders = Regex("\"(x-powered-by|server|via)\"\\s*:\\s*\\[\"([^\"]+)\"")
                .findAll(body).map { "${it.groupValues[1]}: ${it.groupValues[2]}" }.toSet()

            if (internalUrls.isNotEmpty()) {
                log += ""
                log += "CONFIRMED: ${internalUrls.size} internal URL(s) leaked"
                internalUrls.forEach { log += "  -> $it" }
            }
            if (hostnames.isNotEmpty()) {
                log += ""
                log += "Internal microservices disclosed:"
                hostnames.forEach { log += "  -> $it" }
            }
            if (backendHeaders.isNotEmpty()) {
                log += ""
                log += "Backend response headers leaked in SSR body:"
                backendHeaders.forEach { log += "  $it" }
            }

            PocResult(confirmed = internalUrls.isNotEmpty() || hostnames.isNotEmpty(), log = log)
        }
    } catch (e: Exception) {
        log += "Error: ${e.message}"
        PocResult(confirmed = false, log = log)
    }
}

private val HEALTH_ENDPOINTS = listOf(
    "/health", "/api/health", "/api/v1/health", "/status",
    "/ping", "/ready", "/live", "/actuator/health", "/metrics"
)

fun scanHealthUnauth(baseUrl: String): PocResult {
    val log = mutableListOf("Target: $baseUrl")
    val trimmed = baseUrl.trimEnd('/')
    val found = mutableListOf<String>()
    for (endpoint in HEALTH_ENDPOINTS) {
        try {
            get("$trimmed$endpoint").use { resp ->
                if (resp.code == 200) {
                    val body = resp.body?.string().orEmpty().take(200)
                    log += ""
                    log += "OPEN: $endpoint -> HTTP 200"
                    log += "  Response: $body"
                    val lower = body.lowercase()
                    if (listOf("database", "db", "status", "up", "down", "ok").any { it in lower }) {
                        log += "  [!] Possible status/DB info disclosed"
                    }
                    found += endpoint
                }
            }
        } catch (_: Exception) {
            // Endpoint unreachable or refused — not itself a finding.
        }
    }
    return PocResult(confirmed = found.isNotEmpty(), log = log)
}

fun scanOpenRedirect(url: String, param: String, payloadUrl: String): PocResult {
    val log = mutableListOf<String>()
    val target = payloadUrl.ifBlank { "https://example.invalid/poc-redirect-target" }
    val host = target.substringAfter("://").substringBefore("/")
    val payloads = listOf(target, "//$host", "/\\$host")
    log += "Target: $url ($param=<payload>)"

    var confirmed = false
    val separator = if (url.contains("?")) "&" else "?"
    for (payload in payloads) {
        try {
            get("$url$separator$param=$payload", followRedirects = false).use { resp ->
                val location = resp.header("Location").orEmpty()
                log += "  $param=$payload -> HTTP ${resp.code} Location: ${location.take(80)}"
                if (location.contains(host, ignoreCase = true)) {
                    log += "CONFIRMED: redirects to the supplied host via $param=$payload"
                    confirmed = true
                }
            }
        } catch (e: Exception) {
            log += "  $param=$payload -> error: ${e.message}"
        }
    }
    return PocResult(confirmed = confirmed, log = log)
}

fun scanIdor(baseUrl: String, idStart: Int, idEnd: Int): PocResult {
    val end = minOf(idEnd, idStart + IDOR_MAX_RANGE - 1)
    val log = mutableListOf("Target: $baseUrl/<id> for id in $idStart..$end")
    val trimmed = baseUrl.trimEnd('/')
    val accessible = mutableListOf<Int>()

    for (id in idStart..end) {
        try {
            get("$trimmed/$id").use { resp ->
                log += "  ID $id: HTTP ${resp.code}"
                if (resp.code == 200) accessible += id
            }
        } catch (e: Exception) {
            log += "  ID $id: error ${e.message}"
        }
    }
    if (accessible.isNotEmpty()) {
        log += ""
        log += "ACCESSIBLE IDs: $accessible"
    }
    return PocResult(confirmed = accessible.isNotEmpty(), log = log)
}

/**
 * Starts an OAuth 2.0 device authorization request and, if one is issued, surfaces the
 * user_code/verification_uri as a pretext for the operator to relay through their own
 * authorized engagement channel. This function never contacts or messages anyone itself.
 */
fun scanOauthDevicePhishing(oktaDomain: String, clientId: String): PocResult {
    val domain = oktaDomain.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
    val log = mutableListOf("Domain: $domain | client_id: $clientId")
    return try {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "openid email profile offline_access")
            .build()
        val request = Request.Builder()
            .url("https://$domain/oauth2/v1/device/authorize")
            .header("User-Agent", UA)
            .post(formBody)
            .build()
        redirectFollowingClient.newCall(request).execute().use { resp ->
            log += "HTTP ${resp.code}"
            val bodyText = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
            val userCode = json?.optString("user_code")?.takeIf { it.isNotBlank() }
            val verificationUri = json?.optString("verification_uri")?.takeIf { it.isNotBlank() }

            if (userCode != null && verificationUri != null) {
                val expiresIn = json.optInt("expires_in", -1)
                log += ""
                log += "DEVICE CODE FLOW ACTIVE — usable as a phishing pretext"
                log += "  user_code:        $userCode"
                log += "  verification_uri: $verificationUri"
                if (expiresIn > 0) log += "  expires_in:       ${expiresIn}s"
                log += ""
                log += "Pretext to relay through your engagement's approved channel:"
                log += "  \"Go to $verificationUri and enter code: $userCode\""
                PocResult(confirmed = true, log = log)
            } else {
                log += "Response: ${bodyText.take(300)}"
                PocResult(confirmed = false, log = log)
            }
        }
    } catch (e: Exception) {
        log += "Error: ${e.message}"
        PocResult(confirmed = false, log = log)
    }
}

fun scanCorsMisconfig(url: String): PocResult {
    val testOrigin = "https://cors-probe-${System.currentTimeMillis()}.invalid"
    val log = mutableListOf("Target: $url", "Test Origin: $testOrigin")
    return try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Origin", testOrigin)
            .build()
        redirectFollowingClient.newCall(request).execute().use { resp ->
            val allowOrigin = resp.header("Access-Control-Allow-Origin")
            val allowCredentials = resp.header("Access-Control-Allow-Credentials")
            log += "HTTP ${resp.code}"
            log += "Access-Control-Allow-Origin: ${allowOrigin ?: "(not present)"}"
            log += "Access-Control-Allow-Credentials: ${allowCredentials ?: "(not present)"}"

            val reflectsArbitraryOrigin = allowOrigin == testOrigin
            if (reflectsArbitraryOrigin) {
                log += ""
                log += "CONFIRMED: an arbitrary Origin is reflected back in Access-Control-Allow-Origin"
                if (allowCredentials.equals("true", ignoreCase = true)) {
                    log += "  [!] Combined with Allow-Credentials: true — any site can read authenticated responses"
                }
            } else if (allowOrigin == "*") {
                log += ""
                log += "Access-Control-Allow-Origin is a wildcard (*) — not itself a vulnerability unless " +
                    "credentials are also allowed, which browsers block for wildcard origins"
            }
            PocResult(confirmed = reflectsArbitraryOrigin, log = log)
        }
    } catch (e: Exception) {
        log += "Error: ${e.message}"
        PocResult(confirmed = false, log = log)
    }
}

private val SENSITIVE_PATHS = listOf(
    "/.env", "/.env.local", "/.env.production", "/.git/HEAD", "/.git/config",
    "/config.json", "/config.yml", "/package.json", "/server.js", "/app.js",
    "/wp-config.php", "/phpinfo.php", "/.htaccess", "/web.config"
)

fun scanHiddenFileExposure(baseUrl: String): PocResult {
    val log = mutableListOf("Target: $baseUrl")
    val trimmed = baseUrl.trimEnd('/')
    val found = mutableListOf<String>()

    for (path in SENSITIVE_PATHS) {
        try {
            get("$trimmed$path").use { resp ->
                if (resp.code == 200) {
                    val preview = resp.body?.string().orEmpty().take(150)
                    log += ""
                    log += "ACCESSIBLE: $path -> HTTP 200"
                    log += "  Preview: $preview"
                    found += path
                }
            }
        } catch (_: Exception) {
            // Path unreachable or connection refused — not itself a finding.
        }
    }
    return PocResult(confirmed = found.isNotEmpty(), log = log)
}

/**
 * Reflects a marker containing HTML-special characters (no script tags or executable payload)
 * to detect unescaped reflection — confirms the reflection surface exists without ever
 * constructing or executing a working XSS payload.
 */
fun scanXssReflected(url: String, param: String): PocResult {
    val marker = "fxss${System.currentTimeMillis() % 100000}"
    val probe = "$marker\"'<>"
    val log = mutableListOf("Target: $url ($param=<probe>)")
    return try {
        val separator = if (url.contains("?")) "&" else "?"
        val encodedProbe = URLEncoder.encode(probe, "UTF-8")
        get("$url$separator$param=$encodedProbe").use { resp ->
            val body = resp.body?.string().orEmpty()
            log += "HTTP ${resp.code}"
            val rawReflected = body.contains(probe)
            val markerReflected = body.contains(marker)
            when {
                rawReflected -> {
                    log += ""
                    log += "CONFIRMED: probe reflected unescaped, including \"'<> characters"
                }
                markerReflected -> {
                    log += ""
                    log += "Marker reflected but special characters appear escaped or stripped — not exploitable as-is"
                }
                else -> log += "Marker not found in response"
            }
            PocResult(confirmed = rawReflected, log = log)
        }
    } catch (e: Exception) {
        log += "Error: ${e.message}"
        PocResult(confirmed = false, log = log)
    }
}
