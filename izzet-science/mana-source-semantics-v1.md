# Mana-Source Semantics v1

Applies to frozen Izzet Science?! v0.1.
Purpose: remove ambiguity before turn-sequence execution.

## Lands
Island: enters untapped; taps U; counts as Island for High Tide.
Mountain: enters untapped; taps R.
Command Tower: enters untapped; taps U or R.
Ash Barrens: enters untapped; taps C; basic landcycling {1} is available from hand.
Evolving Wilds / Terramorphic Expanse: enter untapped, tap+sacrifice to fetch a tapped basic.
Izzet Boilerworks: enters tapped; on entry return a land controlled; taps UR.
Volatile Fjord: enters tapped; taps U/R; counts as Island and Mountain.
Swiftwater Cliffs: enters tapped; taps U/R.
Silverbluff Bridge: enters tapped; taps U/R; artifact land.
Lonely Sandbar: enters tapped; taps U; cycling available.
Forgotten Cave: enters tapped; taps R; cycling available.

## Dedicated infrastructure
Everflowing Chalice:
- MV 0; multikicker {2}.
- Cast only at a declared affordable kick count.
- Enters with that many charge counters; taps for C per counter.
- Development policy chooses the highest kick count that does not reduce current-turn required colored development.

Fellwar Stone:
- Costs 2; taps for one color an opponent land could produce.
- Baseline assumption for multiplayer: do NOT credit it with U or R for threshold claims unless a modeled opponent land supports that color.
- For opponent-free construction baseline, count it as one generic nonland mana for gross-output metrics but mark colored threshold as unresolved/conservative.

Mind Stone:
- Costs 2; taps C.

Star Compass:
- Costs 2; enters tapped.
- Taps for a color a basic land controlled could produce; therefore U if Island controlled, R if Mountain controlled, both choices if both.

Sky Diamond:
- Costs 2; enters tapped; taps U.

Fire Diamond:
- Costs 2; enters tapped; taps R.

Izzet Signet:
- Costs 2.
- {1}, tap -> UR.
- Net contribution when activated is +1 mana and color conversion; threshold model must account for one external input.

Network Terminal:
- Costs 3; enters untapped; taps for one mana of any color.
- Draw/loot ability is excluded from mana-output calculations.

Ur-Golem's Eye:
- Costs 4; taps CC.

Sisay's Ring:
- Costs 4; taps CC.

## Utility creatures affecting Reversal
Ornithopter of Paradise:
- Costs 2; flying; taps for any color.
- Cannot tap for mana the turn it enters unless haste is somehow present.

Silver Myr:
- Costs 2; taps U; summoning sickness applies.

Iron Myr:
- Costs 2; taps R; summoning sickness applies.

## Dramatic Reversal accounting
- Guildmage copy activation requires exactly {2}{U}.
- Reversal untaps all nonland permanents controlled.
- Mana already floating is retained.
- Neutral repeatability: after resolving a copy and retapping eligible sources, the state can again pay {2}{U}.
- Mana-positive: repeating the cycle increases floating mana while retaining ability to pay the next {2}{U}.
- Gross printed output is never substituted for net Signet output.
- Summoning-sick mana creatures cannot contribute.
- Colorless-only sources cannot satisfy U without another blue-producing source/converter.

## High Tide
- Only lands with Island subtype receive the additional U.
- Frozen v0.1 Island-subtype lands: 20 basic Island + Volatile Fjord.
- Non-Island blue lands do not receive High Tide bonus.

Disposition: MANA_SEMANTICS_V1_FROZEN
