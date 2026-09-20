# Gate 3A: telemetry validation v1

Status: harness validation only; no experimental seeds and no promotion evidence.

## Purpose

Before spending a paired goldfish namespace, prove that the shared test arena can expose the
accepted engine transition rather than an AI proposal and can run with or without London
mulligans. This gate changes no production path and preserves the arena's historical
`skipMulligans = true` default.

## Fail-closed checks

1. Every accepted action callback includes its exact before-state, after-state, and emitted event
   batch.
2. A rejected AI proposal is never mislabeled as accepted; a recovered safe fallback is the
   accepted action reported.
3. The test observer sees `hasKept = true` under the default-compatible skipped-mulligan mode and
   `hasKept = false` when real mulligans are enabled.
4. The focused fixture suite must execute (not skip) and pass in CI.

Only a clean run authorizes registration and execution of a fresh, small paired goldfish sample.
