# Precision Upgrade R1 — Runtime gates and unresolved card coverage

This branch composes the source-bound three-arm deck experiment onto accepted R22 runtime source `10675d8dc3133294c035606f656f9e4d12d50939`.

## Authority

- Prior control: `experiments/manual-transmission/control/v0.7.decklist.txt`, unchanged blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`.
- Imported CONTROL / A / B blobs: `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`, `7984a2e87d8ddbe023d45620414d25a61691a040`, `fc217e1a282eec67cb0abf46b824fc1cd9e73c66`.
- Runtime preflight: `ai/src/test/kotlin/com/wingedsheep/ai/engine/ManualPrecisionR1PreflightTest.kt`.
- New **opt-in** game harness: `ai/src/test/kotlin/com/wingedsheep/ai/engine/ManualPrecisionR1GameplayHarnessTest.kt`. Exact preset Stage 1 gameplay seeds are `202610202111` and `202610202112`, not yet used.
- Source-only workflow: `.github/workflows/manual-precision-r1-preflight-20261020.yml`. It has no ActionProcessor/gameplay step.

## Incomplete required gates

Compiled source-path audit on the pinned R22 ancestor found definitions for all seven replacement lands and for Bloom Tender and Worldly Tutor. It did **not** find a compiled `FierceGuardianship.kt` card definition in the era modules or core. A source-path audit is not a compiled registry audit and does not establish behavior.

No exact-name `{CardName}ScenarioTest.kt` was found for any of the ten added cards in the source tree (other tests may cover shared mechanics, but cannot be assumed to prove these exact interactions). In particular, the original `WorldlyTutor.kt` is headed as a generated predictive/approximate implementation that demands a scenario test before reliance.

Required before any four-player Precision game:

1. Complete the source-only compile/registry/initializer preflight and retain its logs and hashes. The new test deliberately **fails closed** for an unregistered card, including Fierce Guardianship.
2. If Fierce Guardianship is missing, implement it under the repository `add-card` / `add-feature` rules on a separately reviewed opt-in source branch, with *real* commander-control conditional free-casting (not a legendary-creature proxy or unconditional free cost). Compose it only after its targeted scenario and underlying engine regression checks pass.
3. Verify actual functionality of all seven lands, Bloom Tender, Worldly Tutor and Fierce Guardianship with targeted ActionProcessor/scenario evidence; do not treat their existence in a catalog as passing rules support. Preserve the existing R5 Pact, Ballista, Statue, combat and Animar deployment regressions.
4. Prove CONTROL/A/B four-seat opening-state parity for both prospective seeds. Maintain exact seat ID and nonchanged slot alignment, and refuse replaced/unknown seeds.
5. Only after all above pass, execute the predeclared Stage 1 six-game budget (CONTROL/A/B for each of two seeds), with per-arm 64-action smoke before full game, bounded game censoring and durable artifacts. Stage 2 remains reserved, with three new seeds to predeclare only after Stage 1 evidence is reviewed.

## Experiment integrity

Neither the newly published harness nor a CI run constitutes gameplay or qualification. Never count source checks as real card behavior, a nonterminal censor as a loss, or a candidate as adopted. No promotion into frozen v0.7 or `AiProfile.CURRENT` is authorized.
