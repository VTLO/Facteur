package com.vtlo.facteur.model

/**
 * Scan types ported from the mythos-web-attacker PoC generator script.
 * For authorized security testing only — every scan is a single explicit
 * action against a target the operator supplies, never an automatic sweep.
 */
enum class PocScanType(val label: String) {
    SSR_LEAK("SSR internal URL/hostname disclosure"),
    HEALTH_UNAUTH("Unauthenticated health/status endpoint"),
    OPEN_REDIRECT("Open redirect via parameter"),
    IDOR("IDOR via numeric ID"),
    OAUTH_DEVICE("OAuth device-code phishing pretext"),
    CORS_MISCONFIG("CORS misconfiguration"),
    HIDDEN_FILE_EXPOSURE("Hidden file / secret exposure"),
    XSS_REFLECTED("Reflected XSS probe")
}

data class PocResult(
    val confirmed: Boolean,
    val log: List<String>
)
