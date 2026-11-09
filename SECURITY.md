# Security Policy

WatchWave is self-hosted software that can expose a video and a WebSocket to the public
internet through a tunnel. Treat every room link as a secret.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for a security problem.

Use GitHub's private vulnerability reporting:

1. Open the repository on GitHub.
2. Go to **Security → Advisories → Report a vulnerability**.
3. Include the affected version/commit, Android version, steps to reproduce, and impact.

If private reporting is unavailable, open a minimal public issue asking for a private
contact channel; do not include exploit details.

We will acknowledge reports as soon as possible and coordinate a fix and disclosure.

## Supported versions

The `main` branch and the latest published release receive security fixes. Debug APKs from
different CI runs use ephemeral debug keys; they are for testing, not durable distribution.

## What is in scope

- Authentication or authorization bypass (room, host, viewer, media token).
- Access to files outside the user-selected video document.
- Leakage of invite tokens, session tokens, media tokens or tunnel credentials.
- Path traversal, range-parser abuse, request smuggling, or denial of service that is not
  bounded by the documented limits.
- WebSocket identity spoofing, chat spoofing, or cross-room message injection.
- Tunnel URL being shown as verified without a successful health check.
- Android Keystore misuse or a bypass of the control policy.

## What is not a vulnerability by itself

- A LAN link uses plain HTTP. This is documented; use an HTTPS tunnel on untrusted networks.
- A Quick Tunnel hostname is public and best-effort. Anyone with the full room link can join
  while the room is active and joins are enabled.
- The host must keep the app alive; screen-off/background behavior depends on the device's
  battery policy.
- The debug APK is signed with a debug certificate and is not a production distribution
  channel.

See [`docs/SECURITY.md`](docs/SECURITY.md) for the threat model and design limits.
