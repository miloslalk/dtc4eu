-- ============================================================================
-- V3: create the first annual cycle 2026-2027 and mark it current (spec §5).
-- Dates assume a September-to-August project year; adjust via the admin UI
-- if the project calendar differs.
-- ============================================================================

INSERT INTO annual_cycles (code, start_date, end_date, is_current, status)
VALUES ('2026-2027', DATE '2026-09-01', DATE '2027-08-31', TRUE, 'OPEN');
