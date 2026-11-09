# Android setup and device checklist

## Requirements

- JDK 17
- Android SDK platform 34 and build-tools 34.0.0
- Node.js 20+ for the browser client
- A physical Android 8.0+ device (API 26+) with USB debugging, or an emulator with a
  system image

The project uses Gradle 8.7, AGP 8.6.1, Kotlin 2.0.20, Compose BOM 2024.09.00, Ktor
2.3.12, Media3 1.4.1 and Room 2.6.1. These versions are pinned in
`android/gradle/libs.versions.toml`.

> This environment had no connected device, AVD or system image, so the APK was compiled
> and assembled but never installed or exercised on Android hardware. Treat device
> behaviour as unverified until you complete the checklist below.

## Environment

PowerShell example:

```powershell
$env:JAVA_HOME = "C:\Users\you\tools\jdk-17"
$env:ANDROID_HOME = "C:\Users\you\tools\android-sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

Create `android/local.properties` if your SDK is not found automatically:

```properties
sdk.dir=C\:\\Users\\you\\tools\\android-sdk
```

`local.properties` is machine-specific and must not be committed.

Verify:

```powershell
java -version
adb version
```

## Build the browser client

The Android app bundles `web/dist` into its assets. Build it first:

```powershell
cd web
npm ci
npm run typecheck
npm test
npm run build
```

`web/dist` must contain `index.html`, `assets/*.js`, `assets/*.css`, the manifest, the
service worker and the icons. The Gradle task `syncWebAssets` copies it to
`android/app/src/main/assets/web` before `preBuild`.

If you skip this step, the APK still builds, but the host serves a fallback page saying
the browser client was not bundled.

## Build the APK

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Useful variants:

```powershell
# Only compile the Kotlin sources
.\gradlew.bat :app:compileDebugKotlin

# Run all JVM/unit tests
.\gradlew.bat :shared:test :streaming:testDebugUnitTest

# Build a minified, unsigned release APK
.\gradlew.bat :app:assembleRelease
```

The release output is `android/app/build/outputs/apk/release/app-release-unsigned.apk`
unless you configure signing.

## Install

```powershell
adb devices
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

If `adb devices` is empty, enable Developer Options → USB debugging, accept the RSA
prompt, and use a data-capable USB cable. Wireless debugging is also supported by recent
adb versions.

### Emulator

Create an AVD with API 34 and a Google APIs or AOSP image:

```powershell
sdkmanager "platform-tools" "platforms;android-34" "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n watchwave -k "system-images;android-34;google_apis;x86_64"
emulator -avd watchwave
```

No AVD was created or tested in this environment.

## App configuration

Open **Settings** in the app:

| Setting | Default | Notes |
| --- | --- | --- |
| Display name | Host | 1–32 characters |
| Listen port | 8080 | Scans up to 25 nearby ports if busy |
| Max participants | 10 | Clamped to 2–50 |
| Room lifetime | 360 min | Clamped to 5–1440 min |
| Tunnel mode | None | Companion URL or cloudflared process |
| Drift thresholds | 300/1200/3000 ms | Tune if your network is slow |
| Auto-reconnect | On | Reconnect the host/viewer WebSocket |

Build-time values:

```powershell
.\gradlew.bat :app:assembleDebug `
  -Pwatchwave.applicationId=com.example.watchwave `
  -Pwatchwave.baseDomain=wave.example.com
```

`watchwave.baseDomain` is required only for `watchwave://room/...` links and bare room
codes. Without it, use a full `http://` or `https://` invitation link.

## Host a room

1. Open the **Library** tab and import a video with the system file picker (or import a
   folder). The app takes a persistable read grant; it does not copy the video.
2. Tap **Create party**, choose the video, room name and whether viewers may control
   playback.
3. Start hosting. The app starts the embedded server, the foreground service and the
   local sync client.
4. Share the LAN link on the same Wi-Fi, or configure a tunnel for remote viewers.
5. On the host screen you can play/pause/seek/speed, lock viewer controls, disable joins,
   remove participants, rotate the invite and close the room.
6. Diagnostics shows the server address, active connections, bytes sent and tunnel state.

The host must stay in the foreground service; force-stopping the app ends the room and
invalidates every link.

## Join as an Android viewer

1. Open the invitation link on the device, or paste it into **Join**.
2. Preview the room, enter a display name, and join.
3. The viewer uses the same ExoPlayer and `PlaybackSync` as the host, but connects to the
   host's URL over the network.
4. If the connection drops, the client reconnects with the saved session token. If the
   host closed the room, the app shows the closure instead of retrying forever.

Do not run a host and viewer session simultaneously in the same app process; the UI and
engine intentionally allow one session at a time.

## Join from a browser

Open the share URL in a modern browser (Chrome, Edge, Firefox, Safari). The client is
served by the host, so no separate deployment is needed. On a LAN this is HTTP; remote
viewers should use the verified HTTPS tunnel URL.

For local browser development against a phone:

```powershell
# Forward the phone's port to the development machine
adb reverse tcp:8080 tcp:8080

cd web
$env:WATCHWAVE_DEV_HOST = "http://127.0.0.1:8080"
npm run dev
```

Then open `http://localhost:5173`. The Vite proxy forwards `/api` and WebSocket traffic
to the host. `VITE_WATCHWAVE_ORIGIN` is only for serving the static client from a
different origin; leave it empty for the normal same-origin deployment.

## Permissions and privacy

The manifest requests:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS` (Android 13+; denying it keeps the service running silently)

The app does not request broad storage permission. It reads only documents the user
selects through the Storage Access Framework. `android:allowBackup="false"` prevents
cloud backup of app data.

## Release signing

The default `assembleRelease` output is unsigned. To sign it manually:

```powershell
keytool -genkeypair -v -keystore watchwave-release.jks `
  -alias watchwave -keyalg RSA -keysize 4096 -validity 10000

cd android
.\gradlew.bat :app:assembleRelease
$env:APKSIGNER = "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat"
& $env:APKSIGNER sign --ks ..\watchwave-release.jks `
  --out app\build\outputs\apk\release\app-release-signed.apk `
  app\build\outputs\apk\release\app-release-unsigned.apk
& $env:APKSIGNER verify --print-certs app\build\outputs\apk\release\app-release-signed.apk
```

Never commit a keystore or its passwords. The release CI workflow uploads the unsigned
artifact; add your own signing step or configure a local `signingConfig` if you need a
distributable APK.

## Device test checklist

Complete these on real hardware before claiming the project works end to end:

### Host

- [ ] Install the debug APK and launch it.
- [ ] Grant notification permission; verify a Media3 foreground notification appears
      while hosting.
- [ ] Import a video via SAF; verify the title, duration and size.
- [ ] Start hosting on the default port; verify the local playback starts.
- [ ] Stop another app that occupies 8080; verify the app picks a nearby free port.
- [ ] Play, pause, seek and change speed on the host.
- [ ] Lock viewer controls and verify a viewer cannot change playback.
- [ ] Unlock controls and verify a viewer can change playback.
- [ ] Disable joins and verify a new viewer is rejected.
- [ ] Remove a participant and verify their WebSocket closes.
- [ ] Rotate the invite and verify the old link returns `403 INVITE_INVALID`.
- [ ] Close the room and verify viewers see `ROOM_CLOSED`.
- [ ] Turn the screen off and verify playback/streaming survives.
- [ ] Revoke the document grant (or delete/move the file) and verify
      `MEDIA_PERMISSION_REVOKED` instead of corrupt bytes.
- [ ] Force-stop the app and verify every previous link is dead.

### Android viewer

- [ ] Open an HTTPS invitation link; the app opens the join screen.
- [ ] Preview the room and join with a display name.
- [ ] Verify play/pause/seek/speed stay within the drift thresholds.
- [ ] Put the viewer in the background and return; verify state catches up.
- [ ] Toggle airplane mode for 10 seconds; verify reconnect and resync.
- [ ] Let the host close the room; verify the viewer stops retrying.

### Browser viewer

- [ ] Open the LAN link in Chrome/Edge/Firefox.
- [ ] Join, unmute, play/pause/seek/speed, fullscreen and PiP.
- [ ] Verify a second browser tab does not clash with the first session.
- [ ] Verify a blocked autoplay shows the tap-to-join overlay.
- [ ] Verify the media token refresh works after 45 minutes (or shorten the TTL for a
      test build).
- [ ] Verify the page works over the HTTPS tunnel, including WSS.

### Streaming

```bash
# Health
curl -s https://<host>/api/v1/health

# Full response
curl -I https://<host>/api/v1/rooms/<id>/video?t=<media token>

# Partial response
curl -s -D - -o /dev/null \
  -H "Range: bytes=0-1023" \
  "https://<host>/api/v1/rooms/<id>/video?t=<media token>"
```

Expect `206`, `Accept-Ranges: bytes`, a `Content-Range` header and exactly 1,024 bytes.
A range beyond the file must return `416`.

### Tunnel

- [ ] Configure a companion HTTPS URL that forwards to the phone.
- [ ] Verify the app shows the link only after `/api/v1/health` returns `WatchWave`.
- [ ] Verify a wrong/dead URL stays unverified and is never shown as shareable.
- [ ] Verify the tunnel link works on a cellular network with Wi-Fi disabled.
- [ ] Verify a named cloudflared tunnel token is not visible in logs or the API.

## Common setup problems

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for Gradle, SDK, port, LAN, tunnel, media and
browser issues.
