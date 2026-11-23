-- Module 5-bis assignment rule engine (spec COL-01).
-- The V1 baseline already holds assignment_rules (AND/OR condition tree in
-- JSONB, one target form per rule) and user_profiles. This adds what the rule
-- engine needs on top: a description, a validity window, the form target made
-- mandatory, and the updated_at trigger the baseline left out. No rules are
-- seeded — the assignment matrix is still pending from the client (spec Q3).

ALTER TABLE assignment_rules ADD COLUMN description TEXT;
ALTER TABLE assignment_rules ADD COLUMN valid_from DATE;
ALTER TABLE assignment_rules ADD COLUMN valid_to DATE;
ALTER TABLE assignment_rules ADD CONSTRAINT assignment_rules_validity_check
    CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_to >= valid_from);
ALTER TABLE assignment_rules ALTER COLUMN target_form_id SET NOT NULL;

CREATE INDEX idx_assignment_rules_active ON assignment_rules (is_active, priority DESC);

CREATE TRIGGER trg_assignment_rules_updated BEFORE UPDATE ON assignment_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- profile categories follow the v0.2 vocabulary of questionnaire_forms (V6)
ALTER TABLE user_profiles ADD CONSTRAINT user_profiles_role_category_check
    CHECK (role_category IN ('USER','FAMILY','STAFF','MANAGER'));
