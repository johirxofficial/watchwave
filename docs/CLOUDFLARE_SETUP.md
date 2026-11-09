# Cloudflare tunnel setup

WatchWave can expose the phone's embedded server through Cloudflare Tunnel. Two modes
are supported. The companion mode is recommended because Cloudflare's stock Linux
`cloudflared` binary will not run on Android's Bionic libc.

A public URL is **never** shown as shareable until the app verifies:

```text
GET https://<public-url>/api/v1/health  ->  body contains "WatchWave"
```

An unverified URL stays out of the share sheet, even if the process reports it.

## Mode 1: companion URL (recommended)

Run `cloudflared` on a computer, router, NAS or Termux session that can reach the phone
over the LAN. The tunnel forwards to the phone's LAN address and port.

### Step 1: start the host

In WatchWave, choose a video and start hosting. Note the LAN URL and port, for example:

```text
http://192.168.1.20:8080/room/k3f9xz2m7q1a?t=...
```

Verify the phone is reachable from the companion machine:

```bash
curl http://192.168.1.20:8080/api/v1/health
```

You must see the WatchWave health JSON. If this fails, fix the LAN/firewall first; a
tunnel cannot fix an unreachable origin.

### Step 2: start a quick tunnel

On the companion machine:

```bash
cloudflared tunnel --url http://192.168.1.20:8080
```

`cloudflared` prints a URL like:

```text
https://random-words-1234.trycloudflare.com
```

Quick tunnel URLs are temporary and change every time `cloudflared` restarts. Use a
named tunnel if you need a stable hostname.

### Step 3: configure the app

1. Open **Settings → Tunnel**.
2. Select **Companion URL**.
3. Paste the full `https://...` URL (no path).
4. Save and open **Tunnel status**.
5. Wait for state **CONNECTED**, verified = true.

The app polls the health endpoint every 15 seconds. After five consecutive failures it
reports `FAILED` and stops advertising the URL.

### Step 4: share

Return to the host screen. The tunnel link replaces the LAN link when it is verified.
Viewers on cellular data can now open it. The browser client is served by the phone, so
there is nothing to deploy separately.

### Named tunnel with a stable hostname

```bash
cloudflared tunnel login
cloudflared tunnel create watchwave
cloudflared tunnel route dns watchwave wave.example.com
```

Create `config.yml` on the companion machine:

```yaml
tunnel: <TUNNEL-UUID>
credentials-file: /home/you/.cloudflared/<TUNNEL-UUID>.json
ingress:
  - hostname: wave.example.com
    service: http://192.168.1.20:8080
  - service: http_status:404
```

Run:

```bash
cloudflared tunnel run watchwave
```

Then enter `https://wave.example.com` as the companion URL in WatchWave.

The service address must be reachable **from the machine running cloudflared**. If
cloudflared runs on the phone, the service belongs to the phone and should be
`http://127.0.0.1:8080`.

## Mode 2: on-device cloudflared process

WatchWave can launch a cloudflared binary directly. It does **not** bundle or download
one, because a normal Linux glibc build will not execute on Android.

Requirements:

- An Android/Bionic-compatible `cloudflared` build that you trust.
- The binary placed in app-writable storage and marked executable.
- A quick tunnel (no token) or a named tunnel token from Cloudflare.

### Push the binary

```bash
adb push cloudflared-android /sdcard/Download/
adb shell run-as com.watchwave.app cp /sdcard/Download/cloudflared-android files/cloudflared
adb shell run-as com.watchwave.app chmod 700 files/cloudflared
```

The path depends on your application id. If you built with
`-Pwatchwave.applicationId=com.example.watchwave`, use that package name.

In **Settings → Tunnel → On-device cloudflared**, set:

```text
Executable path: /data/data/<your.application.id>/files/cloudflared
```

### Quick tunnel

Leave the token empty and save. The app runs approximately:

```text
cloudflared tunnel --no-autoupdate --url http://127.0.0.1:<port>
```

It parses the `https://*.trycloudflare.com` URL from stdout/stderr, then verifies the
health endpoint. The URL changes on every restart.

### Named tunnel

1. In the Cloudflare dashboard, create a tunnel and copy its token.
2. Configure the tunnel's public hostname to point at `http://127.0.0.1:<port>` because
   cloudflared runs on the phone itself.
3. Paste the token into WatchWave. It is encrypted with an AES-GCM key in the Android
   Keystore and passed to the child process through the `TUNNEL_TOKEN` environment
   variable, not the command line.
4. Enter the public hostname, e.g. `wave.example.com`.
5. Save and wait for verification.

If the named hostname is omitted, the app waits for cloudflared to print a URL. A named
tunnel normally does not print a quick-tunnel URL, so configure the hostname explicitly.

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
- The phone's LAN IP changed after the tunnel was configured.
- The phone is asleep, the host was force-stopped, or the room was closed.
- Cloudflare DNS/edge is still propagating.
- A firewall blocks the phone's port from the companion machine.
- The URL is HTTP; remote sharing expects HTTPS. The verifier accepts `http://` only for
  local testing; use HTTPS for anything beyond a trusted LAN.
- Quick tunnel rate limits or an expired temporary URL.

Check locally first:

```bash
curl -i http://<phone-ip>:<port>/api/v1/health
curl -i https://<public-url>/api/v1/health
```

Both must return the WatchWave JSON. The second request is exactly what the app checks.

## Security notes

- Use HTTPS for remote viewers. The session, media and invite tokens travel in URLs and
  headers over the tunnel.
- Do not expose the embedded server directly on the public internet. Cloudflare Tunnel
  terminates TLS and gives you a controlled ingress.
- Quick tunnels are public URLs. Anyone who obtains the share link can attempt to join;
  the 256-bit invite token is still required, and you can rotate it.
- Treat the cloudflared token like a password. WatchWave encrypts it at rest and redacts
  it from process errors, but a compromised phone can still read it while the app runs.
- The app never returns the token, executable path or arbitrary logs through the API.
- Rotate the invite from the host screen if a link leaks; old links stop working
  immediately.

## Optional: Cloudflare Access

Cloudflare Access can put an identity-aware proxy in front of the hostname. It is not
configured by WatchWave and may interfere with browser API/WebSocket requests unless
your Access policy allows them. If you use it, test `/api/v1/health`, the WebSocket
upgrade and the video Range requests through the same session.
