-- ============================================================================
-- V2 — Placeholder content for the public site (dev/demo seed)
--
-- Structure mirrors the agreed reference layout: Home, The Project,
-- Partnership, Research & Policy, Resources, Get In Touch. All copy is
-- PLACEHOLDER: project facts that are not yet confirmed by the client are
-- marked "(to be confirmed)" and partner names are generic. Replace through
-- the CMS — nothing here is final content.
--
-- Dev CMS login: webadmin / Dtc4eu!Web2026  (rotate in production)
-- ============================================================================

INSERT INTO admin_users (username, password_hash, full_name, role)
VALUES ('webadmin', '$2a$10$6iTJI6LVqyneHa9mi80mFuv3BqXWEQv8PkVPoPR38FjpKezTVN9A.',
        'Website Administrator', 'ADMIN');

-- ---------------------------------------------------------------------------
-- Site settings
-- ---------------------------------------------------------------------------

INSERT INTO site_settings (setting_key, value) VALUES
('footer', '{
  "tagline": "Democratic Therapeutic Communities for Social Inclusion",
  "copyright": "DTC4EU project consortium"
}'::jsonb),
('eu', '{
  "fundingStatement": "Co-funded by the European Union",
  "disclaimer": "Funded by the European Union. Views and opinions expressed are however those of the author(s) only and do not necessarily reflect those of the European Union or the European Education and Culture Executive Agency (EACEA). Neither the European Union nor EACEA can be held responsible for them."
}'::jsonb),
('contact', '{
  "email": "info@dtc4eu.eu (to be confirmed)"
}'::jsonb);

-- ---------------------------------------------------------------------------
-- Gallery (images are added through the CMS media library)
-- ---------------------------------------------------------------------------

INSERT INTO galleries (slug, name, description)
VALUES ('project-gallery', 'Project gallery',
        'Placeholder gallery — photos from project meetings and activities will appear here.');

-- ---------------------------------------------------------------------------
-- Pages
-- ---------------------------------------------------------------------------

INSERT INTO pages (slug, locale, title, status, show_in_nav, nav_label, nav_order, seo_title, seo_description)
VALUES
('home', 'en', 'Home', 'PUBLISHED', TRUE, 'Home', 0,
 'DTC4EU — Democratic Therapeutic Communities for Social Inclusion',
 'An Erasmus+ KA220 cooperation partnership strengthening quality evaluation across democratic therapeutic communities in Europe.'),
('the-project', 'en', 'The Project', 'PUBLISHED', TRUE, 'The Project', 1,
 'The Project — DTC4EU',
 'What DTC4EU is, why it exists and how it works. Placeholder description pending final project copy.'),
('partnership', 'en', 'Partnership', 'PUBLISHED', TRUE, 'Partnership', 2,
 'Partnership — DTC4EU',
 'The organisations working together in the DTC4EU consortium. Placeholder list pending confirmation.'),
('research-policy', 'en', 'Research & Policy', 'PUBLISHED', TRUE, 'Research & Policy', 3,
 'Research & Policy — DTC4EU',
 'Evaluation instruments, research outputs and policy recommendations produced by the project.'),
('resources', 'en', 'Resources', 'PUBLISHED', TRUE, 'Resources', 4,
 'Resources — DTC4EU',
 'News, photos and downloadable materials from the DTC4EU project.'),
('get-in-touch', 'en', 'Get In Touch', 'PUBLISHED', TRUE, 'Get In Touch', 5,
 'Get In Touch — DTC4EU',
 'Contact the DTC4EU project team.');

-- --------------------------------- Home ------------------------------------

