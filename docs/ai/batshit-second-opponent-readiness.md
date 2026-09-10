# Variant C second-opponent readiness audit

## Boundary

This is a feasibility audit only. Variant C, the permanent original control, the engine, Gym,
agent/evaluator, mulligan policy, and telemetry remain unchanged except for the separately validated
general rescue-policy and Bats-summary corrections. No opponent deck has been added to a harness,
no seeds have been generated, and no games have been run.

## Provenance-locked candidate

The preferred second opponent is Grixis Affinity, using the exact 75 registered by Giandomenico
Pasquale for the 16-player `1.tappa Lega Str Autumn` Pauper event in Santa Teresa di Riva, Italy,
on 2026-09-07, where the list placed first:

- Source: <https://www.mtgtop8.com/event?e=90623&d=887996&f=PAU>
- Source-recorded upstream: <https://www.instagram.com/lega_pauper_str/>
- Main deck: 60; sideboard: 15.

This list is not reconstructed from an archetype average. The quantities below are transcribed from
that single published event entry. A future benchmark declaration should pin this URL, pilot, event,
date, and full list verbatim.

### Published list

| Section | Card | Count | Argentum status |
|---|---|---:|---|
| Land | Drossforge Bridge | 3 | Implemented |
| Land | Great Furnace | 2 | Implemented |
| Land | Mistvault Bridge | 3 | Implemented |
| Land | Seat of the Synod | 3 | Implemented |
| Land | Silverbluff Bridge | 2 | Implemented |
| Land | Swamp | 1 | Implemented basic land |
| Land | Vault of Whispers | 4 | Implemented |
| Creature | Krark-Clan Shaman | 2 | Implemented; no dedicated card scenario |
| Creature | Myr Enforcer | 4 | Implemented; affinity has generic and card-level coverage elsewhere |
| Creature | Refurbished Familiar | 4 | **Missing** |
| Creature | Utrom Monitor | 3 | **Missing** |
| Instant/sorcery | Cast Down | 3 | Implemented; exercised by existing Batshit tests |
| Instant/sorcery | Fanatical Offering | 2 | Implemented; dedicated scenarios |
| Instant/sorcery | Galvanic Blast | 4 | **Missing** |
| Instant/sorcery | Reckoner's Bargain | 4 | **Missing** |
| Instant/sorcery | Thoughtcast | 4 | Implemented; dedicated affinity scenarios |
| Instant/sorcery | Toxin Analysis | 2 | Implemented; no dedicated card scenario |
| Other | Blood Fountain | 3 | Implemented; dedicated scenarios |
| Other | Ichor Wellspring | 4 | **Missing** |
| Other | Makeshift Munitions | 1 | Implemented; dedicated scenarios and agent tests |
| Other | Nihil Spellbomb | 2 | **Missing** |
| Sideboard | Blue Elemental Blast | 2 | Implemented |
| Sideboard | Duress | 4 | Implemented |
| Sideboard | Mesmeric Fiend | 2 | **Missing** |
| Sideboard | Nihil Spellbomb | 1 | **Missing** |
| Sideboard | Red Elemental Blast | 4 | Implemented |
| Sideboard | Unexpected Fangs | 2 | **Missing** |

Argentum currently implements 39 of the 60 maindeck slots and 10 of the 15 sideboard slots. The
missing 21 maindeck slots make even a preboard benchmark **not ready**; substituting cards would
invent a different opponent and is not permitted.

## Rules-coverage readiness

Existing reusable coverage is encouraging but incomplete:

- Generic artifact typing, indestructible, unconditional enters-tapped replacement effects,
  artifact sacrifice costs, affinity cost reduction, deathtouch, lifelink, and non-flying group
  damage all exist in the engine test corpus.
- Thoughtcast, Fanatical Offering, Blood Fountain, and Makeshift Munitions have dedicated scenario
  coverage. The Batshit work also exercises Cast Down and artifact-token sacrifice interactions.
- The six artifact lands are implemented, but the bridge cycle, Mirrodin artifact lands,
  Krark-Clan Shaman, Myr Enforcer, and Toxin Analysis do not all have dedicated scenarios proving
  their exact card scripts.
- Refurbished Familiar, Utrom Monitor, Galvanic Blast, Reckoner's Bargain, Ichor Wellspring, and
  Nihil Spellbomb require implementation plus focused scenarios before a 60-card preboard deck can
  be registered. Mesmeric Fiend and Unexpected Fangs are additionally required before the exact 75
  is postboard-ready.
- Focused interaction coverage should include affinity counting artifact lands/tokens; metalcraft
  thresholds; Wellspring enter/leave draws; Familiar and Monitor triggers; Bargain's sacrifice,
  draw, and life calculation; Nihil Spellbomb's graveyard exile and optional draw; Shaman's
  repeatable activations, simultaneous damage, flying exclusion, Toxin Analysis, and opposing death
  triggers; Blood Fountain token creation and two-creature return; and indestructible artifact-land
  survival where relevant.

No new SDK primitive is yet proven necessary. That conclusion must be rechecked card by card during
implementation, especially for Utrom Monitor and any conditional payment/trigger wording. If a
card cannot be expressed through existing effects, the work must stop at the architectural boundary
and request approval before adding a general engine/SDK primitive.

## Agent-policy readiness

The current agent can legally sequence mana, cast affinity spells, choose targets, pay ordinary
sacrifice costs, and use the already-covered draw/removal cards. That is not sufficient for an
experimental opponent:

- It needs deterministic sacrifice-selection tests preferring expendable tokens and productive
  death/leave artifacts over colored artifact lands, while preserving mana and lethal resources.
- Krark-Clan Shaman needs activation-policy and combat tests for profitable sweep thresholds,
  repeated activations, flying creatures, Toxin Analysis/deathtouch lines, and avoiding destructive
  activations that lose its own board without compensation.
- Galvanic Blast needs metalcraft-aware target and timing tests; the agent must not price two and
  four damage as the same spell.
- Reckoner's Bargain needs tests for life-sensitive sacrifice selection, removal responses, and
  preserving an artifact count needed for affinity/metalcraft when that matters more.
- Ichor Wellspring, Refurbished Familiar, Utrom Monitor, Blood Fountain, and Nihil Spellbomb need
  card-value/trigger tests so one-ply and sacrifice heuristics recognize their on-entry, on-leave,
  graveyard, and recursion value rather than treating them as generic bodies or artifacts.
- The three-color artifact-land manabase needs deterministic land-sequencing and mulligan probes for
  early black/red/blue access, tapped bridges, artifact-count preservation, and casting Thoughtcast
  or an affinity creature without stranding interaction.

## Readiness verdict

Grixis Affinity is strategically distinct and has a provenance-locked exact list, but it is **not
ready for gameplay**. Readiness requires implementing six missing maindeck card identities, adding
their focused scenarios, closing the dedicated coverage gaps in the implemented package, and
validating the opponent's sacrifice/sweeper/metalcraft/affinity policy. The two missing sideboard
identities are not a preboard execution blocker if the harness explicitly strips the sideboard
before registry resolution, but they are required before postboard work or any claim that the full
published 75 is implemented.
