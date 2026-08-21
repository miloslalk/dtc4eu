-- ============================================================================
-- V10: spec v0.2 register §4 — QCNC schema carrier. The register defines the
-- QCNC model (five clusters, letter-coded standards, three administrations per
-- facility per cycle, mandatory Q1 standards); whether DATA ENTRY moves into
-- Phase 1 is open question 12.2, so this migration carries the model only.
-- The standards catalogue TEXT was not delivered — qcnc_standards stays empty
-- until the client supplies it (never invent clinical content).
-- ============================================================================

-- Standards catalogue (Haigh 1999 clusters).
CREATE TABLE qcnc_standards (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cluster_code       VARCHAR(2) NOT NULL
                       CHECK (cluster_code IN ('Q1','Q2','Q3','Q4','Q5')),
    letter_code        VARCHAR(4) NOT NULL,     -- '1A'…'1R', '2B'…'2R', '3A'…'3L', '4A'…'4J', '5A'…'5N'
    standard_type      VARCHAR(30) NOT NULL
                       CHECK (standard_type IN ('ORGANIZATIONAL_STRUCTURAL','COMMUNITY_FUNCTIONAL')),
    is_mandatory       BOOLEAN NOT NULL DEFAULT FALSE,  -- Q1 1D,1E,1K,1O,1P,1Q,1R block accreditation on NC
    setting_parameters JSONB,                   -- per-setting values (e.g. 1A open days/hours per RTC/STC/Day TC)
    description        TEXT,                    -- standard text — pending the client's catalogue
    sort_order         INTEGER,
    UNIQUE (letter_code)
);

-- Three administrations per facility per cycle (register §4):
-- (1) Internal Facilitator self-assessment, (2) Terna Coordinator for their
-- own facility, (3) Terna Coordinator for the visiting delegations.
-- The V1 single-administration UNIQUE is replaced accordingly. Existing dev
-- rows (placeholder Q1–Q5 payloads) become INTERNAL_FACILITATOR ones; the
-- DEFAULT stays so the legacy placeholder endpoint keeps working until the
-- QCNC UI lands.
ALTER TABLE qcnc_assessments DROP CONSTRAINT qcnc_assessments_facility_id_annual_cycle_id_key;
ALTER TABLE qcnc_assessments ADD COLUMN administration_type VARCHAR(40) NOT NULL
    DEFAULT 'INTERNAL_FACILITATOR';
ALTER TABLE qcnc_assessments ADD CONSTRAINT qcnc_assessments_administration_type_check
    CHECK (administration_type IN
           ('INTERNAL_FACILITATOR','TERNA_COORDINATOR_OWN','TERNA_COORDINATOR_DELEGATION'));
ALTER TABLE qcnc_assessments ADD CONSTRAINT qcnc_assessments_facility_cycle_admin_key
    UNIQUE (facility_id, annual_cycle_id, administration_type);

-- Per-standard Conformity / Non-Conformity results of one administration.
CREATE TABLE qcnc_standard_results (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_id BIGINT NOT NULL REFERENCES qcnc_assessments(id) ON DELETE CASCADE,
    standard_id   BIGINT NOT NULL REFERENCES qcnc_standards(id),
    conformity    VARCHAR(2) NOT NULL CHECK (conformity IN ('C','NC')),
    note          TEXT,
    UNIQUE (assessment_id, standard_id)
);
