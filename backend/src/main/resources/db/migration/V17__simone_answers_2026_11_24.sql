-- Simone's answers to the open questions (WhatsApp, 23-24 Nov 2026).

-- 1. ViVaCom effectiveness bands (TAB "Punteggio ViVaCom"): 0 <= M <= 3 critical,
--    3 < M < 4 intermediate, 4 <= M <= 5 efficacy. DTCRO (V15: <= 2 / >= 3)
--    already matches the same table.
UPDATE questionnaires SET band_critical_max = 3, band_efficacy_min = 4 WHERE code = 'VIVACOM';

-- 2. ViVaCom answer labels, 0-5, as worded by Simone (identical to the scale
--    printed in VIVACOM 3a Ed. 2017). They are longer than the old 60-char cap.
ALTER TABLE questionnaire_scale_anchors ALTER COLUMN label TYPE VARCHAR(200);

INSERT INTO questionnaire_scale_anchors (questionnaire_id, value, label)
SELECT q.id, v.val, v.lab
FROM questionnaires q,
     (VALUES (0, 'Nothing or almost nothing is being done, and there are no concrete plans to meet the requirement'),
             (1, 'There is a written program to meet the requirement, but nothing has been done to meet it'),
             (2, 'Requirement partially met, but not yet acceptable'),
             (3, 'Requirement met acceptably (50% of the time)'),
             (4, 'Requirement met more than acceptably (75% of the time)'),
             (5, 'Requirement met excellently (90% of the time)')) AS v(val, lab)
WHERE q.code = 'VIVACOM'
ON CONFLICT (questionnaire_id, value) DO UPDATE SET label = EXCLUDED.label;

-- 3. QCNC stars: "they can be removed (they are not necessary)" is a display
--    change, done in the frontend. is_mandatory stays: it still selects the
--    seven Q1 standards that count for the meta-areas (open question to Simone
--    whether 1A-1C should count too).

-- 4. Member register: Staff and Users belong to one community per cycle; Family
--    members and Managers may also belong to other communities in the same cycle.
--    The code is therefore unique per facility, not per cycle; the cross-facility
--    rule for STAFF/USER is enforced by FacilityService.addMember.
ALTER TABLE facility_members DROP CONSTRAINT facility_members_member_code_annual_cycle_id_key;
ALTER TABLE facility_members ADD CONSTRAINT facility_members_facility_cycle_code_key
    UNIQUE (facility_id, annual_cycle_id, member_code);

-- 5. "A person can only be an internal facilitator in one community per annual
--    cycle." FacilityService.setParticipation rejects a second community; the
--    index backs it up. If existing data already breaks the rule the index is
--    skipped with a warning instead of failing the deployment; fix the data and
--    the application check still applies.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM facility_cycle_participation
                WHERE internal_facilitator_id IS NOT NULL
                GROUP BY annual_cycle_id, internal_facilitator_id HAVING count(*) > 1) THEN
        RAISE WARNING 'V17: a person facilitates several communities in one cycle; unique index not created';
    ELSE
        CREATE UNIQUE INDEX uq_participation_one_facilitator_per_cycle
            ON facility_cycle_participation (annual_cycle_id, internal_facilitator_id)
            WHERE internal_facilitator_id IS NOT NULL;
    END IF;
END $$;
