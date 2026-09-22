# Pest Control Tier-1 coverage — Mono-Blue Terror readiness

## Boundary

Status: `SEEDLESS_READINESS_BLOCKED_ON_ESCAPE_SUPPORT`

This is a seedless, no-game construction/readiness gate. It does **not** authorize an execution runner,
generate or freeze seeds, initialize a game, expose an outcome, alter Pest Control v1.0, revisit the
accepted Mono Red qualification, or extend/replay the closed Grixis block.

All previously consumed or retired Pest Control seeds remain retired.

## Why Mono-Blue Terror is next

The contemporary Pauper snapshot reviewed on 2026-09-21 continues to place Mono-Blue Terror among the
largest Tier-1/top-metagame archetypes. Pest Control already has an accepted 50-game preboard
qualification against SoterX Mono Red Madness (32-18), while the subsequent Grixis program closed with
mixed/non-confirmatory evidence: the earlier smoke was 3-1, and the pre-registered
`SALVAGE_CONTINUATION_11` finished 4-7 across untouched original Games 2-12.

The prior Grixis readiness record already identified Mono-Blue Terror as the other high-share matchup
deferred principally because its card-support cost was higher. With the Grixis gate now terminal,
Mono-Blue Terror is the next distinct unresolved Tier-1 coverage question.

This ordering is a coverage/provenance decision, not a performance claim about Pest Control and not a
claim that one Terror list represents every build.

Metagame source:
<https://mtgdecks.net/Pauper>

## Frozen opponent identity

- Archetype: Mono-Blue Terror
- Pilot: `Serpico_CC`
- Event: MTGO Pauper Challenge 32 #12854518
- Date: 2026-09-20
- Finish: fourth place, 6-2
- Event source:
  <https://mtgdecks.net/Pauper/mtgo-pauper-challenge-32-12854518-tournament-270693>
- List cross-check:
  <https://decksnipe.com/archetype/pauper/mono-blue-terror>
- Scope: preboard readiness only
- Main SHA-256:
  `6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62`
- Sideboard SHA-256:
  `af4296c5e6af4be3b05b96d0267bd04da12188126e774ef8e63bb16c00bc908c`
- Complete-75 SHA-256:
  `ae25ede2663cbc2fa41b4413381485df962b272791e1b3f49e2d69c45054e7d9`

### Main deck — 60

```text
13 Island
1 Snow-Covered Island
4 Cryptic Serpent
4 Tolarian Terror
2 Deem Inferior
4 Lórien Revealed
2 Artful Dodge
4 Ponder
4 Preordain
2 Sleep of the Dead
4 Brainstorm
4 Dispel
4 Mental Note
4 Thought Scour
4 Counterspell
```

### Sideboard — 15, provenance only

```text
4 Annul
3 Gut Shot
4 Hydroblast
1 Murmuring Mystic
3 Spreading Seas
```

The sideboard is recorded as part of the opponent identity but is not instantiated by this gate.

## Current repository support audit

The exact current registry audit is encoded in
`PestControlTierOneMonoBlueTerrorReadinessTest`.

### Preboard blocker

Support A added and deterministically covered Deem Inferior, Artful Dodge, and Thought Scour.
The remaining preboard blocker is now exactly one identity spanning two slots:

- 2 Sleep of the Dead

The currently supported maindeck identities are:
Island, Snow-Covered Island, Cryptic Serpent, Tolarian Terror, Deem Inferior, Lórien Revealed,
Artful Dodge, Ponder, Preordain, Brainstorm, Dispel, Mental Note, Thought Scour, and Counterspell.

Deem Inferior is implemented with the shared CardsDrawnThisTurn cost-reduction source and the existing
owner-choice second-from-top/bottom library primitive. Artful Dodge uses the existing true Flashback
rail; Thought Scour uses targeted mill-two followed by draw-one.

### Sideboard blocker

Four sideboard identities spanning 11 slots are currently unresolved:

- 3 Gut Shot
- 4 Hydroblast
- 1 Murmuring Mystic
- 3 Spreading Seas

Annul is already expected to resolve. These sideboard gaps do not affect the preboard readiness
question, but they keep postboard work explicitly blocked.

## Fail-closed state

- Pest Control v1.0: unchanged and immutable
- Opponent 60/15 identity: frozen by ordered hashes
- Opponent maindeck support: **blocked — Sleep of the Dead only (1 identity / 2 slots)**
- Runner: `DISABLED`
- Execution adapter: absent
- Official games authorized: `0`
- Official seeds generated: `0`
- Outcome exposure: `0`
- Seed vector: absent
- Gameplay workflow: absent
- Deck optimization/tuning: not authorized by this gate

The activation audit must continue to return all of the following blockers:

1. maindeck card support is incomplete;
2. no execution runner is defined;
3. no official seed vector is frozen;
4. official Mono-Blue Terror games are not authorized.

## Next justified gate

Implement and deterministically validate true **Escape** support, then add Sleep of the Dead on that
rail. Sleep's base effect already maps to the existing tap plus "doesn't untap during its controller's
next untap step" primitive, but Escape must remain distinct from Flashback: it pays {2}{U} plus exile
three other cards from the caster's graveyard and the resolved sorcery returns to the graveyard
normally.

After Sleep of the Dead and focused Escape scenarios are green, rerun the exact readiness audit. Only
a separate later gate may specify a disabled smoke harness. Seed generation and official gameplay
remain outside this authorization.
