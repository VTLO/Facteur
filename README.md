# Facteur

Facteur is an Android HTTP client for testing your own APIs and web services — in the spirit
of Postman/Insomnia, but as a native app. Build a request (method, URL, headers, body/payload),
send it, and inspect the response (status, headers, body) directly on-device. It also includes
a PoC Scanner tab with a handful of targeted checks for authorized security testing.

## Features

### Request tab

- All common HTTP methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`
- Editable header list
- Free-form request body/payload (JSON, form-encoded, XML, plain text, ...) with a
  configurable `Content-Type`
- Response viewer: status line, elapsed time, response headers, response body
- Cleartext (`http://`) traffic allowed, for testing local/dev servers

### PoC Scan tab

Each scan is a single explicit action against a target you supply — nothing runs on a
schedule or against a target list:

- **SSR internal URL/hostname disclosure** — flags internal URLs, microservice hostnames,
  and backend headers leaked in server-rendered HTML.
- **Unauthenticated health/status endpoint** — checks a fixed list of common health/status
  paths (`/health`, `/actuator/health`, `/metrics`, ...) for unauthenticated access.
- **Open redirect** — tests whether a URL parameter redirects to a host you control.
- **IDOR via numeric ID** — probes a small, capped range of sequential IDs (50 max per run)
  against a base URL for missing access control.
- **OAuth device-code phishing pretext** — starts an OAuth 2.0 device authorization request
  and, if issued, displays the resulting `user_code`/`verification_uri` as a pretext.
  This only displays the pretext for you to relay through your engagement's own approved
  channel — the app never contacts or messages a target itself.
- **CORS misconfiguration** — sends a test `Origin` header and reports whether it's
  reflected back in `Access-Control-Allow-Origin` (especially combined with
  `Allow-Credentials: true`).
- **Hidden file / secret exposure** — checks a fixed list of common sensitive paths
  (`.env`, `.git/HEAD`, `config.json`, ...) for unauthenticated access.
- **Reflected XSS probe** — sends a marker containing HTML-special characters (`"'<>`,
  no script tags) and reports whether it comes back unescaped, without ever constructing
  or executing a working payload.

## Building

Requires JDK 17+ and the Android SDK (compileSdk 34).

```bash
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Intended use

The Request tab sends exactly the request you build, once, to the endpoint you specify.
The PoC Scan tab is for **authorized security testing only** — pentest engagements, bug
bounty programs, CTFs, or your own infrastructure. Neither tab has built-in target lists,
scheduling, or request-flooding features. Only point Facteur at systems you own or are
explicitly authorized to test.
