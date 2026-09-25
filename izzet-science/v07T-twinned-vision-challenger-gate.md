# Izzet Science?! v0.7-T — Twinned Vision Controlled Challenger Gate

Disposition at freeze: `FROZEN_CHALLENGER / UNPROMOTED / SEED-FREE PREPARATION ONLY`

## Immutable identities

- Permanent control: `izzet-science/v0.7-control.md`
- Control SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
- Challenger: `izzet-science/challengers/v0.7-T-twinned-vision.md`
- Challenger SHA-256:
  `0da295e9fcf066728181323dd9acbc0c122f623c99a607709a1e8a85e8936f85`
- Exactly one substitution:
  `-1 Strategic Planning; +1 Twinned Vision`
- The control file is never edited by this program.
- No outcome-dependent tuning of v0.7-T is permitted.

## Why this slot was chosen before gameplay

Strategic Planning is the cleanest bounded flex hypothesis inside the selection/draw package.

Its original paired promotion over Think Twice was positive but modest: +0.122 percentage points
of cumulative deterministic lethal by T10 and +0.120 pp primary-pair access. By contrast, Pieces of
the Puzzle was later promoted on a larger accepted improvement and the deck-level audit explicitly
protects the accepted engine, interaction, tutor, and backup-plan packages from theory-only cuts.

Twinned Vision therefore challenges the least disruptive plausible two-mana selection slot rather
than an established interaction spell or engine piece.

## Card identity and pre-release boundary

Twinned Vision — Reality Fracture #157

- Mana cost: `{1}{U/R}`
- Type: Instant
- Rarity: Common
- Color identity: UR
- Oracle text:
  `Draw a card. If this spell wasn't cast from your hand, draw two cards instead.`
  `Flashback—{1}{U/R}{U/R}, Discard a card.`
- Reality Fracture tabletop release: 2026-10-02.

The common rarity and UR color identity make Twinned Vision structurally eligible for the 99 of
Izzet Guildmage Pauper Commander after release, subject to the governing format's post-release
legality update. Before release, all evidence is explicitly preview-period experimental evidence and
must not be represented as current sanctioned tournament legality.

## Mechanic hypothesis

Twinned Vision is not being tested as a generic two-mana cantrip. The hypothesis is that its
non-hand replacement clause creates unusually high conversion with this deck's existing machinery:

1. a normal hand cast draws one;
2. an Izzet Guildmage spell copy is not cast and therefore draws two;
3. a flashback cast originates in the graveyard and therefore draws two;
4. a Guildmage copy of the flashback spell also draws two;
5. recursion/discard lines may turn an initially low-rate hand cast into later resource depth.

The failure hypothesis is equally important: the hand mode may be too inefficient, flashback may
consume too much mana/card economy, and the card may need Guildmage or a developed engine state to
outperform Strategic Planning's immediate three-card selection.

## Engine qualification before any challenger gameplay

The challenger remains gameplay-disabled until deterministic tests prove:

- exact hand-cast one-card branch;
- exact non-hand two-card branch for a real spell copy;
- Izzet Guildmage can legally target/copy it at mana value 2;
- flashback cost `{1}{U/R}{U/R}` plus discard-a-card is enforced;
- flashback cast draws two and exits the stack to exile;
- Guildmage copy of a flashback cast draws two independently;
- missing discard payment fails atomically;
- hybrid mana, priority, copy handling, discard tracking, and zone transitions use existing generic rails;
- full relevant golden snapshots pass or a fail-closed FRA-only rebless is integrated;
- exact control SHA and challenger SHA remain unchanged.

No official Izzet control seed may be consumed by these tests.

## Challenger telemetry contract

Every later full-engine comparison record must expose, at minimum:

- deck identity and SHA;
- opponent identity and SHA;
- engine SHA and pilot-policy version;
- seed/position identifier;
- starting position / play-draw role;
- mulligan count;
- W/L result and decisive turn;
- Twinned Vision seen/drawn;
- cast from hand count;
- cast from graveyard count;
- copied count;
- cards drawn attributable to each Twinned Vision object;
- Guildmage + Twinned Vision overlap;
- mana spent per realized Twinned Vision card drawn;
- materially enabled conversion/win flag with reason;
- stranded/inefficient flag with reason;
- disruption/recovery markers;
- whether Strategic Planning would have been live/useful in the corresponding control state when
  that comparison is observable without hidden-information leakage.

## Matched comparison — frozen before outcomes

This comparison is separate from the existing official v0.7 sample and must not reuse or alter its
seed vector.

It may begin only after the control program has reached a scientifically valid full-engine execution
state and the Twinned Vision mechanic gate is accepted.

Stage A:
- freeze a fresh 24-position matched vector before any outcomes;
- run exact v0.7 and exact v0.7-T on every position under the same frozen opponent, engine,
  mulligan policy, pilot policy, and starting-position schedule;
- 24 matched pairs / 48 games total;
- no rerolls of valid games.

Primary Stage-A promotion-to-replication trigger:
- among discordant matched pairs, a one-sided exact sign test favoring v0.7-T must be <= 0.10;
- v0.7-T must not increase mulligans by more than 0.25 per game;
- v0.7-T must not show a reproducible early-tempo failure that accounts for at least 3 challenger
  losses in which the control wins;
- engine/protocol-invalid games are quarantined only for documented technical cause.

If Stage A misses the trigger, stop: `KEEP_V07 / TWINNED_VISION_UNPROMOTED`.

Replication:
- if and only if Stage A triggers, freeze an independent fresh 24-position vector before outcomes;
- repeat the exact same comparison with no tuning;
- replication must again have a positive discordant-pair balance and one-sided exact sign-test
  p <= 0.10;
- pooled Stage A + replication must remain positive with no guardrail breach.

Only replicated evidence may justify promotion. Mixed, null, or guardrail-breaching evidence yields
`KEEP_V07 / TWINNED_VISION_UNPROMOTED`.

## Relationship to the active v0.7 program

The permanent control's engine coverage, readiness, and official sample continue on
`mayhem/izzet-science` independently. Challenger preparation may proceed in parallel only while
seed-free. This branch does not authorize control-game execution, alter PR #15, or supersede any
live control receipt.
