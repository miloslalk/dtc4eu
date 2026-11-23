# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

DTC4EU (Erasmus KA220 — Democratic Therapeutic Communities for Social Inclusion): a platform for running annual quality-evaluation cycles across therapeutic-community facilities. Stack: Java 17 / Spring Boot 3.3.x / Maven backend, PostgreSQL 16 + Flyway, JWT (jjwt) security, MinIO/S3 storage; React 18 + TypeScript + Vite frontend.

**Read `CONSTRAINTS.md` before writing code. Do not weaken it to make a change pass.** Run `npm run check:fast` after edits and `npm run check:task` (≈ 70 s, Docker must be running) before handing work back.

**`DTC4EU_BUILD_SPEC.md` is the source of truth.** It defines the stack, conventions, endpoints, business rules, and the ordered Phase 1 build plan (T0–T6) with acceptance criteria. Read it before building anything. Its companion `V1__baseline_schema.sql` is the authoritative database schema (44 tables, PostgreSQL 16), destined for `backend/src/main/resources/db/migration/`.

## Current state

Phase 1 is implemented on the `phase-1` branch, **amended by the spec v0.2 client revision** (`docs/DTC4EU_Client_Revision_Change_Register_v0.2.md` — the authoritative changelog): foundation (auth, users/roles/scope, overlapping cycles, `X-Cycle-Id` context), Module 1 (Facilities with the 3-settings × 8-subtypes taxonomy + participation/members/SAG/QCNC + quality-document applications + accreditation history + CSV export), Module 2 (Ternas with the 3-facility and coordinator-count rules + benchmark/population analytics), Module 4 (six-form DTCRO item bank, per-form profile headers, responses with autosave, submit → per-response area means **and** the facility-cycle scoring engine of register §3: Quant-SPWP/SAWA grids, TAB.5 classifications, strength/weakness points, EP, summary table), plus the branded React frontend for all of it.

Migrations: `V1` baseline (never edit) → `V2` roles + dev super-admin (`admin` / `Dtc4eu!Admin2026`, rotate in prod) → `V3` cycle 2026-2027 → `V4` DTCRO placeholder (superseded) → `V5` adds `questionnaire_responses.created_by` → `V6` six-form model + vocabulary renames (LONG_FORM/DELEGATION, USER/FAMILY/STAFF/MANAGER, anchors, form area labels) → `V7` **generated** DTCRO item-bank seed (216 verbatim items from `tools/dtcro_item_bank.csv` via `tools/generate_v7_item_bank.py`; destructively replaces the V4 placeholder) → `V8` facility scoring tables → `V9` taxonomy/overlapping cycles/new roles/GPP-CGS-EEN → `V10` QCNC carrier (three-administration model + empty standards catalogue) → `V11` expert-feedback participation checklist + QCNC catalogue → `V12` Phase 2 audits, GAS-Set, quality documents → `V13` QCNC per-standard 1–4 scores → `V14` assignment rules (Module 5-bis columns, validity dates, `user_profiles` category check; **no rules seeded**) → `V15` Simone's feedback (22 Nov 2026): cycle ordinals derived from start date (never typed in; `CycleService` renumbers on every create/update), per-questionnaire TAB.5 bands (`band_critical_max`/`band_efficacy_min`; DTCRO 2/3, ViVaCom 0–5 with no bands yet → unclassified), back-fill of facilitator facility access. → `V16` `users.must_change_password` (temporary passwords; the prod profile flags the seed `admin` at start-up while the published password still works — `SeedAdminRotation`; the JWT filter then allows only `/auth/me`, `/auth/me/password`, `/auth/logout`) and `annual_cycles.is_default` (optional admin landing cycle, at most one).

