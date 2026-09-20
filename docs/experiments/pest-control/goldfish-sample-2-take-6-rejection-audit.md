# Pest Control v1.0 — Goldfish Sample #2 Take 6 Rejection Audit

## Disposition

**Sample #2 Take 6 is formally rejected in full.** Its complete 30-seed vector, SHA-256
`79659bf0a8823c94e288b2df46f246385d02dba236ce3b70f9a4c6dc477eb79b`, is permanently retired and
hard-disabled. It may never be replayed, rehabilitated, replaced, compared against, optimized
against, or reused. Sample #1 remains the sole accepted Pest Control performance/engine sample, and
no pooled 60-game analysis is admissible.

## Pre-execution and execution record

The hard final-JSON artifact contract, the nested/modal removal regression requiring
`policyApplied=true`, and the permanent-control/vector guard all passed before seed generation, with
every gameplay runner skipped. The new vector was then frozen before Game 1 with zero overlap against
the repository-wide exclusion set. Freeze head `eeb8321d5f0f1dc84cf206a2767ffaaec134ca54` passed
CI #234.

A first forced invocation failed during Kotlin test compilation before test discovery; it executed
zero games and consumed no seeds. The identical disabled freeze state then passed a clean rebuild.
The one gameplay invocation executed Games 1–30 exactly once in frozen CSV order and completed green
in 2m43s. There was no reroll, replay, replacement, exclusion, substitution, deck change, policy
change, telemetry change, schema change, or mid-sample correction.

The exact uncompressed final JSON SHA-256 is
`21f09a946adce222dc812fb7d1ccfee935ad812d658075f5c98cd2b94adf921b`. It is preserved losslessly as
`goldfish-sample-2-take-6-raw.json.gz`; decompression reproduces that hash. The compressed file SHA-256
is `49e4b3481182a83503438b0dfd4fb717be254d102c3e9daf6df919bb24408ac6`. The generated human report
SHA-256 is `786f969ecd423532d2cc57f429106d3e71206ba452d49166f7948ed3512c5bd0`.

## Audit-completeness gate

The predeclared completeness gate is green:

- the artifact contains 30 games in exact frozen seed order and the ordered vector hash matches;
- every game has zero built-in audit errors and a normal engine terminal record;
- 28 setup evaluations preserve complete structured prefixes, continuations, actual lines,
  counterfactuals, comparisons, targets/costs, resources, classifications, and reasons;
- observed labels comprise 14 currently executable, 13 land-unlocked, one still unexecutable after
  land, and 27 executable-but-not-materially-superior classifications; the synthetic artifact
  contract separately proves all five classifications survive final serialization;
- 691 friendly-removal candidate evaluations are preserved; all three executed friendly removals
  have corresponding selected records with complete target, cost, alternatives, pass/hold,
  downstream, margin, policy, and selection data, and each records `policyApplied=true`; and
- required zero, false, empty, null, and default-valued members are present in the final JSON.

No mandatory field was inferred or repaired after execution.

## Blocking Game 18 sequencing defect

Game 18's T6 trace proves a complete superior sequence that the land-drop-aware detector omitted.
Before Weather, the player had four untapped lands established by the recorded plays—Forest T1,
Swamp T2, Swamp T4, and Forest T5—and held Blood Researcher, Weather the Storm, and Swamp. The actual
line was:

1. cast Weather the Storm for `{1}{G}`;
2. play the untapped Swamp; and
3. cast Blood Researcher for `{1}{B}{G}`.

Those same two Forests and three Swamps make the reordered line land → Blood Researcher → Weather
fully executable for the same five total mana and the same colored requirements. The reordered line
puts the second Researcher onto the battlefield before Weather's lifegain event, so it produces one
additional relevant +1/+1 counter at no resource or timing cost. It is therefore materially superior
to the actual order.

Nevertheless, the Weather record serializes empty `currentlyExecutablePreWeatherSpells`, empty
`spellsExecutableAfterLegalLandPlay`, empty `evaluatedSetupSequences`, a null best setup, and
`weatherCastBeforeSuperiorSetup=false`. It separately records `usefulSpellCastLaterThisTurn="cast
Blood Researcher"`, confirming the omitted spell was subsequently cast in the same valid main-phase
window. The agent consequently took the inferior Weather-first line and the telemetry failed to
classify the genuine land-unlocked missed-superior opportunity.

This is a clear sequencing and telemetry defect under the whole-block acceptance rule. No correction
is included; production policy/telemetry changes require separate authorization.

## Complete strategic audit

Apart from the blocking Game 18 defect, the all-30 audit found no additional clear defect:

- Rules/state, mana, and terminals: all games completed through authoritative engine actions, all 30
  ended by engine-reported combat lethal, and no illegal action, state, or terminal inconsistency was
  recorded.
- Weather/Storm: all 20 casts have matching expected and observed copy counts. No other preserved
  setup evaluation is marked materially superior. Game 24's Weather-before-Follow line follows a
  same-turn Food activation and reaches enhanced Follow; it is not an unsupported raw-life line.
- Temporary conditions and Follow: all six enhanced casts have same-turn lifegain and all 18 normal
  casts have none; no temporary-condition mismatch was found.
- Pure lifegain: each activation has Researcher/Mascot conversion except Game 24's Food, which enables
  the same-turn enhanced Follow continuation. No unsupported activation was found.
- Removal: Games 17 and 19 used Cast Down on Carrier Thrall and Game 23 used Bone Shards on Carrier
  Thrall while sacrificing that same Carrier. Each has exactly one selected audit, applies the
  general friendly-removal policy, and clears its recorded established margin by approximately
  `0.393`, `1.298`, and `0.981`, respectively. No other friendly or modal removal executed. Chainer's
  Edict remained correctly stranded against the blank opponent.
- Carrier Thrall/Scion: three Carrier deaths produced exactly three Scions. No Scion was sacrificed
  for mana, so there is no unmatched mana-provenance claim.
- Researcher/Mascot: trigger totals equal counters added in every game, and every lifegain event has
  a positive amount and source.
- Generous Ent: 15 Forestcycles and two legal creature deployments are represented; no contradictory
  cycle/cast decision was found.
- Bottlenecks and taplands: all 146 actionable observations preserve card, turn, required/available
  mana, and cause (134 total-mana, four color, eight tapland). Five games record eight proximate
  Jungle Hollow delays.
- Solitaire interaction is explicitly reported as constrained rather than treated as matchup
  evidence.

The generated aggregate and per-game statistics remain quarantined audit history only. They are not
Sample #2 evidence and must not be compared or pooled with accepted Sample #1.

## Preserved invariants

Pest Control v1.0 was unchanged. Sample #1 remains the sole accepted performance/engine sample. The
challenger remains audit-only and unconstructed. No Sample #3, optimization, challenger work, or
opponent self-play began. No Batshit Economics or Project X resource was written.
