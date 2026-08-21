# DTC4EU Platform — Build Specification (for Claude Code)

**Purpose of this file.** This is the implementation brief for the DTC4EU backend + frontend. It is written to be used directly by Claude Code as the source of truth for building Phase 1. Keep it at the repo root and reference it from `CLAUDE.md`. The database schema it describes is the companion file `V1__baseline_schema.sql`.

**Golden rules (read first).**
- Build **Phase 1 only**: foundation + Module 1 (Facilities), Module 2 (Ternas), Module 4 (DTCRO/MHSRO). The schema defines all modules; do **not** build endpoints for Modules 3, 5, 6–8, 5-bis, or the general platform yet.
- The **facility is the anchor entity**; everything is scoped to an **annual cycle**.
- **Never overwrite raw data.** Aggregated scores are computed into separate tables.
- Subjects and facilities are identified by **pseudonymous codes**, never personal names.
- Enforce **role + data scope** on every data endpoint (hide out-of-scope data, don't just disable it).

---

## 1. Stack & versions

| Layer | Choice |
|-------|--------|
| Language | Java 17 (LTS) |
| Backend | Spring Boot 3.3.x |
| Build | Maven |
| DB | PostgreSQL 16 |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + JWT (jjwt) |
| API docs | springdoc-openapi (Swagger UI) |
| Object storage | S3 API; MinIO locally, S3/Blob in cloud |
| DTO mapping | MapStruct (or plain mappers) |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres) |
| Frontend | React 18 + TypeScript + Vite |
| FE data | React Query; React Router; React Hook Form + Zod |
| FE i18n | i18next |

Do not add Hibernate `ddl-auto` beyond `validate`. The schema is owned by Flyway.

---

## 2. Local environment

Provide a `docker-compose.yml` for dev dependencies:

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: dtc4eu
      POSTGRES_USER: dtc4eu
      POSTGRES_PASSWORD: dtc4eu
    ports: ["5432:5432"]
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: dtc4eu
      MINIO_ROOT_PASSWORD: dtc4eu123
    ports: ["9000:9000", "9001:9001"]
```

All config comes from environment variables / Spring profiles (`application-local.yml`, `application-prod.yml`). Never hardcode secrets. Required vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`, `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`.

Because everything is env-driven and the app is containerised, deploying to **AWS or Azure is interchangeable**: managed Postgres + a container host + object storage. Defer that choice; it does not affect application code.

---

## 3. Project structure

```
backend/
  src/main/java/eu/dtc4eu/
    config/          # security, openapi, storage, cors
    security/        # JWT filter, UserDetails, data-scope resolver
    common/          # error handling, base DTOs, pagination, exceptions
    cycle/           # annual cycles
    facility/        # Module 1
    terna/           # Module 2
    questionnaire/   # scale defs + responses (Module 4) + scoring
    user/            # users, roles, profile
    file/            # storage integration
    audit/           # audit log
  src/main/resources/
    db/migration/    # Flyway: V1__baseline_schema.sql (provided), V2__seed_roles.sql, ...
    application.yml
frontend/
  src/
    app/             # shell, routing, providers
    features/facilities/  ternas/  questionnaire/  admin/  auth/
    components/      # shared UI (table, filter bar, Likert, questionnaire container)
    lib/             # api client, i18n, auth
```

Each backend domain package contains `entity/ dto/ repository/ service/ controller/ mapper/`.

---

## 4. Conventions

- **REST base:** `/api/v1`. JSON only. Bearer JWT.
- **Naming:** snake_case in DB, camelCase in Java/JSON. Entities map to the exact table names in the schema.
- **IDs:** DB uses `GENERATED ALWAYS AS IDENTITY` (BIGINT). Never set ids manually.
- **Timestamps:** `TIMESTAMPTZ`; `updated_at` maintained by DB triggers — do not set it in code.
- **Pagination:** all list endpoints return `{ content, page, size, totalElements }`; accept `page`, `size`, `sort`.
- **Filtering:** list filters combine with AND; use Spring Data Specifications.
- **Validation:** Bean Validation on DTOs; business rules in services. Likert values validated against the scale's `scale_min/scale_max`.
- **Errors:** single global `@RestControllerAdvice` returning:

```json
{ "timestamp":"...", "status":400, "error":"Bad Request",
  "message":"Validation failed", "path":"/api/v1/...",
  "details":[{"field":"subjectCode","message":"required"}] }
```

- **Migrations:** every schema change is a new `V__` file. Never edit `V1`.
- **Tests:** service-layer unit tests + Testcontainers integration tests for repositories and the scoring engine.

---

## 5. Data model

