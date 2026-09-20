# Pest Control V2 official execution

This directory is the durable block-level record for the frozen ten-game V2 calibration execution.

## Immutable provenance

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10`
- Qualified runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`
- Pinned execution harness: `cafa38d496479c863480a4f5db019d1ec58d9cbb`
- Workflow source commit: `abdc58f41e18103ee9502965ea4fc2d30fcb22a1`
- Official workflow run: `35525262024` (`Pest V2 official EXECUTE #2`)
- Official artifact: `10610310479` (`pest-control-v2-official-execution`)
- Official artifact archive SHA-256: `577ecf33bbbd6fa2b9967867228b6eabb81b6b2c5590f2c0670f5d3536f80a2b`
- Frozen seed artifact run: `35510807996`
- Frozen seed artifact: `10605531402`
- Frozen seed archive SHA-256: `9b7e61ddd92cf008c82368d4d5f897fc91815fdf07e6f0d4ab04b651db313731`
- Ordered vector SHA-256: `c88d45352f531a08a484004f35ffbcc44ef08331f2c7844e5c11449e02c2d104`
- Assignment CSV SHA-256: `0df747d1928753b92b7fa812fbeccdd3f639490a26a9fdab99a9d203b3e9857f`
- Freeze manifest SHA-256: `8dbff485b5adfe0b2d9d683668958494c055ab40de0c7335f923448cee50b450`

The workflow accepted the frozen vector once, in order. There were no rerolls, replacements, regenerated seeds, deck changes, protocol changes, or retries.

## Disposition

- Execution state: `COMPLETED`
- Audit disposition: `ACCEPTED_CALIBRATION`
- Attempted games: 10/10
- Recorded games: 10/10
- Terminal games: 10/10
- Protocol defects: 0
- Pest Control wins: 6
- Mono Red Madness wins: 4
- Pest Control record when starting: 2-3
- Pest Control record when drawing: 4-1
- Mean terminal turn: 14.3
- Total recorded actions: 3,160

These are ten independent preboard games, not a best-of match. The 6-4 result is calibration evidence from the complete frozen block and should not be overstated as a precise matchup estimate.

## Preserved files

- `artifact-index.json` binds the frozen identities, exact attempted seeds, per-game raw hashes, summary hash, and completed disposition.
- `attempted-seeds.csv` records the exact one-shot attempt order.
- `block-summary.csv` records terminal outcome, seat/start assignment, turn, action count, and protocol-defect status for every game.
- `archive-provenance.json` binds this durable record to the GitHub workflow and artifact archive.
- `post-execution-audit-input.json` preserves the machine reconciliation of all 3,160 actions.
- `post-execution-acceptance-audit.md` records the controlling audit disposition and the reviewed game-8 terminal target.

The full artifact additionally contains each game's raw JSON, deterministic gzip, report, and manifest. The archive digest above commits to those exact bytes.
