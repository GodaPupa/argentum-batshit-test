# Project Pest Control — Status

- Status: Goldfish Sample #1 executed once and rejected by audit; investigation gate
- Laboratory: Project Pest Control
- Branch: `pest-control/lab`
- Validated base: `47882cd645caf126afee6cf13a65909806fa40ab`
- Accepted rules-complete remote head: `b08d8fad6e6709db4834e8e956e6853152efb3b5`
- Accepted Phase 3 remote head: `8dd4a429e0799b60e7449c214a540dd8414b1f1c`
- Control version: Pest Control v1.0 (permanent, immutable)
- Candidate status: proposed Tier-1 architecture only; not approved, constructed, or run

## Laboratory boundary

This document and `docs/experiments/pest-control/` belong exclusively to Project Pest Control.
Batshit Economics/Affinity, `mayhem/project-x`, and their seed vectors and experiment protocols are
read-only dependencies and are outside this laboratory's write and workflow scope. The only new
seeds and gameplay run are the explicitly authorized Pest Control v1.0 Goldfish Sample #1 described
below. No challenger construction, opponent self-play, optimization, or Sample #2 was performed.

## Goldfish Sample #1 audit result

The exact frozen 30-seed vector was executed once, in order, without rerolls, replacements,
exclusions, deck changes, policy changes, or seed substitutions. The run completed all 30 games,
but **Sample #1 is rejected and is not a Pest Control performance/engine baseline**.

Three independent audit blockers were found:

1. The solitaire agent cast Chainer's Edict into an empty opposing battlefield in games 9, 13, 19,
   and 27. This is a genuine general agent-policy defect, not ordinary interaction stranded in hand.
2. In game 25, a Carrier Thrall Scion was sacrificed for mana, but no spell recorded that Scion as
   a mana source. This is an unresolved agent-sanity or mana-provenance telemetry defect.
3. The generated “genuine mana bottleneck” metric samples pre-land-drop and repeated priority
   states. It therefore reports false positives (including missing green with a Forest in the kept
   hand) and cannot support the requested bottleneck aggregate.

The rules/state portions that can be audited from the run were internally consistent: 142 separate
life-gain events gained 234 life; Researcher recorded 41 triggers and 41 counters; Mascot recorded
71 triggers and 71 counters; all 23 Weather casts had the expected Storm-copy count; four Carrier
Thrall deaths created four Scions; all 30 terminals were engine-reported combat lethal. These facts
do not cure the invalidating defects above.

The complete rejected-run record is preserved under `docs/experiments/pest-control/`. The seed
vector must not be rerun or repaired in place. Investigation and any smallest general correction
require a separately authorized phase. The permanent control remains exact and the challenger
remains audit-only and unconstructed.

## Phase 3 deterministic agent-validation result

Thirty-seven deterministic positions now validate the requested Pest Control strategy surface
through the live production-candidate agent and rules engine. They are board-state probes, not deck
construction, seeds, goldfish games, matchup samples, or optimization data.

Existing generic policy passed unchanged for supported Blood Researcher/Pest Mascot deployment,
survival-first Weather the Storm, refusing irrelevant Storm inflation, Blight-Priest drain lethal,
Carrier Thrall blocking, all four Bone Shards sacrifice/discard comparisons, removal target value,
hexproof/ward use of Chainer's Edict, flashback, removal patience, ordinary-cost Snuff Out at low
life, race-sensitive Snuff Out, productive Carrier Thrall death, Follow the Lumarets setup and
immediate normal-mode use, late-game Ent/Wildling casting, non-lifegain winning lines, and general
pending-lethal recognition.

The deterministic coverage also demonstrated genuine general gaps, corrected without card-name
heuristics:

- positive +1/+1-counter effects now advertise a lasting `PUMP` payoff to structural intent;
- life-gain-enhanced selection and basic-land tutor faces have explicit structural intent tags;
- land typecycling is recognized from the cycling mechanic's search metadata, independently from
  an Omen face;
- useful pre-Storm spells receive bounded option value for the additional event and visible
  repeatable counter/drain payoffs, while a spell that is already worse than passing receives none;
- a non-mana alternative cost receives bounded tempo value only when the preserved mana makes a
  relevant follow-up executable;
