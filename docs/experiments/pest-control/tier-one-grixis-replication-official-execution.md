# Pest Control Tier-1 Grixis — 12-game official replication execution gate

## Scope

This gate adds the one-shot production execution surface for the already-frozen and read-only
validated 12-game replication vector.

The workflow has no user-entered values. Dispatching the uniquely named workflow once is the explicit
execution action.

## Fail-closed requirements

Before gameplay, the workflow:

- rejects GitHub reruns via `GITHUB_RUN_ATTEMPT=1`;
- checks out the exact workflow-dispatch commit;
- records the exact commit and computed tree;
- downloads artifact `10660335894`;
- verifies archive SHA-256 `49597c4a3011464e1d4bb707dfa1b554090054059e0675254524c92fcce8d6fd`;
- verifies the artifact's internal checksum inventory;
- creates a durable execution envelope.

The runner then uses the exact frozen loader and strict 12-game coordinator. Before each game is
initialized it creates a write-once attempt marker. A completed raw game is written once after the
production driver returns. Any exception rejects the block and stops continuation.

No reroll, replacement, seed regeneration, retry-after-failure, or outcome-conditioned continuation
is permitted.

## Evidence

The workflow uploads the execution envelope, all attempt markers, all completed raw games, summary,
and per-game raw SHA-256 inventory even if execution fails after the envelope exists.

The primary replication result is admissible only if all 12 frozen assignments execute once in exact
order and the post-run audit finds no protocol or integrity defect. The earlier four-game smoke remains
separate for primary inference.

This PR constructs the execution surface but does not itself dispatch it.
