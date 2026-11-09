## Problem

What user-visible problem does this change solve?

## Change

What changed, and why is this the smallest useful change?

## Verification

- [ ] `cd web && npm run typecheck && npm test && npm run build`
- [ ] `cd android && ./gradlew :shared:test :streaming:testDebugUnitTest :app:assembleDebug`
- [ ] Manual device/browser check (describe device, Android version, network, and steps)

## Security and privacy

- [ ] No tokens, invite links, public tunnel hostnames, personal IPs, or secrets are included.
- [ ] A tunnel link is only shown as verified after the health check passes.
- [ ] No arbitrary filesystem path is accepted from the network.
- [ ] Chat/identity fields remain server-authoritative.

## Screenshots (optional)

Redact room ids, invite tokens, public hostnames, and personal information.
