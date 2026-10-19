# Umami web statistics — implementation report

Status: deployed to Firebase Hosting `demo` on 19 October 2026; ready for Claude review.

## OCI

- Umami 3.4.0 and Postgres 15 are running as healthy Docker containers on `oci`.
- HTTPS dashboard and tracker: `https://stats.kidfocus.134-185-83-168.sslip.io/`; Caddy reverse proxy leaves the existing KidFocus site in place.
- `kidfocus.web.app` was created as the tracked website. Session replay/recording is disabled. Default admin password was replaced with a random one and stored only in a mode-600 file on OCI. Database and app secrets are likewise only in OCI `.env` (mode 600). Database port is not public.
- `stats.kidfocus.web.app` currently resolves to Firebase Hosting, so it cannot route to OCI without a different DNS arrangement. This deployment uses a separate HTTPS subdomain of the existing OCI `sslip.io` name. See `ops/umami/README.md` for operations and credential location (no credential values).

## Web code

- `/tu-duy/`, `/app/`, `/cai-app/`, `/beta/` load the self-hosted Umami tracker only on production KidFocus hosts. Local tests and preview hosts send no analytics.
- Events: `tuduy_start` when a round opens; `tuduy_done` on completion; `band_45` on selecting 4–5 years or opening `/tu-duy/?band=4-5`; `install_click` on install CTAs; `guest_start` after a guest session starts successfully. No event properties containing user or child data are sent.
- Tracker respects Do Not Track, retains only `utm_source`, `utm_medium` and `utm_campaign` in tracked URLs, strips other parameters (including `profile`) and hash, and reduces referrer to origin. The tracker script fetch sends no referrer. Android code is untouched.
- Vietnamese and English privacy notices now describe self-hosted, cookie-free website statistics and their scope.

## Validation

- `npm --prefix web test`: 22 passed, including browser flow checks for `tuduy_start`, `tuduy_done`, `band_45`, and `guest_start`. Vite reports its existing large learning bundle warning and notes that the standalone `/analytics.js` is not bundled; the build copies it to the distribution root.
- `node --test shared/learning/tests/*.test.js`: 113 passed.
- `git diff --check`: passed.
- HTTPS checks: Umami dashboard, `/script.js`, and pre-existing KidFocus OCI site each returned HTTP 200 with valid TLS.

## Release verification

- Feature follow-up: `dd35b5d` changes referrer to its origin and uses `allowed.toString()` for the UTM allowlist. `npm --prefix web test` passed 22/22 after that change.
- Merged into `main` as `35f9a18` and pushed to GitHub through SSH. Built `web/dist` from that exact `main` worktree; `firebase deploy --only hosting:demo --project kid-focus-app --non-interactive` succeeded after refreshing the existing Firebase CLI sign-in.
- Production `https://kidfocus.web.app/analytics.js` contains `allowed.toString()` and `referrer.origin`; `/tu-duy/?band=4-5&utm_source=test` returned HTTP 200 and selected the 4–5 band.
- After one browser visit to that URL, Umami's dashboard API reported `utm_source=test` with **1 view** and `band_45` with **1 event** for `/tu-duy/` in the verification window.
- The updated privacy copy is served at `/tu-duy/privacy.html` by `hosting:demo`. The separate `kidfocus-studio.web.app` legal site was not part of this deploy target and needs its own publish to show the same revised notice.
