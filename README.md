# DTC4EU Platform

Digital platform for the Erasmus+ KA220 project **DTC4EU — Democratic Therapeutic Communities for Social Inclusion**: annual quality-evaluation cycles across therapeutic-community facilities.

The implementation brief is [`DTC4EU_BUILD_SPEC.md`](DTC4EU_BUILD_SPEC.md) (source of truth). Phase 1 covers the foundation plus Module 1 (Facilities), Module 2 (Ternas) and Module 4 (DTCRO/MHSRO questionnaires + scoring).

## Stack

- **Backend:** Java 17 · Spring Boot · Maven · PostgreSQL 16 · Flyway · Spring Security + JWT · springdoc-openapi
- **Frontend:** React 18 · TypeScript · Vite · Tailwind v4 (brand tokens) · React Query · React Router · React Hook Form + Zod · i18next
- **Dev dependencies:** Docker (Postgres 16 + MinIO via `docker-compose.yml`)

## Quickstart

Prerequisites: JDK 17+, Node 20+, Docker Desktop (running).

```bash
# 1. Start dev dependencies (Postgres 16 on :5432, MinIO on :9000/:9001)
docker compose up -d

# 2. Backend — boots on :8080, applies Flyway migrations (local profile is the default)
cd backend && ./mvnw spring-boot:run

# 3. Frontend — dev server on :5173, proxies /api to :8080
cd frontend && npm install && npm run dev
```

- App: http://localhost:5173
- Swagger UI: http://localhost:8080/swagger-ui.html
- Dev super-admin: `admin` / `Dtc4eu!Admin2026` — **development seed only; rotate before any real deployment.**

## Tests

```bash
cd backend && ./mvnw verify   # unit + Testcontainers integration tests (needs Docker running)
cd frontend && npm run build  # type-check + production build
```

## Configuration

All configuration is environment-driven (see spec §2): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`, `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`. The `local` Spring profile carries docker-compose-matching defaults; the `prod` profile has no defaults and requires all variables.

The database schema is owned by Flyway (`backend/src/main/resources/db/migration/`); never edit `V1__baseline_schema.sql` — every change is a new `V__` file.
