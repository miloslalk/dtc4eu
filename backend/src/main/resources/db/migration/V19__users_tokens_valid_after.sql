-- V19 — security review: logins do not survive a password change.
-- Tokens are stateless JWTs (no token table); a refresh token issued before this
-- instant is refused, so other sessions end when their 15-minute access token
-- expires. Set on every password change (self-service, admin reset, seed-admin
-- rotation). NULL = no cut-off.
ALTER TABLE users ADD COLUMN tokens_valid_after TIMESTAMPTZ;
