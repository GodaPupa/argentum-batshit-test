# Pest Control V2 matchup qualification — frozen readiness

## Accepted boundary

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Future block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_QUALIFICATION_50`
- Qualified runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`
- Accepted calibration commit: `c4f02ff2c19bd256a356e1f97a18d762484ecb7d`
- Accepted calibration run: `35525262024`
- Accepted calibration artifact: `10610310479`
- Accepted calibration archive SHA-256: `577ecf33bbbd6fa2b9967867228b6eabb81b6b2c5590f2c0670f5d3536f80a2b`
- Qualification runner state: `DISABLED`
- Qualification freeze run: `35532647383`, attempt 1, success
- Qualification freeze artifact: `10612245984`
- Qualification freeze archive SHA-256: `bf7456303c355202587484f208da20a1ca37a2ba326d081e8a3fb7c3a31be8f0`
- Qualification vector SHA-256: `c38ff24c9b45e36036cab506475bb8283f4e14e211318a7a7e6ab99acef6f497`
- Assignment CSV SHA-256: `510476250ec0fce2eac7b574c0cc01d80d8b91ce723aa39e543e5388f7f3a476`
- Freeze manifest SHA-256: `25c88ddca3fe0b65a4727cdb503cfaa40d78c2f0ae0055b113024e194a9d7f40`
- Qualification vector disposition: `FROZEN_UNEXECUTED`
- Outcome exposure: 0/50

The accepted ten-game V2 block validated the corrected runner and production-candidate pilots. Its 6-4
result is calibration evidence only. It is not pooled into the future qualification block, and its ten
seeds are permanently unavailable for replay or reassignment.

The pre-V2 replacement freeze remains a separate historical identity and is not an input to this gate.
This readiness change neither executes nor changes the disposition of that vector.

## Frozen design

The qualification block contains exactly 50 fresh, unique, nonzero signed 64-bit seeds with no
overlap against all 484 preserved exclusion values. Assignment was fixed before outcome exposure:

- Pest play/draw: 25/25;
- Pest engine seat: 25/25;
- joint seat/play-draw cells: 12/12/13/13; and
- two contiguous execution shards: games 1-25 and 26-50.

Each shard has a 240-minute ceiling and remains one half of the same logical experiment. A missing,
failed, cancelled, timed-out, retried, replaced, duplicated, or reordered shard rejects and permanently
retires the complete vector. No partial salvage is allowed.

## Predeclared acceptance boundary

Methodological acceptance requires all 50 assignments attempted exactly once in frozen order, all 50
records terminal, zero protocol defects, zero rejected or fallback actions, complete artifact hashes,
and a post-execution trace audit. The observed win count does not control the integrity disposition.

Performance is reported separately after the integrity decision. This block can qualify evidence for
the Mono Red Madness matchup; it cannot by itself establish Tier-1 status across the Pauper metagame.

## Current stop condition

The exact freeze identity is attached, and the artifact-bound two-shard plan and reconciliation
contract are constructed, but the compiled runner state remains `DISABLED`. The validation workflow
cannot initialize a game and proves that the runner guard rejects activation. The freeze operation is
documented in [the qualification seed-freeze gate](v2-qualification-seed-freeze-gate.md), and the
coordinator boundary is documented in
[the qualification execution harness](v2-qualification-execution-harness.md). Authorization requires
a later, separately reviewed workflow pinned to the exact green coordinator commit and source tree.