The full schema is `V1__baseline_schema.sql` (44 tables, validated against PostgreSQL 16, derived from the specialist's Excel). Entity groups relevant to Phase 1:

- **Foundation:** `users, roles, permissions, user_roles, role_permissions, annual_cycles, user_facility_access, user_terna_access`
- **Module 1:** `facilities, facility_cycle_participation, facility_members, set_analysis_grids, qcnc_assessments`
- **Module 2:** `ternas, terna_facilities, terna_coordinators, terna_visit_dates`
- **Questionnaire engine + Module 4:** `questionnaires, questionnaire_areas, questionnaire_items, questionnaire_forms, questionnaire_responses, questionnaire_response_items, response_area_scores`
- **Support:** `files, audit_logs`

Enum values are fixed by CHECK constraints in the schema — mirror them as Java enums. **Amended by spec v0.2** (`docs/DTC4EU_Client_Revision_Change_Register_v0.2.md`, applied by migrations V6–V10):
- facility_type (= the *setting*): `TC, GA, SH` (displayed as DTC-TC / DTC-GA / SH); facility_subtype: `RTC, STC, DAY_TC, DOUBLE_GA, SIMPLE_GA, COUPLE_GA, SUPPORTED_APARTMENTS, HOME_CARE` (paired with the setting)
- activity: `VISITING_TERNA, QUALITY_AUDIT, TRAINING_PAUSE`
- member category: `USER, FAMILY, STAFF, MANAGER`
- terna_type: `TCS, GAS, SHS`
- respondent_category: `USER, FAMILY, STAFF, MANAGER, LONG_FORM, DELEGATION`
- administration_mode: `INDIVIDUAL, LONG_FORM, DELEGATION`
- cycle status: `OPEN, CLOSED, ARCHIVED` (annual cycles span 16 months, Sep 1 → Dec 31 of the following year, and **adjacent cycles overlap** — there is no single "current" cycle; every request selects its cycle explicitly)
- response status: `DRAFT, SUBMITTED`
- standard_type (quality documents): `GPP, CGS, EEN`
- DTCRO items are **per-form**: six forms (`USER, FAMILY, STAFF, MANAGER, LONG_FORM, DELEGATION`) over a shared coordinate grid (area 1–9 × letter a–e); cross-form aggregation joins on the coordinate.

Add `V2__seed_roles.sql` seeding the roles and one super-admin, and `V3__seed_cycle.sql` creating cycle `2026-2027` (V9 later extends it to the 16-month span and removes the "current" flag).

---

## 6. Security & data scoping

- JWT access token (short-lived) + refresh token. `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `GET /auth/me` (returns user, roles, scope).
- Passwords: BCrypt.
- **Data-scope resolver** applied to every data query:
  - `INTERNAL_FACILITATOR` → only facilities in `user_facility_access`
  - `COORDINATOR` → only ternas in `user_terna_access` and their member facilities
  - `RESPONDENT` → only their own responses
  - `RESEARCH_TEAM / ADMIN / SUPER_ADMIN` → all data in the cycle
- Implement scope as reusable JPA Specifications composed into every list/detail query. Out-of-scope records return 404, not 403 (don't reveal existence).
- Log create/update/delete, login, submit, and export to `audit_logs`.
- The **selected annual cycle** is passed as a header (`X-Cycle-Id`) or query param and applied as an implicit filter on all cycle-scoped queries. Closed/archived cycles are read-only.

---

## 7. Phase 1 build plan (tasks with acceptance criteria)

Build in this order. Each task is done when its acceptance criteria pass with tests.

### T0 — Project scaffold
Set up backend + frontend skeletons, docker-compose, Flyway with `V1` applied, Swagger, global error handler, JWT security, CORS.
**Done when:** app boots, `V1` migrates cleanly against Postgres 16, Swagger UI lists endpoints, `POST /auth/login` issues a token for the seeded super-admin.

### T1 — Users, roles, cycles, scope
CRUD for users (admin), role assignment, facility/terna scope assignment; annual cycle CRUD + set-current + status; `X-Cycle-Id` resolution.
**Done when:** an admin can create a user, assign a role + scope, create/select a cycle; non-admins cannot reach admin endpoints.

### T2 — Module 1: Facilities
Endpoints:
- `GET/POST /facilities` (filters: type, country, city, activity, cycle), `GET/PUT /facilities/{id}`
- `GET/POST /facilities/{id}/participation`
- `GET/POST /facilities/{id}/members`
- `GET/PUT /facilities/{id}/set-analysis-grid` (payload JSONB)
- `GET/PUT /facilities/{id}/qcnc`
**Done when:** an internal facilitator sees only their facility; members can be recorded in the four categories with codes; participation activity is settable per cycle; list filters and CSV export work.

### T3 — Module 2: Ternas
Endpoints:
- `GET/POST /ternas` (filters: type, facility, date range, cycle), `GET/PUT /ternas/{id}`
- assign 3 facilities (+ auto-pull each facility's internal facilitator), coordinators, and the 5 dates.
**Business rules:** exactly 3 facilities; 2 coordinators for `TCS`, 1 for `GAS/SHS`; a facility may belong to other ternas in other cycles.
**Done when:** a coordinator can build a terna, sees only assigned ternas, and validation enforces the facility/coordinator counts.

### T4 — Questionnaire definitions (admin)
Endpoints to define a scale, its areas, items, and forms so DTCRO can be loaded:
- `GET/POST /questionnaires`, `/{id}/areas`, `/{id}/items`, `/{id}/forms`.
**Done when:** an admin can create the DTCRO scale, add 9 areas and 45 items (once content is supplied), define forms, and activate it. Until content arrives, seed placeholder items so downstream work proceeds.

### T5 — Module 4: Responses + scoring (the core)
Endpoints:
- `GET/POST /responses` (filters: facility, questionnaire, category, status, cycle), `GET/PUT /responses/{id}`
- `PUT /responses/{id}/items` (save raw Likert + free-text; autosave to DRAFT)
- `POST /responses/{id}/submit` (validate required items → lock → compute scores)
- `GET /responses/{id}/scores`
**Scoring engine:** on submit, for each area compute the mean of that area's numeric item values → write `response_area_scores`. Keep raw items immutable. Run scoring in a service method (may be `@Async`); it must be idempotent and re-runnable.
**Done when:** a respondent completes and submits a questionnaire; area means are computed correctly (unit-tested); submitted responses are read-only; research role can list, view scores, and export raw item-level data.

### T6 — Frontend Phase 1
Implement the shell (sidebar, top bar with cycle selector, language switcher, auth) and the screens from the UI Screen & Flow spec: S-00, S-01, S-02, S-10–S-16, S-20–S-22, S-30–S-33, S-40–S-42. Build the shared **Likert 1–4 control** and **questionnaire container** first.
**Done when:** all five key flows in the UI spec work end-to-end against the API.

---

## 8. Explicitly out of scope for Phase 1

Do not build (schema exists, but no endpoints/UI yet): Module 3 Audits, Module 5 ViVaCom, Modules 6–8 standard tracking, Module 5-bis rule engine (profiles are captured but assignment is manual), and the general platform (website, Academy, forum, repository, certificates). Leave the tables in place; ignore them in code.

---

## 9. Open questions (blocking / to confirm with the clinical specialist)

Treat these as inputs, not assumptions. Where blocked, stub with placeholder data and keep building the container.

1. ~~**DTCRO/MHSRO item content** (45 items / 9 areas) + Likert anchor labels~~ — **RESOLVED by spec v0.2**: six-form item bank (216 items, `tools/dtcro_item_bank.csv`, seeded by V7) with anchors 1 Not at all / 2 A little / 3 A lot / 4 Very much.
2. Set Analysis Grid structure and QCNC standards catalogue **content** (the v0.2 register defines the QCNC model — five clusters, letter codes, three administrations, mandatory Q1 standards — carried by V10; the catalogue text itself is still pending).
3. Whether respondents self-complete remotely or via a facilitator (affects T5 access model).
4. ~~Confirmation of facility types beyond TC/GA/SH~~ — **RESOLVED by spec v0.2**: 3 settings × 8 subtypes.
5. Data-retention period and consent/withdrawal workflow (GDPR/KVKK).
6. Confirmed interface languages + who supplies translations.
7. Spec v0.2 register §12 open questions (document-code suffixes, QCNC/GAS-Set Phase 1 scope, schedule OK/NO marks, VIVACOM EP entry, per-form area-name display policy).

> **Naming (reversed in spec v0.2):** quality-document types are **GPP / CGS / EEN** (Good Practice Procedure / Community Group Setting / Enabling Environment Setting), per the client's corrected July 2026 document set and annotated prototype. The earlier PGP/DGC/NEE convention from the first Excel data dictionary is obsolete.

---

## 10. Definition of done for Phase 1

Modules 1, 2, 4 work end-to-end for the September deadline: an admin sets up a cycle and the DTCRO scale; facilities and ternas are recorded with correct scoping; respondents complete DTCRO and the platform computes and displays area scores; the research role can filter, view, and export. All backed by tests, with Flyway-managed schema and env-driven config ready to deploy to AWS or Azure.
