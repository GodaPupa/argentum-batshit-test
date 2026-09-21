# Face Value EDH — R1 Reconstruction Ledger

Status: DEVELOPMENT / FAIL-CLOSED
Created: 2026-09-21

## Purpose
Reconstruct the missing Face Value EDH qualification harness without consuming or deriving information from the frozen official experiment.

## Preserved experimental identity
- Project: Face Value EDH / Never Pay Retail
- Accepted identity label: v0.11 — Hope-Ender Coatl
- Previously recorded identity prefix: 1548264c…9b026
- Commander context: Animar
- Deck changes authorized during R1: none
- Official games executed during reconstruction: 0/12
- Official outcomes exposed during reconstruction: 0
- Official seeds consumed during reconstruction: 0

The abbreviated historical identity above is provenance only. R1 MUST NOT claim byte-for-byte deck reconstruction until the complete frozen list/hash is recovered and independently verified.

## R1 mandatory surfaces
1. Ordinary cast sequencing
2. Targeted interaction and target legality
3. Stack / priority / triggered and activated ability handling
4. Commander-unavailable and commander-recast states
5. Food Chain pressure / interaction states

## Qualification contract
R1 is synthetic methodology development only.

The reconstructed runner MUST:
- be deterministic for identical synthetic inputs;
- fail closed on unsupported actions or states;
- never silently approximate an unsupported rule/action;
- expose legal-action and decision provenance sufficient for audit;
- distinguish runner incompleteness from observed frozen-deck behavior;
- use no official seed, position, outcome, or information derived from the official vector.

R1 MUST NOT:
- alter the frozen deck identity;
- optimize card choices;
- retroactively alter pilot policy in response to official outcomes;
- execute an official game;
- claim equivalence to the historical runner until deterministic equivalence evidence exists.

## Admission gates
R1-A: fixture schema and fail-closed validator.
R1-B: deterministic synthetic fixtures for all five mandatory surfaces.
R1-C: repeatability replay and cross-regression.
R1-D: identity recovery: complete frozen v0.11 deck identity/hash must be recovered and verified.
R1-E: qualification decision.

Official execution remains BLOCKED until every gate above passes.

## Current decision
R1 reconstruction authorized. Official experiment remains untouched.
