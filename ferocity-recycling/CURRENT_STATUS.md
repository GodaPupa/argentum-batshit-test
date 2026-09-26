# Ferocity Recycling — current status

Record cut: **2026-09-26 15:16 UTC**. Research continues; no research stopping rule has been reached. This **M1 reviewed-source and fixed-evidence checkpoint** descends from public `d50e66dc12a58e1276b56bdf9ce78daecda51e1a`. It is an isolated review-branch publication. Gameplay admission and shared-engine acceptance remain pending.

## Research answers and actual game counts

**A, strongest Ferocity configuration: unresolved. B, Ferocity improvement: unresolved. C, competitiveness: unresolved.** No configuration has advanced and no supported final 60/15 exists. The first operational cell remains A3-F4/A3-N0 versus MisterTwin Red; this scheduling choice does not rank the families.

| Stage | Actual randomized games or matches | Frozen ceiling |
|---|---:|---:|
| Initial development D2 | 0 games | 720 games |
| One refinement D3 | 0 games | 240 games |
| Frozen evaluation E | 0 games | 3,000 games |
| Independent confirmation C | 0 games | 3,000 games |
| Sideboard development | 0 best-of-three matches | 300 matches |
| Gated postboard evaluation | 0 best-of-three matches | 1,500 matches |

No production admission manifest or research allocation exists. All nine Ferocity and nine no-Ferocity prototype sixties remain unchanged at manifest `ef75f5cec859dcc9f765de0feacb7cb7696037f4c7035d4b68b6bdfccee1c87d`. Families, comparative budgets, thresholds and stopping rules remain frozen.

Ferocity remains verified-text **prerelease research**, with archived release date 2026-10-02 and separately recorded platform status. No simulation is sanctioned tournament evidence. Deadly Dispute is excluded. Baleful Strix eligibility uses its actual common Zeta Set #88 printing and separate official platform admission. See [rules audit](RULES_LEGALITY_AUDIT.md), [benchmark audit](BENCHMARK_AUDIT.md), [synergy map](SYNERGY_MAP.md) and [complete prototype construction](DECK_DEVELOPMENT.md).

## Exact M1 qualification and remaining gates

M1 source freeze is `41283f68392d7792131eff26877a67a5905ca254dbdeee5529da7f8c17094199`: 214 owned source/tool/protocol inputs and 24,161 compiled consumer inputs. Complete source map `72c33adeb5015e6ad0cdf1da43785f89c3953a5cfb1d8c066936fd715e6e4a84` includes the separately pinned tool/protocol closure. The build checkout is dirty by design; its old Git HEAD is not asserted to identify these source bytes. Before/after source guards and actual runtime-classpath observations are retained for every completed JVM batch.

| Fresh exact-source batch | Actual cases | Passed | Failed / error / skipped | Scope |
|---|---:|---:|---|---|
| Gym36 | 213 | 213 | 0 / 0 / 0 | 182 primary project cases plus 31 existing observation/submission regressions |
| Engine37 | 234 | 234 | 0 / 0 / 0 | Selected rules, priority, LKI, Aura, damage, trigger and multiplayer cases |
| Card eras38 | 80 | 80 | 0 / 0 / 0 | 21 selected card classes across seven era modules |
| **Executed JVM total** | **527** | **527** | **0 / 0 / 0** | **496 of the 508 primary cases, plus 31 separate regressions** |
| Server on M1 | 0 | 0 | — | Twelve original cases remain pending |
| Browser on M1 | 0 | 0 | — | Four unchanged actual-UI cases remain pending |

The complete [closed JVM evidence manifest](evidence/build/m1-local527-publication-02/publication-manifest.json) retains all raw XML, logs, classpath observations, maps and source histories. Root independently verified all three unchanged before/after maps as exact subsets of the frozen full map.

Receipt SHA-256 identities: gym36 `f60f5cc45c27e2c7bb7a8a14cedebdd6a7317417da401ae116f730b6928d956f`; engine37 `0de8cb4b184f77303ee2d49a32684dfe5251b2fb331b4587ddf86865930911b2`; eras38 `0cfa30995d555da85ac13eac5dc09bfacc7436037df07004a96484a755efcbf7`.

Separately, watchdog35 passed **13/13 process assertions**: the nine retained controls and four new resource cases. Its exact [closed archive and manifest](evidence/build/m1-watchdog35-publication/publication-manifest.json) preserve the hard-limit prefix and failure fixtures. Ten Python inventory/validator synthetic guards also passed. These are not extra JVM cases or research games. The finite near-limit codec calibration remains unexecuted and is required before production use.

The exact-head CI selection now contains **63 classes / 508 cases** in ten independent module jobs. It adds only the resource2 and priority4 classes, and updates the two explicitly revised test-source hashes. The validator's code outside its class-routing table is unchanged. The separate browser source04 changes only an invalid workflow environment placement; all four browser cases, zero-retry rule, caps and checkout identity remain unchanged.

## Changes and version boundaries