Structure notes that go beyond the spec: assigned terna coordinators are auto-granted `user_terna_access` scope, and an assigned Internal Facilitator (role required) is auto-granted `user_facility_access` (revoked when no cycle names them there); global cycle labels show the code only, the "1st 2025-26" ordinal is per facility (facility history, `frontend/src/lib/ordinal.ts`, tested with `npm --prefix frontend test`); questionnaire scale and bands lock once responses exist; `eu.dtc4eu.progress` serves the per-role task list and terna matrix via read-only SQL; refresh tokens are stateless signed JWTs (schema has no token table); per-form profile headers are declarative JSON resources under `backend/src/main/resources/profile-schemas/` validated by `ProfileValidationService` (required fields only at submit); the facility scoring engine (`eu.dtc4eu.scoring`) recomputes inline on every submit and via `POST /facilities/{id}/scores/recompute`; EP currently equals the item-level quantitative strength set and benchmark ties break by terna position (both flagged to the client); QCNC meta-areas (RO.1 = Q1+Q2 → areas 1–3, RO.2 = Q3 → 4–6, RO.3 = Q4+Q5 → 7–9) are computed in `QcncStandardsService` and feed the Summary Table's qualitative areas on the frontend; classification bands apply to unrounded scores (§6.8). Spreadsheets use Apache POI: paper-questionnaire XLSX import (`ResponseImportService`, validate then all-or-nothing commit), terna workbook export (`TernaExportService`); research export is CSV + `.sps` + R (`ResearchExportService`). Interface languages: EN bundled, TR/PL machine drafts in `frontend/public/locales/` loaded on demand (pending partner review). Facility setting SH is "Supported Housing" (provisional, client to confirm).

## Public website + CMS (`website/`)

`website/` is a **separate product** living beside the platform: the public DTC4EU marketing/info site plus its own standalone CMS. It shares only the design tokens (copied from `frontend/src/styles/tokens.css`) and the dev MinIO container (bucket `dtc4eu-website`). It has its **own database** (`website-db` compose service, port 5434, db `dtc4eu_website`, own Flyway chain), its **own admin users/auth** (roles ADMIN/EDITOR, dev login `webadmin` / `Dtc4eu!Web2026`), and its own ports: Spring Boot CMS API on **8090** (`website/backend`, base package `eu.dtc4eu.website`), one Vite app on **5174** (`website/frontend` — public site + code-split `/admin` CMS). Pages are free-form: ordered typed blocks (HERO, RICH_TEXT, CARDS, …) with JSONB payloads validated by `BlockValidationService`; rich-text HTML is sanitized server-side on write. Content is EN-only but every table carries a `locale` column. This is unrelated to the platform schema's Phase-3 "general platform" tables — never mix the two.

## Deployment (`deploy/`)

Production runs on a single Aruba Cloud VPS (VPS O4A8, Italian DC; domain `dtc4.eu`, platform on `platform.dtc4.eu`) via `deploy/docker-compose.prod.yml` (build from repo root with `--env-file deploy/.env`): Caddy (auto-HTTPS, serves both SPAs, proxies `/api` per vhost via `SITE_DOMAIN`/`PLATFORM_DOMAIN` env vars), both Spring APIs (prod profile, env-only config), one Postgres 16 with two databases (created by `deploy/initdb/`), internal-only MinIO, and a nightly backup container (`deploy/backup.sh`, 14-day rotation). Runbook: `deploy/DEPLOY.md`. Staging (§9 there): `deploy/docker-compose.staging.yml` (own DB/storage/secrets, reached through the prod Caddy at `STAGING_DOMAIN` over the `dtc4eu-edge` network) refreshed from prod by `deploy/staging-refresh.sh`, which runs `deploy/staging-anonymise.sql` — extend that script whenever a migration adds a column that can hold personal data. Secrets template: `deploy/.env.example` — real `.env` is never committed.

## Scope guard — Phase 2 in progress

Implemented: foundation, Module 1 (Facilities), Module 2 (Ternas), Module 4 (DTCRO + scoring), Module 3 (**Audits** — `eu.dtc4eu.qualityaudit`, V1 §5 tables, coordinator auto-scope, evaluator count rule, accredited outcomes), Modules 6–8 (quality documents with named assessor, GPP effectiveness snapshots, accreditation history) and the structured **GAS-Set** (`set_analysis_grids` variant `GAS`, schema `profile-schemas/gasset/GAS_SET.json`, Section N restricted to clinical-access roles).

**Module 5-bis** assignment **engine** is built (`eu.dtc4eu.assignment`: nested AND/OR conditions in JSONB, `/admin/rules` editor + tester, respondent profile confirmation and "My questionnaires"), but **no rules are seeded** — the client's assignment matrix is entered through the UI, never hardcoded. Response creation is not yet restricted server-side to assigned forms (it would add a questionnaire→assignment edge to the frozen ArchUnit cycles).

Still out of scope, blocked on client content: **Module 5 ViVaCom** (questionnaire shell seeded INACTIVE by V12 — no item bank delivered; historical EP entered manually via `manual_excellence_points`), and the general platform (Academy, forum, repository, certificates — note the public website lives separately in `website/`).

## Non-negotiable design rules