- early land-tutor/typecycle actions receive bounded development value, while feasible late-game
  bodies retain their normal board value;
- sacrifice-for-mana actions are considered only when their resulting mana unlocks an executable
  spell, and are not used when the spell is already executable or no productive use exists;
- targeted protection is held until opposing targeting or committed combat creates a credible
  window; and
- `SacrificeSelf` mana abilities now expose their irreversible payment in generic legal-action
  metadata, making the existing meaningful-action policy usable for Scions, Spawn, Treasure-like
  resources, and future equivalents.

**SHARED ARGENTUM CHANGE: yes.** The changes above are general intent, sequencing, protection-window,
and legal-action metadata corrections. No Pest Control card-name heuristic, Gym change, seed vector,
or opponent policy was added. No requested strategic behavior remains unresolved at this gate.

The frozen Pest Control v1.0 main deck and sideboard blocks below remain byte-for-byte identical to
the accepted rules-complete head. The proposed Tier-1 challenger remains audit-only and has not been
constructed.

## Phase 2 rules-complete result

The approved shared addition is complete: `PredefinedTokens.EldraziScion` is the authoritative,
reusable 1/1 colorless Creature — Eldrazi Scion definition with the mana ability “Sacrifice this
creature: Add `{C}`.” `Effects.CreateEldraziScion` provides fixed- and dynamic-count creation
facades. Generic SDK and rules-engine tests pin its characteristics, colorlessness, creature types,
cost payment, zone departure, ordinary use of the resulting mana, and visibility of both creature-
entry and death events to normal triggers.

The eight previously missing cards are individually implemented and registered:

| Card | Implemented rules | Focused proof |
|---|---|---|
| Carrier Thrall | Dies trigger creates exactly one predefined Eldrazi Scion | Direct death/token scenario plus Pest engine integration |
| Bone Shards | Choose sacrifice or discard as an additional cost; destroy target creature or planeswalker | Both additional-cost branches |
| Nature's Claim | Destroy target artifact or enchantment; its controller gains 4 | Target removal and controller life gain |
| Snuff Out | Conditional `{0}` alternative cost while controlling a Swamp, pay 4 life, nonblack-creature restriction, no regeneration | Alternative-cost cast and life payment |
| Suffocating Fumes | Opposing creatures get -1/-1 until end of turn; cycling `{2}` | One-sided characteristic change; cycling supplied by the shared primitive |
| Pulse of Murasa | Return target creature or land card from a graveyard to its owner's hand; gain 6 | Graveyard return and life gain |
| Nihil Spellbomb | Tap/sacrifice to exile target player's graveyard; battlefield-to-graveyard `{B}` may-pay draw trigger | Graveyard exile, sacrifice, optional mana payment, and draw |
| Masked Vandal | Changeling; ETB may exile a creature card from your graveyard to exile an opponent's artifact/enchantment | Targeting, optional graveyard payment, both exile movements |

The generated/approximate markers were removed only after human review of Chainer's Edict,
Marauding Blight-Priest, and Unearth. Chainer's Edict now uses canonical target-player sacrifice
wording and its flashback path is directly tested. Marauding Blight-Priest's one trigger per life-
gain event is directly tested. Unearth's existing return scenario remains green after review.

Sagu Wildling was corrected from an Adventure approximation to the existing Omen primitive. Its
Roost Seek face now searches for a basic land and shuffles the card into its owner's library. It is
not modeled as landcycling. Generous Ent's actual Forestcycling is directly tested.

## Deterministic Pest Control interactions

The rules suite now proves:

1. Essence Warden creates one life-gain event for each qualifying creature entry, including an
   opponent's entry.
2. Bogwater Lumaret creates life-gain events only for qualifying entries under its controller.
3. Blood Researcher receives exactly one +1/+1 counter per separate life-gain event.
4. Pest Mascot follows its implemented Oracle text and receives one +1/+1 counter per separate
   life-gain event.
5. Multiple Wardens and Lumarets create independent triggers, not an aggregated gain.
6. Weather the Storm's original and Storm copies resolve into separate gain-3 events; each produces
   independent Researcher, Mascot, and Blight-Priest triggers.
