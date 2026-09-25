# Pest Control — Medic's Kitesail Reality Fracture challenger

Status: **FROZEN SEED-FREE CHALLENGER — CONTROL UNCHANGED — OFFICIAL GAMEPLAY BLOCKED ON RELEASE/LEGALITY AND QUALIFICATION**

Entry canonical main: `d0c78bb4cca79b7402ba65bd62b5cb621230a054` (accepted Pest Postboard B merged).
Permanent control: **Pest Control v1.0**, immutable.
Control main SHA-256: `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`.
Control sideboard SHA-256: `c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c`.
The 81 historically affected Pest games remain quarantined and are not challenger evidence.

Emergency Phytomedic // Seed Suture and Silence the Echo remain separate seed-free challengers on
`pest-control/reality-fracture-challengers` / PR #166. This experiment does not combine with them.

## Published card identity and release gate

Medic's Kitesail is FRA #173, common, {2}, Artifact — Equipment:

> Equipped creature gets +1/+0 and has flying and "Whenever this creature attacks, you gain 1 life."
> Equip {2}.

Reality Fracture is scheduled for 2026-10-02. At this 2026-09-25 entry point the card is pre-release.
The compiled definition on this branch is therefore **direct-test registration only**: no FRA set object
or repository format-legality admission is created. Before any official challenger seed is loaded, the
live legality source must be re-read after release and explicitly admit the printing to Pauper.

Source receipt: `docs/experiments/pest-control/reality-fracture/medics-kitesail-source.json`.

## Frozen control and one-card challenger

The control is the exact permanent 60:

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

Initial Medic's Kitesail challenger, exactly one substitution:

**-1 Fierce Witchstalker, +1 Medic's Kitesail**

