# v0.8-A Mizzium Skin — Frozen Challenger Gate

Control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
Challenger: `izzet-science/challengers/v08A-mizzium-skin.md`
Challenger SHA256: `575bfc592082f5999ebc50cca348b1bfeb157e9fa27bff09b5e092cc1aa496d1`
Change: `-1 Turn Aside, +1 Mizzium Skin`

## Legality and rules scope

The repository's Scryfall snapshot and a fresh Scryfall API lookup identify Mizzium
Skin as a common with Pauper Commander legality. Its Oracle text gives a creature
hexproof until end of turn for `U`; its overload mode is outside the primary metric.

Turn Aside can counter a spell targeting a permanent. It cannot counter an activated
or triggered ability. Mizzium Skin protects Izzet Guildmage from an opponent's
targeted spell or targeted ability by making the target illegal on resolution. It
does not answer a spell countering Lava Spike.

This adds one new fixed hostile event: an opponent-controlled targeted ability would
remove Izzet Guildmage during a legal primary-combo launch. The event is reported
separately and is not assigned a metagame frequency or combined into a win rate.

## Hypothesis

Replacing Turn Aside with the same-cost Mizzium Skin will preserve every existing
goldfish and interaction-readiness metric while creating measurable readiness against
targeted creature-removal abilities.

## Frozen execution

- Paired accepted-control and challenger trajectories
- Pilot: 10,000 samples per deck, seed `0x1A22E700C`
- Confirmation: 100,000 samples per deck, seed `0x1A22E700D`, only after pilot pass
- Horizon: T1–T10
- One execution per stage; no rerolls or replacement seeds
- A separate `validate` dispatch must pass with `sampled_games=0` before the `pilot`
  dispatch is authorized.

The 10,000-game pilot cannot promote the challenger.

## Pilot pass criteria

At T10, all conditions must hold:

1. Guaranteed readiness against targeted Guildmage-removal abilities improves by at
   least 0.30 percentage points.
2. Every pre-v0.8-A standard and interaction telemetry field is exactly equal between
   the paired outputs at every turn T1–T10.
3. The new ability-readiness value is a subset of legal current-turn lethal at every
   turn.
4. Deck identity, source, seed, sample count, output completeness, and artifact hashes
   all validate.

Failure rejects v0.8-A permanently. A pass authorizes exactly one 100,000-game
confirmation on the preregistered seed; only confirmation may promote the challenger.

Disposition: `V08A_PILOT_INADMISSIBLE_REJECTED`

The sole pilot run `35539938514` exposed complete outcomes but failed before writing
its manifest or uploading its artifact. Its comparator also found that legacy U-action
telemetry differed because Mizzium Skin was missing from the harness's one-blue action
class. Post-run audit then found that broad spell counters were incorrectly credited
against the newly introduced ability event. These are modeling defects, not evidence
for promotion. The run is incomplete and inadmissible; v0.8-A is permanently rejected,
no rerun is allowed, and its confirmation seed is retired unused. See
`v08A-mizzium-skin-inadmissible.md`.
