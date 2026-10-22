# Manual continuation: repaired Assay, two further ordinary-build blockers

Status: **Assay repaired and verified; ordinary build FAILED; copy-entry HELD.**

This continuation found an existing active run and let it finish without cancellation, restart, duplicate builds, or modification of its source. It independently received the final artifact, verified its API size/SHA-256, all 443 internal manifest entries and ZIP CRCs, and recounted all 435 JUnit reports. No source, test assertion, workflow, frozen deck, protocol, seed allocation, or execution reservation was changed.

## Exact evidence

| Item | Identity |
|---|---|
| Repaired source | `6cf30d14d4be570bc1786e48079549cf850fa16a` |
| Validation-only child | `0317a91b0bdbbebf386d1d035936e1e26c67e627` |
| Original run / attempt | [39787740747 / 1](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39787740747) |
| Ordinary build job | `119857846056` |
| Original final artifact | `12430304494`, 591887 bytes |
| Original ZIP SHA-256 | `d443bf105e499a58fc9cd94ee03da0faccdb793c58ef7e98ac2e6052e4d06ee7` |
| Prior Assay receiving checkpoint | `e295f6bdfbdeb9828bf251d1725976f9e1660e79` |
| Prior whole-build failure / saved draft | `f57d961780ab196f2c6d74269a9c9f9d752de80e` |
| Pinned main used for identity comparison | `37a603617a03dfd2d81b39dae004f7f336a70dfc` |

The build step ran from 2026-10-22 13:37:49 to 13:59:55 UTC (06:37:49–06:59:55 America/Los_Angeles). Gradle reported `BUILD FAILED in 22m 5s`, 106 actionable tasks executed, exit 1. Its collector correctly failed after preserving the original failure reports. The exact command was:

```sh
scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
```

This requests the ordinary build graph with the verification skill's routine AI filter; it is not a claim about an unfiltered build. The wrapper reported unavailable `shlock` and ran unlocked; no semaphore protection is claimed. The runner's tracked working tree was clean.

## Actual test results

| Module | Passed | Failed | Errors | Skipped | Recorded cases |
|---|---:|---:|---:|---:|---:|
| ai | 741 | 0 | 0 | 1 | 742 |
| game-server | 557 | 0 | 0 | 4 | 561 |
| gym | 488 | 1 | 0 | 35 | 524 |
| gym-server | 11 | 0 | 0 | 0 | 11 |
| gym-trainer | 23 | 0 | 0 | 0 | 23 |
| mtg-sdk | 527 | 0 | 0 | 0 | 527 |
| mtg-search | 163 | 0 | 0 | 0 | 163 |
| mtg-sets | 378 | 1 | 0 | 0 | 379 |
| mtgish-tooling | 144 | 0 | 0 | 3 | 147 |
| oracle-assay | 568 | 0 | 0 | 0 | 568 |
| **Total** | **3600** | **2** | **0** | **43** | **3645** |

The original artifact contains no rules-engine or card-era test XML. Their earlier successful suites must not be attributed to this run. All skipped cases are retained by name in `INDEPENDENT_AUDIT.json` and the original SUMMARY.json; none are counted as passes. Opt-in official execution runners and seed generation remained skipped. Unit tests include synthetic/component gameplay, which is not admitted competitive evidence.

The separate Assay job also passed 568/568. The old-production control had 31 cases: 24 passing and exactly seven expected failures, comprising the four original failures and three new positive regressions; no errors/skips. Both prior archive hashes and complete manifests were rechecked. The full touchstone had zero print mismatches or ambiguities. Its separate differential still reports 51 divergences, preserved and classified in the preceding checkpoint; a green differential exit does not mean zero divergences.

## Blocker 1: facade boundary

`FacadeBoundaryTest` rejects three direct `CompositeEffect` constructors: one in Arcane Denial (line 36) and two in Pact of Negation (lines 34 and 42). The test and original assertions are unchanged.

Arcane Denial was adopted to main by `e3e27b6a68a3fdaa1fd3ba9c52a0891f2a84f496` (Izzet Position-1 counter pair). Its current source is byte-identical to pinned main. Pact of Negation entered the Manual candidate lineage at `18d7cb6ca18adac50c0ee8ecc3c0bfce9f948f89`, importing its previously GREEN definition. That earlier result did not establish this source-level facade gate.

Concrete proposed correction, **not applied**: replace only these three calls with the existing `Effects.Composite(List<Effect>)` facade and remove the unused raw import. The facade delegates to the same `CompositeEffect` with the same defaults, so the intended compiled card trees remain unchanged. Verify the original facade test, exact snapshot equality without reblessing, and both cards' existing scenarios. Do not modify assertions, reorder effects, or change card semantics.

## Blocker 2: existing Monster Tron runner surface

`PestControlTierOneMonsterTronRunnerSurfacePreflightTest` rejects `.github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml`. The guard permits exactly the two older reviewed workflow path/hash pairs; the existing R1 file contains official-runner references outside that list. This is a cross-project execution safeguard, not an Assay failure.

The guard, its test, and the rejected workflow are byte-identical to pinned main. The immutable R1 execution source `ed35c3035d41a740aac1657708b2023b624cfc47` also carries the old guard; there is no already-correct guard at that source to adopt blindly. The R1 workflow has a branch/attempt-1 dispatch gate, exact source/rules/input hashes, durable one-shot claim, and a date admission check ending before 2026-10-12. None of those controls were changed, dispatched, or bypassed.

Proposed scope requiring clearance: reconcile this specific registered workflow with the runner-surface audit while retaining exact path/byte checks, mutation/relocation rejection, sealed public-method checks, zero game/seed execution in tests, and all existing activation/admission locks. Whether to recognize the exact registered workflow or retire it requires review of its authority and lifecycle. Do not add a broad allowlist, remove the original assertion, exclude the file from inventory, relax the expired admission boundary, or execute official games merely to make this build green.

## Authority and next dependency

Root `AGENTS.md` and `.agents/skills/verify/SKILL.md` require stopping before repairs to unrelated pre-existing failures until the user confirms. The existing explicit exception authorizes the four Oracle Assay failures. It does not explicitly authorize changing the pre-existing Arcane Denial implementation or reconciling Monster Tron's official execution safeguard. These two additional failures are fully diagnosed and preserved for a scoped approval. No source correction has been applied in this checkpoint.

After clearance: make only the approved corrections, preserve all original assertions, verify targeted gates and immutable card trees, then rerun the ordinary whole-project gate with complete artifacts. Resume copy-entry only after that build succeeds and source/tests are qualified.

The held copy-entry archive was reassembled in memory in documented order. Its SHA-256 `21be517dafadc4600cdbae728a3445486d9953a53ddc483b5b141189093299bf` and all ten manifest-file hashes matched. It was not applied, compiled, tested, or adopted. Carrier support still does not qualify pre-entry publication, simultaneous-entry eligibility, copied as-enters choices, or Phyrexian Metamorph itself.

Manual remains 92/100 registered, opponents 392/700, combined 484/800; the earlier focused mission bank remains 214/214. Capability games remain 0/36, primary games 0/864, Precision 10 used / 2 reserved. Frozen v0.7 remains blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`. No deck changes or new official seeds/games occurred. The mission is incomplete.

## Reproduce the receiving audit

Run `python3 verify.py` from any directory. It reconstructs the original archive in memory from ordered parts, verifies hashes/size/CRC/manifests, recounts all JUnit reports, checks source/deck pins and failure identities, and confirms failure rather than turning it into a passing build. It executes no builds or games. `SOURCE_IDENTITY.json` records separately inspected Git blob identities; source identity is not a separate baseline rerun.
