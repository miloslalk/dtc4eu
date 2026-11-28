-- Simone's answers to the remaining questions (WhatsApp, 27 Nov 2026).

-- 1. QCNC Q1: "1A, 1B, 1C are counted in the calculation" (C/NC only). With
--    them every Q1 standard counts for the meta-areas (RO.1 = Q1 + Q2).
UPDATE qcnc_standards SET is_mandatory = true WHERE letter_code IN ('1A', '1B', '1C');

-- 2. ViVaCom answers 8 and 9: "they should be added, but they don't count
--    towards the score". An anchor outside the questionnaire's scale with
--    counts_in_score = false is an accepted answer that scoring skips.
ALTER TABLE questionnaire_scale_anchors ADD COLUMN counts_in_score BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO questionnaire_scale_anchors (questionnaire_id, value, label, counts_in_score)
SELECT q.id, v.val, v.lab, false
FROM questionnaires q,
     (VALUES (8, 'Disagree'),
             (9, 'Not pertinent')) AS v(val, lab)
WHERE q.code = 'VIVACOM'
ON CONFLICT (questionnaire_id, value) DO UPDATE SET label = EXCLUDED.label, counts_in_score = false;
