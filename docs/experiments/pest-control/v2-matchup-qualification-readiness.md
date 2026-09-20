# Pest Control V2 matchup qualification — seedless readiness

## Accepted boundary

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Future block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_QUALIFICATION_50`
- Qualified runner: `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`
- Accepted calibration commit: `c4f02ff2c19bd256a356e1f97a18d762484ecb7d`
- Accepted calibration run: `35525262024`
- Accepted calibration artifact: `10610310479`
- Accepted calibration archive SHA-256: `577ecf33bbbd6fa2b9967867228b6eabb81b6b2c5590f2c0670f5d3536f80a2b`
- Qualification runner state: `DISABLED`
- Qualification vector: not generated
- Outcome exposure: 0/50

The accepted ten-game V2 block validated the corrected runner and production-candidate pilots. Its 6-4
result is calibration evidence only. It is not pooled into the future qualification block, and its ten
seeds are permanently unavailable for replay or reassignment.

The pre-V2 replacement freeze remains a separate historical identity and is not an input to this gate.
This readiness change neither executes nor changes the disposition of that vector.

## Future frozen design

The qualification block will contain exactly 50 fresh, unique, nonzero signed 64-bit seeds with no
overlap against the complete Pest registry. Assignment is fixed before outcome exposure:

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

No freeze identity is attached, and the compiled runner state is `DISABLED`. Unit tests use no seed
vector and cannot activate execution. The next gate is the manual, artifact-only operation specified
in [the qualification seed-freeze gate](v2-qualification-seed-freeze-gate.md), after its implementation
and every required check are green.
