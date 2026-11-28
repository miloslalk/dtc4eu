-- V5 — security review: logins do not survive a password change.
-- A CMS refresh token issued before this instant is refused, so other sessions end
-- when their short-lived access token expires. Set whenever an admin sets a user's
-- password. NULL = no cut-off.
ALTER TABLE admin_users ADD COLUMN tokens_valid_after TIMESTAMPTZ;
