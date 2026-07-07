-- ============================================================================
-- DTC4EU DIGITAL PLATFORM - DATABASE BASELINE (PostgreSQL)
-- Flyway migration: V1__baseline_schema.sql
-- Derived from: DatabaseErasmus (SIMONE) data dictionary + requirement documents
-- Target: PostgreSQL 16
--
-- Design rules:
--   * Every data-collection record is scoped to an annual_cycle (replicable cycles).
--   * The facility is the anchor entity; most modules link to it.
--   * Raw item-level and per-evaluator data is preserved; aggregates are DERIVED
--     into separate tables and never overwrite raw data.
--   * Subjects and facilities are identified by pseudonymous codes.
--
-- Phase 1 (September) tables are marked [P1]. All other tables are defined now so
-- the schema does not need redesign for later phases.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. Shared trigger: maintain updated_at
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 1. FOUNDATION - users, roles, access scope, annual cycles   [P1]
-- ============================================================================

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(120) UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    preferred_lang  VARCHAR(8)   DEFAULT 'en',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code    VARCHAR(40)  NOT NULL UNIQUE,   -- SUPER_ADMIN, ADMIN, RESEARCH_TEAM, COORDINATOR,
                                            -- INTERNAL_FACILITATOR, EVALUATOR, RESPONDENT,
                                            -- TRAINER, DTC_REPRESENTATIVE, POLICYMAKER
    name    VARCHAR(120) NOT NULL
);

