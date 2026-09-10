# The Lost Caverns of Ixalan (LCI) — Mechanics

**Set complete — 286 / 286 booster cards implemented.** Counts are over the 286 booster cards
(excluding basic lands), by front-face + back-face oracle text. Every headline mechanic is
modelled by the engine; all cards are done, so "Unimpl" is 0 across the board (kept as a column
for the archived record).

Engine-support column reflects the SDK/rules-engine primitive that models each mechanic.

## Set mechanics

| Mechanic | Total | Unimpl | Engine support | Notes |
|----------|------:|-------:|----------------|-------|
| **Explore** | 25 | 0 | ✅ `ExploreEffect` | Creature reveals top card; land → hand, nonland → +1/+1 counter + may mill. |
| **Discover N** | 19 | 0 | ✅ `DiscoverEffect` | Exile from top until nonland MV ≤ N; cast free or to hand; rest bottomed random (CR 701.57). Fixed or dynamic threshold + optional discovered-card follow-up. |
| **Craft** | 19 | 0 | ✅ `Craft*` + DFC transform | Activate from battlefield (exile materials), transform to back face. All Craft cards are DFCs. |
| **Descend (4 / 8 / fathomless)** | 28 | 0 | ✅ `descended` tracking | Cares about permanent cards in your graveyard; "descend N" / "fathomless descent". |
| **Map token** | 7 | 0 | ✅ `MapToken` | Artifact token; `{1}, {T}, Sacrifice: target creature you control explores.` |
| **Transform / DFC** | 35 | 0 | ✅ `TransformEffects` | Includes Craft backs, MDFC lands (front spell // back land), god // land flips. |
| **Treasure** | 16 | 0 | ✅ Treasure token | Standard artifact token, sac for one mana of any color. LCI cards mint the in-set (`tlci`) printing via the predefined-token `imageUri` override. |
| **Vehicle / Crew** | 6 | 0 | ✅ Crew | Standard vehicles. |
| **Cave (land subtype)** | 12 | 0 | ✅ (subtype only) | New land subtype; some cards care "you control a Cave". |
| **Finality counter** | 5 | 0 | ✅ finality counters | -1/-1-ish removal-on-death counter; used by a few cards. |

## Evergreen / returning keywords present

Flying (25), Trample (14), Vigilance (10), Ward (9), Menace (6), Lifelink (5), Deathtouch (4),
Haste (4), Reach (4), First strike (2), Double strike (1), Flash (12), Defender (1),
Indestructible (1), Hexproof (1). Plus Equip (12), Cycling / typecycling / landcycling (13),
Mill (12), Scry (11), Surveil (1), Enchant (7), Fight (1). **All engine-supported.**

## Status

Complete. All 286 booster cards are implemented as human-authored `cardDef`s with passing
scenario tests, and every headline mechanic is modelled by the engine:

- **Discover N** — `DiscoverEffect` (`Effects.Discover(N)`, CR 701.57): exile from the top until a
  nonland card with mana value ≤ N; cast it free or put it into hand; bottom the rest at random.
  Supports a dynamic threshold (Hurl into History — X = the countered spell's mana value) and a
  follow-up keyed on the discovered card (Hit the Mother Lode's Treasures).
- **Craft / DFC transform**, **Explore**, **Descend / fathomless descent**, **Map** and
  **Finality counters** are all engine-native (see the table above for the backing primitive).

The final wrap-up pass added the predefined-token `imageUri` override so LCI Treasure/creature
tokens mint their in-set (`tlci`) art, and fixed a handful of token images and two card bugs
(Huatli color identity, Abuelo self-targeting).
