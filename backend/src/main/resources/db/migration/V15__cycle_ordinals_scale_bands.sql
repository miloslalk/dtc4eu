-- V15 — Simone's feedback of 22 Nov 2026 (FIX-LIST-simone-feedback-2026-11-22.md).

-- 1. Cycle ordinals are derived, never typed in: the n-th cycle of the programme
--    is its position by start date. A hand-entered -1 on 2025-2026 rendered as
--    "-1undefined". Renumber every cycle; the application keeps this in sync on
--    every create/update.
UPDATE annual_cycles c
   SET sequence_number = ranked.n
  FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY start_date, code) AS n FROM annual_cycles) ranked
 WHERE ranked.id = c.id;

ALTER TABLE annual_cycles
    ADD CONSTRAINT annual_cycles_sequence_number_check CHECK (sequence_number IS NULL OR sequence_number >= 1);

-- 2. Effectiveness bands (TAB.5) are per questionnaire, not global. A score M is
--    CRITICAL when M <= band_critical_max, EFFICACY when M >= band_efficacy_min,
--    INTERMEDIATE in between. NULL bands = no classification yet (client to supply).
ALTER TABLE questionnaires
    ADD COLUMN band_critical_max NUMERIC(5, 2),
    ADD COLUMN band_efficacy_min NUMERIC(5, 2),
    ADD CONSTRAINT questionnaires_bands_check
        CHECK ((band_critical_max IS NULL) = (band_efficacy_min IS NULL)
               AND (band_critical_max IS NULL OR band_critical_max < band_efficacy_min)),
    ADD CONSTRAINT questionnaires_scale_check CHECK (scale_min < scale_max);

UPDATE questionnaires SET band_critical_max = 2, band_efficacy_min = 3 WHERE code = 'DTCRO';

-- 3. ViVaCom is a 0–5 Likert scale (Simone, 22 Nov 2026). Its band table is still
--    pending (spec Q3), so it stays unclassified; it has no items or responses yet.
UPDATE questionnaires SET scale_min = 0, scale_max = 5 WHERE code = 'VIVACOM';

-- 4. An assigned Internal Facilitator gets data access to the facility (Simone's
--    feedback #4). Back-fill the access for assignments made before this rule.
INSERT INTO user_facility_access (user_id, facility_id)
SELECT DISTINCT p.internal_facilitator_id, p.facility_id
  FROM facility_cycle_participation p
 WHERE p.internal_facilitator_id IS NOT NULL
ON CONFLICT DO NOTHING;
