# SimpleSMS

A small, original Android app that sends **standard, user-visible SMS text
messages** through the platform `SmsManager` API. Compose a recipient and a
message, grant the `SEND_SMS` runtime permission, and send; the app reports the
radio's send result for each message part.

It is deliberately an ordinary SMS sender:

- Uses only the public `SmsManager` API.
- Every message is composed by the user and visible to the recipient.
- No "silent" (Type-0) pings, no forced Class-0/flash, no PDU injection — those
  require privileged radio-level access and exist to reach phones covertly,
  which is out of scope. See [`docs/SMS_PDU.md`](docs/SMS_PDU.md) for a
  technical explanation of SMS PDU encoding and why the public API cannot (and
  this app does not) produce those message types.

## Build

Requires a JDK 17+ and the Android SDK (platform 34, build-tools 34.0.0).

```bash
# point the build at your SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Install & use

1. Install on a device/emulator with a SIM and cellular service:
   `adb install app/build/outputs/apk/debug/app-debug.apk`
2. Open **SimpleSMS**, enter a recipient number and a message.
3. Tap send and approve the `SEND_SMS` permission prompt.

Standard carrier SMS charges apply. Only send messages to recipients who
expect to hear from you.

## Project layout

```
app/src/main/
  AndroidManifest.xml              SEND_SMS permission, single activity
  java/com/vtlo/simplesms/
    MainActivity.kt                Compose UI + runtime permission flow
    SmsSender.kt                   SmsManager wrapper + send-status reporting
    ui/theme/Theme.kt              Material 3 theme
docs/SMS_PDU.md                    SMS PDU / DCS / PID reference
```
