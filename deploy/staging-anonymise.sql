-- DTC4EU staging: scrub personal data from a fresh copy of the production
-- platform database (FIX-LIST X3). Run ONLY through deploy/staging-refresh.sh,
-- which marks the session as staging; anywhere else the guard below aborts.
--
--   psql -v ON_ERROR_STOP=1 -v staging_password="..." -f staging-anonymise.sql
--
-- Kept: the structure people test and train on — facilities (organisations),
-- ternas, cycles, item bank, pseudonymous member/subject codes, Likert answers,
-- scores, checklists. Replaced or removed: everything that names a person or
-- may contain free text about one.

\set ON_ERROR_STOP on

DO $$
BEGIN
    IF current_setting('dtc4eu.staging', true) IS DISTINCT FROM 'yes' THEN
        RAISE EXCEPTION 'refusing to anonymise: session is not marked as staging (use staging-refresh.sh)';
    END IF;
END $$;

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- People: neutral names and addresses; one shared staging password; no forced change.
UPDATE users
   SET username             = CASE WHEN username = 'admin' THEN 'admin' ELSE 'user' || id END,
       full_name            = CASE WHEN username = 'admin' THEN 'Staging Administrator'
                                   ELSE 'Test user ' || id END,
       email                = CASE WHEN email IS NULL THEN NULL ELSE 'user' || id || '@staging.invalid' END,
       password_hash        = crypt(:'staging_password', gen_salt('bf', 10)),
       must_change_password = FALSE;

-- Free text that may describe people.
UPDATE questionnaire_response_items SET text_value = '[redacted on staging]' WHERE text_value IS NOT NULL;
UPDATE qcnc_standard_results       SET note       = '[redacted on staging]' WHERE note IS NOT NULL;
UPDATE forum_posts                 SET body       = '[redacted on staging]';

-- GAS-Set Section N is per-user clinical data (restricted to clinical roles in prod).
UPDATE set_analysis_grids SET payload = payload - 'n_users' WHERE payload ? 'n_users';

-- Street addresses are not needed for testing.
UPDATE facilities SET address = NULL WHERE address IS NOT NULL;

-- Audit trail: user ids, IPs and usernames of failed logins.
TRUNCATE audit_logs;

DROP EXTENSION pgcrypto;

COMMIT;

SELECT count(*) AS users_anonymised FROM users;
