# Pest Control v1.0 — Goldfish Sample #1

Development/engine goldfish only; not matchup evidence. Frozen vector, no rerolls or exclusions.

## Audit verdict: rejected

All 30 games completed in a single execution of the frozen vector, but this sample is **invalid and
is not the first Pest Control v1.0 baseline**.

- General agent-policy defect: Chainer's Edict was cast into an empty opposing battlefield in games
  9 (T4), 13 (T5), 19 (T3), and 27 (T6). Removal merely remaining in hand is separately reported as
  solitaire-stranded interaction and is not treated as deck failure.
- Agent/telemetry defect requiring investigation: game 25 sacrificed its sole Scion for mana, but
  `spentManaSourceIds` attributed no subsequently cast spell to it. The raw run therefore cannot
  prove that the sacrifice funded a relevant spell.
- Telemetry defect: the raw “mana bottleneck” field samples pre-land-drop and repeated priority
  snapshots. It emits demonstrable false positives, such as `MISSING_GREEN_SOURCE` while a Forest
  is in the kept hand and is played that turn. Its 30-game/371-observation aggregate is retained
  below as raw output but is **not a valid genuine-bottleneck statistic**.

Auditable rules/state checks passed: 142 separate life-gain events, 234 total life, Researcher
41 triggers/41 counters, Mascot 71 triggers/71 counters, 23 Weather casts with zero Storm-copy
mismatches, four Thrall deaths/four Scions, and 30 engine-reported combat-lethal terminals. The
seed vector must not be rerun or repaired in place.

## Aggregate

- Games: 30; mulligan games: 5 (16.7%), total mulligans: 7
- Meaningful permanent development by T1/T2/T3: 9/18/27
- Median first Warden: 2.0; median first payoff: 3.5
- Median actual win: 7.0; wins by T4/T5/T6/T7: 0/1/11/23
- Average separate lifegain events: 4.73; average life gained: 7.80
- Weather Storm counts: {0=15, 1=8}
- Maximum Researcher sizes: {2/2=3, 3/3=6, 4/4=2, 5/5=1, 7/7=4}
- Maximum Mascot sizes: {12/13=1, 2/3=2, 3/4=4, 4/5=4, 5/6=1, 6/7=3, 7/8=2, 8/9=1, 9/10=1}
- Warden + payoff coexistence: 17/30 (56.7%)
- Carrier deaths / Scions / mana sacrifices: 4/4/1; funded: {}
- Ent cycles / creature casts: 10/0; cycling rate: 100.0%
- Follow normal / enhanced: 14/2; enhanced rate: 12.5%
- Mana bottlenecks: 30 games, 371 observations
- Jungle Hollow tempo: 19 games, 28 tapped-entry events
- Solitaire-stranded interaction: 25 games, 243 observations
- Functional states: {ENGINE_FUNCTIONAL=24, FAIR_CREATURE_FUNCTIONAL=6}

## Games

| # | Seed | Mull | Open access | T1 | First perm | Win | Class | Life events/gain | R max | M max | Board | Interaction | Bottleneck |
|---:|---|---:|---|---|---:|---|---|---:|---|---|---:|---:|---:|
| 1 | `0x877DB7317239105` | 0 | G/B/uG/uB | play Forest; cast Essence Warden | 1 | T7 COMBAT_LETHAL | FAIR_CREATURE_FUNCTIONAL | 5/9 | — | — | 3 | 0 | 11 |
| 2 | `0xE734EF6E2AE6905` | 0 | G/B/uG/uB | play Jungle Hollow | 3 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 4/4 | 5/5 | 3/4 | 5 | 6 | 12 |
| 3 | `0x6535C962D0425F1` | 0 | G/B/uG/uB | play Forest | 2 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 4/10 | 2/2 | 6/7 | 4 | 6 | 13 |
| 4 | `0x3AE231C40D961AF` | 2 | none | — | 2 | T11 COMBAT_LETHAL | FAIR_CREATURE_FUNCTIONAL | 5/9 | — | — | 3 | 28 | 38 |
| 5 | `0x589AB42AF5A4108` | 0 | G/B/uG/uB | play Swamp | 4 | T8 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 5/15 | 3/3 | — | 4 | 16 | 8 |
| 6 | `0xD41E88D1E6A0ACB` | 0 | G/B/uG/uB | play Forest; cast Essence Warden | 1 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 6/8 | — | 7/8 | 4 | 6 | 6 |
| 7 | `0x9082CF1C35D7C` | 0 | G/B/uG/uB/Ent | play Jungle Hollow | 3 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 4/8 | — | 4/5 | 3 | 0 | 11 |
| 8 | `0x86912BBA77EAE52` | 0 | G/B/uB | play Jungle Hollow | 2 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 6/10 | 7/7 | 7/8 | 5 | 0 | 16 |
| 9 | `0x492298A5DA7DAC7` | 0 | B/uB | play Swamp | 3 | T8 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 2/2 | — | 4/5 | 4 | 10 | 15 |
| 10 | `0xAB55D19D3E8F9EC` | 0 | G/B/uG/uB | play Forest; cast Essence Warden | 1 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 6/10 | — | 4/5 | 3 | 0 | 11 |
| 11 | `0xE27196A0B3A9D06` | 0 | G/uG/Ent | play Forest; typecycle Generous Ent | 3 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 3/9 | 4/4 | — | 3 | 7 | 16 |
| 12 | `0x761B45A1E75EE5D` | 0 | G/B/uG/uB | play Forest; cast Essence Warden | 1 | T5 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 8/12 | — | 9/10 | 4 | 0 | 14 |
| 13 | `0x5501A3AF9739C6D` | 0 | B/uB/Ent | play Swamp; typecycle Generous Ent | 3 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 1/3 | 3/3 | — | 4 | 14 | 5 |
| 14 | `0x84FE09D7E162465` | 0 | G/B/uG/uB | play Forest; cast Essence Warden | 1 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 4/6 | 4/4 | 5/6 | 4 | 4 | 7 |
| 15 | `0x97BE6932F3424E6` | 1 | G/B/uG/uB | play Forest | 2 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 2/4 | 3/3 | — | 5 | 12 | 2 |
| 16 | `0x72E4CFDB07BDDED` | 0 | G/B/uG/uB | play Forest; cast Essence Warden | 1 | T6 COMBAT_LETHAL | FAIR_CREATURE_FUNCTIONAL | 6/10 | — | — | 3 | 3 | 11 |
| 17 | `0xE911C74469D9900` | 0 | G/B/uB | play Jungle Hollow | 2 | T7 COMBAT_LETHAL | FAIR_CREATURE_FUNCTIONAL | 3/7 | 2/2 | 2/3 | 4 | 14 | 9 |
| 18 | `0x24D876891266C15` | 0 | G/B/uG/uB/Ent | play Forest; typecycle Generous Ent | 2 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 8/14 | — | 4/5 | 5 | 11 | 10 |
| 19 | `0x418113833A2FFEC` | 0 | G/B/uG/uB | play Forest; cast Essence Warden | 1 | T6 COMBAT_LETHAL | FAIR_CREATURE_FUNCTIONAL | 5/7 | — | — | 5 | 9 | 10 |
| 20 | `0xF92C6C66D1C227B` | 0 | G/B/uG/uB | play Swamp | 3 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 4/4 | — | 6/7 | 5 | 12 | 16 |
| 21 | `0xF7646EA9E3BB42E` | 0 | G/B/uG/uB | play Swamp | 2 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 4/4 | — | 6/7 | 5 | 6 | 8 |
| 22 | `0xA2549E1A1FAAC7E` | 0 | G/B/uG/Ent | play Jungle Hollow | 3 | T9 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 8/8 | 7/7 | — | 6 | 4 | 8 |
| 23 | `0x2AEA10F9A51B8ED` | 0 | G/B/uG | play Forest; cast Essence Warden | 1 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 7/13 | 7/7 | — | 4 | 2 | 9 |
| 24 | `0x8C38EE062DE7396` | 0 | G/B/uG/uB | play Jungle Hollow | 3 | T6 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 6/12 | 7/7 | — | 4 | 6 | 12 |
| 25 | `0xD82019B3AD968F1` | 1 | G/uG | play Forest | 7 | T14 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 6/12 | 3/3 | 3/4 | 4 | 26 | 6 |
| 26 | `0x9BB211DABD1F49` | 0 | G/B/uB | play Jungle Hollow | 3 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 7/9 | 3/3 | 8/9 | 5 | 5 | 11 |
| 27 | `0xE788E35A769F5AD` | 2 | G/B/uG/uB | play Swamp | 2 | T7 COMBAT_LETHAL | FAIR_CREATURE_FUNCTIONAL | 0/0 | — | 2/3 | 4 | 8 | 17 |
| 28 | `0xF35096C55EC515F` | 1 | G/uG | play Forest | 5 | T9 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 1/1 | 3/3 | 3/4 | 5 | 17 | 12 |
| 29 | `0x218EB85A5D80BED` | 0 | G/uG | play Forest; cast Essence Warden | 1 | T9 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 11/11 | — | 12/13 | 6 | 8 | 27 |
| 30 | `0xC2D50F1C8F23077` | 0 | G/B/uG/uB | play Swamp | 2 | T7 COMBAT_LETHAL | ENGINE_FUNCTIONAL | 1/3 | 2/2 | 3/4 | 5 | 3 | 20 |

