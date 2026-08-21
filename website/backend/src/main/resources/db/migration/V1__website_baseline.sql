-- ============================================================================
-- DTC4EU public website CMS — baseline schema (V1)
--
-- Standalone database, fully separate from the platform DB. Content is
-- English-only for now but every content table carries a locale column so
-- additional languages can be added without a migration.
-- ============================================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- 1. CMS admin users (separate from all platform accounts)
-- ---------------------------------------------------------------------------

CREATE TABLE admin_users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(60)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'EDITOR')),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_admin_users_updated
    BEFORE UPDATE ON admin_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 2. Media library (objects live in the dtc4eu-website MinIO/S3 bucket)
-- ---------------------------------------------------------------------------

CREATE TABLE media_assets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(120) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_key     VARCHAR(255) NOT NULL UNIQUE,
    alt_text        VARCHAR(300),
    created_by      BIGINT REFERENCES admin_users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 3. Pages + free-form block builder
-- ---------------------------------------------------------------------------

CREATE TABLE pages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug            VARCHAR(120) NOT NULL,
    locale          VARCHAR(10)  NOT NULL DEFAULT 'en',
    title           VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    show_in_nav     BOOLEAN      NOT NULL DEFAULT FALSE,
    nav_label       VARCHAR(60),
    nav_order       INT          NOT NULL DEFAULT 0,
    seo_title       VARCHAR(200),
    seo_description VARCHAR(320),
    updated_by      BIGINT REFERENCES admin_users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (slug, locale)
);

CREATE TRIGGER trg_pages_updated
    BEFORE UPDATE ON pages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Ordered typed sections. Payload shape per block_type is enforced in the
-- application layer (BlockValidationService); rich-text payload fields are
-- sanitized server-side before they are stored.
CREATE TABLE page_blocks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    page_id         BIGINT       NOT NULL REFERENCES pages (id) ON DELETE CASCADE,
    sort_order      INT          NOT NULL,
    block_type      VARCHAR(30)  NOT NULL CHECK (block_type IN
                        ('HERO', 'RICH_TEXT', 'IMAGE_TEXT', 'CARDS', 'FACTS', 'PARTNERS',
                         'GALLERY', 'NEWS_LIST', 'DOWNLOADS', 'CTA', 'CONTACT_FORM')),
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (page_id, sort_order) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX idx_page_blocks_page ON page_blocks (page_id, sort_order);

-- ---------------------------------------------------------------------------
-- 4. News
-- ---------------------------------------------------------------------------

CREATE TABLE news_posts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug            VARCHAR(120) NOT NULL,
    locale          VARCHAR(10)  NOT NULL DEFAULT 'en',
    title           VARCHAR(200) NOT NULL,
    excerpt         VARCHAR(500),
    body_html       TEXT         NOT NULL DEFAULT '',
    cover_media_id  BIGINT REFERENCES media_assets (id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    published_at    TIMESTAMPTZ,
    updated_by      BIGINT REFERENCES admin_users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (slug, locale)
);

CREATE TRIGGER trg_news_posts_updated
    BEFORE UPDATE ON news_posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_news_posts_published ON news_posts (status, published_at DESC);

-- ---------------------------------------------------------------------------
-- 5. Photo galleries
-- ---------------------------------------------------------------------------

CREATE TABLE galleries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug            VARCHAR(120) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_galleries_updated
    BEFORE UPDATE ON galleries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE gallery_images (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gallery_id      BIGINT       NOT NULL REFERENCES galleries (id) ON DELETE CASCADE,
    media_id        BIGINT       NOT NULL REFERENCES media_assets (id),
    caption         VARCHAR(300),
    sort_order      INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_gallery_images_gallery ON gallery_images (gallery_id, sort_order);

-- ---------------------------------------------------------------------------
-- 6. Contact form submissions (stored only; no mail sending)
-- ---------------------------------------------------------------------------

CREATE TABLE contact_submissions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(200) NOT NULL,
    subject         VARCHAR(200),
    message         TEXT         NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 7. Site-wide settings (footer, disclaimer, contact email, social links...)
-- ---------------------------------------------------------------------------

CREATE TABLE site_settings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setting_key     VARCHAR(60)  NOT NULL UNIQUE,
    value           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_site_settings_updated
    BEFORE UPDATE ON site_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
