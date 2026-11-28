# DTC4EU — Production deployment runbook (Aruba Cloud)

One server runs everything: the evaluation **platform** (`https://platform.dtc4.eu`),
the public **website + CMS** (`https://dtc4.eu`), PostgreSQL, MinIO object storage,
a Caddy reverse proxy with automatic HTTPS, and nightly backups. The production
domain is **`dtc4.eu`** (pre-filled in `.env.example`; EURid-checked available
Aug 2026).

Running cost: **~€13.50/month + VAT** (server + Aruba agentless backup) plus
**~€10–15/year** for the domain. The client-facing checklist lives beside the
repo checkout as `Aruba.md` (the earlier `Hetzner.md` is superseded).

---

## 1. Aruba Cloud account & server (~10 min)

1. Sign up at https://www.arubacloud.com — register as a **company** so
   invoices are issued in the organization's name (Italian organizations add
   their SDI code / PEC for electronic invoicing — needed for the Erasmus+
   cost claim). Add a card/PayPal and enable automatic renewal.
2. Console → **VPS** → Create:
   - Data centre: **IT1 (Arezzo)** — or Bergamo (both Italy, EU data residency)
   - Image: **Ubuntu 24.04**
   - Size: **VPS O4A8** (4 vCPU, 8 GB, 80 GB NVMe, 50 TB traffic/month,
     public IPv4 included, €11.79/mo + VAT)
   - Enable the **Agentless Backup** add-on (~€0.022/GB/month ≈ €1.75/mo
     for the 80 GB disk — whole-server restore)
   - SSH key: add your public key (`cat ~/.ssh/id_ed25519.pub`)
3. **Firewall** — runs on the server itself (`ufw`, configured in §3 below):
   inbound TCP 22, 80, 443, nothing else. PostgreSQL and MinIO are never
   exposed — they live on the internal Docker network only.

## 2. Domain & DNS (~10 min, can run in parallel)

1. Register **dtc4.eu** at any registrar (e.g. the client's preferred one;
   Aruba also sells .eu domains via aruba.it if one invoice for everything
   helps). Holder must be an EU/EEA organization or resident.
2. Create records pointing at the server's IPv4 (and IPv6 if you like):

   | Type | Name       | Value        |
   |------|------------|--------------|
   | A    | `@`        | `<server-ip>` |
   | A    | `www`      | `<server-ip>` |
   | A    | `platform` | `<server-ip>` |

3. Wait for DNS to resolve (`dig +short dtc4.eu`), usually minutes.

## 3. Server preparation (~10 min)

```bash
ssh root@<server-ip>

# Firewall — only SSH/HTTP/HTTPS reachable from outside (Aruba has no
# console-side firewall for the VPS line; ufw on the host covers it)
ufw default deny incoming && ufw default allow outgoing
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp
ufw --force enable

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
Flyway migrates each schema (platform V1–V16, website V1–V4 with placeholder
content), MinIO buckets are created on first use, Caddy obtains Let's
Encrypt certificates for all three hostnames.

### First-boot checklist (do immediately)

The repository is public, so the seeded passwords (`admin` / `Dtc4eu!Admin2026`,
`webadmin` / `Dtc4eu!Web2026`) are known to anyone. At start-up each API
replaces a seeded password that still works with `PLATFORM_SEED_ADMIN_PASSWORD`
/ `WEBSITE_SEED_ADMIN_PASSWORD` from `deploy/.env`, and **refuses to start** while
a published password still works and its variable is empty. The published
passwords never work in production.

1. `https://platform.dtc4.eu` → log in `admin` / your `PLATFORM_SEED_ADMIN_PASSWORD`
   → the platform shows **only a change-password screen** until you choose a
   new one. Then create **one named admin account per person**
   (Users → New user, "Temporary password" ticked, so each person picks their
   own) and stop sharing `admin`.
2. `https://dtc4.eu/admin` → log in `webadmin` / your `WEBSITE_SEED_ADMIN_PASSWORD`
   → **change the password** (Users screen).
3. CMS → Settings → **Platform link** → `https://platform.dtc4.eu`.
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

- **Aruba agentless backup** — automatic, whole-machine. Restore via the
  Aruba Cloud console (server → Backup → Restore); brings back *everything*
  as of the backup point.
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

If load ever grows: move the VPS to the next plan size (O8A16 — 8 vCPU,
16 GB, 160 GB) via the Aruba console; plan the short downtime. No
architectural change needed until much later.

## 8. Local smoke test (no server needed)

The whole prod stack can be rehearsed on a laptop:

```bash
cp deploy/.env.example deploy/.env
# set SITE_DOMAIN=localhost, PLATFORM_DOMAIN=platform.localhost, fill secrets
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
# https://localhost (accept the internal-CA certificate warning)
```

## 9. Staging (QA, golden-fixture test, training)

A second, isolated copy of the platform on the same server: same images,
**its own database, storage and secrets**, and an **anonymised** copy of the
production data. Use it for every test and training session — production holds
real names and e-mail addresses.

One-time setup:

```bash
# DNS: add an A record  staging.platform  →  <server-ip>
cd /opt/dtc4eu
echo 'STAGING_DOMAIN=staging.platform.dtc4.eu' >> deploy/.env
cp deploy/staging.env.example deploy/staging.env
nano deploy/staging.env    # NEW secrets — never reuse production's
# production first (it creates the shared dtc4eu-edge network), then staging
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d
docker compose -f deploy/docker-compose.staging.yml --env-file deploy/staging.env up -d --build
bash deploy/staging-refresh.sh
```

`staging-refresh.sh` dumps production (read-only), replaces the staging
database, and runs `staging-anonymise.sql`: users become `admin` / `user<id>`
with neutral names and `@staging.invalid` addresses, all sharing
`STAGING_USER_PASSWORD`; free-text answers and QCNC notes are redacted,
GAS-Set Section N is removed, facility street addresses are cleared and the
audit log is emptied. Facilities, ternas, codes, Likert answers and scores are
kept. Uploaded files are not copied. Re-run the script whenever staging should
catch up with production. The anonymiser refuses to run on a session not
marked as staging, so it cannot scrub production by mistake.

Deploying a change to staging before production: check out the branch in a
second directory (e.g. `/opt/dtc4eu-staging`), copy `deploy/staging.env`
there, and run the staging `up -d --build` from it. Production keeps running
the code in `/opt/dtc4eu`.
