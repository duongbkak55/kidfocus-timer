# KidFocus web statistics (OCI)

- Dashboard/tracker: `https://stats.kidfocus.134-185-83-168.sslip.io/` via the existing OCI Caddy container.
- Tracked website: `kidfocus.web.app`, Umami website ID `f5ea3996-ba9a-4a7e-8b29-846364770b3a` (public tracker ID, not a credential).
- OCI deployment directory: `/home/ubuntu/kidfocus-umami`. `compose.yaml` is copied from this directory. The private `.env` and `.admin-password` live only on OCI, mode 600. Do not commit, copy into build artifacts, or print them in logs.
- Containers: `postgres:15-alpine` and `ghcr.io/umami-software/umami:3.4.0`; the database has no published port. Umami binds only `127.0.0.1:3000` and is exposed by Caddy over HTTPS through the shared `kidfocus-analytics` Docker network.
- Caddy config: `/home/ubuntu/kidfocus-web/Caddyfile`; a timestamped backup was made before appending the `stats...` site block. The original KidFocus site remains on the same Caddy container.

Check health without revealing credentials:

```sh
ssh oci 'cd /home/ubuntu/kidfocus-umami && docker compose ps'
curl -I https://stats.kidfocus.134-185-83-168.sslip.io/script.js
```

The default Umami admin password was rotated at bootstrap. To access the dashboard, retrieve `/home/ubuntu/kidfocus-umami/.admin-password` through a private channel and sign in as `admin`. The file is not part of this repository. Back up the `kidfocus-umami_umami-postgres` volume or run a `pg_dump` before upgrades. Keep the Umami image version pinned and test upgrades before replacing it.

`kidfocus.web.app` is the Firebase Hosting domain being measured. Its `stats.kidfocus.web.app` child DNS resolves to Firebase, not OCI, so the existing OCI `sslip.io` namespace provides the separate HTTPS analytics host. Moving to a custom domain later requires DNS and a Caddy/tracker URL update; the website ID can remain the same.