7. Marauding Blight-Priest produces one opponent-life-loss trigger per qualifying gain event.
8. Carrier Thrall creates exactly one Scion on death, and that Scion's entry participates normally
   in the Warden/Lumaret engine.
9. Sacrificing the Scion removes it, produces usable `{C}`, and creates no false life-gain event.
10. Follow the Lumarets' existing direct scenario distinguishes its normal maximum-one behavior from
    its life-gained-this-turn maximum-two behavior.
11. Bone Shards separately validates sacrifice and discard additional costs.
12. Chainer's Edict validates sacrifice and flashback, including exile after flashback resolution.
13. Snuff Out validates its alternative cost and four-life payment.
14. Generous Ent validates actual Forestcycling.
15. Sagu Wildling validates actual Roost Seek/Omen resolution and shuffle-back behavior.

No new production rules-engine primitive was needed beyond the approved reusable Scion token and
creation facade. No Gym or agent-policy behavior was changed.

## Validation record

- Focused Scion SDK/rules tests: green.
- Focused card and Pest interaction scenarios: green.
- Card-definition golden snapshots: regenerated through the authoritative exporter and green,
  including JSON round trips.
- Full CI: run 188 on accepted remote head `b08d8fad6e6709db4834e8e956e6853152efb3b5`
  is the authoritative green validation for the rules-complete gate.
- Argentum Validation workflow-equivalent local run: compilation completed; all Pest-relevant,
  engine, AI, and Gym tests reached in the run were green. The monolithic `test` task was ultimately
  red only because this container forbids Byte Buddy's dynamic self-attachment used by unrelated
  server mocking tests; the same server suite is green in CI run 187. A separate local `:gym:test`
  completed green. The GitHub connection available to this laboratory does not expose
  `workflow_dispatch`, so the named workflow itself could not be dispatched on this non-main branch.
- Phase 3 focused validation: 37 Pest agent decisions, the full structural-intent analyzer suite,
  and the generic mana-ability enumerator suite are green.
- Phase 3 full local engine and AI suites: green. The monolithic offline `test` entry point cannot
  construct `:mtg-search:testRuntimeClasspath` in this container because its cache lacks Byte Buddy
  1.10.9 and kotlinx-serialization-core 1.9.0. Separately, this container forbids Byte Buddy dynamic
  self-attachment in the unrelated server mocking tests. Neither environment limitation is being
  accommodated by production changes or altered test semantics.
- Phase 3 full CI: run 189 on implementation commit
  `7ba1c8568ca68e64c6e0230fa90cad7d560a9417` is the authoritative green full-matrix gate. Frontend,
  engine, all scenario partitions, tools, server, content, and the aggregate backend job passed.

The laboratory stops at the goldfish-readiness gate. The first untouched control goldfish baseline
requires later explicit approval. Challenger construction, deck materialization, seeds, gameplay
sampling, goldfishing, matchup self-play, and optimization remain prohibited in this phase.

## 1. Branch and base

The dedicated branch `pest-control/lab` was created directly from
`47882cd645caf126afee6cf13a65909806fa40ab` (`Replay frozen Affinity vector on residual policy
candidate`). At inspection time this was the current `main` head and the latest head with completed,
successful CI, Argentum Validation, and Frozen Grixis Affinity Regression Replay checks. A later E2E
Nightly run on the same SHA was cancelled; it did not supersede or fail those completed validation
gates. The previously shared green head `004ccc27...` was eight commits behind this head.

## 2. Permanent Pest Control v1.0 control

The following list is frozen exactly. It must never be silently updated or replaced.

### Main deck — 60 cards

```text
4 Essence Warden
4 Carrier Thrall
4 Blood Researcher
4 Pest Mascot
4 Fierce Witchstalker
3 Generous Ent
4 Follow the Lumarets
4 Weather the Storm
4 Cast Down
2 Bone Shards
2 Chainer's Edict
10 Forest
7 Swamp
4 Jungle Hollow
```

Count verification: 23 creatures + 16 noncreature spells + 21 lands = 60.

### Sideboard — 15 cards

```text
4 Tamiyo's Safekeeping
3 Nature's Claim
3 Snuff Out
3 Suffocating Fumes
2 Pulse of Murasa
```

Count verification: 4 + 3 + 3 + 3 + 2 = 15.