## Per-game telemetry

### Game 1 — `0x877DB7317239105`

- Kept hand: [Weather the Storm, Essence Warden, Swamp, Forest, Swamp, Fierce Witchstalker, Forest]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1]/[]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [6]/0; 0/0/[]
- Witchstalker: [4]; Ent cycle/cast: []/[]
- Follow: []; Weather: [PestWeatherCast(turn=2, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=2, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Food, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false)]; Researcher triggers/counters: 0/0; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T3:ENTERED_TAPPED]
- Solitaire-stranded interaction: []
- Genuine mana bottlenecks: [Weather the Storm@T1:MISSING_GREEN_SOURCE, Essence Warden@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T1:TOTAL_MANA_1_OF_4, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=260
- Audit: [clean]

### Game 2 — `0xE734EF6E2AE6905`

- Kept hand: [Follow the Lumarets, Cast Down, Forest, Blood Researcher, Swamp, Swamp, Jungle Hollow]; mulligans: 0; T1: [play Jungle Hollow]
- Warden/Researcher/Mascot casts: [4]/[3, 4]/[6]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: [5]; Ent cycle/cast: [6]/[]
- Follow: [PestFollowCast(turn=2, mode=NORMAL)]; Weather: []
- Lifegain: [PestLifeEvent(turn=1, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=true)]; Researcher triggers/counters: 6/6; Mascot: 1/1
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=true, researcherMascot=true, wardenResearcherMascot=true, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T1:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Cast Down x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Follow the Lumarets@T1:TOTAL_MANA_1_OF_2, Blood Researcher@T1:TOTAL_MANA_1_OF_3, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2, Blood Researcher@T2:TOTAL_MANA_1_OF_3, Blood Researcher@T2:TOTAL_MANA_2_OF_3, Blood Researcher@T3:TOTAL_MANA_2_OF_3, Generous Ent@T4:TOTAL_MANA_3_OF_6, Generous Ent@T4:TOTAL_MANA_4_OF_6, Generous Ent@T5:TOTAL_MANA_4_OF_6, Generous Ent@T6:TOTAL_MANA_4_OF_6]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=225
- Audit: [clean]

### Game 3 — `0x6535C962D0425F1`

