-- ============================================================================
-- V8: spec v0.2 register §3 — derived tables for the facility-cycle scoring
-- engine (Quant-SPWP / Quant-SAWA pipelines from the Terna_Example1 workbook).
-- All tables are derived analytics: rewritten idempotently by the scoring
-- service, never entered manually. Absent components are NULL, never 0
-- (Excel AVERAGE semantics).
-- ============================================================================

-- Item-level grid: one row per coordinate (area × letter a–e) per facility,
-- cycle and questionnaire. Columns mirror the workbook's Quant-SPWP block.
CREATE TABLE facility_item_scores (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id      BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id  BIGINT NOT NULL REFERENCES annual_cycles(id),
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id),
    area_id          BIGINT NOT NULL REFERENCES questionnaire_areas(id),
    item_letter      VARCHAR(1) NOT NULL CHECK (item_letter IN ('a','b','c','d','e')),
    user_mean        NUMERIC(6,3),                   -- Quant-SPWP-U-I
    family_mean      NUMERIC(6,3),                   -- Quant-SPWP-F-I
    staff_mean       NUMERIC(6,3),                   -- Quant-SPWP-S-I
    manager_mean     NUMERIC(6,3),                   -- Quant-SPWP-M-I
    aver_i           NUMERIC(6,3),                   -- Quant-SPWP-Aver-I
    com_value        NUMERIC(6,3),                   -- LONG_FORM (Com)
    del_value        NUMERIC(6,3),                   -- DELEGATION (Del; NULL for d/e)
    final_score      NUMERIC(6,3),                   -- Quant-SPWP
    classification   VARCHAR(12)
                     CHECK (classification IN ('CRITICAL','INTERMEDIATE','EFFICACY')),
    computed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (facility_id, annual_cycle_id, questionnaire_id, area_id, item_letter)
);
CREATE INDEX idx_facility_item_scores_scope
    ON facility_item_scores (facility_id, annual_cycle_id, questionnaire_id);

-- Area-level grid (Quant-SAWA block), same structure without the letter.
CREATE TABLE facility_area_scores (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id      BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id  BIGINT NOT NULL REFERENCES annual_cycles(id),
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id),
    area_id          BIGINT NOT NULL REFERENCES questionnaire_areas(id),
    user_mean        NUMERIC(6,3),
    family_mean      NUMERIC(6,3),
    staff_mean       NUMERIC(6,3),
    manager_mean     NUMERIC(6,3),
    aver_i           NUMERIC(6,3),
    com_value        NUMERIC(6,3),
    del_value        NUMERIC(6,3),
    final_score      NUMERIC(6,3),                   -- Quant-SAWA
    classification   VARCHAR(12)
                     CHECK (classification IN ('CRITICAL','INTERMEDIATE','EFFICACY')),
    computed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (facility_id, annual_cycle_id, questionnaire_id, area_id)
);
CREATE INDEX idx_facility_area_scores_scope
    ON facility_area_scores (facility_id, annual_cycle_id, questionnaire_id);

-- Facility overall score = mean of the nine area finals.
CREATE TABLE facility_questionnaire_scores (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id      BIGINT NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id  BIGINT NOT NULL REFERENCES annual_cycles(id),
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id),
    overall_score    NUMERIC(6,3),
    classification   VARCHAR(12)
                     CHECK (classification IN ('CRITICAL','INTERMEDIATE','EFFICACY')),
    computed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (facility_id, annual_cycle_id, questionnaire_id)
);

-- Benchmark Strength Areas of the terna (register §3.4): per area, the best
-- final area score among the terna's three facilities. Terna rows are already
-- cycle-scoped (ternas.annual_cycle_id).
CREATE TABLE terna_benchmark_areas (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terna_id         BIGINT NOT NULL REFERENCES ternas(id) ON DELETE CASCADE,
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id),
    area_id          BIGINT NOT NULL REFERENCES questionnaire_areas(id),
    best_facility_id BIGINT REFERENCES facilities(id),
    best_score       NUMERIC(6,3),
    computed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (terna_id, questionnaire_id, area_id)
);

-- facility_scale_scores (V1) holds the point metrics (QUANT_STRENGTH /
-- QUANT_WEAKNESS / EXCELLENCE_POINT …). An item-level "point" is a coordinate
-- shared by up to six form-specific item rows, so it is identified by
-- area_id + item_letter rather than a single item_id.
ALTER TABLE facility_scale_scores ADD COLUMN item_letter VARCHAR(1)
    CHECK (item_letter IN ('a','b','c','d','e'));
CREATE INDEX idx_facility_scale_scores_scope
    ON facility_scale_scores (facility_id, annual_cycle_id, questionnaire_id);