## Proposed Tier-1 challenger (audit-only)

The proposed challenger also counts to 60 cards with a 15-card sideboard. It remains a candidate
architecture only. It has not been built, seeded, played, or approved as a replacement.

```text
4 Essence Warden
4 Bogwater Lumaret
4 Blood Researcher
4 Pest Mascot
3 Marauding Blight-Priest
3 Sagu Wildling
4 Follow the Lumarets
4 Unearth
3 Cast Down
3 Snuff Out
2 Tamiyo's Safekeeping
2 Weather the Storm
6 Forest
5 Swamp
4 Jungle Hollow
3 Illegitimate Business
2 Khalni Garden
```

```text
Sideboard
3 Duress
3 Nihil Spellbomb
3 Suffocating Fumes
2 Masked Vandal
2 Tamiyo's Safekeeping
2 Weather the Storm
```

## Phase 1 audit record (superseded)

Sections 3–7 below preserve the accepted Phase 1 audit as a historical record. Their “missing” and
“recommended” labels describe the pre-implementation state and are superseded by the Phase 2 result
and validation record above.

## 3. Card coverage

Registry legality means the repository legality registry contains `PAUPER`; it does not imply that a
production definition exists or is authoritative. Every one of the 28 unique names across both lists
(including Forest and Swamp) is Pauper-legal as represented by the registry. Cast Down and Chainer's
Edict have uncommon canonical source definitions but are Pauper-legal through common printings
represented by that registry.

| Card | Deck use | Definition | Scenario coverage | Audit result |
|---|---|---|---|---|
| Essence Warden | Both | Present | None direct | Hand-authored trigger watches every other creature entering under any player's control. Needs multiplayer-side and event-multiplicity tests. |
| Carrier Thrall | Control | **Missing** | None | Registry-legal. Dies-to-Eldrazi-Scion needs a new shared predefined token/facade; this is the one identified shared SDK vocabulary gap. |
| Blood Researcher | Both | Present | None direct | Hand-authored `YouGainLife` trigger adds one +1/+1 counter per trigger resolution. Separate-event interaction is unpinned. |
| Pest Mascot | Both | Present | None direct | Hand-authored 2/3 trample with the same per-lifegain counter trigger. It is not a Pest token. Hunt for Specimens tests the separate 1/1 Pest token's death lifegain only. |
| Fierce Witchstalker | Control | Present | None direct | Hand-authored ETB creates Food. Card-specific ETB/token coverage is absent. |
| Generous Ent | Control | Present | None direct | Hand-authored ETB creates Food and Forestcycling `{1}`. No direct ETB or typecycling scenario. |
| Follow the Lumarets | Both | Present | **Direct** | Direct scenario covers maximum one without prior lifegain and maximum two after lifegain, including hand/bottom movement. |
| Weather the Storm | Both | Present | None direct | Hand-authored Storm plus gain 3. Generic Storm copying exists, but no scenario pins each copy as a separate lifegain event or its trigger fan-out. |
| Cast Down | Both | Present | Gym smoke only | Hand-authored nonlegendary-creature target and destruction. No one-card scenario. |
| Bone Shards | Control | **Missing** | None | Registry-legal. Existing choice, sacrifice, discard, and destroy primitives appear sufficient; needs both additional-cost branches and target tests. |
| Chainer's Edict | Control | **Generated / approximate** | None direct | Generated definition composes forced sacrifice and flashback `{5}{B}{B}`. Requires human review and hand-authored normal/flashback scenarios. |
| Forest | Both | Built-in basic | Generic | Baseline land behavior is broadly covered. |
| Swamp | Both | Built-in basic | Generic | Baseline land behavior is broadly covered. |
| Jungle Hollow | Both | Present | Partial direct | Direct scenario pins entering tapped from hand and off-stack. ETB gain 1 and both mana abilities are not card-specifically pinned. |
| Tamiyo's Safekeeping | Both | Present | None direct | Hand-authored hexproof + indestructible until EOT, then gain 2. Needs response-window and lifegain-trigger scenarios. |
| Nature's Claim | Control | **Missing** | None | Registry-legal. Existing artifact/enchantment destruction and target-controller lifegain primitives appear sufficient. |
| Snuff Out | Both | **Missing** | None | Registry-legal. Existing conditional self-alternative-cost, pay-life, color filtering, and destruction primitives appear sufficient; needs normal/alternate path and AI life-payment tests. |
| Suffocating Fumes | Both | **Missing** | None | Registry-legal. Existing opponent-creature group modification and cycling primitives appear sufficient. |
| Pulse of Murasa | Control | **Missing** | None | Registry-legal. Existing graveyard target, return-to-hand, and gain-life primitives appear sufficient. |
| Bogwater Lumaret | Challenger | Present | **Direct** | Direct scenario covers its own ETB, another controlled creature's ETB, and the opponent-creature negative case. |
| Marauding Blight-Priest | Challenger | **Generated / approximate** | None direct | Generated definition drains each opponent once for each `YouGainLife` trigger. Requires human review and separate-event/fan-out tests. |
| Sagu Wildling | Challenger | Present | Partial direct | Direct scenario covers cast/ETB gain 3. Its Roost Seek Omen basic-land search and later creature recast are untested. This card has an Omen, not landcycling. |
| Unearth | Challenger | **Generated / approximate** | Partial direct | Direct scenario exercises a special prepared/Craft return plus agent target choice elsewhere. Ordinary mana-value ≤3, ownership, invalid-target, and cycling boundaries remain unpinned. |
| Illegitimate Business | Challenger | Present | None direct | Hand-authored enters tapped, gain 1, and black/green mana abilities. Needs land sequencing plus lifegain-trigger coverage. |
| Khalni Garden | Challenger | Present | None direct | Hand-authored enters tapped, creates a 0/1 Plant, and taps for green. Needs token/trigger and sequencing coverage. |
| Duress | Challenger SB | Present | None direct | Hand-authored reveal/select noncreature nonland/discard composition. Needs legal-choice and whiff scenarios. |
| Nihil Spellbomb | Challenger SB | **Missing** | None | Registry-legal. Existing targeted graveyard exile, tap/sacrifice, leaves-battlefield, optional mana payment, and draw primitives appear sufficient. |
| Masked Vandal | Challenger SB | **Missing** | None | Registry-legal. Changeling is implemented and tested generically. Existing graveyard exile and artifact/enchantment exile primitives appear sufficient, but the optional “if you do” ETB chain needs a focused composition proof. |