- Kept hand: [Carrier Thrall, Weather the Storm, Forest, Swamp, Blood Researcher, Forest, Follow the Lumarets]; mulligans: 0; T1: [play Forest]
- Warden/Researcher/Mascot casts: []/[6]/[4]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2]/0; 0/0/[]
- Witchstalker: [7]; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=3, mode=NORMAL)]; Weather: [PestWeatherCast(turn=5, stormCount=0, expectedCopies=0, observedCopies=0), PestWeatherCast(turn=6, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 4/4
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=true, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T5:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Bone Shards x1@T2:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE, Bone Shards x1@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Carrier Thrall@T1:MISSING_BLACK_SOURCE, Weather the Storm@T1:MISSING_GREEN_SOURCE, Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Blood Researcher@T1:MISSING_BLACK_SOURCE, Follow the Lumarets@T1:TOTAL_MANA_1_OF_2, Carrier Thrall@T2:MISSING_BLACK_SOURCE, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Blood Researcher@T2:MISSING_BLACK_SOURCE, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2, Blood Researcher@T2:TOTAL_MANA_2_OF_3, Blood Researcher@T3:TOTAL_MANA_2_OF_3]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=265
- Audit: [clean]

### Game 4 — `0x3AE231C40D961AF`

- Kept hand: [Weather the Storm, Cast Down, Follow the Lumarets, Carrier Thrall, Essence Warden]; mulligans: 2; T1: []
- Warden/Researcher/Mascot casts: [2, 6]/[]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: [9]; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=7, mode=NORMAL)]; Weather: [PestWeatherCast(turn=11, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=10, amount=3, source=Food, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=11, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false)]; Researcher triggers/counters: 0/0; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Cast Down x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Chainer's Edict x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE, Chainer's Edict x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE, Bone Shards x1@T7:NO_OPPONENT_CREATURE, Chainer's Edict x1@T7:NO_OPPONENT_CREATURE, Cast Down x1@T8:NO_OPPONENT_CREATURE, Bone Shards x1@T8:NO_OPPONENT_CREATURE, Chainer's Edict x1@T8:NO_OPPONENT_CREATURE, Cast Down x1@T9:NO_OPPONENT_CREATURE, Bone Shards x1@T9:NO_OPPONENT_CREATURE, Chainer's Edict x1@T9:NO_OPPONENT_CREATURE, Cast Down x1@T10:NO_OPPONENT_CREATURE, Bone Shards x1@T10:NO_OPPONENT_CREATURE, Chainer's Edict x1@T10:NO_OPPONENT_CREATURE, Cast Down x1@T11:NO_OPPONENT_CREATURE, Bone Shards x1@T11:NO_OPPONENT_CREATURE, Chainer's Edict x1@T11:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Weather the Storm@T1:MISSING_GREEN_SOURCE, Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Essence Warden@T1:MISSING_GREEN_SOURCE, Weather the Storm@T2:MISSING_GREEN_SOURCE, Follow the Lumarets@T2:MISSING_GREEN_SOURCE, Carrier Thrall@T2:MISSING_BLACK_SOURCE, Essence Warden@T2:MISSING_GREEN_SOURCE, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2, Weather the Storm@T3:TOTAL_MANA_1_OF_2, Follow the Lumarets@T3:TOTAL_MANA_1_OF_2, Carrier Thrall@T3:MISSING_BLACK_SOURCE, Weather the Storm@T4:TOTAL_MANA_1_OF_2, Follow the Lumarets@T4:TOTAL_MANA_1_OF_2, Carrier Thrall@T4:MISSING_BLACK_SOURCE, Weather the Storm@T5:TOTAL_MANA_1_OF_2, Follow the Lumarets@T5:TOTAL_MANA_1_OF_2, Carrier Thrall@T5:MISSING_BLACK_SOURCE, Fierce Witchstalker@T5:TOTAL_MANA_1_OF_4, Weather the Storm@T6:TOTAL_MANA_1_OF_2, Follow the Lumarets@T6:TOTAL_MANA_1_OF_2, Carrier Thrall@T6:MISSING_BLACK_SOURCE, Fierce Witchstalker@T6:TOTAL_MANA_1_OF_4, Weather the Storm@T7:TOTAL_MANA_1_OF_2, Follow the Lumarets@T7:TOTAL_MANA_1_OF_2, Carrier Thrall@T7:MISSING_BLACK_SOURCE, Fierce Witchstalker@T7:TOTAL_MANA_1_OF_4, Fierce Witchstalker@T7:TOTAL_MANA_2_OF_4, Carrier Thrall@T8:MISSING_BLACK_SOURCE, Fierce Witchstalker@T8:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T8:TOTAL_MANA_3_OF_4, Carrier Thrall@T9:MISSING_BLACK_SOURCE, Fierce Witchstalker@T9:TOTAL_MANA_3_OF_4, Carrier Thrall@T10:MISSING_BLACK_SOURCE, Pest Mascot@T10:MISSING_BLACK_SOURCE, Carrier Thrall@T11:MISSING_BLACK_SOURCE, Pest Mascot@T11:MISSING_BLACK_SOURCE]
- Terminal: T11 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=403
- Audit: [clean]

### Game 5 — `0x589AB42AF5A4108`

- Kept hand: [Swamp, Weather the Storm, Bone Shards, Forest, Forest, Cast Down, Fierce Witchstalker]; mulligans: 0; T1: [play Swamp]
- Warden/Researcher/Mascot casts: []/[7]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [7]/0; 0/0/[]
- Witchstalker: [4, 8]; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=6, mode=ENHANCED)]; Weather: [PestWeatherCast(turn=5, stormCount=0, expectedCopies=0, observedCopies=0), PestWeatherCast(turn=5, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=6, amount=3, source=Food, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=8, amount=3, source=Food, researcherPresent=true, mascotPresent=false)]; Researcher triggers/counters: 1/1; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Bone Shards x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T1:NO_OPPONENT_CREATURE, Bone Shards x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Bone Shards x1@T7:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE, Bone Shards x1@T8:NO_OPPONENT_CREATURE, Cast Down x1@T8:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Weather the Storm@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Weather the Storm@T2:MISSING_GREEN_SOURCE, Fierce Witchstalker@T2:MISSING_GREEN_SOURCE, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T8 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=287
- Audit: [clean]

### Game 6 — `0xD41E88D1E6A0ACB`

- Kept hand: [Bone Shards, Swamp, Essence Warden, Swamp, Pest Mascot, Forest, Swamp]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1]/[]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: [4]/0; 0/0/[]
- Witchstalker: [6]; Ent cycle/cast: []/[]
- Follow: []; Weather: []
- Lifegain: [PestLifeEvent(turn=2, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=4, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=3, source=Food, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 5/5
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T2:ENTERED_TAPPED, Jungle Hollow@T4:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Bone Shards x1@T1:NO_OPPONENT_CREATURE, Bone Shards x1@T2:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Essence Warden@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Pest Mascot@T1:MISSING_BLACK_SOURCE, Pest Mascot@T2:MISSING_BLACK_SOURCE, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Pest Mascot@T3:TOTAL_MANA_2_OF_3]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=234
- Audit: [clean]

### Game 7 — `0x9082CF1C35D7C`

- Kept hand: [Jungle Hollow, Forest, Jungle Hollow, Generous Ent, Swamp, Swamp, Fierce Witchstalker]; mulligans: 0; T1: [play Jungle Hollow]
- Warden/Researcher/Mascot casts: []/[]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: [5]/0; 0/0/[]
- Witchstalker: [4]; Ent cycle/cast: [2]/[]
- Follow: []; Weather: [PestWeatherCast(turn=6, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=1, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=2, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Food, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 2/2
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T1:ENTERED_TAPPED, Jungle Hollow@T2:ENTERED_TAPPED]
- Solitaire-stranded interaction: []
- Genuine mana bottlenecks: [Generous Ent@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Generous Ent@T1:TOTAL_MANA_1_OF_6, Fierce Witchstalker@T1:TOTAL_MANA_1_OF_4, Generous Ent@T2:TOTAL_MANA_1_OF_6, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Pest Mascot@T3:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=248
- Audit: [clean]

### Game 8 — `0x86912BBA77EAE52`

- Kept hand: [Pest Mascot, Jungle Hollow, Blood Researcher, Swamp, Swamp, Carrier Thrall, Weather the Storm]; mulligans: 0; T1: [play Jungle Hollow]
- Warden/Researcher/Mascot casts: [4]/[5]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2, 6]/0; 0/0/[]
- Witchstalker: []; Ent cycle/cast: []/[]
- Follow: []; Weather: [PestWeatherCast(turn=6, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=1, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Jungle Hollow, researcherPresent=true, mascotPresent=true), PestLifeEvent(turn=6, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=true), PestLifeEvent(turn=6, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=true), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=true)]; Researcher triggers/counters: 5/5; Mascot: 5/5
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=true, researcherMascot=true, wardenResearcherMascot=true, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T1:ENTERED_TAPPED, Jungle Hollow@T5:ENTERED_TAPPED]
- Solitaire-stranded interaction: []
- Genuine mana bottlenecks: [Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Weather the Storm@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:TOTAL_MANA_1_OF_3, Blood Researcher@T1:TOTAL_MANA_1_OF_3, Carrier Thrall@T1:TOTAL_MANA_1_OF_2, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Pest Mascot@T2:TOTAL_MANA_1_OF_3, Blood Researcher@T2:TOTAL_MANA_1_OF_3, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Blood Researcher@T2:TOTAL_MANA_2_OF_3, Pest Mascot@T3:TOTAL_MANA_2_OF_3, Blood Researcher@T3:TOTAL_MANA_2_OF_3]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=238
- Audit: [clean]

