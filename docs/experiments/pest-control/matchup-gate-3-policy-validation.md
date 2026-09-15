# Pest Control Matchup Gate 3 — deterministic policy validation

## Frozen boundary

- Gate 2 evidence head: `6bc23a88286bfcf9e0eb279769a12b279c3060a3`
- Gate 2 tree: `b3803658f46d59c2d2d8e16bf6bddad9f5369e09`
- Gate 2 validation: CI #334 and Argentum Validation #160 passed on the same head
- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Scope: seedless deterministic preboard policy validation only

Pest Control v1.0 and SoterX Mono Red Madness remain frozen. The accepted Gate 2 fixtures below
are reused as evidence; Gate 3 does not duplicate them under new names.

## Final coverage matrix

“Gate 2 / `6bc23a…`” means the named fixture was part of the complete suite accepted by CI #334 and
Argentum Validation #160 at the exact Gate 2 head. Gate 3 additions are seedless deterministic
fixtures; none are games or sample evidence.

| Requirement | Accepted Gate 2 evidence | Positive and restraint controls | Gate 3 disposition |
|---|---|---|---|
| Functional land counts and color access | `PestControlMonoRedMadnessMulliganPolicyTest`: zero, two, five, six lands; colored and colorless early mana — Gate 2 / `6bc23a…` | Keepable two/five-land and rejected zero/six-land controls; payable and wrong-color actions | Covered |
| Deterministic typed landcycling | Same fixture: Generous Ent, second typed landcycler, absent target, tapped land, wrong color, wrong zone, ordinary draw — Gate 2 / `6bc23a…` | Two positive landcyclers and five negative controls | Covered |
| Curve and development sufficiency | Same fixture: nonempty executable evidence, madness/non-madness slow hands, target interaction, mandatory costs, tapped-land timing — Gate 2 / `6bc23a…` | Payable early line and slow/illegal controls | Covered |
| Madness openers with and without outlets | Same fixture: paired payable Grab and outlet-free opener — Gate 2 / `6bc23a…` | Keep with usable outlet; reject without executable development | Covered; does not claim generalized madness valuation |
| London bottom preservation | Same fixture: preserves sole land/cycler and only early development line — Gate 2 / `6bc23a…` | Qualifying pair and ordinary development controls | Covered |
| Representative keeps and mulligans for both decks | Same fixture exercises frozen Mono Red and Pest cards under the shared evaluator — Gate 2 / `6bc23a…` | Both-deck keep/reject controls | Covered |
| Guttersnipe/Flamebreather before profitable spells | `PestControlMonoRedMadnessDecisionTest`: each engine before Lightning Bolt — Gate 2 / `6bc23a…` | Positive sequencing plus no categorical claim outside profitable line | Covered |
| Faithless Looting, Grab, Highway Robbery, and Moxite sequencing | Grab/Snacker positive exists — Gate 2 / `6bc23a…` | Gate 3 setup-first matrix uses each canonical spell; Highway completed modes and `Done`; Moxite payoff/restraint; Looting and Grab zero-draw restraint | Covered |
| Profitable versus unprofitable discard | Grab discards Snacker when its draw supplies the third draw — Gate 2 / `6bc23a…` | Productive Grab and Moxite/Snacker branches; zero-draw Looting, Grab, Moxite, and Highway restraint | Covered |
| No future-resource assumptions | Madness affordability uses post-originating-action mana; mulligan uses deterministic opening resources — Gate 2 / `6bc23a…` | Floating, spent, tapped, wrong-color, and no-target controls | Covered |
| Fiery Temper affordability | `PestControlMonoRedMadnessDecisionTest`: payable, spent mana, floating mana, tapped source, wrong color — Gate 2 / `6bc23a…` | Two accepts and three declines | Covered |
| Sneaky Snacker discard and return | Grab discard policy plus `SneakySnackerScenarioTest` third-draw, two-draw, battlefield, and opponent-draw controls — Gate 2 / `6bc23a…` | Productive discard/return and no-return controls | Covered for legality; broader discard restraint remains in outlet gap |
| Burn target choice and immediate win | Burn targets Warden, Researcher, and Mascot over irrelevant body; Bolt/Fireblast immediate wins — Gate 2 / `6bc23a…` | Engine-target positives, irrelevant-body control, winning face damage | Covered |
| Imminent-lethal survival removal | Final-must-act Fireblast and survival-override matrix — Gate 2 / `6bc23a…` | Lethal pass, survival action, nonlethal restraint, ineffective/protected targets, cheaper answer, immediate win | Covered |
| No speculative face burn | Fireblast/Lava Dart sacrifice restraint — Gate 2 / `6bc23a…` | Decisive lethal and nondecisive holds | Covered for high-cost burn; ambiguous ordinary-burn planning remains an interpretation limitation |
| Fireblast and Lava Dart sacrifice policy | Same decision fixture — Gate 2 / `6bc23a…` | Lethal/survival use and speculative restraint; Gate 3 adds Dart flashback as necessary removal of a visible lethal attacker | Covered |
| Melded Moxite discard and sacrifice policy | `MeldedMoxiteScenarioTest` proves rules, costs, zones, token, events, serialization — Gate 2 / `6bc23a…` | Gate 3 fixtures cover the sole-Snacker auto-selection payoff, multiple discard candidates, zero-library and insufficient-draw restraint, hidden-order invariance, and productive/restraint sacrifice choices | Covered; the sole eligible exact-one discard is auto-completed and does not expose a selection decision |
| Combat attacks and blocks | `CombatAdvisorTest` profitable/lethal attacks, losing attacks, favorable/survival blocks; final-must-act matchup fixture — Gate 2 / `6bc23a…` | Positive attacks/blocks and losing-line restraint | Covered semantically; not a claim of optimal combat |
| Stack and response timing | Weather lethal response and final must-act Fireblast path — Gate 2 / `6bc23a…` | Immediate lethal, safe-window restraint, final-window survival | Covered for frozen-list outcome-relevant windows; ambiguous exchanges remain limitations |
| Pest threat-sensitive removal | Generic Cast Down high-value targeting and removal patience — Gate 2 / `6bc23a…`; shared Guttersnipe/Flamebreather lethal-engine targeting fixtures | High-value target and hold controls; Gate 3 adds exact Cast Down removal of a visibly lethal attacking Sneaky Snacker | Covered for objectively material frozen-list threats; no claim among ambiguous future-value targets |
| Pest Weather policy | `PestControlAgentDecisionTest`: survival use, setup-first use, no-pressure restraint — Gate 2 / `6bc23a…` | Emergency use and several restraint controls | Covered |
| Warden, Researcher, and Mascot deployment | Same fixture: Warden-before-value and supported-payoff deployment, with stronger-line controls — Gate 2 / `6bc23a…` | Three positive engine cases and noncategorical restraint | Covered |
| Follow timing under live pressure | Same fixture: normal/enhanced timing, survival limits, land pressure — Gate 2 / `6bc23a…` | Normal and enhanced positives plus null/unsafe restraint | Covered to the objective deterministic boundary. Follow is a sorcery whose selection value depends on unknown top-four identities; a stronger live-pressure ranking is not objectively demonstrable without hidden-order access or a new strategic standard, so it remains an interpretation limitation |
| Hidden-hand and library isolation | `DeterminizerInvariantsTest`: visible-state preservation, hidden resampling, known composition, pinned reveals, no source mutation — Gate 2 / `6bc23a…` | Same-seed stability, different hidden samples, visible/pinned controls; Gate 3 holds the visible Carrier Thrall/Warden response fixed while permuting opponent hidden hand identity and library order | Covered; production chooses the same semantic `Lightning Bolt` → `Essence Warden` action in every permutation |
| Modal-action serialization compatibility | CI #383 validated the new defaulted `modalSelectionCompleted` field across existing engine/content/tools/Gym consumers | Gate 3 adds historical JSON with the field omitted, explicit false/true deterministic round trips, and proof that completed zero-mode selection remains distinct from an unperformed selection | Covered subject to final exact-head CI; no serialization semantics changed |

