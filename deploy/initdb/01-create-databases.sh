#!/bin/bash
# Runs once on first boot of a fresh Postgres volume: creates the two
# application databases with their own users. Passwords come from the
# compose environment (deploy/.env).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-SQL
    CREATE USER dtc4eu WITH PASSWORD '${PLATFORM_DB_PASSWORD}';
    CREATE DATABASE dtc4eu OWNER dtc4eu;

    CREATE USER dtc4eu_website WITH PASSWORD '${WEBSITE_DB_PASSWORD}';
    CREATE DATABASE dtc4eu_website OWNER dtc4eu_website;
SQL
