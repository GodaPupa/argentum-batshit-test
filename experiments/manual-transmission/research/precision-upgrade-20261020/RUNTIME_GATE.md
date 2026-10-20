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

## R1 execution delta (2026-10-20, after first CI failure)

- Initial source/compiled workflow `39468179923` on `b59889e90df7d5e87813a5ef94cb5becfaae2895` successfully reached Kotlin compilation but produced three `NoSuchFileException` test failures. Cause: workflow `MANUAL_R4_ROOT: .` was relative to the Gradle test worker's module directory. This is harness configuration, **not** a card or deck behavioral failure. No actions/games were run.
- The corrected workflow explicitly sets `MANUAL_R4_ROOT` to the absolute GitHub workspace, fetches sufficient R22 history, and keeps source identity checks.
- The catalog/initializer tests are separated: `ManualPrecisionR1PreflightTest` validates CONTROL + A; `ManualPrecisionR1CandidateBPreflightTest` fails closed for B until missing cards are implemented. The isolated three-arm gameplay harness is still opt-in, and **no gameplay workflow is present or dispatched**.
- Nine new **unqualified** single-card scenario tests were authored for Mana Confluence, City of Brass, Breeding Pool, Steam Vents, Stomping Ground, Misty Rainforest, Wooded Foothills, Bloom Tender, and Worldly Tutor. Passing them is required; their publication alone proves nothing about behavior. No test for missing Fierce Guardianship is claimed.
- Corrected source/rules preflight workflow committed as `79bded6ba609e3cde0d6deb2492aaf0eaaa93607`, GitHub Actions run `39469732819` (status in progress at publication). Continue by auditing its **original** logs/artifacts before changing any code, and fix only demonstrated defects. Challenger B and gameplay remain blocked on exact rules support, independent of source-count checks.

## Optional fallback source (not an accepted gameplay arm)

`CHALLENGER-C.supported-core.decklist.txt` preserves all seven A lands and makes just two additional in-place swaps: `Farhaven Elf → Bloom Tender` and `Lifecrafter’s Bestiary → Worldly Tutor`. It retains `Rapid Hybridization` because `Fierce Guardianship` remains unimplemented in the pinned compiled catalog. This is a **new, separately labeled 100-card opt-in candidate**, not a silent change to B and not runnable under the R1 harness until a separately validated C adapter/initializer is added. It has **zero tests or gameplay** at publication. Do not replace the authorized R1 arm B with C in artifacts or results.