### Game 9 — `0x492298A5DA7DAC7`

- Kept hand: [Fierce Witchstalker, Chainer's Edict, Swamp, Swamp, Fierce Witchstalker, Follow the Lumarets, Pest Mascot]; mulligans: 0; T1: [play Swamp]
- Warden/Researcher/Mascot casts: [5]/[]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: [7, 8]; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=6, mode=NORMAL), PestFollowCast(turn=6, mode=NORMAL)]; Weather: []
- Lifegain: [PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=8, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 2/2
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: []
- Solitaire-stranded interaction: [Chainer's Edict x1@T1:NO_OPPONENT_CREATURE, Chainer's Edict x1@T2:NO_OPPONENT_CREATURE, Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE, Cast Down x1@T8:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Pest Mascot@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T2:MISSING_GREEN_SOURCE, Follow the Lumarets@T2:MISSING_GREEN_SOURCE, Pest Mascot@T2:MISSING_GREEN_SOURCE, Fierce Witchstalker@T3:MISSING_GREEN_SOURCE, Follow the Lumarets@T3:MISSING_GREEN_SOURCE, Pest Mascot@T3:MISSING_GREEN_SOURCE, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T5:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T6:TOTAL_MANA_3_OF_4, Generous Ent@T8:TOTAL_MANA_5_OF_6]
- Terminal: T8 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=289
- Audit: [agent cast Chainer's Edict into an empty opposing battlefield on T4]

### Game 10 — `0xAB55D19D3E8F9EC`

- Kept hand: [Swamp, Follow the Lumarets, Forest, Jungle Hollow, Forest, Essence Warden, Fierce Witchstalker]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1]/[]/[6]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: [4]; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=3, mode=NORMAL), PestFollowCast(turn=6, mode=NORMAL)]; Weather: [PestWeatherCast(turn=7, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=2, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Food, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=7, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 2/2
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T2:ENTERED_TAPPED, Jungle Hollow@T3:ENTERED_TAPPED]
- Solitaire-stranded interaction: []
- Genuine mana bottlenecks: [Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Essence Warden@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Follow the Lumarets@T1:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T1:TOTAL_MANA_1_OF_4, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=274
- Audit: [clean]

### Game 11 — `0xE27196A0B3A9D06`

- Kept hand: [Fierce Witchstalker, Blood Researcher, Generous Ent, Forest, Weather the Storm, Forest, Forest]; mulligans: 0; T1: [play Forest, typecycle Generous Ent]
- Warden/Researcher/Mascot casts: []/[3, 5]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: [4]; Ent cycle/cast: [1]/[]
- Follow: []; Weather: [PestWeatherCast(turn=2, stormCount=0, expectedCopies=0, observedCopies=0), PestWeatherCast(turn=7, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=2, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Food, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=7, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=false)]; Researcher triggers/counters: 4/4; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Bone Shards x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Bone Shards x1@T7:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Generous Ent@T1:MISSING_GREEN_SOURCE, Weather the Storm@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T1:TOTAL_MANA_1_OF_4, Blood Researcher@T1:MISSING_BLACK_SOURCE, Generous Ent@T1:TOTAL_MANA_1_OF_6, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Blood Researcher@T2:MISSING_BLACK_SOURCE, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Blood Researcher@T3:MISSING_BLACK_SOURCE, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=252
- Audit: [clean]

### Game 12 — `0x761B45A1E75EE5D`

- Kept hand: [Essence Warden, Fierce Witchstalker, Jungle Hollow, Swamp, Forest, Swamp, Pest Mascot]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1, 4]/[]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: [5]; Ent cycle/cast: []/[]
- Follow: []; Weather: [PestWeatherCast(turn=4, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=2, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 7/7
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T2:ENTERED_TAPPED, Jungle Hollow@T5:ENTERED_TAPPED]
- Solitaire-stranded interaction: []
- Genuine mana bottlenecks: [Essence Warden@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Fierce Witchstalker@T1:TOTAL_MANA_1_OF_4, Pest Mascot@T1:MISSING_BLACK_SOURCE, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Pest Mascot@T2:MISSING_BLACK_SOURCE, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Pest Mascot@T3:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T5 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=206
- Audit: [clean]

### Game 13 — `0x5501A3AF9739C6D`

- Kept hand: [Swamp, Generous Ent, Swamp, Blood Researcher, Chainer's Edict, Cast Down, Blood Researcher]; mulligans: 0; T1: [play Swamp, typecycle Generous Ent]
- Warden/Researcher/Mascot casts: []/[3, 4, 6]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [7]/0; 0/0/[]
- Witchstalker: []; Ent cycle/cast: [1]/[]
- Follow: []; Weather: [PestWeatherCast(turn=5, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=false)]; Researcher triggers/counters: 2/2; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Chainer's Edict x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T1:NO_OPPONENT_CREATURE, Chainer's Edict x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x1@T5:NO_OPPONENT_CREATURE, Cast Down x2@T5:NO_OPPONENT_CREATURE, Cast Down x2@T6:NO_OPPONENT_CREATURE, Chainer's Edict x1@T6:NO_OPPONENT_CREATURE, Cast Down x2@T7:NO_OPPONENT_CREATURE, Chainer's Edict x1@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Generous Ent@T1:MISSING_GREEN_SOURCE, Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Blood Researcher@T1:MISSING_GREEN_SOURCE, Blood Researcher@T2:MISSING_GREEN_SOURCE, Blood Researcher@T3:MISSING_GREEN_SOURCE]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=242
- Audit: [agent cast Chainer's Edict into an empty opposing battlefield on T5]

### Game 14 — `0x84FE09D7E162465`

- Kept hand: [Essence Warden, Pest Mascot, Forest, Swamp, Forest, Forest, Swamp]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1, 6]/[4]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: []; Ent cycle/cast: []/[]
- Follow: []; Weather: [PestWeatherCast(turn=2, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=2, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=true), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=true)]; Researcher triggers/counters: 2/2; Mascot: 3/3
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=true, researcherMascot=true, wardenResearcherMascot=true, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: []
- Solitaire-stranded interaction: [Cast Down x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Essence Warden@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Pest Mascot@T1:MISSING_BLACK_SOURCE, Pest Mascot@T2:MISSING_BLACK_SOURCE, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Pest Mascot@T3:TOTAL_MANA_2_OF_3]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=230
- Audit: [clean]

### Game 15 — `0x97BE6932F3424E6`

- Kept hand: [Forest, Carrier Thrall, Swamp, Forest, Carrier Thrall, Bone Shards]; mulligans: 1; T1: [play Forest]
- Warden/Researcher/Mascot casts: [6]/[7]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2, 3]/0; 0/0/[]
- Witchstalker: [5]; Ent cycle/cast: []/[]
- Follow: []; Weather: []
- Lifegain: [PestLifeEvent(turn=6, amount=3, source=Food, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false)]; Researcher triggers/counters: 1/1; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Bone Shards x1@T1:NO_OPPONENT_CREATURE, Bone Shards x1@T2:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x2@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Chainer's Edict x2@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE, Chainer's Edict x2@T6:NO_OPPONENT_CREATURE, Bone Shards x1@T7:NO_OPPONENT_CREATURE, Chainer's Edict x2@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Carrier Thrall@T1:MISSING_BLACK_SOURCE, Carrier Thrall@T2:MISSING_BLACK_SOURCE]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=248
- Audit: [clean]

### Game 16 — `0x72E4CFDB07BDDED`

- Kept hand: [Jungle Hollow, Carrier Thrall, Jungle Hollow, Forest, Weather the Storm, Swamp, Essence Warden]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1]/[]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2]/0; 0/0/[]
- Witchstalker: [4]; Ent cycle/cast: []/[]
- Follow: []; Weather: [PestWeatherCast(turn=3, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=2, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Food, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false)]; Researcher triggers/counters: 0/0; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T3:ENTERED_TAPPED, Jungle Hollow@T5:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Bone Shards x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Carrier Thrall@T1:MISSING_BLACK_SOURCE, Weather the Storm@T1:MISSING_GREEN_SOURCE, Essence Warden@T1:MISSING_GREEN_SOURCE, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Carrier Thrall@T2:MISSING_BLACK_SOURCE, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=224
- Audit: [clean]

