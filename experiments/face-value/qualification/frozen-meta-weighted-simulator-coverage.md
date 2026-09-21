# Face Value — frozen-meta weighted simulator coverage

Date: 2026-09-21

## Scope

Protocol v1 permits a metagame-weighted estimate only when exact frozen weights are stated and matchup-level gates remain visible.

This calculation uses the primary MTGDecks Pauper snapshot frozen on 2026-09-20 and only the seven Tier-A archetypes with accepted simulator authority. Mono-Blue Faeries (4.14% primary share) remains HUMAN_ONLY_REQUIRED and is excluded rather than imputed.

Included primary shares:
- Mono-Red Madness 9.93%
- Mono-Blue Terror 6.54%
- Grixis Affinity 6.12%
- Monster Tron 5.36%
- Jund Wildfire 4.77%
- Elves 4.66%
- Mono-Red Rally 4.56%

Included frozen-meta mass: **41.94 percentage points**.

## Accepted postboard matchup estimates

- Madness 28-4 = 87.50%
- Terror 25-7 = 78.125%
- Affinity 26-6 = 81.25%
- Monster Tron 25-7 = 78.125%
- Jund Wildfire 24-8 = 75.00%
- Elves 13-19 = 40.625%
- Rally 20-12 = 62.50%

Renormalizing the frozen primary shares across only these seven accepted simulator-eligible archetypes gives a descriptive weighted postboard game-win estimate of **74.58%**.

Using the same weights on accepted Stage 1B preboard results gives **52.18%**.

Descriptive weighted delta: **+22.40 percentage points**.

## Interpretation limits

This is a simulator engineering estimate, not a human-event win rate and not a Tier-1 declaration.

It excludes Mono-Blue Faeries rather than estimating it. It also covers only the frozen Tier-A snapshot and does not model tournament pairings, match structure, draws, pilot skill, sideboard adaptation, metagame evolution after 2026-09-20, or correlation/uncertainty from 32-game matchup blocks.

The matchup-level weakness remains visible: Elves is 13-19 postboard despite strong coverage elsewhere.

## Next gate

The remaining protocol-level gap is external validity:
1. obtain human-play evidence for Mono-Blue Faeries or keep it explicitly unresolved;
2. separate simulator coverage from real League/Challenge evidence;
3. do not label the deck Tier 1 from simulator results alone.

The frozen Final Forge 75 remains unchanged.
