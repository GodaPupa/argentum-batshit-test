# Pest Control Tier-1 Grixis replication — official Game 1 initialization incident

Official replication workflow run `35654021529` is permanently terminal and must never be rerun.

## Observed boundary

The workflow successfully verified the exact frozen 12-game artifact and wrote the durable execution
envelope. The coordinator then durably wrote `attempt-1.marker` and entered the Game 1 callback.

No completed raw game was recorded.

The uploaded evidence reports:

- disposition: `REJECTED`
- attempted games: `1`
- recorded games: none
- failure: `Failed requirement.`
- rerolls: `0`
- replacements: `0`
- seed regeneration: `0`

Therefore **Game 1's frozen assignment/seed is consumed as an attempted official game**. Games 2-12
were never attempted and remain unconsumed. No outcome from Game 1 was exposed because initialization
failed before the production driver returned a terminal raw artifact.

## Root cause

The replication loader correctly binds the new 12-game vector hashes. However, the reused
`PestControlTierOneGrixisAuthorizedInitializer` still requires the old four-game smoke vector,
assignment, and manifest hashes. The first replication assignment therefore fails at the initializer's
old-vector identity guard.

This is an execution-harness integration defect, not a deck, pilot, rules-engine, or matchup result.

## Experimental disposition

The 12-game replication block cannot be accepted or restarted as though Game 1 were untouched.

Prohibited:

- rerunning run `35654021529`;
- replaying replication Game 1;
- regenerating the vector;
- replacing Game 1's seed;
- silently restarting all 12 assignments;
- treating this failed attempt as a win/loss.

Permitted next work is seedless infrastructure correction and deterministic validation only. Any
future continuation decision must preserve Game 1 as consumed/incomplete and must be separately
reviewed before Games 2-12 can be considered.