Summary: 18 named nonbasic cards have production definitions, 8 named nonbasic cards are missing,
and Forest/Swamp are built-ins. Three present definitions are explicitly generated/approximate and
require human review: Chainer's Edict, Marauding Blight-Priest, and Unearth.

## 4. Rules and test gaps

No new general rules-engine mechanic is presently proven necessary for the lifegain core. The engine
already emits a gain-life event per resolved gain-life effect; Storm copies are distinct stack
objects, so Weather the Storm should produce separately resolving gain-life effects. The required
proof is missing, however. Before any games, add deterministic scenarios that demonstrate:

1. One and multiple Essence Warden triggers, including opponent creature entry.
2. Blood Researcher and Pest Mascot receiving one counter for each separate lifegain event, and no
   extra counter merely for the amount of life gained.
3. Weather the Storm's original plus Storm copies resolving as separate lifegain events and producing
   the expected Warden/Researcher/Mascot/Blight-Priest trigger fan-out.
4. Follow the Lumarets before and after any earlier life-gain event in the turn.
5. Marauding Blight-Priest draining separately for separate events and each opponent where relevant.
6. Unearth's ordinary target boundaries, cycling, and resolution when the target becomes illegal.
7. Bone Shards' sacrifice and discard choices as additional costs, including nonrefundable costs on
   counter/illegal resolution and sensible automatic payment choices.
8. Chainer's Edict normal cast, opponent sacrifice choice, flashback permission/cost, and exile after
   flashback resolution.
9. Snuff Out's Swamp condition, black-creature exclusion, normal mana cost, four-life alternative
   cost, and low-life legality/agent behavior.
10. Generous Ent Forestcycling; Sagu Wildling separately through its actual Roost Seek Omen flow.
11. Jungle Hollow, Illegitimate Business, and Khalni Garden entering tapped, generating ETB life/token
    events, and supporting black/green sequencing.
