# Pest Control Grixis — SALVAGE_CONTINUATION_11 official execution gate

This gate adds the one-shot production execution surface for the untouched original frozen Games 2-12.

The workflow takes no user-entered values. It verifies the exact original frozen artifact and creates
a durable envelope declaring Game 1 consumed/incomplete and Games 2-12 as the only authorized suffix.

The runner structurally verifies that Game 1 has no attempt marker or raw-game file before and after
execution. The continuation coordinator exposes only Games 2-12. Each game receives a write-once
attempt marker before initialization and a write-once raw artifact after completion.

The replication-specific initializer and provenance adapter accept only the exact frozen replication
identity and refuse Game 1.

Any exception stops continuation permanently. There is no rerun, Game-1 replay, replacement, seed
regeneration, or outcome-conditioned restart path.

Evidence includes the durable envelope, Games 2-12 attempt markers, completed raw games, summary, and
per-game SHA-256 inventory. Results remain classified `SALVAGE_CONTINUATION_11`, not a clean
12-game replication.

This PR constructs the execution surface but does not dispatch it.
