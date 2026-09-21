# Face Value EDH — R2-A Runner Contract

Status: QUALIFICATION CANDIDATE
Frozen deck SHA-256: 9a29093306b92cab815112c61b2401a1b5e48ac418b87d50ee263b4fd2b402ca

## Determinism
Given identical visible game state and identical policy version, legal-action enumeration and action selection MUST be identical.

## Information boundary
The runner may use only information available to the acting player at the decision point. Hidden zones, future draws, unrevealed opponent information, and future random events are forbidden inputs.

## Totality / fail-closed
Every legal state admitted by the qualification surface must return either:
1. a legal deterministic action; or
2. an explicit UNSUPPORTED_STATE termination.

Silent approximation, arbitrary fallback, and illegal-action substitution are forbidden.

## Required decision surfaces
- mulligan/keep under the frozen policy;
- land/resource sequencing;
- ordinary creature/noncreature casting;
- target selection and target legality;
- stack response and priority passing;
- triggered/activated ability choices;
- commander-zone/recast-tax handling;
- play with Animar unavailable;
- protection/counter/removal timing;
- Food Chain pressure decisions;
- combat declarations where admitted by the engine.

## Provenance
Every decision trace must record state identifier, legal actions, selected action, policy version, and whether the decision was supported.

## Change control
Any semantic runner/pilot change after qualification creates a new runner identity. After R2-E seed freeze, such a change blocks execution under the old assignment.

## Admission
R2-A passes only when this contract and the R2 protocol are present and the prior R1 A/B/C evidence remains green. Passing R2-A does not authorize official seeds or games.
