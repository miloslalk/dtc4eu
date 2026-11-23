#!/usr/bin/env bash
# Refresh staging from production, anonymised (FIX-LIST X3). Run on the server
# from the repo root:   bash deploy/staging-refresh.sh
#
# 1. dumps the production platform DB (read-only for production),
# 2. replaces the staging platform DB with it,
# 3. scrubs personal data (staging-anonymise.sql) — every account then logs in
#    with STAGING_USER_PASSWORD,
# 4. restarts the staging API (Flyway applies any newer migrations).
# Uploaded files are NOT copied: staging storage stays empty.
set -euo pipefail
cd "$(dirname "$0")/.."

PROD=(docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env)
STAGING=(docker compose -f deploy/docker-compose.staging.yml --env-file deploy/staging.env)
# shellcheck disable=SC1091
STAGING_USER_PASSWORD=$(set -a; . deploy/staging.env; printf '%s' "$STAGING_USER_PASSWORD")
if [ -z "$STAGING_USER_PASSWORD" ] || [ "$STAGING_USER_PASSWORD" = "change-me" ]; then
  echo "Set STAGING_USER_PASSWORD in deploy/staging.env first" >&2
  exit 1
fi

DUMP=$(mktemp)
trap 'rm -f "$DUMP"' EXIT

echo "1/4 dumping production (read-only)…"
"${PROD[@]}" exec -T db pg_dump -U postgres -Fc dtc4eu > "$DUMP"

echo "2/4 replacing the staging database…"
"${STAGING[@]}" stop platform-api
"${STAGING[@]}" exec -T db psql -v ON_ERROR_STOP=1 -U postgres -c "DROP DATABASE IF EXISTS dtc4eu WITH (FORCE)" \
  -c "CREATE DATABASE dtc4eu OWNER dtc4eu"
"${STAGING[@]}" exec -T db pg_restore -U postgres -d dtc4eu --no-owner --role=dtc4eu < "$DUMP"

echo "3/4 anonymising…"
"${STAGING[@]}" exec -T -e PGOPTIONS='-c dtc4eu.staging=yes' db \
  psql -v ON_ERROR_STOP=1 -U postgres -d dtc4eu -v staging_password="$STAGING_USER_PASSWORD" \
  -f - < deploy/staging-anonymise.sql

echo "4/4 starting the staging API…"
"${STAGING[@]}" up -d platform-api
echo "Staging refreshed. Accounts: admin, user<id> — password from STAGING_USER_PASSWORD."
