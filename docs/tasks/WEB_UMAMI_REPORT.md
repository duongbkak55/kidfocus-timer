# Umami web statistics — implementation report

Status: ready for Claude review. Firebase Hosting has **not** been deployed.

## OCI

- Umami 3.4.0 and Postgres 15 are running as healthy Docker containers on `oci`.
- HTTPS dashboard and tracker: `https://stats.kidfocus.134-185-83-168.sslip.io/`; Caddy reverse proxy leaves the existing KidFocus site in place.
- `kidfocus.web.app` was created as the tracked website. Session replay/recording is disabled. Default admin password was replaced with a random one and stored only in a mode-600 file on OCI. Database and app secrets are likewise only in OCI `.env` (mode 600). Database port is not public.
- `stats.kidfocus.web.app` currently resolves to Firebase Hosting, so it cannot route to OCI without a different DNS arrangement. This deployment uses a separate HTTPS subdomain of the existing OCI `sslip.io` name. See `ops/umami/README.md` for operations and credential location (no credential values).

## Web code

- `/tu-duy/`, `/app/`, `/cai-app/`, `/beta/` load the self-hosted Umami tracker only on production KidFocus hosts. Local tests and preview hosts send no analytics.
- Events: `tuduy_start` when a round opens; `tuduy_done` on completion; `band_45` on selecting 4–5 years; `install_click` on install CTAs; `guest_start` after a guest session starts successfully. No event properties containing user or child data are sent.
- Tracker respects Do Not Track, strips URL search/hash and referrer, and sends no referrer on script fetch. Android code is untouched.
- Vietnamese and English privacy notices now describe self-hosted, cookie-free website statistics and their scope.

## Validation

- `npm --prefix web test`: 22 passed, including browser flow checks for `tuduy_start`, `tuduy_done`, `band_45`, and `guest_start`. Vite reports its existing large learning bundle warning and notes that the standalone `/analytics.js` is not bundled; the build copies it to the distribution root.
- `node --test shared/learning/tests/*.test.js`: 113 passed.
- `git diff --check`: passed.
- HTTPS checks: Umami dashboard, `/script.js`, and pre-existing KidFocus OCI site each returned HTTP 200 with valid TLS.

## Release boundary

No Firebase Hosting deploy or git push was performed. Tracking on `kidfocus.web.app` remains unchanged until the reviewed web build and updated privacy page are published together.
