# Cloudflare tunnel setup

WatchWave can expose the phone's embedded server through Cloudflare Tunnel. A public URL is
**never** shown as shareable until the app verifies:

```text
GET https://<public-url>/api/v1/health  ->  body contains "WatchWave"
```

An unverified URL stays out of the share sheet, even if the process prints one.

## Mode 1: Instant public (default, no setup)

The APK bundles Cloudflare's official `cloudflared-linux-arm64` binary and the Mozilla CA
bundle. On arm64 phones the app:

1. Extracts the binary into Android's executable `nativeLibraryDir` at install time.
2. Extracts `assets/tunnel/cacert.pem` into app storage.
3. Runs:
   ```text
   cloudflared tunnel --no-autoupdate --url http://127.0.0.1:<port>
   ```
   with `SSL_CERT_FILE` pointing at the CA bundle, plus a writable `HOME`/`TMPDIR`.
4. Parses the `https://*.trycloudflare.com` URL from the output.
5. Verifies the health marker through the public HTTPS edge.
6. Shows **PUBLIC LINK · TUNNEL VERIFIED** only after step 5 succeeds.

You do not need a PC, Termux, cloudflared install, Cloudflare account or port forwarding.
The tunnel starts when the party starts and stops when the room ends.

Quick Tunnels are free and best-effort:

- No uptime guarantee.
- Usually a new random hostname on each start.
- Subject to Cloudflare's terms and rate limits.
- Do not use them for sensitive or long-lived rooms without a named tunnel.

**Requirements:** `arm64-v8a` (most modern phones). Other ABIs fall back to LAN, companion
URL or a custom executable. The phone must stay alive; the foreground media service keeps
the host process running.

### Verified on-device result

On a Samsung SM-A217F (Android 12, arm64-v8a), the same binary with the same CA bundle ran
from `/data/local/tmp` and produced a temporary URL like:

```text
https://<random-words>.trycloudflare.com
```

Through that tunnel:

- `/api/v1/health` → `200`
- browser index → `200`
- room metadata → `200`

The hostname was temporary and is not a permanent service. The packaged APK stores the
binary at `lib/arm64-v8a/libcloudflared.so`; executing it from the app's native-library
directory under the app UID was also verified. The final tap-to-start live-room pass is
tracked in `docs/IMPLEMENTATION_CHECKLIST.md`.

## Mode 2: Companion URL (fallback / stable hostname)

Use this when the host phone is not arm64, or when you already run a tunnel on a computer,
router, NAS or Termux session that can reach the phone over the LAN.

### Step 1: start the host

In WatchWave, choose a video and start hosting. Note the LAN URL and port, for example:

```text
http://192.168.1.20:8080/room/k3f9xz2m7q1a?t=...
```

Verify the phone is reachable from the companion machine:

```bash
curl http://192.168.1.20:8080/api/v1/health
```

You must see the WatchWave health JSON. If this fails, fix the LAN/firewall first; a tunnel
cannot fix an unreachable origin.

### Step 2: start a quick tunnel

On the companion machine:

```bash
cloudflared tunnel --url http://192.168.1.20:8080
```

`cloudflared` prints a URL like:

```text
https://random-words-1234.trycloudflare.com
```

Quick tunnel URLs are temporary and change every time `cloudflared` restarts. Use a named
tunnel if you need a stable hostname.

> **Do not use `http://localhost/` unless WatchWave is running on the same PC.** On a normal
> setup the phone runs WatchWave and the PC only runs `cloudflared`, so `localhost` points
> at whatever web server the PC has (for example XAMPP), not at the phone. A 404 from the
> health check usually means the tunnel reached the wrong origin.

### Step 3: configure the app

1. Open **Settings → Tunnel**.
2. Select **Companion**.
3. Paste the full `https://...` URL (no path).
4. Open **Tunnel status** and wait for **CONNECTED**, verified = true.

The app polls the health endpoint every 15 seconds. After five consecutive failures it
reports `FAILED` and stops advertising the URL.

### Verified companion result

A Windows `cloudflared` quick tunnel was run against the **real Android host** over
`adb forward`. Through HTTPS/WSS the browser viewer joined, HTML5 video reached
`readyState=4`, an actual frame rendered, play/pause worked, and the UI showed
**WSS · host relay** and **IN SYNC**. Health, index and room metadata all returned `200`.
Range streaming also returned `206`.

## Mode 3: custom cloudflared binary (advanced)

The app also accepts a user-supplied Android-compatible executable. This is useful for a
different ABI or a custom build you trust.

```bash
adb push cloudflared-android /sdcard/Download/
adb shell run-as com.watchwave.app cp /sdcard/Download/cloudflared-android files/cloudflared
adb shell run-as com.watchwave.app chmod 700 files/cloudflared
```

The path depends on your application id. If you built with
`-Pwatchwave.applicationId=com.example.watchwave`, use that package name.

In **Settings → Tunnel → Custom cloudflared**, set:

```text
Executable path: /data/data/<your.application.id>/files/cloudflared
```

Leave the token empty for a quick tunnel. The app runs approximately:

```text
cloudflared tunnel --no-autoupdate --url http://127.0.0.1:<port>
```

and verifies the public health endpoint before showing the link.

### Named tunnel with a stable hostname

1. In the Cloudflare dashboard, create a tunnel and copy its token.
2. Configure the tunnel's public hostname to point at `http://127.0.0.1:<port>` because
   cloudflared runs on the phone.
3. Paste the token into WatchWave. It is encrypted with an AES-GCM key in the Android
   Keystore and passed to the child process through the `TUNNEL_TOKEN` environment
   variable, not the command line.
4. Enter the public hostname, e.g. `wave.example.com`.
5. Save and wait for verification.

If the named hostname is omitted, the app waits for cloudflared to print a URL. A named
tunnel normally does not print a quick-tunnel URL, so configure the hostname explicitly.

For a companion named tunnel, create `config.yml` on the companion machine:

```yaml
tunnel: <TUNNEL-UUID>
credentials-file: /home/you/.cloudflared/<TUNNEL-UUID>.json
ingress:
  - hostname: wave.example.com
    service: http://192.168.1.20:8080
  - service: http_status:404
```

Then enter `https://wave.example.com` as the companion URL.

## Verification and failure states

| State | Meaning |
| --- | --- |
| `NOT_CONFIGURED` | No mode/URL selected |
| `NOT_INSTALLED` | Executable path missing or not executable |
| `STARTING` | Process launched or URL being verified |
| `CONNECTED` | HTTPS URL returned WatchWave's health marker |
| `RECONNECTING` | Temporary failure; retrying |
| `FAILED` | Retries exhausted or URL consistently unreachable |
| `STOPPED` | Tunnel stopped with the room |

Only `CONNECTED` + `verified` produces a share link.

Common causes of an unverified URL:

- The URL points to the wrong port or to a machine that cannot reach the phone.
- The phone's LAN IP changed after the companion tunnel was configured.
- The phone is asleep, the host was force-stopped, or the room was closed.
- Cloudflare DNS/edge is still propagating.
- A firewall blocks the phone's port from the companion machine.
- Quick tunnel rate limits or an expired temporary URL.
- The bundled tunnel was used on a non-arm64 device (fall back to LAN/companion/custom).

Check locally first:

```bash
curl -i http://<phone-ip>:<port>/api/v1/health
```

Then check the public URL:

```bash
curl -i https://<public-url>/api/v1/health
```

The app never uses a tunnel URL until that second check contains the WatchWave marker.