- **Flyway owns the schema.** Hibernate `ddl-auto` never beyond `validate`. Never edit `V1__baseline_schema.sql`; every schema change is a new `V__` file.
- **`updated_at` is maintained by DB triggers** — never set it in application code.
- **The facility is the anchor entity; every data record is scoped to an annual cycle.** The selected cycle is passed as `X-Cycle-Id` header (or query param) and applied as an implicit filter on all cycle-scoped queries. Closed/archived cycles are read-only.
- **Never overwrite raw data.** Raw item-level responses are immutable after submit; aggregates (`response_area_scores`, `facility_scale_scores`) are derived into separate tables. The scoring engine (area mean on submit) must be idempotent and re-runnable.
- **Pseudonymous codes only** — subjects and facilities are identified by codes, never personal names.
- **Role + data scope enforced on every data endpoint**, implemented as reusable JPA Specifications: `INTERNAL_FACILITATOR` → facilities in `user_facility_access`; `COORDINATOR` → ternas in `user_terna_access` + their member facilities; `RESPONDENT` → own responses; `RESEARCH_TEAM`/`ADMIN`/`SUPER_ADMIN` → all data in the cycle. Out-of-scope records return **404, not 403**.
- **Enums are fixed by CHECK constraints in the schema** — mirror them exactly as Java enums (lists in spec §5).
- Standard abbreviations are **GPP / CGS / EEN** (Good Practice Procedure / Community Group Setting / Enabling Environment Setting) per the spec v0.2 client revision (`docs/DTC4EU_Client_Revision_Change_Register_v0.2.md`) — this **reverses** the earlier PGP/DGC/NEE convention from the first Excel data dictionary.
- Log create/update/delete, login, submit, and export to `audit_logs`.

## Conventions (details in spec §4)

- REST base `/api/v1`, JSON only, Bearer JWT, BCrypt passwords.
- snake_case in DB ↔ camelCase in Java/JSON; entities map to exact schema table names; IDs are `GENERATED ALWAYS AS IDENTITY` — never set manually.
- List endpoints return `{ content, page, size, totalElements }`; filters combine with AND via Spring Data Specifications.
- One global `@RestControllerAdvice` producing the error shape in spec §4.
- All config from env vars / Spring profiles (`application-local.yml`, `application-prod.yml`); required vars in spec §2. AWS vs Azure stays interchangeable — don't couple code to either.
- Backend packages per domain (`cycle/`, `facility/`, `terna/`, `questionnaire/`, `scoring/`, `assignment/`, `qualityaudit/`, `user/`, `file/`, `audit/`), each containing `entity/ dto/ repository/ service/ controller/ mapper/`.
- **ArchUnit freezes the existing domain cycles** (`backend/src/test/resources/archunit_store`). A new Java dependency between facility/questionnaire/scoring/terna classes adds a violation; prefer a native query or frontend composition. `check:arch` does not compile — run `./mvnw -q test-compile` first. Back up the store before running it: on a mismatch it can silently drop stored cycles.
- The repo sits in an iCloud-synced folder, which can leave `Foo 2.class` duplicates in `backend/target/` and break Spring context start-up with duplicate beans. If ITs fail with "Failed to load ApplicationContext", run `./mvnw clean` first.

## Development environment & commands

Local dependencies run via `docker-compose.yml` (Postgres 16 on 5432, MinIO on 9000/9001 — definition in spec §2): `docker compose up -d`. Once T0 is scaffolded:

- Backend (from `backend/`): `mvn spring-boot:run`; tests with `mvn test` (JUnit 5 + Mockito; Testcontainers with Postgres for repositories and the scoring engine — Docker must be running); single test: `mvn test -Dtest=ClassName#method`. API docs at Swagger UI (springdoc-openapi).
- Frontend (from `frontend/`): `npm run dev` (Vite); React Query, React Router, React Hook Form + Zod, i18next.

## Open questions — do not invent answers

Spec §9 lists unresolved inputs. The DTCRO item content arrived with spec v0.2 (six forms / 216 items, seeded verbatim from `tools/dtcro_item_bank.csv` — including the client's typos; clinical content is never edited or invented). Where still blocked (QCNC catalogue text, GAS-Set structure, register §12 questions), stub clearly-labeled placeholder data and keep building the container — never fabricate clinical content as if real.

## Agent skills

### Issue tracker

GitHub Issues in `miloslalk/dtc4eu`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The default five: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: a root `GLOSSARY.md` plus `docs/adr/`, both created when first needed. See `docs/agents/domain.md`.