INSERT INTO page_blocks (page_id, sort_order, block_type, payload)
SELECT id, s.ord, s.btype, s.payload::jsonb FROM pages, (VALUES
(0, 'HERO', '{
  "kicker": "Erasmus+ KA220 Cooperation Partnership",
  "heading": "Democratic Therapeutic Communities for Social Inclusion",
  "subheading": "Placeholder introduction: DTC4EU brings together therapeutic communities and their partners across Europe to evaluate, improve and recognise the quality of recovery-oriented care.",
  "ctaLabel": "Discover the project",
  "ctaHref": "/the-project"
}'),
(1, 'RICH_TEXT', '{
  "heading": "Care, community and evaluation",
  "bodyHtml": "<p>This is placeholder copy. Democratic therapeutic communities support people on their recovery journey through participation, shared responsibility and everyday community life. DTC4EU builds a common European approach to <strong>evaluating the quality</strong> of that work — so that what communities do well becomes visible, and what can improve is identified together.</p><p>The final project description will replace this text.</p>"
}'),
(2, 'CARDS', '{
  "heading": "What the project does",
  "items": [
    {"title": "Evaluate", "bodyHtml": "<p>Placeholder — annual quality-evaluation cycles across participating facilities, combining self-evaluation and peer review.</p>"},
    {"title": "Improve", "bodyHtml": "<p>Placeholder — shared standards and benchmarks that turn evaluation results into concrete improvement steps.</p>"},
    {"title": "Include", "bodyHtml": "<p>Placeholder — keeping the voices of community members, families, staff and managers at the centre of quality.</p>"}
  ]
}'),
(3, 'FACTS', '{
  "heading": "Project at a glance",
  "items": [
    {"label": "Programme", "value": "Erasmus+ / KA220 Cooperation Partnership"},
    {"label": "Focus", "value": "Democratic Therapeutic Communities for Social Inclusion"},
    {"label": "Duration", "value": "(to be confirmed)"},
    {"label": "Partner countries", "value": "(to be confirmed)"},
    {"label": "Coordinator", "value": "(to be confirmed)"}
  ]
}'),
(4, 'NEWS_LIST', '{
  "heading": "Latest news",
  "limit": 3
}'),
(5, 'CTA', '{
  "heading": "Want to know more?",
  "bodyHtml": "<p>Placeholder — reach out to the project team with questions about the project, the partnership or the evaluation platform.</p>",
  "buttonLabel": "Get in touch",
  "buttonHref": "/get-in-touch"
}')
) AS s(ord, btype, payload)
WHERE pages.slug = 'home' AND pages.locale = 'en';

-- ------------------------------ The Project --------------------------------

INSERT INTO page_blocks (page_id, sort_order, block_type, payload)
SELECT id, s.ord, s.btype, s.payload::jsonb FROM pages, (VALUES
(0, 'HERO', '{
  "kicker": "About DTC4EU",
  "heading": "The Project",
  "subheading": "Placeholder — why DTC4EU exists, what it aims to change and how the partnership works."
}'),
(1, 'RICH_TEXT', '{
  "heading": "The challenge",
  "bodyHtml": "<p>Placeholder copy. People recovering from addiction and mental-health difficulties too often face exclusion — from work, from housing, from community life. Democratic therapeutic communities counter that exclusion with participation and shared responsibility, but the quality of this work is hard to demonstrate and compare across countries.</p>"
}'),
(2, 'IMAGE_TEXT', '{
  "heading": "Our approach",
  "bodyHtml": "<p>Placeholder copy. The project develops a shared evaluation framework: communities evaluate themselves every year, peer facilities review one another in small groups, and the results feed into recognised quality standards. A dedicated digital platform supports the whole cycle.</p>",
  "imageSide": "RIGHT"
}'),
(3, 'CARDS', '{
  "heading": "Work packages",
  "items": [
    {"title": "WP1 — Management", "bodyHtml": "<p>Placeholder — coordination, quality assurance and reporting.</p>"},
    {"title": "WP2 — Evaluation framework", "bodyHtml": "<p>Placeholder — instruments, standards and the annual evaluation cycle.</p>"},
    {"title": "WP3 — Digital platform", "bodyHtml": "<p>Placeholder — the online platform supporting evaluation and reporting.</p>"},
    {"title": "WP4 — Dissemination", "bodyHtml": "<p>Placeholder — sharing results with practitioners and policy makers.</p>"}
  ]
}')
) AS s(ord, btype, payload)
WHERE pages.slug = 'the-project' AND pages.locale = 'en';

-- ------------------------------ Partnership --------------------------------

INSERT INTO page_blocks (page_id, sort_order, block_type, payload)
SELECT id, s.ord, s.btype, s.payload::jsonb FROM pages, (VALUES
(0, 'HERO', '{
  "kicker": "Who we are",
  "heading": "Partnership",
  "subheading": "Placeholder — the consortium behind DTC4EU. Final partner names, logos and countries will be added here."
}'),
(1, 'RICH_TEXT', '{
  "bodyHtml": "<p>Placeholder copy. The partnership brings together therapeutic communities, umbrella organisations and research partners from across Europe. Each partner contributes its own tradition of community-based care and quality work.</p>"
}'),
(2, 'PARTNERS', '{
  "heading": "Project partners",
  "items": [
    {"name": "Partner organisation 1 (to be confirmed)", "country": "Country"},
    {"name": "Partner organisation 2 (to be confirmed)", "country": "Country"},
    {"name": "Partner organisation 3 (to be confirmed)", "country": "Country"},
    {"name": "Partner organisation 4 (to be confirmed)", "country": "Country"},
    {"name": "Partner organisation 5 (to be confirmed)", "country": "Country"},
    {"name": "Partner organisation 6 (to be confirmed)", "country": "Country"}
  ]
}')
) AS s(ord, btype, payload)
WHERE pages.slug = 'partnership' AND pages.locale = 'en';

