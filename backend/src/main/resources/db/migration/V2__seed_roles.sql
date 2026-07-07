-- ============================================================================
-- V2: seed the ten platform roles (spec §5) and one super-admin.
-- The super-admin password below is a DEVELOPMENT seed (Dtc4eu!Admin2026);
-- rotate it before any real deployment.
-- ============================================================================

INSERT INTO roles (code, name) VALUES
    ('SUPER_ADMIN',          'Super Administrator'),
    ('ADMIN',                'Administrator'),
    ('RESEARCH_TEAM',        'Research Team'),
    ('COORDINATOR',          'Terna Coordinator'),
    ('INTERNAL_FACILITATOR', 'Internal Facilitator'),
    ('EVALUATOR',            'Evaluator'),
    ('RESPONDENT',           'Respondent'),
    ('TRAINER',              'Trainer'),
    ('DTC_REPRESENTATIVE',   'DTC Representative'),
    ('POLICYMAKER',          'Policymaker');

INSERT INTO users (username, email, password_hash, full_name, preferred_lang)
VALUES ('admin', 'admin@dtc4eu.eu',
        '$2y$10$.tu5hI4ShWfDWkyN93/lR.cqldm6CWvwE.aH0J07IwvHAXIiDuITi',
        'Super Administrator', 'en');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.code = 'SUPER_ADMIN';
