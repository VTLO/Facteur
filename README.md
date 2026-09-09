# Facteur

Facteur is an Android HTTP client for testing your own APIs and web services — in the spirit
of Postman/Insomnia, but as a native app. Build a request (method, URL, headers, body/payload),
send it, and inspect the response (status, headers, body) directly on-device.

## Features

- All common HTTP methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`
- Editable header list
- Free-form request body/payload (JSON, form-encoded, XML, plain text, ...) with a
  configurable `Content-Type`
- Response viewer: status line, elapsed time, response headers, response body
- Cleartext (`http://`) traffic allowed, for testing local/dev servers

## Building

Requires JDK 17+ and the Android SDK (compileSdk 34).

```bash
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Intended use

Facteur sends exactly the request you build, once, to the endpoint you specify. It has no
built-in target lists, scheduling, or request-flooding features — it's a manual testing tool.
Only point it at services you own or are authorized to test.
