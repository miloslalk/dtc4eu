-- ============================================================================
-- V4: DTCRO scale with PLACEHOLDER content (spec T4: "Until content arrives,
-- seed placeholder items so downstream work proceeds"). The real 45 items /
-- 9 areas and Likert anchor labels are open input #1 in spec §9 — replace the
-- texts via the admin UI (or a follow-up migration) when the clinical
-- specialist supplies them. Structure: 9 areas × 5 Likert items, scale 1–4.
-- ============================================================================

INSERT INTO questionnaires (code, name, scale_min, scale_max, is_active)
VALUES ('DTCRO', 'DTCRO — Residents Outcome Scale (PLACEHOLDER content)', 1, 4, TRUE);

INSERT INTO questionnaire_areas (questionnaire_id, area_code, name, sort_order)
SELECT q.id, 'A' || n, 'PLACEHOLDER Area ' || n || ' — awaiting clinical content', n
FROM questionnaires q, generate_series(1, 9) AS n
WHERE q.code = 'DTCRO';

-- 45 items, 5 per area (D01..D45)
INSERT INTO questionnaire_items (questionnaire_id, area_id, item_code, item_text, response_type, sort_order)
SELECT q.id,
       a.id,
       'D' || lpad(s.n::text, 2, '0'),
       'PLACEHOLDER item ' || s.n || ' — text awaiting clinical specialist (spec §9, open question 1)',
       'LIKERT',
       s.n
FROM questionnaires q
JOIN generate_series(1, 45) AS s(n) ON TRUE
JOIN questionnaire_areas a
  ON a.questionnaire_id = q.id
 AND a.area_code = 'A' || (((s.n - 1) / 5) + 1)
WHERE q.code = 'DTCRO';

-- individual forms for the four stakeholder categories
INSERT INTO questionnaire_forms (questionnaire_id, administration_mode, stakeholder_category, name)
SELECT q.id, 'INDIVIDUAL', c.cat, 'DTCRO — ' || initcap(lower(c.cat)) || ' (individual)'
FROM questionnaires q,
     (VALUES ('USERS'), ('FAMILIARS'), ('PRACTITIONERS'), ('MANAGERS')) AS c(cat)
WHERE q.code = 'DTCRO';

-- collective / delegation forms
INSERT INTO questionnaire_forms (questionnaire_id, administration_mode, name)
SELECT q.id, m.mode, 'DTCRO — ' || m.label
FROM questionnaires q,
     (VALUES ('SELFEVAL_COLLECTIVE', 'Collective self-evaluation'),
             ('HETEROEVAL_DELEGATION', 'Hetero-evaluation delegation')) AS m(mode, label)
WHERE q.code = 'DTCRO';