**Red policy v0.2 consumes its first of two permitted D1 revisions.** Red case10 now states the intended used-land-drop precondition while retaining the original mana and post-cast assertions. Case18 identifies the actual canonical `token:Fish` / `Fish Token`, checks its owner, controller, color, type, tapped state and size, and continues through an actual Red pass to a legal Artifact-pilot proposal. The production guard accepts only that exact qualified token. Original failures and source are retained; all 24 successor cases now pass. Artifact policy v0.1, all tactical tails and all decklists remain unchanged.

The gym's public `hasPriority` now uses the engine's actual priority query, rather than equating it with the declaration baton. Four actual fixed scenarios and 31 existing regressions pass. The shared-team fixture retains the intermediate teammate pass before opposing-team priority. Engine routing and broader legacy client semantics are not claimed changed.

Resource source05/06 enforces a hard inherited **128 MiB per-file limit**, **768 MiB free-space preflight**, strict replay and explicit unresolved outcomes for the original 16-game cell. Existing 300-second, 6,000-action and 150-complete-turn caps remain. No capped or unsupported result becomes a draw, gets rerolled or is removed from its allocation. The retained 865,291-byte journal observation covered only one action; it is not a full-game bound. [Prospective resource amendment](protocols/FIRST_CELL_RESOURCE_AMENDMENT.md).

The offline card exporter, exact getter table, wrapper and admission assembler are now published as a complete closure. Current helper pins incorporate the resource boundary. They have not yet generated a production bundle, admission document or seed reservation. The six effective protocol files remain separately pinned; no template or historical source freeze substitutes for current executable identities.

## Previous failures and actual remote CI

The complete original Q3 local selection remains preserved: 502 fresh cases, 488 passes, two Red assertion failures and twelve local MockK instrumentation failures. Successor passes do not erase them. The original server failure occurred before behavioral assertions. A separately reviewed invocation-only startup-agent proposal uses the exact already-resolved Byte Buddy dependency; its twelve-case execution is still pending. It does not change tests, dependencies, global Java configuration or gameplay JVMs.

Q3 [scoped CI](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36249219082) completed on exact head `d50e66dc`: nine module jobs succeeded and the old-source gym job failed. Those older passes are not accepted as M1 qualification because the complete compiled map includes the changed observation source.

Q3 [general CI](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36249219090) failed all eight backend test groups; frontend succeeded. Its actual checkout was PR merge `7589ef8436442c8e765e60dce50c498ed7d633ff`, incorporating base `7052de0b64d5d01c3a31465d19157d95089b7d40`. The recorded exact comparison adds only a FRA workflow and two canonical FRA source files to Q3; engine and server sources are unchanged. Broad engine, server and card failures still require source-level classification and applicable repair or qualification. This checkpoint does not call the shared repository green or dismiss those failures as unrelated.

The first Q3 [browser run](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36249215930) was rejected with **zero jobs and zero browser initializations**. Its published job-level environment used `runner.temp`, which GitHub's context table does not allow there. Source04 writes the same isolated paths into `GITHUB_ENV` from a step. The unavailable original validator annotation is not invented; the observed failure and independently verified schema defect are distinguished in the retained audit.

Earlier E2 failures, unrepaired baseline25 failures, Assay26 results, all source before-images and canceled-before-build33 are preserved in history and evidence. No failed run is silently replaced. Old whole-era passes remain tied to their exact source; they are not whole-M1 acceptance.

## Publication and next authorized execution

Draft [PR174](https://github.com/GodaPupa/argentum-batshit-test/pull/174) remains the isolated review surface, on `ferocity-recycling/qualification-review`. The parent checkpoint is public `d50e66dc`; this publication's exact commit/tree receipt is recorded after the non-force update. Source publication does not imply approval, merge or gameplay admission.

This checkpoint publishes only reviewed project source, narrow shared changes, exact source/audit archives, closed evidence and current records. Credentials, private data, unrelated experiment files and concealed future evaluation material are checked before publication. No future evaluation or confirmation seeds exist. Pest Control, Izzet Science, Manual Transmission, Industrial Waste and Sphinx's Approach remain untouched; none of their gameplay allocations is used.

**Next authorized actions:** publish M1 and collect its exact server/browser/CI results; classify the broad shared-gate failures; finish finite resource calibration; satisfy all applicable admission requirements; export and verify the exact offline bundle and source/dependency/policy/protocol closure; then allocate and execute the original 16 D2 games with complete journals and independent replay. Continue the other declared families and exact benchmark packages within the frozen budget. Whole-pool support remains incomplete, including initiative, cycling/ninjutsu, relevant tokens, Troll's blocking restriction, DFC/Craft and Gardens' ordered Spinning Darkness cost. Opponent lists are not weakened to bypass missing support.

A technical blocker gives no deck verdict. `FINAL_CONCLUSION.md`, a strongest supported 60/15 and competitive claims remain unjustified. Prior complete records are preserved in [history/checkpoint-d50e66dc](history/checkpoint-d50e66dc/).