### Game 17 — `0xE911C74469D9900`

- Kept hand: [Weather the Storm, Swamp, Swamp, Chainer's Edict, Jungle Hollow, Carrier Thrall, Cast Down]; mulligans: 0; T1: [play Jungle Hollow]
- Warden/Researcher/Mascot casts: []/[7]/[6]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2]/0; 0/0/[]
- Witchstalker: [4]; Ent cycle/cast: []/[]
- Follow: []; Weather: [PestWeatherCast(turn=3, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=1, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Food, researcherPresent=false, mascotPresent=false)]; Researcher triggers/counters: 0/0; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=true, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T1:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Chainer's Edict x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T1:NO_OPPONENT_CREATURE, Chainer's Edict x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x1@T5:NO_OPPONENT_CREATURE, Cast Down x2@T5:NO_OPPONENT_CREATURE, Chainer's Edict x1@T6:NO_OPPONENT_CREATURE, Cast Down x2@T6:NO_OPPONENT_CREATURE, Chainer's Edict x1@T7:NO_OPPONENT_CREATURE, Cast Down x2@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Weather the Storm@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Carrier Thrall@T1:TOTAL_MANA_1_OF_2, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=249
- Audit: [clean]

### Game 18 — `0x24D876891266C15`

- Kept hand: [Forest, Essence Warden, Follow the Lumarets, Cast Down, Swamp, Generous Ent, Forest]; mulligans: 0; T1: [play Forest, typecycle Generous Ent]
- Warden/Researcher/Mascot casts: [2]/[]/[7]
- Carrier casts/deaths; Scions created/sacrificed/funded: [5]/0; 0/0/[]
- Witchstalker: [4, 6]; Ent cycle/cast: [1]/[]
- Follow: [PestFollowCast(turn=3, mode=NORMAL)]; Weather: [PestWeatherCast(turn=5, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=3, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=7, amount=3, source=Food, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 2/2
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T3:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Cast Down x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Chainer's Edict x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Chainer's Edict x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE, Chainer's Edict x1@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Essence Warden@T1:MISSING_GREEN_SOURCE, Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Generous Ent@T1:MISSING_GREEN_SOURCE, Follow the Lumarets@T1:TOTAL_MANA_1_OF_2, Generous Ent@T1:TOTAL_MANA_1_OF_6, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=274
- Audit: [clean]

### Game 19 — `0x418113833A2FFEC`

- Kept hand: [Carrier Thrall, Fierce Witchstalker, Essence Warden, Bone Shards, Chainer's Edict, Forest, Swamp]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1]/[]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2, 4, 6]/0; 0/0/[]
- Witchstalker: [5]; Ent cycle/cast: []/[]
- Follow: []; Weather: []
- Lifegain: [PestLifeEvent(turn=2, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=6, amount=3, source=Food, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false)]; Researcher triggers/counters: 0/0; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Bone Shards x1@T1:NO_OPPONENT_CREATURE, Chainer's Edict x1@T1:NO_OPPONENT_CREATURE, Bone Shards x1@T2:NO_OPPONENT_CREATURE, Chainer's Edict x1@T2:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Carrier Thrall@T1:MISSING_BLACK_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Essence Warden@T1:MISSING_GREEN_SOURCE, Fierce Witchstalker@T1:TOTAL_MANA_1_OF_4, Carrier Thrall@T2:MISSING_BLACK_SOURCE, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=227
- Audit: [agent cast Chainer's Edict into an empty opposing battlefield on T3]

### Game 20 — `0xF92C6C66D1C227B`

- Kept hand: [Follow the Lumarets, Cast Down, Swamp, Forest, Carrier Thrall, Bone Shards, Fierce Witchstalker]; mulligans: 0; T1: [play Swamp]
- Warden/Researcher/Mascot casts: [4]/[]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: [4, 5]/0; 0/0/[]
- Witchstalker: [6]; Ent cycle/cast: [5]/[]
- Follow: [PestFollowCast(turn=2, mode=NORMAL)]; Weather: []
- Lifegain: [PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 4/4
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T6:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Cast Down x1@T1:NO_OPPONENT_CREATURE, Bone Shards x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Bone Shards x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:TOTAL_MANA_1_OF_2, Follow the Lumarets@T2:MISSING_GREEN_SOURCE, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Fierce Witchstalker@T2:MISSING_GREEN_SOURCE, Pest Mascot@T2:MISSING_GREEN_SOURCE, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Pest Mascot@T3:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T5:TOTAL_MANA_3_OF_4, Generous Ent@T5:TOTAL_MANA_3_OF_6]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=219
- Audit: [clean]

### Game 21 — `0xF7646EA9E3BB42E`

- Kept hand: [Swamp, Forest, Cast Down, Forest, Cast Down, Pest Mascot, Carrier Thrall]; mulligans: 0; T1: [play Swamp]
- Warden/Researcher/Mascot casts: [3]/[]/[4]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2, 5]/0; 0/0/[]
- Witchstalker: [6]; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=3, mode=NORMAL)]; Weather: []
- Lifegain: [PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 4/4
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T5:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Cast Down x2@T1:NO_OPPONENT_CREATURE, Cast Down x2@T2:NO_OPPONENT_CREATURE, Cast Down x2@T3:NO_OPPONENT_CREATURE, Cast Down x2@T4:NO_OPPONENT_CREATURE, Cast Down x2@T5:NO_OPPONENT_CREATURE, Cast Down x2@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Pest Mascot@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:TOTAL_MANA_1_OF_2, Pest Mascot@T2:MISSING_GREEN_SOURCE, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Pest Mascot@T3:TOTAL_MANA_2_OF_3]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=225
- Audit: [clean]

### Game 22 — `0xA2549E1A1FAAC7E`

- Kept hand: [Forest, Jungle Hollow, Jungle Hollow, Forest, Forest, Generous Ent, Carrier Thrall]; mulligans: 0; T1: [play Jungle Hollow]
- Warden/Researcher/Mascot casts: [5, 8]/[7, 9]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [3, 6]/1; 1/0/[]
- Witchstalker: []; Ent cycle/cast: [2, 3]/[]
- Follow: [PestFollowCast(turn=6, mode=NORMAL)]; Weather: []
- Lifegain: [PestLifeEvent(turn=1, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=2, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=8, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false)]; Researcher triggers/counters: 7/7; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T1:ENTERED_TAPPED, Jungle Hollow@T2:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Cast Down x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Generous Ent@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Generous Ent@T1:TOTAL_MANA_1_OF_6, Carrier Thrall@T1:TOTAL_MANA_1_OF_2, Generous Ent@T2:TOTAL_MANA_1_OF_6, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Generous Ent@T2:TOTAL_MANA_2_OF_6, Generous Ent@T3:TOTAL_MANA_2_OF_6]
- Terminal: T9 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=355
- Audit: [clean]

