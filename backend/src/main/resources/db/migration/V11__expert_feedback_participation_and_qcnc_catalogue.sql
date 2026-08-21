-- ============================================================================
-- V11 — Expert feedback on the Phase 1 prototype
--       (tools/dtc4euexpertfeedbackontheplatform (1))
--
-- 1. Per-cycle activity checklist on facility participation (the OK/NO grid
--    requested on the prototype screens): TRUE = OK, FALSE = NO, NULL = not
--    yet marked.
-- 2. qcnc_assessments.administered_by — the reviewer asked for the person's
--    NAME on each administration, not just the role.
-- 3. Seed of the QCNC standards catalogue, VERBATIM from
--    "BRUSCHETTA C N-C Quest_En_ Correct" (2026) — including its typo
--    "flield" (5A); clinical content is never edited. The Q1 standards
--    1D, 1E, 1K, 1O, 1P, 1Q, 1R are flagged mandatory per the reviewer's
--    annotation ("standard 1D; 1E; 1K; 1O; 1P; 1Q; 1R are requested").
-- ============================================================================

ALTER TABLE facility_cycle_participation
    ADD COLUMN sag_done                BOOLEAN,
    ADD COLUMN qcnc_done               BOOLEAN,
    ADD COLUMN dtcro_long_form_done    BOOLEAN,
    ADD COLUMN foundation_meeting_done BOOLEAN,
    ADD COLUMN final_forum_done        BOOLEAN;

ALTER TABLE qcnc_assessments
    ADD COLUMN administered_by BIGINT REFERENCES users(id);

INSERT INTO qcnc_standards (cluster_code, letter_code, standard_type, is_mandatory, setting_parameters, description, sort_order) VALUES
-- Q1. Attachment - Belonging
('Q1','1A','ORGANIZATIONAL_STRUCTURAL', FALSE,
 '{"RTC":"Service open: Days 7/7; 24 h.","STC":"Service open: Days 7/7; 24 h.","DAY_TC":"Service open: Days 2-7/7; 5-12 h."}'::jsonb,
 'Days Open with staff in presence', 10),
('Q1','1B','ORGANIZATIONAL_STRUCTURAL', FALSE,
 '{"RTC":"n. 6 - 20 users.","STC":"n. 6 - 10 users.","DAY_TC":"n. 6 - 15 users."}'::jsonb,
 'Number of Users', 11),
('Q1','1C','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL,
 'DTC Staff with social and healthcare practitioners', 12),
('Q1','1D','ORGANIZATIONAL_STRUCTURAL', TRUE, NULL,
 'Reflective Team of Directorate (leadership coordination sub-group of the staff)', 13),
('Q1','1E','ORGANIZATIONAL_STRUCTURAL', TRUE, NULL,
 'Almost a Psychotherapist, or almost a Psychiatrist/Psychologist and an Expert by Experience, in the reflective coordination sub-group of the staff.', 14),
('Q1','1K','COMMUNITY_FUNCTIONAL', TRUE, NULL, 'Community Presentation on Joining', 15),
('Q1','1O','COMMUNITY_FUNCTIONAL', TRUE, NULL, 'Therapeutic Contract', 16),
('Q1','1P','COMMUNITY_FUNCTIONAL', TRUE, NULL, 'Consensual Choice of Key Worker', 17),
('Q1','1Q','COMMUNITY_FUNCTIONAL', TRUE, NULL, 'Gradually Consensual Joining in Coordination with the Family', 18),
('Q1','1R','COMMUNITY_FUNCTIONAL', TRUE, NULL, 'Personalized Therapeutic Project Activity Groups', 19),
-- Q2. Containment - Permissiveness
('Q2','2B','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL,
 'Dining room containing a single table for all members of the Community', 20),
('Q2','2C','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL, 'Communal, home-style kitchen', 21),
('Q2','2H','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Communal care regarding food, cleanliness, and hygiene.', 22),
('Q2','2I','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Communal meals.', 23),
('Q2','2J','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Co-living and informal collaboration.', 24),
('Q2','2K','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Crisis meeting.', 25),
('Q2','2R','COMMUNITY_FUNCTIONAL', FALSE, NULL,
 'Managing boundaries between emotional and administrative affiliations.', 26),
-- Q3. Communication - Communalism
('Q3','3A','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL, 'Residential Dwelling', 30),
('Q3','3B','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL, 'Therapeutic and Activity Multi-Group Structure', 31),
('Q3','3H','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Community Meeting', 32),
('Q3','3I','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Reflection on community events', 33),
('Q3','3J','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Individual therapeutic support', 34),
('Q3','3K','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Reflection on current and past relationships', 35),
('Q3','3L','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Art therapy', 36),
-- Q4. Involvement – Reality Confrontation
('Q4','4A','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL, 'Room designated as staff office', 40),
('Q4','4B','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL, 'Democratic self-organization by members', 41),
('Q4','4C','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Democratic Management of the Care Budget', 42),
('Q4','4D','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Maintenance of Community Regulations', 43),
('Q4','4H','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Multi-family and Multi-institutional Community Meeting', 44),
('Q4','4I','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Arts and Crafts Workshops', 45),
('Q4','4J','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Political Action for Local Community Development', 46),
-- Q5. Agency – Democratization
('Q5','5A','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL, 'House integrated into the urban flield', 50),
('Q5','5C','ORGANIZATIONAL_STRUCTURAL', FALSE, NULL, 'Absence of architectural barriers', 51),
('Q5','5F','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Democratic Clinical decision-making', 52),
('Q5','5G','COMMUNITY_FUNCTIONAL', FALSE, NULL,
 'Collective responsibility towards the community, care, life, and work', 53),
('Q5','5H','COMMUNITY_FUNCTIONAL', FALSE, NULL, 'Inclusion of expert by experience in the staff', 54),
('Q5','5J','COMMUNITY_FUNCTIONAL', FALSE, NULL,
 'Agreed-upon, gradual discharges supported by building external group and family connections', 55),
('Q5','5N','COMMUNITY_FUNCTIONAL', FALSE, NULL,
 'Creation of enabling environments for employment support', 56);
