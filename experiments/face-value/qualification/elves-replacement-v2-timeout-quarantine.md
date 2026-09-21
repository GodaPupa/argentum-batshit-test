# Elves preboard replacement V2 — timeout quarantine

Date: 2026-09-21
Branch: `face-value/lab`

## Disposition

**QUARANTINED — MATERIAL AI TIMEOUT**

Workflow run: `35576397752`

The official 32-game replacement V2 block executed completely, but exactly one game emitted:

`java.util.concurrent.TimeoutException`

during:

`forge.ai.AiController.chooseSpellAbilityToPlayFromList`

The affected trace was game 28 with Face Value in seat 2. Forge continued after the exception and produced a terminal game result, but the timeout occurred inside AI decision evaluation before the game ended.

Under the frozen Face Value qualification protocol, any material AI, rules, parsing, timeout, target-selection, identity, or terminal-bookkeeping defect quarantines the complete affected matchup block.

Therefore the entire 32-game Elves V2 block is ineligible for qualification evidence.

## Automated summary context

Before quarantine:

- 32/32 games completed.
- 31 games had no automated flags.
- 1 game had `exception`.
- Undercity remediation remained clean: successful Arena targeting telemetry was present and no Arena target failures occurred.
- Quirion Ranger and Winding Way capability telemetry was present.

Raw matchup outcomes are intentionally not granted tuning, structural-diagnosis, sideboard, challenger, or promotion authority.

## Preservation

- All V2 seeds remain permanently retired.
- No V2 seed may be replayed, rerolled, or rehabilitated as qualification evidence.
- The affected official seed must not be reused as a diagnostic seed.
- The Undercity remediation remains accepted; this quarantine is independent and concerns AI decision-timeout behavior.

## Next justified gate

Open a diagnostic-only Elves timeout-reproduction gate using fresh non-official seeds.

The diagnostic must:

1. use the same frozen Face Value and Elves decks;
2. use the same accepted Forge profile including the Undercity remediation;
3. use fresh diagnostic-only seeds with no qualification authority;
4. preserve raw traces;
5. count internal `TimeoutException` occurrences separately from outer process timeouts;
6. fail closed if any internal AI decision timeout occurs;
7. make no deck changes and no qualification-seed replay.

If the timeout reproduces, diagnose the smallest simulator/pilot-policy cause before another official Elves block is authorized.

If a sufficiently broad diagnostic block completes with zero internal timeouts and gameplay review is clean, a fresh cryptographic Elves replacement V3 may be frozen.

Postboard Elves remains blocked.
No challenger is authorized.
