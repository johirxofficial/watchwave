# Troubleshooting

## Build and environment

### `JAVA_HOME is not set` / wrong Java version

Use JDK 17, not 21+ for this pinned toolchain:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
java -version
```

### `SDK location not found`

Set `ANDROID_HOME` or create `android/local.properties`:

```properties
sdk.dir=C\:\\Users\\you\\tools\\android-sdk
```

Install platform 34 and build-tools 34.0.0 with `sdkmanager` or Android Studio.

### Gradle runs out of memory or hangs

The project uses `-Xmx3g`. Close other Gradle/IDE builds and retry:

```powershell
cd android
.\gradlew.bat --stop
.\gradlew.bat :app:assembleDebug --console=plain
```

Do not enable the configuration cache for this build; it is intentionally disabled.

### The APK shows "browser client was not bundled"

`web/dist` was missing or empty when the APK was built. Build the web client first:

```powershell
cd web
npm ci
npm run build
cd ..\android
.\gradlew.bat :app:assembleDebug
```

`syncWebAssets` runs at `preBuild`. Rebuilding the APK after `web/dist` changes is
required; changing `web/` alone does not update an already-built APK.

### `npm.ps1 cannot be loaded` on Windows

PowerShell execution policy is blocking the npm shim. Use:

```powershell
cmd /c "npm ci"
cmd /c "npm test"
cmd /c "npm run build"
```

or run the commands from `cmd.exe`.

### Node warns `localStorage is not available because --localstorage-file was not provided`

This is a harmless Node 26 experimental warning. The Vitest setup polyfills Web Storage.
It does not affect test results.

### Web build fails on a missing icon

The icons are committed under `web/public`. If you removed them, regenerate PNG icons
from `web/public/icon.svg` or restore the files. The build does not download assets.

## Android runtime

### App starts but the host never becomes live

- Check that the selected video still exists and is readable.
- Check the port. The app scans up to 25 nearby ports; if all are busy it reports an
  error. Close the conflicting app or change the port in Settings.
- Check that the foreground service notification permission was granted (or at least
  not blocking service start on your OEM build).
- Open **Diagnostics** for the last error and whether the server is running.

### Viewer cannot reach the LAN link

- Both devices must be on the same network and the network must allow client-to-client
  traffic. Guest Wi-Fi and many public hotspots enable AP isolation, which blocks it.
- Use the phone's actual Wi-Fi IPv4 address, not `127.0.0.1`.
- Allow the port through the phone's firewall/security app and any host firewall.
- Disable VPNs that route LAN traffic away.
- Test from the host itself:

  ```bash
  curl http://127.0.0.1:8080/api/v1/health
  ```

  Then from another device:

  ```bash
  curl http://<phone-ip>:8080/api/v1/health
  ```

### Cleartext HTTP is blocked

The manifest enables cleartext traffic for the LAN use case. If you changed the manifest
or use a custom build, do not disable `usesCleartextTraffic` while relying on HTTP LAN
links. Remote links should be HTTPS.

### Notification permission denied

The foreground service still runs; the notification is simply quieter. On some OEM
builds aggressive battery management may still kill the process. Exempt WatchWave from
battery optimisation for long rooms.

### Background playback stops

- Confirm the foreground notification is visible while hosting.
- Disable battery optimisation for WatchWave.
- Do not swipe the app away; that stops the service and ends the room.
- Some devices suspend Wi-Fi when the screen is off. Test with the charger connected and
  the battery optimisation exemption enabled.

### Deep link does not open

- Use a full `https://.../room/<id>?t=<invite>` link.
- `watchwave://room/...` and bare room codes require a build with
  `-Pwatchwave.baseDomain=<your-domain>`.
- If the link opens a browser instead, the app's link handling was not registered by the
  launcher/OS. Open the app and paste the link into **Join** as a fallback.

## Media and streaming

### `MEDIA_UNAVAILABLE`

The host has no readable media for the room, the file is empty, or the size is zero.
Reselect the video and start a new room.

### `MEDIA_PERMISSION_REVOKED`

The SAF grant was lost because the file was deleted, moved, the provider revoked access,
or the app data was cleared. Reselect the document in the library.

### Video does not play in the browser

WatchWave streams the original file and does not transcode. Browser codec support is
limited:

- H.264/AAC in MP4 is the safest combination.
- HEVC/H.265, some MKV audio tracks, AC-3 and unusual containers may not play in
  Chrome/Firefox even though a native Android player can.
- If ExoPlayer plays but the browser does not, the codec/container is the likely cause.
  Convert the file yourself if cross-browser playback is required.

### Seeking fails or the video stalls

