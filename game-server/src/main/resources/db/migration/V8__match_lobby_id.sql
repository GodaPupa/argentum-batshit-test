-- Link recorded games back to the lobby/tournament that produced them.
--
-- A finished tournament is stored as one `tournaments` row keyed by its `lobby_id`, and its bracket
-- games are stored as ordinary `match_results` rows — but until now nothing tied the two together in
-- the database (the live game→lobby link lives only in the in-memory game cache). This adds the lobby
-- id to `match_results` so a tournament's full game list (every player's games, not just yours) and
-- their replays can be found with a single join: `match_results.lobby_id = tournaments.lobby_id`.
--
-- Nullable: non-lobby games (quick game / casual) never have a lobby id, and games recorded before
-- this migration keep a null (their tournament detail simply shows standings without a game list).
-- Like the rest of the accounts subsystem this only runs when accounts are enabled.
ALTER TABLE match_results ADD COLUMN lobby_id TEXT;
CREATE INDEX idx_match_results_lobby ON match_results (lobby_id);
