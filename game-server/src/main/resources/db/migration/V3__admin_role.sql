-- Per-account admin role. The `game.admin.password` env var stays as a bootstrap credential (it can
-- always reach the dashboard), but a signed-in account flagged here can reach it via its normal auth
-- token instead — so the shared password no longer has to be handed around. Promotion happens from the
-- dashboard's Players view; the first admin is bootstrapped by signing in with the password once and
-- promoting an account.
ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;
