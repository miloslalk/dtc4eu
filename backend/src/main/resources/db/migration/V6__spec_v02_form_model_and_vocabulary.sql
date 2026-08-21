-- ============================================================================
-- V6: spec v0.2 (client revision register, docs/DTC4EU_Client_Revision_
-- Change_Register_v0.2.md §2, §11.2–11.4) — six-form questionnaire model
-- and vocabulary renames:
--   SELFEVAL_COLLECTIVE → LONG_FORM, HETEROEVAL_DELEGATION → DELEGATION,
--   USERS/FAMILIARS/PRACTITIONERS/MANAGERS → USER/FAMILY/STAFF/MANAGER.
-- CHECK swaps follow the pattern: drop constraint → update data → re-add.
-- ============================================================================

-- 1. questionnaire_forms: renamed administration modes and stakeholder
--    categories; form_code identifies the six DTCRO forms.
ALTER TABLE questionnaire_forms DROP CONSTRAINT questionnaire_forms_administration_mode_check;
UPDATE questionnaire_forms SET administration_mode = 'LONG_FORM'  WHERE administration_mode = 'SELFEVAL_COLLECTIVE';
UPDATE questionnaire_forms SET administration_mode = 'DELEGATION' WHERE administration_mode = 'HETEROEVAL_DELEGATION';
ALTER TABLE questionnaire_forms ADD CONSTRAINT questionnaire_forms_administration_mode_check
    CHECK (administration_mode IN ('INDIVIDUAL','LONG_FORM','DELEGATION'));

ALTER TABLE questionnaire_forms DROP CONSTRAINT questionnaire_forms_stakeholder_category_check;
UPDATE questionnaire_forms SET stakeholder_category = CASE stakeholder_category
    WHEN 'USERS' THEN 'USER' WHEN 'FAMILIARS' THEN 'FAMILY'
    WHEN 'PRACTITIONERS' THEN 'STAFF' WHEN 'MANAGERS' THEN 'MANAGER'
    ELSE stakeholder_category END
WHERE stakeholder_category IS NOT NULL;
ALTER TABLE questionnaire_forms ADD CONSTRAINT questionnaire_forms_stakeholder_category_check
    CHECK (stakeholder_category IN ('USER','FAMILY','STAFF','MANAGER'));

ALTER TABLE questionnaire_forms ADD COLUMN form_code VARCHAR(20);
ALTER TABLE questionnaire_forms ADD COLUMN sort_order INTEGER;
CREATE UNIQUE INDEX uq_questionnaire_forms_code
    ON questionnaire_forms (questionnaire_id, form_code) WHERE form_code IS NOT NULL;

-- 2. questionnaire_responses: same vocabulary on respondent_category and the
--    (unconstrained) administration_mode copy.
ALTER TABLE questionnaire_responses DROP CONSTRAINT questionnaire_responses_respondent_category_check;
UPDATE questionnaire_responses SET respondent_category = CASE respondent_category
    WHEN 'USERS' THEN 'USER' WHEN 'FAMILIARS' THEN 'FAMILY'
    WHEN 'PRACTITIONERS' THEN 'STAFF' WHEN 'MANAGERS' THEN 'MANAGER'
    WHEN 'SELFEVAL_COLLECTIVE' THEN 'LONG_FORM' WHEN 'HETEROEVAL_DELEGATION' THEN 'DELEGATION'
    ELSE respondent_category END;
ALTER TABLE questionnaire_responses ADD CONSTRAINT questionnaire_responses_respondent_category_check
    CHECK (respondent_category IN ('USER','FAMILY','STAFF','MANAGER','LONG_FORM','DELEGATION'));
UPDATE questionnaire_responses SET administration_mode = 'LONG_FORM'  WHERE administration_mode = 'SELFEVAL_COLLECTIVE';
UPDATE questionnaire_responses SET administration_mode = 'DELEGATION' WHERE administration_mode = 'HETEROEVAL_DELEGATION';

-- 3. facility_members: shared vocabulary (feeds the terna population table).
ALTER TABLE facility_members DROP CONSTRAINT facility_members_category_check;
UPDATE facility_members SET category = CASE category
    WHEN 'USERS' THEN 'USER' WHEN 'FAMILIARS' THEN 'FAMILY'
    WHEN 'PRACTITIONERS' THEN 'STAFF' WHEN 'MANAGERS' THEN 'MANAGER'
    ELSE category END;
ALTER TABLE facility_members ADD CONSTRAINT facility_members_category_check
    CHECK (category IN ('USER','FAMILY','STAFF','MANAGER'));

-- 4. questionnaire_items become form-owned with a coordinate letter. The
--    coordinate (area sort_order, item_letter) is the cross-form aggregation
--    key; per-form wording lives on the item row.
ALTER TABLE questionnaire_items ADD COLUMN form_id BIGINT REFERENCES questionnaire_forms(id) ON DELETE CASCADE;
ALTER TABLE questionnaire_items ADD COLUMN item_letter VARCHAR(1);
ALTER TABLE questionnaire_items ADD CONSTRAINT questionnaire_items_item_letter_check
    CHECK (item_letter IN ('a','b','c','d','e'));
CREATE UNIQUE INDEX uq_questionnaire_items_form_coord
    ON questionnaire_items (form_id, area_id, item_letter) WHERE form_id IS NOT NULL;
CREATE INDEX idx_questionnaire_items_form ON questionnaire_items (form_id);

-- 5. Likert anchor labels (register §2.3) — per questionnaire, per scale value.
CREATE TABLE questionnaire_scale_anchors (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id) ON DELETE CASCADE,
    value            INTEGER NOT NULL,
    label            VARCHAR(60) NOT NULL,
    UNIQUE (questionnaire_id, value)
);

INSERT INTO questionnaire_scale_anchors (questionnaire_id, value, label)
SELECT q.id, v.val, v.lab
FROM questionnaires q,
     (VALUES (1, 'Not at all'), (2, 'A little'), (3, 'A lot'), (4, 'Very much')) AS v(val, lab)
WHERE q.code = 'DTCRO';

-- 6. Per-form area header phrasing (register §2.2 note / open question 12.5).
--    Canonical R1–R9 names stay on questionnaire_areas; the fill screen may
--    show the form-specific phrasing stored here.
CREATE TABLE questionnaire_form_area_labels (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    form_id      BIGINT NOT NULL REFERENCES questionnaire_forms(id) ON DELETE CASCADE,
    area_id      BIGINT NOT NULL REFERENCES questionnaire_areas(id) ON DELETE CASCADE,
    display_name VARCHAR(200) NOT NULL,
    UNIQUE (form_id, area_id)
);