12. Each sideboard card's legal targets, costs, zone movements, and negative cases.

## 5. Generic agent-policy coverage and gaps

| Concern | Existing generic coverage | Remaining gap before experiments |
|---|---|---|
| Lifegain-engine deployment | Simulation observes resulting life totals; gain-life/drain effects receive card-intent tags. | Creature permanents do not receive an explicit engine-synergy deployment prior. Add deterministic choice tests first; change policy only with separate approval. |
| +1/+1-counter payoff valuation | Board evaluation values current counters and projected power/toughness. | It does not explicitly value future counters from expected lifegain density. |
| Repeated/separate lifegain events | Rules simulation can expose each trigger and resulting state within its rollout. | No explicit multiplicity/combo policy or regression pins Warden/Researcher/Mascot/Blight-Priest fan-out. |
| Storm sequencing | Generic legal-action simulation can cast spells and Storm copies are engine-supported. | No Storm-count-aware hold/order policy or Weather-specific agent test was found. |
| Removal targeting | Removal/sweeper intent tags, target simulation, hold policy, and removal patience already exist. | Add Pest-list target regressions, especially Cast Down restrictions and Bone Shards payment choice. |
| Protection timing | Hexproof/indestructible/prevention intent tags and response-window hold logic already exist. | Add a Tamiyo's Safekeeping regression; do not assume its gain 2 is valued as engine synergy. |
| Recursion | Graveyard-to-battlefield movement is tagged as recursion; target simulation exists; an Unearth target-choice test exists. | Ordinary Unearth target bounds and timing/value choices need broader deterministic coverage. |
| Landcycling / land search | Cycling/typecycling actions are enumerated and evaluated; generic land sequencing exists. | No dedicated Forestcycling-versus-cast policy test. Sagu Wildling is an Omen and needs its own search/recast sequencing test. |
| Alternate-cost removal | Alternative costs and automatic pay-life/sacrifice/discard payments are enumerated. | No general life-preservation policy proves when Snuff Out should use mana versus four life; Bone Shards branch selection also needs proof. |
| Tapland mana sequencing | Generic usable-mana land ordering and board-presence land sequencing tests exist. | ETB lifegain/token synergy across the three Pest lands is not explicitly valued or pinned. |

## 6. Shared Argentum changes requiring approval

One shared SDK/mtg-sets vocabulary addition appears required to implement Carrier Thrall faithfully:
an authoritative 1/1 colorless Eldrazi Scion predefined token with “Sacrifice this creature: Add
`{C}`,” plus its creation facade and SDK/scenario tests. The repository currently exposes an Eldrazi
Spawn helper, not the required Scion behavior. This audit stops before that change, as required.

No other production engine/SDK change is currently indicated. The other seven missing cards appear
composable from existing primitives, but that conclusion must be confirmed by one-card scenario work.
Any failure that demonstrates a general engine/SDK gap must return for approval rather than being
worked around in a Pest-only definition.

No Gym or agent-policy change is approved or included. The policy gaps above should first be tested
against current generic behavior; any proposed general policy change is a separate approval boundary.

## 7. Recommended validation sequence

1. Obtain approval for the shared Eldrazi Scion SDK/mtg-sets addition; implement and validate it in
   isolation if approved.
2. Human-review and replace or explicitly certify the generated definitions for Chainer's Edict,
   Marauding Blight-Priest, and Unearth, each with a direct scenario.
3. Add the eight missing card definitions one card at a time, with one authoritative scenario file per
   card and no deck construction.
4. Add focused interaction scenarios for separate lifegain events, Storm copies, trigger fan-out,
   counters, drains, Follow the Lumarets, and Pest/tapland entry sequencing.
5. Add deterministic current-agent decision tests for deployment, removal/payment choices,
   protection, recursion, Forestcycling/Omen sequencing, Storm ordering, and alternate costs. Report
   failures before changing production agent behavior.
6. Run card/scenario verification, then the standard shared validation gates. Do not generate seeds or
   run games as part of these steps.
7. Only after an explicit later approval, materialize the challenger as a deck candidate; then stage
   goldfish and matchup self-play as separate, opt-in experimental phases. The frozen control remains
   Pest Control v1.0 regardless of challenger results.
