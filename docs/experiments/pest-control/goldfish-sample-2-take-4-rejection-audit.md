# Pest Control v1.0 — Goldfish Sample #2 Take 4 Rejection Audit

## Disposition

**Sample #2 Take 4 is formally rejected in full.** Its complete 30-seed vector is permanently retired
and hard-disabled. It may never be replayed, rehabilitated, replaced, compared against, optimized
against, or reused. Sample #1 remains the sole accepted Pest Control performance/engine sample. No
pooled 60-game analysis is admissible.

The vector was frozen before Game 1 at remote head
`0e5966c5069f8b8b71f8557aceaeaecd0920c0c3`, which passed CI #226. One forced opt-in invocation then
executed all 30 seeds exactly once in CSV order. The raw seed order and ordered-value SHA-256 match the
frozen CSV. No reroll, replay, replacement, exclusion, substitution, deck change, policy change,
telemetry change, or mid-sample correction occurred.

## Mandatory audit-completeness failures

Games 8, 18, and 23 executed Bone Shards on turn 7. The blank opponent controlled no creatures, so
each cast necessarily involved friendly removal, but the artifact contains no selected friendly-
removal audit for any of them. Across the block, 528 friendly-removal evaluations were recorded, all
for Cast Down candidates; none records Bone Shards and none is marked selected. The exact selected
target, target value, additional-cost choice, opposing alternatives, pass/hold value, resulting board,
net/fair-trade comparison, and selection reason are therefore unavailable for all three executed
actions. Their strategic quality is unclassifiable and no gameplay-policy conclusion is drawn.

The cause is a decision-path observability gap. Friendly-removal valuation is attached while a
priority `CastSpell` candidate is scored and requires the friendly permanent to be present in that
action's targets. Bone Shards reaches later target/additional-cost decisions, so the selected completed
action is not represented by that priority-option audit. The artifact also omits default-valued JSON
fields such as zero life cost, empty additional costs/opposing alternatives, null prevention benefit,
and `selected = false` rather than emitting them explicitly. This fails the requirement that the final
machine-readable artifact itself contain every mandatory field.

The predeclared completeness checker additionally raised five errors: two in Game 1, and one each in
Games 9, 16, and 23. These are checker false positives. The compact
`executableButNotMateriallySuperior` summary stores only the setup prefix, while the structured
evaluation's `proposedActionOrder` also includes the focal Weather cast; the checker incorrectly
required exact list equality. The full structured evaluations are present, but the mandatory gate is
not green and may not be repaired after execution.

Game 23 also cast Weather at Storm 0, then Bone Shards, then a second Weather at Storm 2. The
sequencing detector excludes targeted setup actions and therefore did not evaluate Bone Shards as a
pre-Weather candidate. Because its exact target and additional cost were not preserved, the apparent
Bone-Shards-first counterfactual cannot be classified conclusively. This is an additional audit-
coverage gap, not accepted evidence of a gameplay-policy failure.

## Preserved audit results

All 30 games completed normally by Pest combat lethal. Aside from the five completeness-checker
errors, the automatic runner reported no rules/state, illegal-action, terminal, or accounting error.
Weather copy counts matched Storm counts; Researcher and Mascot triggers matched counters; Carrier
deaths matched Scions created; the one Scion mana use balanced exactly and funded a Weather cast; all
life events were positive and attributed; Follow modes matched the recorded temporary life-gain
condition; and every Food activation had a payoff or enabled same-turn enhanced Follow.

Manual review covered every game's opening/development, action timeline, life and counter events,
Weather and Follow casts, removal casts, Thrall/Scion lifecycle, Ent decisions, bottlenecks, Jungle
Hollow events, and terminal record. The quarantined block contains 28 engine-functional and two fair-
creature-functional classifications, 21 Weather casts, 16 normal and seven enhanced Follow casts, 12
Food activations, 20 Ent cycles and no Ent creature casts, 181 actionable bottleneck observations, and
25 Jungle Hollow entries. These figures document audit coverage only and are not performance evidence.

## Preserved invariants

Pest Control v1.0 was unchanged. Sample #1 remains the sole accepted performance/engine sample. The
challenger remains audit-only and unconstructed. No pooled analysis, Sample #3, optimization, or
opponent self-play began. No Batshit Economics or Project X resource was written.

The complete machine-readable artifact is preserved losslessly as
`goldfish-sample-2-take-4-raw.json.gz`; decompression yields the exact JSON written by the single
authorized invocation.
