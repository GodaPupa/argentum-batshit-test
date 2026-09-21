# Elves postboard replacement V1 — corrected gameplay-quality audit

Date: 2026-09-21
Workflow run: `35612799476`

## Parser correction

The original automated audit hard-coded the preboard control deck name:

`Face Value Temur Chrysalis E Final Forge 75`

The postboard materializer names the actual deck:

`Face Value Temur Chrysalis E Final Forge 75 POSTBOARD vs elves`

As a result, 13 Face Value wins were misclassified as missing terminal results. Raw logs show valid terminal results in all 32 games.

No official seed was rerun. This audit reclassifies the preserved immutable evidence only.

## Integrity review

PASS.

- 32/32 official assignments executed exactly once.
- 32/32 valid terminal results.
- 0 draws.
- 0 internal AI timeouts.
- 0 outer process timeouts.
- 0 exceptions / assertion failures / unsupported-operation markers.
- 0 Undercity target failures.
- 42 successful Arena target resolutions observed in the preserved run summary.
- Face Value cast Breath Weapon 15 times.
- Quirion Ranger and Winding Way activity were present at high volume.
- Representative long and short games from both seat assignments were reviewed without a material gameplay-quality defect.

Disposition: **ACCEPTED_POSTBOARD_ELVES_REPLACEMENT_V1**

## Accepted result

Face Value: **13-19** (40.625%)

- Face Value seat 1: 7-9
- Face Value seat 2: 6-10
- Draws: 0

Accepted preboard Elves V3 result: **11-21** (34.375%).

Observed postboard delta: **+2 wins / +6.25 percentage points** over separate fresh blocks of equal size.

This is not a paired estimate and is not treated as a formal causal effect size. It is sufficient to show that the frozen sideboard plan does not fully repair the Elves matchup.

## Structural consequence

Elves is now an accepted repeatable structural weakness across both preboard and postboard evidence.

A targeted challenger-discovery gate is authorized.

Constraints:

1. Permanent Final Forge 75 remains unchanged.
2. Challenger receives a distinct identity and file.
3. No accepted non-Elves matchup may be sacrificed without explicit cross-matchup testing.
4. Discovery may target sideboard composition and/or minimal maindeck support, but must preserve the Face Value engine identity.
5. Promotion requires an accepted discovery block plus independent fresh-seed replication.
6. Any proposed anti-Elves package must first pass legality/capability checks and then be tested against Elves on fresh seeds before cross-matchup regression.
