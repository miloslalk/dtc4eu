-- V16 — FIX-LIST X4 and Q1 (Simone's feedback, 22 Nov 2026).

-- X4: the seeded super-admin password is published in the repo docs and was shared
-- in a chat. A flagged user can only change their password until they do; the prod
-- profile flags the seed admin at start-up while the seed password still works
-- (SeedAdminRotation), and admins can flag accounts they create or reset.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Q1: an optional admin-chosen default cycle for first visits. At most one.
ALTER TABLE annual_cycles ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX annual_cycles_single_default ON annual_cycles (is_default) WHERE is_default;