### Game 23 — `0x2AEA10F9A51B8ED`

- Kept hand: [Weather the Storm, Essence Warden, Forest, Follow the Lumarets, Forest, Jungle Hollow, Carrier Thrall]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1]/[4]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: [3, 6]/0; 0/0/[]
- Witchstalker: []; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=6, mode=ENHANCED)]; Weather: [PestWeatherCast(turn=5, stormCount=0, expectedCopies=0, observedCopies=0), PestWeatherCast(turn=5, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=2, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=3, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=5, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false)]; Researcher triggers/counters: 5/5; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T2:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Bone Shards x1@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Weather the Storm@T1:MISSING_GREEN_SOURCE, Essence Warden@T1:MISSING_GREEN_SOURCE, Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Follow the Lumarets@T1:TOTAL_MANA_1_OF_2, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2, Carrier Thrall@T2:MISSING_BLACK_SOURCE]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=241
- Audit: [clean]

### Game 24 — `0x8C38EE062DE7396`

- Kept hand: [Weather the Storm, Forest, Jungle Hollow, Blood Researcher, Cast Down, Blood Researcher, Swamp]; mulligans: 0; T1: [play Jungle Hollow]
- Warden/Researcher/Mascot casts: [4, 6]/[3]/[]
- Carrier casts/deaths; Scions created/sacrificed/funded: []/0; 0/0/[]
- Witchstalker: [5]; Ent cycle/cast: [2]/[]
- Follow: []; Weather: [PestWeatherCast(turn=4, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=1, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=6, amount=3, source=Food, researcherPresent=true, mascotPresent=false), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=false)]; Researcher triggers/counters: 5/5; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T1:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Cast Down x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Weather the Storm@T1:MISSING_GREEN_SOURCE, Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Blood Researcher@T1:TOTAL_MANA_1_OF_3, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Blood Researcher@T2:TOTAL_MANA_1_OF_3, Generous Ent@T2:TOTAL_MANA_1_OF_6, Blood Researcher@T2:TOTAL_MANA_2_OF_3, Blood Researcher@T3:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4]
- Terminal: T6 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=222
- Audit: [clean]

### Game 25 — `0xD82019B3AD968F1`

