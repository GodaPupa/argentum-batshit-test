# Pest Control v1.0 — Goldfish Sample #2 Take 5 Rejection Audit

## Disposition

**Sample #2 Take 5 is formally rejected in full.** Its complete 30-seed vector, SHA-256
`4e239b8b76df587ec3e14f05f564a288480de42abcd681670c7330323631c1f0`, is permanently retired and
hard-disabled. It may never be replayed, rehabilitated, replaced, compared against, optimized
against, or reused. Sample #1 remains the sole accepted Pest Control performance/engine sample, and
no pooled 60-game analysis is admissible.

## Pre-execution and execution record

The hard final-JSON artifact-contract test passed on exact validated head
`5152f50b463889c9282c7bf1ea0d6a8484fdeb0e` before seed generation. The new vector was then frozen
before Game 1 with zero overlap against the repository-wide exclusion set. The control/vector guard
passed locally with every gameplay runner skipped, and freeze head
`b5ea795833de919f6f94ed314cce6cd8c15dd17a` passed CI #230.

A first forced invocation failed during Kotlin test compilation before test discovery; it executed
zero games and consumed no seeds. The identical disabled freeze state then recompiled green. The one
gameplay invocation executed Games 1–30 exactly once in frozen CSV order and completed green in 4m21s.
There was no reroll, replay, replacement, exclusion, substitution, deck change, policy change,
telemetry change, or mid-sample correction.

The exact uncompressed final JSON SHA-256 is
`bdd9a0f72e6d93f78997210491cd807f948312e9711e7f39d4df0aac94f61896`. It is preserved losslessly as
`goldfish-sample-2-take-5-raw.json.gz`; decompression reproduces that hash. The generated human report
SHA-256 is `9f5c5c8d9a34565b1bad0e8e04e50da34c773ca624480fe5d5487ff6210f446c`.

## Audit-completeness gate

The predeclared completeness gate is green:

- the artifact contains 30 games in exact frozen seed order and the ordered vector hash matches;
- every game has zero built-in audit errors and a normal engine terminal record;
- 49 setup evaluations preserve complete structured prefixes, continuations, actual lines,
  counterfactuals, comparisons, targets/costs, resources, classifications, and reasons;
- the observed classifications comprise 32 currently executable, 15 land-unlocked, 2 still
  unexecutable after land, and 47 executable-but-not-materially-superior labels; no genuine missed-
  superior sequence occurred;
- all five executed friendly removals have corresponding selected audit records with explicit
  target/controller/value, modal cost and paid resource, alternatives, pass value, resulting board,
  downstream effects, net/fair-trade values, policy disposition, and selection reason; and
- required zero, false, empty, null, and default-valued members are present in the final JSON.

No field was inferred or repaired after execution.

## Blocking gameplay-policy defect

The new observability exposed a general policy-coverage defect. Bone Shards candidates are concrete
targeted casts with their additional cost already chosen, but the broad intent fold does not classify
the removal effect nested in the modal wrapper. Their audits therefore record
`policyApplied=false`, and the established friendly-removal fair-trade margin is not enforced.

Five friendly removals executed:

| Game | Turn | Action | Target | Additional cost | Net vs pass | Required margin | Fair-trade surplus | Disposition |
|---:|---:|---|---|---|---:|---:|---:|---|
| 2 | 5 | Bone Shards | Carrier Thrall | sacrifice the targeted Carrier Thrall | +0.945 | 2.100 | **-1.155** | policy not applied; defective selection |
| 9 | 10 | Cast Down | Carrier Thrall | none | +7.469 | 4.200 | +3.269 | policy applied; clears margin |
| 23 | 5 | Bone Shards | Carrier Thrall | discard Chainer's Edict | +4.810 | 2.100 | +2.710 | policy not applied, but line independently clears margin |
| 26 | 7 | Bone Shards | Carrier Thrall | discard Cast Down | +1.181 | 2.100 | **-0.919** | policy not applied; defective selection |
| 28 | 7 | Bone Shards | Carrier Thrall | sacrifice the targeted Carrier Thrall | +0.026 | 2.100 | **-2.074** | policy not applied; defective selection |

All five lines created a Scion and a represented lifegain/counter transition, so this is not a
blanket objection to self-targeting. Games 2, 26, and 28 are defects because the complete recorded
benefit still fails the existing resource/future-interaction margin. Game 23 demonstrates why a
blanket self-removal ban would also be wrong: its complete line clears the margin. Game 9 confirms
that the same audit and policy work for nonmodal Cast Down.

This is the first deterministic performance artifact to demonstrate that the modal-intent gap can
change a selected line adversely. No production policy correction was made; that requires separate
authorization.

## Complete strategic audit

Apart from the blocking removal-policy defect, the all-30 audit found no additional clear defect:

- Rules/state and mana legality: all games completed through authoritative engine actions; no illegal
  action, state, or terminal inconsistency was recorded.
- Weather/Storm and sequencing: expected and observed Storm copies match for every cast; no Weather
  preceded a detector-proven superior setup. Five Storm-0 casts followed by a useful later spell had
  complete counterfactuals and were classified executable-but-not-materially-superior.
- Temporary conditions and Follow: every enhanced Follow has a preceding same-turn lifegain event,
  every normal Follow has none, and no pending or available lifegain line was shown to be a superior
  executable continuation.
- Pure lifegain resources: every recorded activation had a Researcher/Mascot payoff or represented
  survival utility; no raw unsupported activation was found.
- Removal and patience: the five friendly removals are completely audited above. Chainer's Edict had
  no legal opponent-sacrifice execution in the blank-opponent environment and remained correctly
  represented as stranded interaction.
- Carrier Thrall/Scion: five Carrier deaths produced exactly five Scions. No Scion was sacrificed for
  mana, so there is no unmatched mana-provenance claim.
- Researcher/Mascot: trigger totals equal counters added in every game, and every lifegain event has
  a positive amount and source.
- Generous Ent: 19 Forestcycles and one legal six-mana creature deployment are represented; no
  contradictory cycle/cast record was found.
- Bottlenecks and taplands: all 156 actionable observations have card, turn, required and available
  mana, and a classified cause (149 total-mana, 3 color, 4 tapland). Jungle Hollow entries and four
  proximate material delays are preserved.
- Terminal reporting: all 30 games ended by engine-reported combat lethal with complete turn/action
  counts. Solitaire-stranded interaction is reported rather than treated as matchup evidence.

The generated aggregate and per-game statistics remain quarantined audit history only. They are not
Sample #2 evidence and must not be compared or pooled with accepted Sample #1.

## Preserved invariants

Pest Control v1.0 was unchanged. Sample #1 remains the sole accepted performance/engine sample. The
challenger remains audit-only and unconstructed. No Sample #3, optimization, challenger work, or
opponent self-play began. No Batshit Economics or Project X resource was written.