CREATE TABLE permissions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(200)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE annual_cycles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20) NOT NULL UNIQUE,        -- e.g. '2026-2027'
    start_date  DATE,
    end_date    DATE,
    is_current  BOOLEAN     NOT NULL DEFAULT FALSE,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                CHECK (status IN ('OPEN','CLOSED','ARCHIVED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- 2. QUESTIONNAIRE ENGINE - scale definitions   [P1 for DTCRO/MHSRO]
--    (defined before facilities/audits because derived tables reference areas/items)
-- ============================================================================

CREATE TABLE questionnaires (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(30) NOT NULL UNIQUE,        -- DTCRO, MHSRO, VIVACOM
    name        VARCHAR(150) NOT NULL,
    scale_min   INTEGER NOT NULL DEFAULT 1,
    scale_max   INTEGER NOT NULL DEFAULT 4,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE questionnaire_areas (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questionnaire_id  BIGINT NOT NULL REFERENCES questionnaires(id) ON DELETE CASCADE,
    area_code         VARCHAR(20) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    sort_order        INTEGER,
    UNIQUE (questionnaire_id, area_code)
);

CREATE TABLE questionnaire_items (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questionnaire_id  BIGINT NOT NULL REFERENCES questionnaires(id) ON DELETE CASCADE,
    area_id           BIGINT REFERENCES questionnaire_areas(id) ON DELETE SET NULL,
    item_code         VARCHAR(20) NOT NULL,
    item_text         TEXT NOT NULL,
    response_type     VARCHAR(20) NOT NULL DEFAULT 'LIKERT'
                      CHECK (response_type IN ('LIKERT','FREE_TEXT')),
    sort_order        INTEGER,
    UNIQUE (questionnaire_id, item_code)
);

-- Administration forms: DTCRO/ViVaCom are administered in several modes and to
-- several stakeholder categories (Excel "Type": Users/Familiars/Practitioners/
-- Managers, Selfeval. Com, Etheroeval Del).
CREATE TABLE questionnaire_forms (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questionnaire_id     BIGINT NOT NULL REFERENCES questionnaires(id) ON DELETE CASCADE,
    administration_mode  VARCHAR(40) NOT NULL
                         CHECK (administration_mode IN
                         ('INDIVIDUAL','SELFEVAL_COLLECTIVE','HETEROEVAL_DELEGATION')),
    stakeholder_category VARCHAR(20)
                         CHECK (stakeholder_category IN
                         ('USERS','FAMILIARS','PRACTITIONERS','MANAGERS')),
    facility_type        VARCHAR(10) CHECK (facility_type IN ('TC','GA','SH')),
    name                 VARCHAR(150)
);

-- ============================================================================
-- 3. MODULE 1 - FACILITIES   [P1]
-- ============================================================================

CREATE TABLE facilities (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_code   VARCHAR(40) NOT NULL UNIQUE,    -- Excel: 'Iden. F. code' (pseudonym)
    facility_type   VARCHAR(10) NOT NULL CHECK (facility_type IN ('TC','GA','SH')),
    name            VARCHAR(200) NOT NULL,          -- Excel: 'Name'
    provider        VARCHAR(255),                   -- Excel: 'Provider'
    place           VARCHAR(200),                   -- Excel: 'Place'
    country         VARCHAR(80),
    city            VARCHAR(120),
    address         TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per-cycle participation (Excel: 'Activity')
CREATE TABLE facility_cycle_participation (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id              BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id          BIGINT NOT NULL REFERENCES annual_cycles(id),
    activity                 VARCHAR(30) NOT NULL
                             CHECK (activity IN ('VISITING_TERNA','QUALITY_AUDIT','TRAINING_PAUSE')),
    internal_facilitator_id  BIGINT REFERENCES users(id),
    UNIQUE (facility_id, annual_cycle_id)
);

-- Members (Excel: 'Members' = Users/Familiars/Practitioners/Managers) + 'Codes'
CREATE TABLE facility_members (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id     BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id BIGINT NOT NULL REFERENCES annual_cycles(id),
    member_code     VARCHAR(40) NOT NULL,           -- Excel: 'Codes' (pseudonym)
    category        VARCHAR(20) NOT NULL
                    CHECK (category IN ('USERS','FAMILIARS','PRACTITIONERS','MANAGERS')),
    UNIQUE (member_code, annual_cycle_id)
);

-- Set Analysis Grids (Excel: variants A/B/C/D). Compiled by the internal facilitator.
CREATE TABLE set_analysis_grids (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id     BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id BIGINT NOT NULL REFERENCES annual_cycles(id),
    grid_variant    VARCHAR(4) NOT NULL,            -- 'A','B','C','D',...
    compiled_by     BIGINT REFERENCES users(id),
    payload         JSONB,                          -- structured grid answers
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (facility_id, annual_cycle_id, grid_variant)
);

-- QCNC assessment across 5 quality areas (Excel: Q1..Q5)
CREATE TABLE qcnc_assessments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id     BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id BIGINT NOT NULL REFERENCES annual_cycles(id),
    payload         JSONB,                          -- answers keyed by Q1..Q5
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (facility_id, annual_cycle_id)
);

-- ============================================================================
-- 4. MODULE 2 - TERNAS   [P1]
-- ============================================================================

CREATE TABLE ternas (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terna_code             VARCHAR(20) NOT NULL,        -- Excel: 'terna Cod' (T1, T2, ...)
    annual_cycle_id        BIGINT NOT NULL REFERENCES annual_cycles(id),
    terna_type             VARCHAR(10) NOT NULL
                           CHECK (terna_type IN ('TCS','GAS','SHS')),
    founding_meeting_date  DATE,
    final_forum_date       DATE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (terna_code, annual_cycle_id)
);

-- Three member facilities per terna (Excel: Facility 1/2/3), each with its facilitator.
-- A facility may join many ternas across cycles (historical tracking).
CREATE TABLE terna_facilities (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terna_id                BIGINT NOT NULL REFERENCES ternas(id) ON DELETE CASCADE,
    facility_id             BIGINT NOT NULL REFERENCES facilities(id),
    internal_facilitator_id BIGINT REFERENCES users(id),
    position                INTEGER CHECK (position BETWEEN 1 AND 3),
    UNIQUE (terna_id, facility_id)
);

-- Coordinators (Excel: Coord. 1 / Coord. 2 -> 2 for TC ternas, 1 for GA/SH)
CREATE TABLE terna_coordinators (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terna_id        BIGINT NOT NULL REFERENCES ternas(id) ON DELETE CASCADE,
    coordinator_id  BIGINT NOT NULL REFERENCES users(id),
    UNIQUE (terna_id, coordinator_id)
);

-- The 5 key dates (Excel: '5 Dates'): founding + 3 visits + final forum.
-- Founding/final live on ternas; the visit dates live here.
CREATE TABLE terna_visit_dates (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terna_id    BIGINT NOT NULL REFERENCES ternas(id) ON DELETE CASCADE,
    visit_date  DATE NOT NULL,
    visit_order INTEGER CHECK (visit_order BETWEEN 1 AND 3)
);

-- ============================================================================
-- 5. MODULE 3 - AUDITS
-- ============================================================================

CREATE TABLE audits (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_code      VARCHAR(20) NOT NULL,            -- Excel: 'Audit Cod' (A1, A2, ...)
    annual_cycle_id BIGINT NOT NULL REFERENCES annual_cycles(id),
    facility_id     BIGINT NOT NULL REFERENCES facilities(id),
    audit_type      VARCHAR(10) NOT NULL
                    CHECK (audit_type IN ('SIMPLE','BQ','CGQ')),  -- Excel: Simple / BQ / CGQ
    renewal_number  INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (audit_code, annual_cycle_id)
);

CREATE TABLE audit_coordinators (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id       BIGINT NOT NULL REFERENCES audits(id) ON DELETE CASCADE,
    coordinator_id BIGINT NOT NULL REFERENCES users(id),
    UNIQUE (audit_id, coordinator_id)
);

-- Up to 3 expert evaluators (Excel: Expert Eval 1/2/3). Scores kept per evaluator
-- for inter-rater analysis (3 for CGQ, 1 for Simple/BQ - enforced as business rule).
CREATE TABLE audit_evaluators (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id        BIGINT NOT NULL REFERENCES audits(id) ON DELETE CASCADE,
    evaluator_id    BIGINT NOT NULL REFERENCES users(id),
    evaluator_order INTEGER CHECK (evaluator_order BETWEEN 1 AND 3),
    UNIQUE (audit_id, evaluator_id)
);

-- Accredited quality outcomes (Excel: AQProcesses / AQAreas / AQEPoints)
CREATE TABLE audit_accredited_processes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id     BIGINT NOT NULL REFERENCES audits(id) ON DELETE CASCADE,
    process_code VARCHAR(40) NOT NULL
);

CREATE TABLE audit_accredited_areas (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id BIGINT NOT NULL REFERENCES audits(id) ON DELETE CASCADE,
    area_id  BIGINT NOT NULL REFERENCES questionnaire_areas(id),
    UNIQUE (audit_id, area_id)
);

CREATE TABLE audit_accredited_items (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id BIGINT NOT NULL REFERENCES audits(id) ON DELETE CASCADE,
    item_id  BIGINT NOT NULL REFERENCES questionnaire_items(id),
    excellence_points NUMERIC(6,3),                  -- AQEPoints
    UNIQUE (audit_id, item_id)
);

-- ============================================================================
-- 6. MODULES 4 & 5 - QUESTIONNAIRE RESPONSES   [Module 4 is P1]
--    Generic tables shared by DTCRO/MHSRO and ViVaCom (differ only by questionnaire_id)
-- ============================================================================

CREATE TABLE questionnaire_responses (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questionnaire_id     BIGINT NOT NULL REFERENCES questionnaires(id),
    form_id              BIGINT REFERENCES questionnaire_forms(id),
    annual_cycle_id      BIGINT NOT NULL REFERENCES annual_cycles(id),
    facility_id          BIGINT REFERENCES facilities(id),
    subject_code         VARCHAR(40) NOT NULL,       -- Excel: 'Sob. Code' (pseudonym)
    respondent_category  VARCHAR(30) NOT NULL
                         CHECK (respondent_category IN
                         ('USERS','FAMILIARS','PRACTITIONERS','MANAGERS',
                          'SELFEVAL_COLLECTIVE','HETEROEVAL_DELEGATION')),
    administration_mode  VARCHAR(40),
    profile_snapshot     JSONB,                      -- respondent profile at completion time
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT','SUBMITTED')),
    submitted_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE questionnaire_response_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    response_id   BIGINT NOT NULL REFERENCES questionnaire_responses(id) ON DELETE CASCADE,
    item_id       BIGINT NOT NULL REFERENCES questionnaire_items(id),
    numeric_value INTEGER,                           -- Likert (validated against scale in app)
    text_value    TEXT,                              -- free-text
    UNIQUE (response_id, item_id)
);

-- Derived: mean score per area (never entered manually)
CREATE TABLE response_area_scores (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    response_id  BIGINT NOT NULL REFERENCES questionnaire_responses(id) ON DELETE CASCADE,
    area_id      BIGINT NOT NULL REFERENCES questionnaire_areas(id),
    mean_value   NUMERIC(6,3),
    computed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (response_id, area_id)
);

-- ============================================================================
-- 7. FACILITY SCALE SCORES (derived analytics)
--    Excel "Scales" block: Excellence Points, qual/quant Strength & Weakness
--    Points, Benchmark Areas - computed per facility, per scale, per cycle,
--    at item or area level. Modelled as flexible derived records.
-- ============================================================================

CREATE TABLE facility_scale_scores (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id      BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id  BIGINT NOT NULL REFERENCES annual_cycles(id),
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id),
    metric_type      VARCHAR(30) NOT NULL
                     CHECK (metric_type IN
                     ('EXCELLENCE_POINT','QUAL_STRENGTH','QUANT_STRENGTH',
                      'QUAL_WEAKNESS','QUANT_WEAKNESS','BENCHMARK_AREA')),
    area_id          BIGINT REFERENCES questionnaire_areas(id),
    item_id          BIGINT REFERENCES questionnaire_items(id),
    value            NUMERIC(10,4),
    computed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- 8. MODULES 6, 7, 8 - STANDARD APPLICATION TRACKING (PGP / DGC / NEE)
--    Unified table; PGP effectiveness scoring lives in a child table.
-- ============================================================================

CREATE TABLE standard_applications (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id              BIGINT NOT NULL REFERENCES facilities(id),   -- Excel: 'Iden. F. code'
    annual_cycle_id          BIGINT NOT NULL REFERENCES annual_cycles(id),
    standard_type            VARCHAR(10) NOT NULL
                             CHECK (standard_type IN ('PGP','DGC','NEE')),
    standard_code            VARCHAR(40),                                 -- Excel: 'Standard Code'
    service_name             VARCHAR(255),                                -- Excel: 'NOME SERVIZIO'
    service_type             VARCHAR(120),                                -- Excel: 'TIPO DI SERVIZIO'
    num_users                INTEGER,                                     -- Excel: 'Num. Utenti nel CA'
    num_staff                INTEGER,                                     -- Excel: 'Num. Staff nel CA'
    origin                   VARCHAR(20)
                             CHECK (origin IN ('DEVELOPED','IMPLEMENTED')), -- Sviluppata/Implementata
    application_cycles_count INTEGER,                                     -- 'Annualita di Applicazioni'
    application_level_pct    NUMERIC(5,2),                                -- 'Livello Applicazione ... nel CA'
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (facility_id, annual_cycle_id, standard_type, standard_code)
);

-- PGP-only hierarchical effectiveness scoring (Module 6)
CREATE TABLE pgp_effectiveness_scores (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    standard_application_id   BIGINT NOT NULL REFERENCES standard_applications(id) ON DELETE CASCADE,
    questionnaire_item_id     BIGINT REFERENCES questionnaire_items(id),  -- 'Item Questionario corrispondente'
    individual_effectiveness  NUMERIC(6,3),   -- Livello Efficacia Item Quest Indiv. CA
    mean_item_effectiveness   NUMERIC(6,3),   -- Livello Efficacia Item Quest Medio CA
    mean_area_effectiveness   NUMERIC(6,3),   -- Livello Efficacia Area Quest Medio CA
    mean_quest_effectiveness  NUMERIC(6,3),   -- Livello Efficacia Questionario Medio CA
    areas_with_score          INTEGER,        -- Numero Aree ... con Punteggio di Efficacia
    areas_total               INTEGER,        -- Numero Aree ... in Totale con Punteggio
    computed_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- 9. MODULE 5-bis - PROFILING & RULE ENGINE
-- ============================================================================

CREATE TABLE user_profiles (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    annual_cycle_id BIGINT REFERENCES annual_cycles(id),
    age             INTEGER,
    role_category   VARCHAR(30),   -- maps to facility_members.category
    terna_id        BIGINT REFERENCES ternas(id),
    project_phase   VARCHAR(30),   -- maps to facility_cycle_participation.activity
    country         VARCHAR(80),
    city            VARCHAR(120),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, annual_cycle_id)
);

-- Admin-configured rules: conditions as AND/OR tree in JSONB, editable from admin panel
CREATE TABLE assignment_rules (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                    VARCHAR(150) NOT NULL,
    conditions              JSONB NOT NULL,
    target_questionnaire_id BIGINT REFERENCES questionnaires(id),
    target_form_id          BIGINT REFERENCES questionnaire_forms(id),
    priority                INTEGER NOT NULL DEFAULT 0,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_by              BIGINT REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- 10. DATA-SCOPE ASSIGNMENTS   [P1]  (after facilities & ternas exist)
-- ============================================================================

CREATE TABLE user_facility_access (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    facility_id BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, facility_id)
);

CREATE TABLE user_terna_access (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    terna_id BIGINT NOT NULL REFERENCES ternas(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, terna_id)
);

-- ============================================================================
-- 11. SUPPORT - files & audit log   [P1]
-- ============================================================================

CREATE TABLE files (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type   VARCHAR(40) NOT NULL,
    entity_id     BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_path  VARCHAR(500) NOT NULL,
    mime_type     VARCHAR(100),
    size_bytes    BIGINT,
    uploaded_by   BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id         BIGINT REFERENCES users(id),
    action          VARCHAR(80) NOT NULL,
    entity_type     VARCHAR(40),
    entity_id       BIGINT,
    details         JSONB,
    status          VARCHAR(20) NOT NULL
);

-- ============================================================================
-- 12. GENERAL PLATFORM (Phase 3 - defined for completeness)
-- ============================================================================

CREATE TABLE repository_documents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    document_type VARCHAR(40),
    is_public     BOOLEAN NOT NULL DEFAULT FALSE,
    file_id       BIGINT REFERENCES files(id),
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE courses (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE course_sessions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id    BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    session_date DATE,
    trainer_id   BIGINT REFERENCES users(id),
    location     VARCHAR(150)
);

CREATE TABLE course_attendance (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES course_sessions(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    attended   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (session_id, user_id)
);

CREATE TABLE certificates (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    course_id        BIGINT REFERENCES courses(id),
    certificate_type VARCHAR(80),
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    file_id          BIGINT REFERENCES files(id)
);

CREATE TABLE forum_topics (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    category   VARCHAR(80),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE forum_posts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic_id   BIGINT NOT NULL REFERENCES forum_topics(id) ON DELETE CASCADE,
    author_id  BIGINT REFERENCES users(id),
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- 13. updated_at triggers
-- ============================================================================
CREATE TRIGGER trg_users_updated       BEFORE UPDATE ON users                     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_cycles_updated       BEFORE UPDATE ON annual_cycles            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_facilities_updated   BEFORE UPDATE ON facilities               FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_sag_updated          BEFORE UPDATE ON set_analysis_grids       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_qcnc_updated         BEFORE UPDATE ON qcnc_assessments         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ternas_updated       BEFORE UPDATE ON ternas                   FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_audits_updated       BEFORE UPDATE ON audits                   FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_responses_updated    BEFORE UPDATE ON questionnaire_responses  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_stdapp_updated       BEFORE UPDATE ON standard_applications    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_profiles_updated     BEFORE UPDATE ON user_profiles            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_rules_updated        BEFORE UPDATE ON assignment_rules         FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- 14. INDEXES
-- ============================================================================
CREATE INDEX idx_fac_part_cycle    ON facility_cycle_participation (annual_cycle_id);
CREATE INDEX idx_fac_members_fac   ON facility_members (facility_id, annual_cycle_id);
CREATE INDEX idx_ternas_cycle      ON ternas (annual_cycle_id);
CREATE INDEX idx_terna_fac_fac     ON terna_facilities (facility_id);
CREATE INDEX idx_audits_cycle      ON audits (annual_cycle_id);
CREATE INDEX idx_audits_facility   ON audits (facility_id);
CREATE INDEX idx_resp_cycle        ON questionnaire_responses (annual_cycle_id);
CREATE INDEX idx_resp_facility     ON questionnaire_responses (facility_id);
CREATE INDEX idx_resp_subject      ON questionnaire_responses (subject_code);
CREATE INDEX idx_resp_items_resp   ON questionnaire_response_items (response_id);
CREATE INDEX idx_area_scores_resp  ON response_area_scores (response_id);
CREATE INDEX idx_scale_scores_fac  ON facility_scale_scores (facility_id, annual_cycle_id, questionnaire_id);
CREATE INDEX idx_stdapp_fac        ON standard_applications (facility_id, standard_type);
CREATE INDEX idx_rules_active      ON assignment_rules (is_active, priority);
CREATE INDEX idx_files_entity      ON files (entity_type, entity_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_user   ON audit_logs (user_id);

-- End of V1__baseline_schema.sql
