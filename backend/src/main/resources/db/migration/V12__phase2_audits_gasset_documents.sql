-- ============================================================================
-- V12 — Phase 2 groundwork
--
-- 1. Quality-document assessments carry the assessor (expert feedback: every
--    applied document is assessed by a named Terna/Audit Coordinator each
--    cycle).
-- 2. manual_excellence_points: historical / non-platform Excellence Points
--    per cycle (register §12.4 — e.g. VIVACOM EP codes like '4.16' that do
--    not fit the DTCRO area×letter grid). Labels are stored verbatim.
-- 3. ViVaCom questionnaire shell (Module 5): INACTIVE until the client
--    delivers the item bank; areas/items/forms are added through the
--    existing questionnaire admin CRUD.
--
-- Module 3 (audits) needs no schema work — V1 already defines audits,
-- audit_coordinators, audit_evaluators and the accredited-outcome tables.
-- The structured GAS-Set reuses set_analysis_grids with grid_variant 'GAS'.
-- ============================================================================

ALTER TABLE standard_applications
    ADD COLUMN assessed_by BIGINT REFERENCES users(id),
    ADD COLUMN assessed_at TIMESTAMPTZ;

CREATE TABLE manual_excellence_points (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facility_id        BIGINT      NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    annual_cycle_id    BIGINT      NOT NULL REFERENCES annual_cycles(id),
    questionnaire_code VARCHAR(30) NOT NULL,
    label              VARCHAR(20) NOT NULL,
    created_by         BIGINT REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (facility_id, annual_cycle_id, questionnaire_code, label)
);

CREATE INDEX idx_manual_ep_facility ON manual_excellence_points (facility_id, annual_cycle_id);

INSERT INTO questionnaires (code, name, scale_min, scale_max, is_active)
VALUES ('VIVACOM', 'ViVaCom Questionnaire (pending item bank)', 1, 4, FALSE);
