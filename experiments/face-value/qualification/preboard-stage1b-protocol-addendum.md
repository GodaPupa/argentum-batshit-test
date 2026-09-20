# Preboard Stage 1B hybrid-scope addendum

Status at freeze: profile v2 capability accepted for seven simulator matchups; Mono-Blue Faeries classified human-only; no Stage 1B seeds or outcomes existed.

## Purpose

Stage 1B replaces no evidence. It is a fresh qualification block following the permanent rejection of every original Stage 1 seed. Original Stage 1 artifacts, results, and seeds remain preserved, quarantined, and ineligible.

## Design

- Seven simulator-eligible matchups.
- 32 games per matchup: 16 Face Value on the play and 16 on the draw.
- 224 games total, each with a unique fresh positive signed 64-bit seed.
- All assignments frozen before outcome exposure.
- Mono-Blue Faeries is not silently omitted: it is explicitly routed to human-only evidence and prevents any full-gauntlet simulator aggregate.
- Runtime uses the pinned Forge commit plus immutable profile v1 and profile v2 patches.
- The frozen control and representative lists remain unchanged. The Terror filename copy may receive the already-documented Unicode spelling normalization from `Lorien Revealed` to canonical `Lórien Revealed` at runtime only.

## Acceptance

Each matchup is an independent block. Play/draw telemetry is preserved. Any material rules, AI, timeout, parsing, target-selection, identity, or terminal-bookkeeping defect quarantines the complete affected matchup block. Seeds are permanently retired regardless of disposition.

Automated cleanliness is only the first audit. A matchup cannot be accepted until gameplay-quality review samples opening hands, early turns, key decision traces, and all anomalous or long games. Outcome estimates remain sealed from structural tuning until that review is complete.

Stage 1B cannot authorize a challenger by itself and cannot establish Tier 1 status. It can reject coverage, identify a signal for independently predeclared confirmation, or support later postboard construction from the frozen 15.