Some SAF/document providers do not support random access. The host opens a file
descriptor and seeks to the requested byte offset; if the provider cannot seek, the
stream fails instead of serving corrupt data. Copy the file to local storage or use a
provider that supports random access.

### `416 RANGE_NOT_SATISFIABLE`

The requested range is malformed, has multiple ranges, starts beyond the file, or has an
invalid end. Request one range such as `bytes=0-1023`. Check the file size with a `HEAD`
request.

### `503 RATE_LIMITED` on video

The host is at its concurrent stream limit (24 by default). Close unused tabs/devices
and retry after the `Retry-After` delay.

### Video stops after about an hour

The media token expired. The browser refreshes it every 45 minutes; if the tab was
suspended, reload or rejoin. Android refreshes on reconnect. A host restart invalidates
the old token immediately.

## WebSocket and sync

### Viewer reconnects repeatedly

- Check the host is still live and the server port is reachable.
- `1008` means the session is invalid/revoked: ask the host for a new invite.
- `1000` usually means the room closed or another connection replaced this one.
- Other codes are network failures; the client backs off and retries.
- If the host's own local client keeps reconnecting, the embedded server or loopback
  path is unhealthy; check Diagnostics and the app log.

### Everyone is slightly out of sync

The client projects the authoritative anchor and applies drift correction. Check the
drift thresholds in Settings. Very small thresholds on a jittery network can cause
constant rate nudges. Large thresholds allow more visible drift before a seek.

### Viewer controls do nothing

The host may have locked viewer controls. The viewer receives `CONTROLS_LOCKED` and a
fresh state. The host can always control playback. Ask the host to enable shared
controls.

### Playback jumps backwards after a command

The client applies only state with a sequence at least as new as its current state.
If you see this, capture the `sequence` values and the command id. Duplicate commands
are ignored by design; retrying with the same id is safe.

### Autoplay is blocked in the browser

Browsers require a user gesture before unmuted playback. Click the tap-to-join overlay.
This is expected browser policy, not a WatchWave failure.

## Tunnel

### URL stays `STARTING` or `RECONNECTING`

- Verify `https://<url>/api/v1/health` from another network.
- Confirm the tunnel forwards to the phone's current LAN IP and port.
- Confirm the phone is awake and the room is still live.
- Quick tunnel URLs expire; restart cloudflared and paste the new URL.
- Named tunnel: check DNS, the tunnel UUID, credentials file and ingress service.

### `NOT_INSTALLED` for on-device cloudflared

The configured path is not a file or is not executable. A stock Linux binary will not
run on Android; use an Android/Bionic-compatible build or the companion mode. Check:

```bash
adb shell run-as <application-id> ls -l files/cloudflared
```

### URL is verified but viewers cannot load the browser client

The tunnel must forward all paths, not only `/api`. The APK serves `/`, `/assets/*`,
`/sw.js`, `/manifest.webmanifest` and the icons. Check the tunnel ingress and any
reverse-proxy rules. Do not rewrite `/api/v1` paths.

### WebSocket fails through a proxy

The proxy must support HTTP/1.1 Upgrade and must not buffer the connection. Cloudflare
Tunnel supports WebSocket by default. If you put another reverse proxy in front, enable
WebSocket upgrade for `/api/v1/rooms/*/sync`.

## Browser/PWA

### Old UI after a deploy

The service worker caches the static shell. Close all tabs, clear site data, or bump the
cache version in `web/public/sw.js`. Hard-reload the page.

### Session appears in the wrong tab

Browser credentials are stored per room in `sessionStorage`, which is per tab. Opening a
second tab and joining again is expected to create a separate participant. Use one tab
per viewer.

### Page loads but API calls fail

- Open the share URL from the host, not a copied `/api` URL.
- For local development, check the Vite proxy target (`WATCHWAVE_DEV_HOST`) and that the
  phone's port is reachable/forwarded (`adb reverse tcp:8080 tcp:8080`).
- If the client is deployed on a different origin, set `VITE_WATCHWAVE_ORIGIN` and make
  sure the server's development CORS origin list includes that origin.

## Tests

### Android test task says `UP-TO-DATE`

Gradle skipped unchanged tests. Force a rerun:

```powershell
.\gradlew.bat :shared:test :streaming:testDebugUnitTest --rerun-tasks
```

### Streaming tests fail with a port or coroutine timeout

Close other test runs and ensure no Gradle daemon is stuck. The tests use Ktor's
`testApplication` and should not bind a real public port. A 5-second timeout failure
usually means a WebSocket frame was not sent; check the new `SYNC_PONG` handling and the
server state.

### Web tests fail with storage errors

`src/test/setup.ts` polyfills `localStorage`/`sessionStorage` for jsdom. If you removed
it, restore the setup file referenced by `vitest.config.ts`.