## Interpretation limits carried into Gate 3

- Deterministic fixtures can reject objectively illegal, losing, or materially dominated actions;
  they cannot establish optimal play among strategically reasonable alternatives.
- Known frozen deck composition is legitimate agent knowledge. Opponent hand identity and library
  order are not.
- Sideboard cards and postboard decisions are outside this preboard gate.
- These fixtures are not games, samples, matchup-performance evidence, or a substitute for Gate 4.
- Follow's unknown top-four selection prevents an objective deterministic comparison among otherwise
  reasonable live-pressure lines. Gate 3 validates legality, survival restraint, and normal/enhanced
  sequencing, but does not claim optimal Follow timing against a live opponent.
- When an optional, automatically completed branch and declining have exactly equal evaluated
  outcomes, the legacy yes/no tie-break may choose acceptance. The choice remains visible in
  strategic-provenance telemetry but has no demonstrated gameplay effect. This is a noncanonical
  control-flow distinction, not an outcome-policy defect. It is deferred unless later evidence shows
  a loop, mutation, misleading derived metric, or gameplay consequence. Raw artifacts must retain the
  actual submitted response; a derived gameplay metric may call it a no-op only when that
  classification is explicit.

## Completed-branch protocol diagnostic

The initial completed-branch fixtures incorrectly described `Patterns.Hand.discardCards(1)` as a
`minSelections=0, maxSelections=1` continuation. It is `ChooseExactly(1)`. The collection executor
therefore auto-selects the sole eligible card, or auto-completes an empty result when none exists,
without creating a `SelectCardsDecision`. The alleged Moxite lost-payoff defect is withdrawn:
production accepted the branch, auto-selected Sneaky Snacker, and scored the completed state
`15.21375` versus `10.05` for declining.

The corrected controls keep exact-one auto-completion separate from synthetic effects that genuinely
use `ChooseUpTo(1)`. Those synthetic fixtures assert the actual `0..1` bounds before testing a
beneficial singleton, a beneficial empty branch, a harmful singleton whose empty branch ties decline,
and selection consistency among multiple candidates.

For the exact-one no-candidate/no-payoff case, acceptance and decline have identical final game state,
resources, zones, terminal behavior, and ordered gameplay events. Their audit traces intentionally
differ: the corresponding `DecisionSubmittedEvent` truthfully records `Chose Yes` or `Chose No`.
Tests exclude only that outer provenance record when comparing gameplay events; production does not
filter, suppress, normalize, or relabel it.

## Gate 3 completion candidate

The shared modal-continuation correction at `91a947ac7c7fdb8f30452f9706a8850531833ed4`
(tree `53d2e30cb44ac8f95e92bc11b0c1b3461a28ca8a`) is the authoritative provisional
checkpoint. CI #383 passed; Highway Robbery policy was 25/25 and the complete AI suite was 720
passing with 11 skipped. The unpublished local equivalent `923f2c10…` is not authoritative and is
not incorporated.

The final test/documentation candidate adds only serialization compatibility, frozen-list policy
restraint/survival fixtures, and controlled hidden-state invariance. All deterministic Gate 3
requirements are now either covered by accepted evidence or bounded explicitly as an interpretation
limitation. Final acceptance still requires fresh full CI and Argentum Validation on the same exact
published head. All 13 Pest gameplay runners remain disabled; Gate 3 generated zero seeds and
executed zero games.
