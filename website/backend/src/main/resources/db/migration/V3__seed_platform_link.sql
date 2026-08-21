-- The evaluation-platform link shown as a highlighted button in the site
-- header. URL is environment-specific — editors change it under Settings.
INSERT INTO site_settings (setting_key, value)
VALUES ('platform', '{
  "label": "Platform",
  "url": "http://localhost:5173"
}'::jsonb)
ON CONFLICT (setting_key) DO NOTHING;