- Kept hand: [Forest, Weather the Storm, Forest, Follow the Lumarets, Weather the Storm, Bone Shards]; mulligans: 1; T1: [play Forest]
- Warden/Researcher/Mascot casts: [13]/[12]/[7]
- Carrier casts/deaths; Scions created/sacrificed/funded: [11]/1; 1/1/[]
- Witchstalker: []; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=2, mode=NORMAL), PestFollowCast(turn=7, mode=NORMAL)]; Weather: [PestWeatherCast(turn=4, stormCount=0, expectedCopies=0, observedCopies=0), PestWeatherCast(turn=4, stormCount=1, expectedCopies=1, observedCopies=1)]
- Lifegain: [PestLifeEvent(turn=3, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=5, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=13, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=true)]; Researcher triggers/counters: 1/1; Mascot: 1/1
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=true, researcherMascot=true, wardenResearcherMascot=true, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T3:ENTERED_TAPPED, Jungle Hollow@T5:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Bone Shards x1@T1:NO_OPPONENT_CREATURE, Bone Shards x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Bone Shards x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Bone Shards x1@T4:NO_OPPONENT_CREATURE, Cast Down x2@T4:NO_OPPONENT_CREATURE, Bone Shards x1@T5:NO_OPPONENT_CREATURE, Cast Down x2@T5:NO_OPPONENT_CREATURE, Bone Shards x1@T6:NO_OPPONENT_CREATURE, Cast Down x2@T6:NO_OPPONENT_CREATURE, Bone Shards x1@T7:NO_OPPONENT_CREATURE, Cast Down x2@T7:NO_OPPONENT_CREATURE, Bone Shards x2@T8:NO_OPPONENT_CREATURE, Cast Down x2@T8:NO_OPPONENT_CREATURE, Bone Shards x2@T9:NO_OPPONENT_CREATURE, Cast Down x2@T9:NO_OPPONENT_CREATURE, Bone Shards x2@T10:NO_OPPONENT_CREATURE, Cast Down x2@T10:NO_OPPONENT_CREATURE, Bone Shards x2@T11:NO_OPPONENT_CREATURE, Cast Down x2@T11:NO_OPPONENT_CREATURE, Bone Shards x2@T12:NO_OPPONENT_CREATURE, Cast Down x2@T12:NO_OPPONENT_CREATURE, Bone Shards x2@T13:NO_OPPONENT_CREATURE, Cast Down x2@T13:NO_OPPONENT_CREATURE, Bone Shards x2@T14:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Weather the Storm@T1:MISSING_GREEN_SOURCE, Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Weather the Storm@T1:TOTAL_MANA_1_OF_2, Follow the Lumarets@T1:TOTAL_MANA_1_OF_2, Weather the Storm@T2:TOTAL_MANA_1_OF_2, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2]
- Terminal: T14 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=499
- Audit: [clean]

### Game 26 — `0x9BB211DABD1F49`