-- ---------------------------- Research & Policy ----------------------------

INSERT INTO page_blocks (page_id, sort_order, block_type, payload)
SELECT id, s.ord, s.btype, s.payload::jsonb FROM pages, (VALUES
(0, 'HERO', '{
  "kicker": "Evidence and impact",
  "heading": "Research & Policy",
  "subheading": "Placeholder — the instruments, findings and recommendations the project produces."
}'),
(1, 'RICH_TEXT', '{
  "heading": "Evaluation instruments",
  "bodyHtml": "<p>Placeholder copy. The project works with structured evaluation instruments — self-evaluation questionnaires for community members, families, staff and managers, together with peer-review procedures carried out by partner facilities. Published instruments and manuals will be listed on this page.</p>"
}'),
(2, 'CARDS', '{
  "heading": "Outputs",
  "items": [
    {"title": "Evaluation framework", "bodyHtml": "<p>Placeholder — the shared quality-evaluation methodology.</p>"},
    {"title": "Research reports", "bodyHtml": "<p>Placeholder — findings from the annual evaluation cycles.</p>"},
    {"title": "Policy recommendations", "bodyHtml": "<p>Placeholder — conclusions for national and European policy makers.</p>"}
  ]
}')
) AS s(ord, btype, payload)
WHERE pages.slug = 'research-policy' AND pages.locale = 'en';

-- ------------------------------- Resources ---------------------------------

INSERT INTO page_blocks (page_id, sort_order, block_type, payload)
SELECT id, s.ord, s.btype, s.payload::jsonb FROM pages, (VALUES
(0, 'HERO', '{
  "kicker": "Materials and media",
  "heading": "Resources",
  "subheading": "Placeholder — news, photos and downloadable materials from the project."
}'),
(1, 'NEWS_LIST', '{
  "heading": "News",
  "limit": 6
}'),
(2, 'GALLERY', '{
  "heading": "Photo gallery"
}'),
(3, 'CTA', '{
  "heading": "Looking for something specific?",
  "buttonLabel": "Contact the team",
  "buttonHref": "/get-in-touch"
}')
) AS s(ord, btype, payload)
WHERE pages.slug = 'resources' AND pages.locale = 'en';

-- The GALLERY block references the seeded gallery by id.
UPDATE page_blocks
SET payload = payload || jsonb_build_object('galleryId', (SELECT id FROM galleries WHERE slug = 'project-gallery'))
WHERE block_type = 'GALLERY';

-- ------------------------------ Get In Touch -------------------------------

INSERT INTO page_blocks (page_id, sort_order, block_type, payload)
SELECT id, s.ord, s.btype, s.payload::jsonb FROM pages, (VALUES
(0, 'HERO', '{
  "kicker": "Contact",
  "heading": "Get In Touch",
  "subheading": "Placeholder — questions about the project, the partnership or the evaluation platform are welcome."
}'),
(1, 'CONTACT_FORM', '{
  "heading": "Write to us",
  "intro": "Fill in the form and the project team will get back to you. Fields marked * are required."
}')
) AS s(ord, btype, payload)
WHERE pages.slug = 'get-in-touch' AND pages.locale = 'en';

-- ---------------------------------------------------------------------------
-- News posts (placeholder)
-- ---------------------------------------------------------------------------

INSERT INTO news_posts (slug, locale, title, excerpt, body_html, status, published_at)
VALUES
('project-website-launched', 'en', 'Project website launched',
 'Placeholder news item — the DTC4EU project website is now online.',
 '<p>Placeholder body. The DTC4EU project website is online, presenting the project, the partnership and the resources produced along the way. Content will grow as the project progresses.</p>',
 'PUBLISHED', now() - interval '2 days'),
('kickoff-meeting', 'en', 'Kick-off meeting of the partnership',
 'Placeholder news item — the consortium met to plan the first project year.',
 '<p>Placeholder body. The partner organisations met for the kick-off of the DTC4EU project, agreeing the workplan for the first year: the evaluation framework, the digital platform and the first annual evaluation cycle.</p>',
 'PUBLISHED', now() - interval '10 days'),
('evaluation-cycle-preparations', 'en', 'Preparing the first evaluation cycle',
 'Placeholder news item — facilities and evaluation teams are getting ready.',
 '<p>Placeholder body. Participating therapeutic communities are preparing for the first annual quality-evaluation cycle, supported by the project''s digital platform and the shared evaluation instruments.</p>',
 'PUBLISHED', now() - interval '20 days');
