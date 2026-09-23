# Pest Control Tier-1 coverage — Mono-Blue Terror production-AI compatibility

## Purpose

The card engine, frozen assignments, durable evidence journal and real-engine initialization path are
already green. This gate validates the remaining technical pilot surface before any official seed is
allowed into gameplay.

Four fixed nonofficial seeds are run through the exact four seat/start cells using
`AiProfile.PRODUCTION_CANDIDATE_EXPIRING`, the same production AI profile used by the accepted
Grixis driver. London mulligans and all later priority/decision actions use the real engine and
exact-one submission API.

The gate does not expose winners and its outcomes are not matchup evidence. It asks only whether the
production policy can complete all four synthetic games without rejected actions, wedges or a cell
where the Terror pilot never acts during gameplay.

## Acceptance target

- Status: `PRODUCTION_AI_COMPATIBILITY_REHEARSED_EXECUTION_NOT_AUTHORIZED`
- Proof SHA-256: `38741508238b542304a18e88b4976c83eb87a5c27801424f25e940de16d8b227`
- AI profile: `PRODUCTION_CANDIDATE_EXPIRING`
- Synthetic games: `4`
- Clean terminals: `4/4`
- Terror acted during gameplay: `4/4`
- Rejected actions: `0`
- Wedges: `0`
- Official seed values exposed: `0`
- Official seeds consumed: `0`
- Official games initialized: `0/4`
- Outcome exposure: `0/4`
- Execution authorized: `false`

This qualifies technical compatibility only; it does not prove that the generic AI replicates every
choice made by Serpico_CC or establish matchup strength. Any official smoke still requires a separate
execution authorization gate.