- Kept hand: [Cast Down, Pest Mascot, Carrier Thrall, Follow the Lumarets, Jungle Hollow, Swamp, Swamp]; mulligans: 0; T1: [play Jungle Hollow]
- Warden/Researcher/Mascot casts: [4]/[7]/[3]
- Carrier casts/deaths; Scions created/sacrificed/funded: [4]/1; 1/0/[]
- Witchstalker: [6]; Ent cycle/cast: []/[]
- Follow: [PestFollowCast(turn=2, mode=NORMAL)]; Weather: []
- Lifegain: [PestLifeEvent(turn=1, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=4, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=4, amount=1, source=Jungle Hollow, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=5, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=6, amount=3, source=Food, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=true, mascotPresent=true)]; Researcher triggers/counters: 1/1; Mascot: 6/6
- Coexistence: PestCoexistence(wardenResearcher=true, wardenMascot=true, researcherMascot=true, wardenResearcherMascot=true, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: [Jungle Hollow@T1:ENTERED_TAPPED, Jungle Hollow@T4:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Cast Down x1@T1:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Follow the Lumarets@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:TOTAL_MANA_1_OF_3, Carrier Thrall@T1:TOTAL_MANA_1_OF_2, Follow the Lumarets@T1:TOTAL_MANA_1_OF_2, Pest Mascot@T2:TOTAL_MANA_1_OF_3, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Follow the Lumarets@T2:TOTAL_MANA_1_OF_2, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Pest Mascot@T3:TOTAL_MANA_2_OF_3]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=274
- Audit: [clean]

### Game 27 — `0xE788E35A769F5AD`

- Kept hand: [Swamp, Chainer's Edict, Pest Mascot, Forest, Pest Mascot]; mulligans: 2; T1: [play Swamp]
- Warden/Researcher/Mascot casts: []/[]/[4, 5]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2]/0; 0/0/[]
- Witchstalker: [7]; Ent cycle/cast: []/[]
- Follow: []; Weather: []
- Lifegain: []; Researcher triggers/counters: 0/0; Mascot: 0/0
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Chainer's Edict x1@T1:NO_OPPONENT_CREATURE, Chainer's Edict x1@T2:NO_OPPONENT_CREATURE, Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x1@T5:NO_OPPONENT_CREATURE, Chainer's Edict x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Pest Mascot@T1:MISSING_GREEN_SOURCE, Pest Mascot@T2:MISSING_GREEN_SOURCE, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Pest Mascot@T2:TOTAL_MANA_2_OF_3, Pest Mascot@T3:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Pest Mascot@T4:TOTAL_MANA_2_OF_3, Fierce Witchstalker@T4:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T5:TOTAL_MANA_3_OF_4, Generous Ent@T5:TOTAL_MANA_3_OF_6, Fierce Witchstalker@T6:TOTAL_MANA_3_OF_4, Generous Ent@T6:TOTAL_MANA_3_OF_6, Fierce Witchstalker@T7:TOTAL_MANA_3_OF_4, Generous Ent@T7:TOTAL_MANA_3_OF_6, Generous Ent@T7:TOTAL_MANA_4_OF_6]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=241
- Audit: [agent cast Chainer's Edict into an empty opposing battlefield on T6]

### Game 28 — `0xF35096C55EC515F`

- Kept hand: [Chainer's Edict, Blood Researcher, Forest, Pest Mascot, Forest, Blood Researcher]; mulligans: 1; T1: [play Forest]
- Warden/Researcher/Mascot casts: []/[6, 8]/[5, 7]
- Carrier casts/deaths; Scions created/sacrificed/funded: [9]/0; 0/0/[]
- Witchstalker: []; Ent cycle/cast: []/[]
- Follow: []; Weather: []
- Lifegain: [PestLifeEvent(turn=8, amount=1, source=Jungle Hollow, researcherPresent=true, mascotPresent=true)]; Researcher triggers/counters: 1/1; Mascot: 2/2
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=true, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: [Jungle Hollow@T8:ENTERED_TAPPED]
- Solitaire-stranded interaction: [Chainer's Edict x1@T1:NO_OPPONENT_CREATURE, Chainer's Edict x1@T2:NO_OPPONENT_CREATURE, Cast Down x1@T2:NO_OPPONENT_CREATURE, Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Cast Down x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Cast Down x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T5:NO_OPPONENT_CREATURE, Chainer's Edict x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Chainer's Edict x1@T7:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE, Chainer's Edict x1@T8:NO_OPPONENT_CREATURE, Cast Down x1@T8:NO_OPPONENT_CREATURE, Chainer's Edict x1@T9:NO_OPPONENT_CREATURE, Cast Down x1@T9:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Blood Researcher@T1:MISSING_BLACK_SOURCE, Pest Mascot@T1:MISSING_BLACK_SOURCE, Blood Researcher@T2:MISSING_BLACK_SOURCE, Pest Mascot@T2:MISSING_BLACK_SOURCE, Blood Researcher@T3:MISSING_BLACK_SOURCE, Pest Mascot@T3:MISSING_BLACK_SOURCE, Blood Researcher@T4:MISSING_BLACK_SOURCE, Pest Mascot@T4:MISSING_BLACK_SOURCE, Blood Researcher@T5:MISSING_BLACK_SOURCE, Pest Mascot@T5:MISSING_BLACK_SOURCE]
- Terminal: T9 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=299
- Audit: [clean]

### Game 29 — `0x218EB85A5D80BED`

- Kept hand: [Forest, Essence Warden, Pest Mascot, Forest, Carrier Thrall, Fierce Witchstalker, Carrier Thrall]; mulligans: 0; T1: [play Forest, cast Essence Warden]
- Warden/Researcher/Mascot casts: [1, 6]/[]/[7, 8, 9]
- Carrier casts/deaths; Scions created/sacrificed/funded: [8]/1; 1/0/[]
- Witchstalker: []; Ent cycle/cast: [2]/[]
- Follow: []; Weather: []
- Lifegain: [PestLifeEvent(turn=6, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=false), PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=7, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=8, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=8, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=8, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=8, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true), PestLifeEvent(turn=9, amount=1, source=Essence Warden, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 20/20
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=true, researcherMascot=false, wardenResearcherMascot=false, payoffWhenWeatherResolved=false, payoffDuringMultipleCreatureEntryLifeEvents=true); Hollow: []
- Solitaire-stranded interaction: [Chainer's Edict x1@T3:NO_OPPONENT_CREATURE, Chainer's Edict x1@T4:NO_OPPONENT_CREATURE, Chainer's Edict x1@T5:NO_OPPONENT_CREATURE, Chainer's Edict x1@T6:NO_OPPONENT_CREATURE, Chainer's Edict x1@T7:NO_OPPONENT_CREATURE, Chainer's Edict x1@T8:NO_OPPONENT_CREATURE, Chainer's Edict x1@T9:NO_OPPONENT_CREATURE, Cast Down x1@T9:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Essence Warden@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Fierce Witchstalker@T1:MISSING_GREEN_SOURCE, Pest Mascot@T1:MISSING_BLACK_SOURCE, Fierce Witchstalker@T1:TOTAL_MANA_1_OF_4, Pest Mascot@T2:MISSING_BLACK_SOURCE, Carrier Thrall@T2:MISSING_BLACK_SOURCE, Fierce Witchstalker@T2:TOTAL_MANA_1_OF_4, Generous Ent@T2:TOTAL_MANA_1_OF_6, Fierce Witchstalker@T2:TOTAL_MANA_2_OF_4, Pest Mascot@T3:MISSING_BLACK_SOURCE, Carrier Thrall@T3:MISSING_BLACK_SOURCE, Fierce Witchstalker@T3:TOTAL_MANA_2_OF_4, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Pest Mascot@T4:MISSING_BLACK_SOURCE, Carrier Thrall@T4:MISSING_BLACK_SOURCE, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4, Pest Mascot@T5:MISSING_BLACK_SOURCE, Carrier Thrall@T5:MISSING_BLACK_SOURCE, Fierce Witchstalker@T5:TOTAL_MANA_3_OF_4, Pest Mascot@T6:MISSING_BLACK_SOURCE, Carrier Thrall@T6:MISSING_BLACK_SOURCE, Fierce Witchstalker@T6:TOTAL_MANA_3_OF_4, Pest Mascot@T7:MISSING_BLACK_SOURCE, Carrier Thrall@T7:MISSING_BLACK_SOURCE, Fierce Witchstalker@T7:TOTAL_MANA_3_OF_4]
- Terminal: T9 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=394
- Audit: [clean]

### Game 30 — `0xC2D50F1C8F23077`

- Kept hand: [Pest Mascot, Swamp, Forest, Pest Mascot, Blood Researcher, Weather the Storm, Carrier Thrall]; mulligans: 0; T1: [play Swamp]
- Warden/Researcher/Mascot casts: []/[7]/[3, 5, 6]
- Carrier casts/deaths; Scions created/sacrificed/funded: [2]/0; 0/0/[]
- Witchstalker: []; Ent cycle/cast: []/[]
- Follow: []; Weather: [PestWeatherCast(turn=4, stormCount=0, expectedCopies=0, observedCopies=0)]
- Lifegain: [PestLifeEvent(turn=4, amount=3, source=Weather the Storm, researcherPresent=false, mascotPresent=true)]; Researcher triggers/counters: 0/0; Mascot: 1/1
- Coexistence: PestCoexistence(wardenResearcher=false, wardenMascot=false, researcherMascot=true, wardenResearcherMascot=false, payoffWhenWeatherResolved=true, payoffDuringMultipleCreatureEntryLifeEvents=false); Hollow: []
- Solitaire-stranded interaction: [Cast Down x1@T5:NO_OPPONENT_CREATURE, Cast Down x1@T6:NO_OPPONENT_CREATURE, Cast Down x1@T7:NO_OPPONENT_CREATURE]
- Genuine mana bottlenecks: [Pest Mascot@T1:MISSING_GREEN_BLACK_SOURCE, Blood Researcher@T1:MISSING_GREEN_BLACK_SOURCE, Weather the Storm@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:MISSING_BLACK_SOURCE, Pest Mascot@T1:MISSING_GREEN_SOURCE, Blood Researcher@T1:MISSING_GREEN_SOURCE, Carrier Thrall@T1:TOTAL_MANA_1_OF_2, Pest Mascot@T2:MISSING_GREEN_SOURCE, Blood Researcher@T2:MISSING_GREEN_SOURCE, Weather the Storm@T2:MISSING_GREEN_SOURCE, Carrier Thrall@T2:TOTAL_MANA_1_OF_2, Pest Mascot@T3:MISSING_GREEN_SOURCE, Blood Researcher@T3:MISSING_GREEN_SOURCE, Weather the Storm@T3:MISSING_GREEN_SOURCE, Fierce Witchstalker@T3:MISSING_GREEN_SOURCE, Fierce Witchstalker@T3:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T4:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T5:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T6:TOTAL_MANA_3_OF_4, Fierce Witchstalker@T7:TOTAL_MANA_3_OF_4]
- Terminal: T7 / COMBAT_LETHAL; stop=ENGINE_GAME_OVER; actions=245
- Audit: [clean]
