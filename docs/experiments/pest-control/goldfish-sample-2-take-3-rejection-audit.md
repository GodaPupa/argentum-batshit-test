# Pest Control v1.0 — Goldfish Sample #2 Take 3 Rejection Audit

## Disposition

**Sample #2 Take 3 is formally rejected in full.** Its complete 30-seed vector is permanently retired
and hard-disabled. It may never be replayed, rehabilitated, replaced, compared, optimized against, or
reused. Sample #1 remains the sole accepted Pest Control performance/engine sample. No pooled
60-game analysis is admissible.

The vector was frozen before Game 1 at remote commit
`d9bddfe67f78232e12b09003a239a0890c4f65d8`, which passed CI #222. One forced, opt-in invocation then
executed all 30 seeds exactly once in CSV order. No reroll, replacement, exclusion, substitution,
deck change, policy change, telemetry change, or mid-sample correction occurred.

## Automatic and trace-wide checks

The runner completed all 30 games and reported no built-in audit errors. Seed count, uniqueness,
order, and vector SHA-256 matched the frozen CSV. All games ended normally by Pest combat lethal;
there were no illegal actions or wedged states. Weather copy counts matched Storm counts. Researcher
and Mascot trigger/counter totals matched 72/72 and 82/82. Two Carrier deaths produced two Scions;
neither Scion was sacrificed for mana. Every per-game timeline, creature entry, life event,
bottleneck record, Jungle Hollow entry, Follow cast, Weather cast, Food activation, removal cast, and
terminal record was manually reviewed.

The quarantined descriptive output contains 26 engine-functional and four fair-creature-functional
games; 17 Weather casts; 12 normal and two enhanced Follow casts; 13 Food activations, all with a
Researcher or Mascot present; 14 Ent cycles and no Ent creature casts; 188 actionable mana
bottlenecks; and 37 tapped Jungle Hollow entries. These numbers are retained only to document audit
coverage and are not performance evidence.

## Rejecting telemetry defects

The corrected `PreSpellSetupTelemetry` computes five distinct states: currently executable,
land-unlocked, still unexecutable after a legal land, executable but not materially superior, and
genuine missed-superior. The persisted `PestWeatherCast` record retained only the first two and the
best superior sequence. It discarded `stillUnexecutableAfterLegalLandPlay` and
`executableButNotMateriallySuperior`, so the raw artifact cannot preserve or audit the complete
required classification.

No missed-superior flag was raised. Games 14, 21, and 22 correctly recorded a second Weather as
currently executable and then executed both Weather copies. No land-unlocked line was recorded. The
existing deterministic Game-16-style regression remains green, but this sample artifact cannot prove
the required absence and categorization of every non-superior or post-land-unexecutable candidate.

## Friendly-removal audit blocker

The blank opponent controlled no creatures. Game 13 cast Cast Down on turn 7 and Game 14 cast Bone
Shards on turn 7; each turn also contained a Carrier death and Scion creation. The preserved timeline
records only `cast Cast Down` or `cast Bone Shards`. It does not record the selected target, Bone
Shards' discard-or-sacrifice additional-cost choice, the exact pre/post battlefield, the pass-line
comparison, or whether the removal was necessary for lethal.

Game 13's recorded three Warden life events after the turn's second Warden and Carrier-to-Scion
transition show a plausible productive payoff conversion. Game 14 records only one Warden life event
after the Carrier-to-Scion transition. Without the omitted target/cost and counterfactual state, the
required proof that either friendly-removal line exceeded the destroyed permanent, removal card,
mana, future interaction, and alternative attack cannot be made. A merely legal or eventually lethal
line is insufficient. This auditability failure independently rejects the block; the uncertain lines
are not classified as accepted or repaired.

## Preserved invariants

Pest Control v1.0 was unchanged. The challenger remains audit-only and unconstructed. No Batshit
Economics or Project X resource was written. No Sample #3, optimization, or opponent self-play began.
The raw JSON and generated report are quarantined historical artifacts only.
