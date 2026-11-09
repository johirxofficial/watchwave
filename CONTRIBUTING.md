# Contributing to WatchWave

Thanks for helping make WatchWave better. This project is a real, self-hosted watch-party
app: the Android host streams the original file from the phone, and Android/browser viewers
stay in sync through a room WebSocket.

## Before you start

- Read [`README.md`](README.md) and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- For security issues, **do not open a public issue**. Follow [`SECURITY.md`](SECURITY.md).
- Keep the product promise: no cloud upload, no fake URLs, no hidden file access, and no
  claim that a tunnel works until the health marker has been verified.

## Local setup

Prerequisites:

- JDK 17
- Android SDK platform 34 and build-tools 34.0.0
- Node.js 20+
- A physical Android device is strongly recommended for playback and tunnel work.

```powershell
cd web
npm ci
npm run build

cd ..\android
.\gradlew.bat :shared:test :streaming:testDebugUnitTest :app:assembleDebug
```

The debug APK lands at `android/app/build/outputs/apk/debug/app-debug.apk`.

> The bundled on-device tunnel is currently an arm64-v8a `cloudflared` binary in
> `android/app/src/main/jniLibs/arm64-v8a/`. It is extracted by Android and executed from
> `applicationInfo.nativeLibraryDir`; never point the app at a writable arbitrary path for
> the built-in mode.

## Tests and checks

Run all of these before opening a pull request:

```powershell
# Browser
cd web
npm run typecheck
npm test
npm run build

# Android
cd ..\android
.\gradlew.bat :shared:test :streaming:testDebugUnitTest :app:assembleDebug
```

If you touch the WebSocket protocol, update both sides and add tests:

- `android/shared/.../Protocol.kt`
- `android/streaming/.../WebSocketHub.kt`
- `android/app/.../RoomClient.kt`
- `web/src/lib/protocol.ts`
- `web/src/room/state.ts`
- `web/src/services/sync-client.ts`

CI (`.github/workflows/ci.yml`) is authoritative. Local Gradle builds can run out of memory
on small Windows hosts; that is an environment limitation, not a reason to skip tests.

## Code style

- Kotlin: 4-space indentation, explicit imports, prefer immutable data and StateFlow.
- TypeScript/React: existing Vite + Tailwind conventions; keep components small and typed.
- Do not add secrets, tokens, personal URLs, captured invite links, or device logs.
- Keep user-facing copy honest. A tunnel link must say "verified" only when the health check
  has actually passed.
- Add comments only where the reason is not obvious, especially around security and Android
  process/lifecycle constraints.

## Pull requests

A good pull request:

1. Explains the user-visible problem and the smallest change that fixes it.
2. Includes tests or a clear manual verification section (device, Android version, network).
3. Does not mix unrelated refactors with behavior changes.
4. Keeps the existing API/WS protocol backward compatible unless the protocol version is
   explicitly bumped.

Use the pull request template. Screenshots are welcome, but redact room ids, invite tokens,
public tunnel hostnames and any personal information.

## Commit messages

Use a short imperative subject, for example:

```text
Add room chat with server-authoritative sender identity
Fix ExoPlayer access on the wrong thread
```

## License

By contributing, you agree that your contributions are licensed under the MIT License in
[`LICENSE`](LICENSE).
