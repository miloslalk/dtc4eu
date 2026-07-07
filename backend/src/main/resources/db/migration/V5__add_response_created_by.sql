-- ============================================================================
-- V5: add created_by to questionnaire_responses.
-- Spec §6 scopes RESPONDENT users to "only their own responses", but V1 links
-- responses only to the pseudonymous subject_code (no user reference). The
-- nullable created_by carries who recorded the response so that scope rule is
-- enforceable; it is not part of the research data (exports omit it).
-- ============================================================================

ALTER TABLE questionnaire_responses
    ADD COLUMN created_by BIGINT REFERENCES users(id);

CREATE INDEX idx_resp_created_by ON questionnaire_responses (created_by);
