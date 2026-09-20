# Forge profile v1 disposition — 2026-09-20

## Decision

`FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V1` is rejected as a complete-gauntlet qualification runner. It demonstrated integrated capability for six of eight archetypes, but it did not clear Mono-Red Rally sequencing exposure or Mono-Blue Faeries runtime integrity. No official qualification seeds are authorized by this gate.

The frozen **Face Value — Temur Chrysalis E — Final Forge 75** remains the permanent control and was not changed.

## Composite gate evidence

- Workflow run: `35534978465`
- Artifact: `10613251188`
- Artifact digest: `sha256:2c39d8ac929f273788ad92499b833f5f8a141addc3cd438827dc75e0e4dbb0ac`
- Classification: diagnostic only; all 46 seeds `720107`–`720152` are permanently retired.

| Archetype | Gate result | Diagnostic observation |
|---|---|---|
| Mono-Red Madness | demonstrated | Faithless Looting 4; Highway Robbery 7 |
| Mono-Blue Terror | demonstrated | Lórien Revealed 2 uses |
| Grixis Affinity | demonstrated | Shaman 12 casts/4 activations; Bargain 8 |
| Monster Tron | demonstrated | Prophetic Prism 2 |
| Jund Wildfire | demonstrated | Shaman 4 casts/4 activations |
| Elves | demonstrated | Ranger 8 casts/25 activations; Winding Way 2 |
| Mono-Red Rally | not demonstrated | 0 joint Rally/Bushwhacker turns in 12 games |
| Mono-Blue Faeries | rejected for simulator use | 1 runtime-flagged game in 8 |

## Faeries quarantine

The read-only artifact audit (`35535973211`, job `106144947809`) isolated seed `720149`: Forge stopped a slow game at the time limit and emitted contradictory terminal bookkeeping in which both decks were reported as winners. This independently repeats the material defect seen in the rejected Stage 1 Faeries block. It is therefore a structural simulator boundary, not a candidate for rerolling or timeout inflation.

Mono-Blue Faeries is classified `HUMAN_ONLY_REQUIRED` for coverage evidence unless a separately versioned engine correction is later justified and independently validated. The contaminated records remain preserved and quarantined.

## Rally next gate

Natural actual-list diagnostics have provided insufficient exposure to the same-turn Rally/Bushwhacker decision. A nonlegal forced-exposure fixture may test sequencing only. Its seeds and outcomes must be permanently diagnostic and may never be treated as matchup evidence.

## Research consequences

- No matchup win-rate inference is permitted from any diagnostic run named here.
- No challenger branch is justified.
- No postboard block is authorized yet.
- Any eventual Tier 1 qualification report must separate simulator-supported coverage from human-only Faeries evidence.
