# Face Value EDH — R2 Reconstructed-v0.11 Qualification Protocol

Status: DRAFT / SYNTHETIC READINESS ONLY
Date: 2026-09-21

## Frozen identity
Canonical reconstructed SHA-256:
9a29093306b92cab815112c61b2401a1b5e48ac418b87d50ee263b4fd2b402ca

Historical artifact SHA-256 is provenance only and MUST NOT be used as the R2 identity.

## Research question
Can the reconstructed v0.11 Animar deck demonstrate robust executable functionality under interactive Commander pressure, including commander denial and Food Chain pressure, without changing deck identity or adapting policy to outcomes?

## R2 gates
R2-A — protocol and runner contract.
R2-B — synthetic commander-denial and stack/priority readiness.
R2-C — deterministic pilot-totality/cross-regression.
R2-D — opponent-policy qualification.
R2-E — fresh seed-vector freeze.
R2-F — official execution.

## Experimental safeguards
- No R2 official seed exists before R2-E.
- No historical official seed may be reused, inspected, inferred, or derived.
- Deck identity remains frozen at reconstructed SHA.
- Unsupported actions fail closed.
- Pilot changes after R2-E require a new runner/protocol identity and invalidate unexecuted assignments; exposed outcomes can never be erased.
- No rerolls or replacement games after outcome exposure except under a predeclared invalid-game rule unrelated to outcome.
- Synthetic/development fixtures are never counted as competitive results.

## Initial official design
Target: 12 games only after readiness qualification.
The opponent and seat/play-draw allocation MUST be frozen before seed generation.
No official matchup identity is selected at R2-A; opponent selection requires R2-D evidence.

## Current ledger
Historical experiment: 0/12 executed; untouched.
R2 official games: 0/12.
R2 official seeds: none.
R2 outcomes exposed: 0.
