-- ============================================================================
-- V9: spec v0.2 register §6–§9 — facility taxonomy (3 settings × 8 subtypes),
-- overlapping annual cycles, new roles, quality-document naming reversal.
-- ============================================================================

-- 1. Facility taxonomy (register §7). facility_type stays TC/GA/SH and now
--    means the SETTING (displayed DTC-TC / DTC-GA / SH); the new subtype
--    column carries the 8 types and must pair with its setting.
ALTER TABLE facilities ADD COLUMN facility_subtype VARCHAR(30);
ALTER TABLE facilities ADD CONSTRAINT facilities_facility_subtype_check
    CHECK (facility_subtype IN
           ('RTC','STC','DAY_TC','DOUBLE_GA','SIMPLE_GA','COUPLE_GA',
            'SUPPORTED_APARTMENTS','HOME_CARE'));
ALTER TABLE facilities ADD CONSTRAINT facilities_setting_subtype_pair_check
    CHECK (facility_subtype IS NULL
        OR (facility_type = 'TC' AND facility_subtype IN ('RTC','STC','DAY_TC'))
        OR (facility_type = 'GA' AND facility_subtype IN ('DOUBLE_GA','SIMPLE_GA','COUPLE_GA'))
        OR (facility_type = 'SH' AND facility_subtype IN ('SUPPORTED_APARTMENTS','HOME_CARE')));

-- 2. Annual cycles (register §8): a cycle spans 16 months (Sep 1 → Dec 31 of
--    the following year) and ADJACENT CYCLES OVERLAP. There is no single
--    "current" cycle any more — every request selects its cycle explicitly
--    (X-Cycle-Id); the is_current flag is removed. sequence_number feeds the
--    "1st 2023-24"-style display label.
ALTER TABLE annual_cycles DROP COLUMN is_current;
ALTER TABLE annual_cycles ADD COLUMN sequence_number INTEGER;
UPDATE annual_cycles
   SET end_date = DATE '2027-12-31', sequence_number = 1
 WHERE code = '2026-2027';

-- 3. Roles (register §9): Upper Coordinator (global scope) and Audit
--    Coordinator (facility scope; audits arrive in Phase 2).
INSERT INTO roles (code, name) VALUES
    ('UPPER_COORDINATOR', 'Upper Coordinator'),
    ('AUDIT_COORDINATOR', 'Audit Coordinator');

-- 4. Quality documents (register §1 — NAMING REVERSAL): GPP / CGS / EEN
--    replace PGP / DGC / NEE. Document codes follow {TYPE} CT{n}-{suffix}
--    (suffix taxonomy unconfirmed, open question 12.1 — no format CHECK).
ALTER TABLE standard_applications DROP CONSTRAINT standard_applications_standard_type_check;
UPDATE standard_applications SET standard_type = CASE standard_type
    WHEN 'PGP' THEN 'GPP' WHEN 'DGC' THEN 'CGS' WHEN 'NEE' THEN 'EEN'
    ELSE standard_type END;
ALTER TABLE standard_applications ADD CONSTRAINT standard_applications_standard_type_check
    CHECK (standard_type IN ('GPP','CGS','EEN'));

ALTER TABLE pgp_effectiveness_scores RENAME TO gpp_effectiveness_scores;
