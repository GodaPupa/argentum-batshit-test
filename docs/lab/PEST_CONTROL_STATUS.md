# Project Pest Control — Phase 1 Status

- Status: feasibility/card audit only
- Laboratory: Project Pest Control
- Branch: `pest-control/lab`
- Validated base: `47882cd645caf126afee6cf13a65909806fa40ab`
- Control version: Pest Control v1.0 (permanent, immutable)
- Candidate status: proposed Tier-1 architecture only; not approved or implemented

## Laboratory boundary

This document and `docs/experiments/pest-control/` belong exclusively to Project Pest Control.
Batshit Economics/Affinity, `mayhem/project-x`, and their seed vectors and experiment protocols are
read-only dependencies and are outside this laboratory's write and workflow scope. No production
engine, Gym, or agent behavior was changed during this audit. No deck seed was generated and no
game, goldfish, matchup self-play, or optimization run was performed.

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
