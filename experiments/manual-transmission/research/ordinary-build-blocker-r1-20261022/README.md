# Manual Transmission: whole-build blocker checkpoint, 2026-10-22

Status: **HELD at an unrelated, unchanged Oracle Assay failure. Mission incomplete.**

No deck edits, official seeds, official competitive games, or Precision games occurred. Capability remains 0/36; primary remains 0/864; Precision remains 10 used / 2 reserved. Main and other execution branches were not changed. This checkpoint is evidence preservation, not a passing build, an adopted copy-entry implementation, or competitive evidence.

## Exact lineage and last verified mission state

| Item | Exact reference |
|---|---|
| Main inspected | `37a603617a03dfd2d81b39dae004f7f336a70dfc` |
| Two-card source | `421be62c1f5f540d937ab5e90ac875b8b51737d7` |
| Successful two-card validation | `21c77e717980345fd7d007183ddaf8c8c67d2643` |
| Successful receiving run | [39723738168, attempt 1](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39723738168) |
| Complete two-card original-artifact checkpoint | `760eaa419dd10faccefbe61c9e46a659fb99d864` |
| Ordinary-build source with received goldens | `a9ea02595bcdc681778b1e003e1b79d51737aeb2` |
| Ordinary-build validation | `623db9bb9ce503152947ebee2f037111df419f4a` |
| Failed ordinary run | [39761794103, attempt 1](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39761794103) |
| Failed job | `119767491442` |
| Untested copy-entry preregistration | `2a2cd78fa163a0242bb8a468a47aea1694a6bb6d` |

The verified mission remains **214/214 focused source-bound tests, Manual 92/100, all eight rosters 484/800** (opponents 392/700). Two-card receiving also passed 1085/1085 tests for 2017–2022, 2072/2072 for 2024, and 343/343 snapshot/lint tests. These overlap the focused cases; they are not additional unique competitive observations. Full prior results and all 13 original archives are in the linked receiving checkpoint. No individual whole-card Assay qualification is claimed for Coatl or Hermit.

The new source commits the 23 received golden files covering exactly 30 mission additions, with no existing card-tree modifications or removals. It does not add another registered card.

## Whole-project build result

The source-bound command was:

```sh
scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
```

This requests the ordinary whole-project build graph with the verify skill's AI `*Test` filter, not an unfiltered default-build claim. The wrapper reported unavailable `shlock` and ran unlocked; no semaphore claim is made. No opt-in arena, benchmark, replay-fuzzer, official seed, or official game command was requested.

Build exited 1 after 6m46s at `:oracle-assay:test`. The receiving collector also correctly failed its no-failures assertion. The original ZIP, logs, all 110 JUnit XML files, source pins, source-receiving checks, internal hashes and unmodified-worktree report are preserved byte-for-byte.

| Executed XML module | Passed | Failed | Errors | Skipped | Rows |
|---|---:|---:|---:|---:|---:|
| mtg-sdk | 527 | 0 | 0 | 0 | 527 |
| mtg-search | 163 | 0 | 0 | 0 | 163 |
| mtgish-tooling | 144 | 0 | 0 | 3 | 147 |
| oracle-assay | 558 | 4 | 0 | 0 | 562 |
| Total | 1392 | 4 | 0 | 3 | 1399 |

The three skips are named in `SUMMARY.json`; they are not counted as passes. This run stopped before engine, card-era, server and AI test execution completed. Their earlier passing results must not be attributed to this source or run. Full-game readiness remains unproved.

## Four unrelated failures and required pause

All four cases parse their Oracle text successfully, then fail the canonical printer round-trip because `Grammar.abilityLine.printLine` returns null:

1. `ManaValuesTest`: `the searches and returns that named this band read as whole lines`.
2. `ManaValuesTest`: `the pronoun form of the search is a variant, not a second reading`.
3. `SubtypesTest`: `a basic land type in card position is the land type noun, and only that`.
4. `SubtypesTest`: `a non-land subtype in card position still reads ungated`.

The failing module, its only production project dependency (`mtg-sdk`), tests and build configuration are identical to pinned main. Exact Git object identities are in `source-identity.json`. There was no baseline rerun; source identity must not be described as baseline execution evidence. These are local grammar round-trip assertions, not current-Scryfall lookup failures.

Root `AGENTS.md` (blob `27cf5a95edae6b354762164f8a9c04ca1fade989`) requires reporting and stopping when work we did not change breaks the build, until the user confirms it is safe to continue. `.agents/skills/verify/SKILL.md` (blob `8a5fd2711dcb202dfd1e32bd616cb85e91cb5078`) expressly forbids fixing or working around such an unrelated failure without that confirmation. No grammar repair, exclusion, rerun or follow-on build was performed after this determination.

## Preserved, untested copy-entry draft

`UNTESTED-copy-entry-draft.tar.gz` contains full copies of the nine implementation/test/documentation files prepared while the ordinary build was still running, plus their prior protocol. The ten-case generic donor test remains byte-identical to blob `d35b6b89bb2e2abf9baab2a9edc585f581ce8cb3`; two new SDK serialization cases are also present. The source draft reuses existing `CopyExceptions` composition and application across `EntersAsCopy` and both continuations. It has **not been compiled or tested**, is not adopted, and adds no Phyrexian Metamorph definition or registry slot. Local working files are retained. The archive is evidence, not a request to apply it automatically.

The archive is stored as ordered `.part00`, `.part01`, `.part02` byte segments. Concatenate in that order and check the archive SHA-256 in `UNTESTED-draft-manifest.json` before inspecting it; individual part and source-file hashes are also recorded. Do not extract over an active worktree.

It does not solve pre-entry publication, simultaneous-entry copy eligibility, copied as-enters choices, or all Metamorph qualification requirements. Preregistration accurately retains the historical IN_PROGRESS state of the build; this checkpoint supersedes that status with FAILURE/HELD.

## Next actions after explicit unrelated-repair clearance

1. Reinspect live branches, Actions and execution ownership before changing anything. Obtain the repository-required confirmation that repairing these existing Assay printer failures is safe.
2. Isolate their grammar/printer repair; preserve all four original assertions, add precise regression coverage, and receive source-bound Assay tests plus the touchstone gate. Do not suppress failures or weaken assertions.
3. Rerun the ordinary whole-project gate on the exact repaired source, with complete original artifacts and truthful skip/failure reporting. Diagnose any further blockers under the same ownership safeguards.
4. Resume the held copy-entry draft, then qualify the remaining eight frozen Manual slots and 308 opponent slots. Complete mechanics, multiplayer integration, masked competent pilots and full-game telemetry/replay/legality readiness. Card registration alone is insufficient.
5. Only after all official protocol admission and seed/budget safeguards are satisfied, seek or use the exact authorized one-shot competitive execution authority. Keep both reserved Precision games protected. Publish further durable source-bound checkpoints; never substitute synthetic/component outcomes for competitive evidence.

`artifact-audit.json`, `source-identity.json`, `UNTESTED-draft-manifest.json` and `SHA256SUMS` record the receiving audit. `receive.py` is the collector used in this workspace; it requires the sibling original artifact and `metamorph` worktree to reproduce collection. Audit was performed by the executing agent, not an independent reviewer.
