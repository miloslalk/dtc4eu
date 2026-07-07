# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

DTC4EU (Erasmus KA220 — Democratic Therapeutic Communities for Social Inclusion): a platform for running annual quality-evaluation cycles across therapeutic-community facilities. Stack: Java 17 / Spring Boot 3.3.x / Maven backend, PostgreSQL 16 + Flyway, JWT (jjwt) security, MinIO/S3 storage; React 18 + TypeScript + Vite frontend.

**`DTC4EU_BUILD_SPEC.md` is the source of truth.** It defines the stack, conventions, endpoints, business rules, and the ordered Phase 1 build plan (T0–T6) with acceptance criteria. Read it before building anything. Its companion `V1__baseline_schema.sql` is the authoritative database schema (44 tables, PostgreSQL 16), destined for `backend/src/main/resources/db/migration/`.

## Current state

Greenfield. Only the spec, the baseline schema, and the project logo exist — no `backend/` or `frontend/` has been scaffolded yet. Work starts at task T0 (scaffold) and proceeds strictly in spec order T0 → T6. Expected early migrations beyond V1: `V2__seed_roles.sql` (ten roles + one super-admin), `V3__seed_cycle.sql` (cycle 2026-2027 as current).

## Scope guard — Phase 1 only

The schema defines all modules, but Phase 1 implements only: foundation (users/roles/cycles/scope), Module 1 (Facilities), Module 2 (Ternas), and Module 4 (DTCRO/MHSRO questionnaires + scoring). Do **not** build endpoints or UI for Modules 3 (Audits), 5 (ViVaCom), 5-bis (rule engine), 6–8 (standard tracking), or the general platform (website, Academy, forum, repository, certificates). Their tables exist; ignore them in code.

## Non-negotiable design rules

- **Flyway owns the schema.** Hibernate `ddl-auto` never beyond `validate`. Never edit `V1__baseline_schema.sql`; every schema change is a new `V__` file.
- **`updated_at` is maintained by DB triggers** — never set it in application code.
- **The facility is the anchor entity; every data record is scoped to an annual cycle.** The selected cycle is passed as `X-Cycle-Id` header (or query param) and applied as an implicit filter on all cycle-scoped queries. Closed/archived cycles are read-only.
- **Never overwrite raw data.** Raw item-level responses are immutable after submit; aggregates (`response_area_scores`, `facility_scale_scores`) are derived into separate tables. The scoring engine (area mean on submit) must be idempotent and re-runnable.
- **Pseudonymous codes only** — subjects and facilities are identified by codes, never personal names.
- **Role + data scope enforced on every data endpoint**, implemented as reusable JPA Specifications: `INTERNAL_FACILITATOR` → facilities in `user_facility_access`; `COORDINATOR` → ternas in `user_terna_access` + their member facilities; `RESPONDENT` → own responses; `RESEARCH_TEAM`/`ADMIN`/`SUPER_ADMIN` → all data in the cycle. Out-of-scope records return **404, not 403**.
- **Enums are fixed by CHECK constraints in the schema** — mirror them exactly as Java enums (lists in spec §5).
- Standard abbreviations are **PGP / DGC / NEE** (the Excel is authoritative), not the GPP/CGS/EEN found in some older documents.
- Log create/update/delete, login, submit, and export to `audit_logs`.

## Conventions (details in spec §4)

- REST base `/api/v1`, JSON only, Bearer JWT, BCrypt passwords.
- snake_case in DB ↔ camelCase in Java/JSON; entities map to exact schema table names; IDs are `GENERATED ALWAYS AS IDENTITY` — never set manually.
- List endpoints return `{ content, page, size, totalElements }`; filters combine with AND via Spring Data Specifications.
- One global `@RestControllerAdvice` producing the error shape in spec §4.
- All config from env vars / Spring profiles (`application-local.yml`, `application-prod.yml`); required vars in spec §2. AWS vs Azure stays interchangeable — don't couple code to either.
- Backend packages per domain (`cycle/`, `facility/`, `terna/`, `questionnaire/`, `user/`, `file/`, `audit/`), each containing `entity/ dto/ repository/ service/ controller/ mapper/`.

## Development environment & commands

Local dependencies run via `docker-compose.yml` (Postgres 16 on 5432, MinIO on 9000/9001 — definition in spec §2): `docker compose up -d`. Once T0 is scaffolded:

- Backend (from `backend/`): `mvn spring-boot:run`; tests with `mvn test` (JUnit 5 + Mockito; Testcontainers with Postgres for repositories and the scoring engine — Docker must be running); single test: `mvn test -Dtest=ClassName#method`. API docs at Swagger UI (springdoc-openapi).
- Frontend (from `frontend/`): `npm run dev` (Vite); React Query, React Router, React Hook Form + Zod, i18next.

## Open questions — do not invent answers

Spec §9 lists unresolved inputs; the DTCRO/MHSRO item content (45 items / 9 areas) is critical-path for T4/T5. Where blocked, stub clearly-labeled placeholder data and keep building the container — never fabricate clinical content as if real.
