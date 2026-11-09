# Third-party notices

WatchWave itself is licensed under the MIT License (`LICENSE`). It also redistributes or
depends on the following third-party components. This file is a summary, not legal advice.

## Bundled in the Android APK

### cloudflared

- Version: **2026.10.7** (built 2026-10-30)
- File: `android/app/src/main/jniLibs/arm64-v8a/libcloudflared.so`
- SHA-256: `3DF43AEDC9FF291D1735B5CB576CB97E0B157488796DB2E32B28AECABB445775`
- License: Apache License 2.0
- Source: https://github.com/cloudflare/cloudflared
- Upstream license: [`licenses/cloudflared-LICENSE.txt`](licenses/cloudflared-LICENSE.txt)

The binary is the official `cloudflared-linux-arm64` release, renamed so Android extracts it
into the app's executable native-library directory. It is used to create free Cloudflare
Quick Tunnels on the host device. Cloudflare's Quick Tunnel service is governed by
Cloudflare's own terms and has no uptime guarantee.

### Mozilla CA bundle

- File: `android/app/src/main/assets/tunnel/cacert.pem`
- SHA-256: `427C064216761A1E72A8B30F0D954D08CB6E4435A82091FB865AFD20C8497285`
- Source: https://curl.se/ca/cacert.pem (the Mozilla CA certificate bundle)
- License: Mozilla Public License 2.0
- License text: [`licenses/MPL-2.0.txt`](licenses/MPL-2.0.txt)

The bundle is passed to cloudflared through `SSL_CERT_FILE` because Android does not expose
the Linux `/etc/ssl/certs` layout that Go expects. It does not add any new certificate
authority; it is the standard Mozilla trust store.

## Runtime dependencies

Major dependencies include:

| Component | License |
| --- | --- |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| AndroidX, Jetpack Compose, Media3/ExoPlayer, Room, DataStore | Apache-2.0 |
| Ktor | Apache-2.0 |
| OkHttp / Okio | Apache-2.0 |
| React, React DOM, Vite, TypeScript, Tailwind CSS | MIT / Apache-2.0 (see each package) |
| Vitest and testing libraries | MIT |

The browser client is built with `npm`; the full dependency graph and licenses are recorded
in `web/package-lock.json` and the packages' own license files.

## Not bundled

No video, audio, subtitle, user account, analytics SDK, or advertising SDK is bundled. The
host streams only the document the user explicitly selects through Android's Storage Access
Framework.
