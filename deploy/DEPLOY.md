# DTC4EU — Production deployment runbook (Hetzner Cloud)

One server runs everything: the evaluation **platform** (`https://platform.<domain>`),
the public **website + CMS** (`https://<domain>`), PostgreSQL, MinIO object storage,
a Caddy reverse proxy with automatic HTTPS, and nightly backups.

Running cost: **~€8–9/month** (server + Hetzner backups) plus **~€10–15/year** for the domain.

---

## 1. Hetzner account & server (~10 min)

1. Sign up at https://console.hetzner.cloud — choose **Company** as the account
   type so monthly PDF invoices are issued in the organization's name
   (any nationality; non-EU companies pay no German VAT). Add a card/PayPal.
2. Create a project → **Add Server**:
   - Location: **Falkenstein** or **Nuremberg** (Germany, EU data residency)
   - Image: **Ubuntu 24.04**
   - Type: **CX32** (4 vCPU, 8 GB) — or **CAX21** (ARM, 8 GB, slightly cheaper;
     everything in this stack is multi-arch)
   - **Enable Backups** (adds 20%, 7 rotating daily snapshots)
   - SSH key: add your public key (`cat ~/.ssh/id_ed25519.pub`)
3. **Firewall** (Cloud → Firewalls → Create, then apply to the server):
   - Inbound TCP 22 (ideally restricted to your IP), 80, 443. Nothing else.
   - PostgreSQL and MinIO are never exposed — they live on the internal
     Docker network only.

## 2. Domain & DNS (~10 min, can run in parallel)

1. Register the domain at any registrar (e.g. the client's preferred one,
   or Hetzner DNS works fine as the zone host).
2. Create records pointing at the server's IPv4 (and IPv6 if you like):

   | Type | Name       | Value        |
   |------|------------|--------------|
   | A    | `@`        | `<server-ip>` |
   | A    | `www`      | `<server-ip>` |
   | A    | `platform` | `<server-ip>` |

3. Wait for DNS to resolve (`dig +short <domain>`), usually minutes.

## 3. Server preparation (~10 min)

```bash
ssh root@<server-ip>

# Docker (official convenience script)
curl -fsSL https://get.docker.com | sh

# Get the code onto the server (either git clone from your remote, or from
# your machine:  rsync -a --exclude node_modules --exclude target \
#                  dtc4eu/ root@<server-ip>:/opt/dtc4eu/ )
mkdir -p /opt/dtc4eu && cd /opt/dtc4eu

# Configuration
cp deploy/.env.example deploy/.env
nano deploy/.env        # fill EVERY value; openssl rand -base64 48 for secrets
```

## 4. First launch

```bash
cd /opt/dtc4eu
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env logs -f
```

What happens automatically: Postgres initializes with both databases,
Flyway migrates each schema (platform V1–V10, website V1–V4 with placeholder
content), MinIO buckets are created on first use, Caddy obtains Let's
Encrypt certificates for all three hostnames.

### First-boot checklist (do immediately)

1. `https://platform.<domain>` → log in `admin` / `Dtc4eu!Admin2026`
   → **change the password** (Users screen).
2. `https://<domain>/admin` → log in `webadmin` / `Dtc4eu!Web2026`
   → **change the password** (Users screen).
3. CMS → Settings → **Platform link** → `https://platform.<domain>`.
4. Replace placeholder content/photos through the CMS at your leisure.

## 5. Updating the application

```bash
cd /opt/dtc4eu
git pull                      # or rsync the new code
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

Flyway applies any new migrations on boot. Old images can be cleaned with
`docker image prune -f`.

## 6. Backups & restore

Two independent layers:

- **Hetzner snapshots** — automatic, whole-machine, 7 days. Restore via the
  console (Server → Backups → Restore); brings back *everything* as of the
  snapshot.
- **Nightly dumps** — the `backup` container writes
  `dtc4eu.dump`, `dtc4eu_website.dump` and `minio-data.tar.gz` into the
  `backups` volume daily (14-day rotation). Inspect with:

  ```bash
  docker compose -f deploy/docker-compose.prod.yml exec backup ls -lh /backups
  ```

  **Restore a single database** (example: website DB):

  ```bash
  docker compose -f deploy/docker-compose.prod.yml exec -T db \
    pg_restore -U postgres -d dtc4eu_website --clean /backups/<STAMP>/dtc4eu_website.dump
  ```

  For extra safety, periodically copy `/backups` off the server
  (`rsync -a root@<server-ip>:/var/lib/docker/volumes/dtc4eu-prod_backups/_data/ ./offsite/`).

## 7. Operations quick reference

```bash
# status / logs
docker compose -f deploy/docker-compose.prod.yml ps
docker compose -f deploy/docker-compose.prod.yml logs -f platform-api

# restart one service
docker compose -f deploy/docker-compose.prod.yml restart website-api

# stop everything (data persists in volumes)
docker compose -f deploy/docker-compose.prod.yml down
```

If load ever grows: resize the server in the Hetzner console (scale up in
place, ~1 min downtime) — no architectural change needed until much later.

## 8. Local smoke test (no Hetzner needed)

The whole prod stack can be rehearsed on a laptop:

```bash
cp deploy/.env.example deploy/.env
# set SITE_DOMAIN=localhost, PLATFORM_DOMAIN=platform.localhost, fill secrets
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
# https://localhost (accept the internal-CA certificate warning)
```