```text
4 Essence Warden
4 Carrier Thrall
4 Blood Researcher
4 Pest Mascot
3 Fierce Witchstalker
1 Medic's Kitesail
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

Canonical challenger file:
`docs/experiments/pest-control/reality-fracture/medics-kitesail-challenger-main.dck`

Canonical `Name,count\n` SHA-256:
`fd12868a89704f278e604e052950ec694ca5aaa36021177420018dfb7ea91851`.

The 15-card control sideboard is unchanged.

### Why Fierce Witchstalker

This is a flex-role substitution, not an engine removal. Witchstalker is a four-copy value creature
outside the Warden / Researcher / Mascot / Follow / Weather lifegain core and outside the dedicated
removal suite. Replacing one copy gives Kitesail room without manufacturing synergy by cutting a
payoff or interaction spell. The resulting one-card reduction in creature density and Follow hit
quality is an intentional cost to measure, not something to tune away after results.

## Challenger hypothesis

Kitesail succeeds only if its reusable attack-time life gain plus Equipment characteristics create
materially better Tier-1 game states than the displaced Witchstalker. Relevant positive mechanisms:

- attack-time life gain before combat damage and second main;
- one Researcher counter and one Mascot counter per Kitesail life-gain event;
- simultaneous payoff triggers from the same gain event;
- Follow the Lumarets Infusion enabled in second main;
- flying and +1 power converting existing creatures into meaningful attackers;
- repeatable value across turns without adding another creature.

Failure mechanisms are equally controlling: stranded Equipment, cast/equip tempo, missed interaction
from spending equip mana, weak flying boards, irrelevant one-life events, lower creature density,
lower Follow hit quality, artifact removal exposure, expensive re-equips, and games where the fourth
Witchstalker would have been superior.

No substitution, mana base, quantity, or pilot-policy change may be altered after outcome exposure.

## Seed-free engine qualification

Before gameplay evidence can be accepted, all of these must pass on one exact source:

1. equip {2} legality and attachment;
2. +1/+0 and flying only on the equipped creature;
3. attack declaration creates the gain-1 trigger before combat damage;
4. Blood Researcher receives exactly one +1/+1 counter from that event;
5. Pest Mascot receives exactly one +1/+1 counter from that event;
6. both payoffs trigger from the same life-gain event;
7. re-equipping moves all granted characteristics and the attack trigger;
8. the Equipment remains in play unattached when its creature is destroyed;
9. Kitesail combat life gain enables Follow the Lumarets' two-card second-main branch;
10. ordinary Equipment timing/cost rules remain unchanged.

The focused implementation is
`mtg-sets/2026/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/fra/cards/MedicsKitesail.kt`;
the exact deterministic proof is
`mtg-sets/2026/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/MedicsKitesailScenarioTest.kt`.

## Frozen comparison design

No seed exists yet. Generation is prohibited until the release/legal-admission gate and exact
seed-free qualification are accepted.

### Primary block

- opponent: exact accepted SoterX Mono Red Madness preboard identity;
- 40 fresh matched assignments;
- 20 Pest-on-play and 20 Pest-on-draw assignments;
- each assignment runs the frozen control and Kitesail challenger under the same opponent identity,
  mulligan policy, production decision policy, starting-position treatment, and rules-engine source;
- use the repository's qualified matched-RNG isolation if available; if exact pair isolation is not
  qualified, execution remains blocked rather than approximated;
- all master seeds must be unique, nonzero, collision-audited against every retired/consumed Pest
  vector, and frozen before Game 1.

Primary meaningful-improvement gate: challenger paired score must exceed control by at least **3 wins
of 40 matched assignments (7.5 percentage points)**, with no protocol defect or reroll. At least half
of the favorable discordant pairs must contain a traceable Kitesail materiality event rather than
mere card presence.

### Independent replication

A passing primary block authorizes one fresh 40-pair replication against the first **distinct,
non-red** Tier-1 opponent in this fixed availability order that has clean current engine evidence and
execution authority at freeze time:

1. Grixis Affinity;
2. Mono-Blue Terror;
3. Monster Tron;
4. Spy Combo.

Availability is determined only by accepted repository gates, never by favorable challenger results.
If none is eligible, a second SoterX block may be descriptive replication but **cannot promote**
Kitesail by itself.

Replication must independently clear the same +3/40 paired-score threshold. Promotion requires both
accepted blocks and pooled net improvement of at least +6 across the 80 matched assignments.

### Regression guard

Even if the win threshold passes, promotion fails if either accepted block shows four or more
additional challenger losses with a trace-supported Kitesail cost as a material cause
(stranded card, equip-mana development/interaction constraint, creature-density/Follow miss, artifact
removal blowout, or displaced-Witchstalker line) unless a protocol-defined correction identifies a
gameplay-engine defect rather than deck performance. No post-hoc card or policy tuning is allowed.

## Required telemetry

Per game, retain at minimum:

- result and decisive turn;
- starting position and opponent configuration;
- Kitesail drawn / cast / equipped;
- first-equipped turn and number of equip activations;
- attack-triggered Kitesail life-gain events;
- Researcher counters attributable to Kitesail;
- Mascot counters attributable to Kitesail;
- Follow upgrades attributable to Kitesail;
- attacks materially enabled by flying;
- damage attributable to +1 power / flying;
- turns equip cost constrained development or interaction;
- stranded Kitesail;
- artifact-removal exposure;
- Follow creature/land hit quality;
- displaced-Witchstalker counterfactual where determinable;
- explicit outcome-materiality classification: positive / negative / present-not-material / absent.

## Stopping rule and disposition

The challenger stops after: seed-free qualification fails; legality admission fails; the primary
block fails its frozen threshold; the replication fails its frozen threshold; or both accepted blocks
pass and their evidence audit completes.

Only the final case permits the normal accepted-control promotion process. Until then:
**PEST CONTROL v1.0 remains the control; MEDIC'S KITESAIL remains UNPROMOTED.**

Entry counters: challenger seeds generated 0; games initialized 0; actions submitted 0; outcomes
exposed 0.
