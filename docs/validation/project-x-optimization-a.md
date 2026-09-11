# Project X Optimization Experiment A

Declared changes: -1 Falkenrath Noble, -1 Masked Vandal, +2 Llanowar Elves
Each pair uses the same seed, starting-player assignment, mulligan policy, engine, and solitaire policy.
Turn deltas are variant minus control; negative is earlier.

## Paired primary outcomes

| Pair | Seed | Ivy delta | 3-mana stalls | Primary state | Any-infinite state | Combo-before-lethal | Win delta | Witness returns | Noble availability |
|---:|---:|---:|---:|---|---|---|---:|---:|---:|
| 1 | 570673414098032481 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 2 | 506099272810314930 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 3 | 747476981334286865 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | -2 | 0 | -9 |
| 4 | 643253893278530017 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 1 | -4 |
| 5 | 58643563539967979 | -1 | -1 | NONE->NONE | NONE->NONE | false->false | -1 | 0 | -1 |
| 6 | 904089825615897503 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 7 | 606702488740676940 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 8 | 26179542770151623 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 1 | 0 | -4 |
| 9 | 575072485447328822 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 10 | 889718536992052628 | -1 | 0 | NONE->NONE | NONE->NONE | false->false | 1 | -1 | 1 |
| 11 | 927659816071111052 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 12 | 259680750764972615 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 13 | 429982048822765225 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 14 | 29680932372619853 | 0 | 0 | T6->T6 | T6->T6 | true->true | 0 | 0 | 0 |
| 15 | 719336296616628832 | N/A | -2 | NONE->T6 | NONE->T6 | false->true | 0 | 0 | 0 |
| 16 | 887502734349099046 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 17 | 358175891093163109 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 18 | 1110318407443851163 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 19 | 603559417145884891 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | -1 | 0 | -6 |
| 20 | 375820755781804720 | N/A | -6 | NONE->NONE | NONE->NONE | false->false | -4 | -1 | -11 |
| 21 | 544476075921527314 | 0 | 0 | T7->T7 | T7->T7 | true->true | 0 | 0 | 0 |
| 22 | 876632317326033351 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 23 | 391405298772948924 | N/A | -1 | NONE->NONE | NONE->NONE | false->false | -1 | 0 | 0 |
| 24 | 469872797459320707 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 25 | 166385730207593607 | 0 | 0 | T6->T6 | T6->T6 | true->true | 0 | 0 | 0 |
| 26 | 675177437552859093 | 0 | 0 | NONE->NONE | NONE->NONE | false->false | 0 | -1 | 0 |
| 27 | 73579894737359832 | N/A | 1 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 28 | 789112864763132689 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 1 | 0 | -2 |
| 29 | 804306394771827857 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | 0 |
| 30 | 448373285043802966 | N/A | 0 | NONE->NONE | NONE->NONE | false->false | 0 | 0 | -6 |

Control summary: ProjectXGoldfishSummary(actualWinsByT4=0, actualWinsByT5=3, actualWinsByT6=16, medianActualWinningTurn=6.0, meanActualWinningTurn=6.633333333333334, winningMechanismDistribution={COMBAT_LETHAL=26, DETERMINISTIC_COMBO:CARRION_FEEDER_COMBAT=3, TRIGGERED_OR_ABILITY_LETHAL=1}, engineByT4=0, engineByT5=0, engineByT6=2, lethalByT4=0, lethalByT5=0, lethalByT6=1, medianEngineTurn=6.0, medianLethalTurn=7.0, anyInfiniteByT4=0, anyInfiniteByT5=0, anyInfiniteByT6=2, medianComboTurn=6.0, comboAssemblyGames=3, comboAssemblyRate=0.1, comboBeforeGameEndGames=3, comboBeforeGameEndRate=0.1, comboBeforeOrdinaryLethalGames=3, ordinaryLethalBeforeComboGames=27, infiniteLifeGames=0, nobleDeterministicDrainGames=0, hugeFeederGames=3, hugeFeederWithoutImmediateLethalGames=2, mulliganGames=7, totalMulligans=10, mulliganGameRate=0.23333333333333334, functionalWithoutComboGames=26, functionalWithoutComboRate=0.8666666666666667, exactlyOneRoleMissingGames=23, exactlyOneRoleMissingRate=0.7666666666666667, roleShortGamesByMissingRole={Carrion Feeder=5, Ivy Lane Denizen=15, Safehold Elite=4}, roleShortGamesByAttribution={GAME_ENDED_BEFORE_DEPLOYMENT=2, MANA_CONSTRAINED=13, NEVER_DRAWN=9, OTHER_UNDEPLOYED_OR_TRANSIENT=3, SACRIFICED=4, TUTORED_BUT_NOT_YET_CAST=4}, heraldContributionGames=9, witnessContributionGames=9, birchloreContributionGames=5, nettleContributionGames=17, quirionContributionGames=6, colorBottleneckGames=25, khalniGardenTempoGames=6, hauntedMireTempoGames=6, intentionallyHeldGames=9, birchloreManaAvailableGames=7, quirionSequenceAvailableGames=5, tappedLandConstraintGames=11, insufficientTotalManaGames=30)

Variant summary: ProjectXGoldfishSummary(actualWinsByT4=0, actualWinsByT5=3, actualWinsByT6=17, medianActualWinningTurn=6.0, meanActualWinningTurn=6.433333333333334, winningMechanismDistribution={COMBAT_LETHAL=25, DETERMINISTIC_COMBO:CARRION_FEEDER_COMBAT=3, DETERMINISTIC_COMBO:FALKENRATH_NOBLE_DRAIN=1, TRIGGERED_OR_ABILITY_LETHAL=1}, engineByT4=0, engineByT5=0, engineByT6=3, lethalByT4=0, lethalByT5=0, lethalByT6=2, medianEngineTurn=6.0, medianLethalTurn=6.5, anyInfiniteByT4=0, anyInfiniteByT5=0, anyInfiniteByT6=3, medianComboTurn=6.0, comboAssemblyGames=4, comboAssemblyRate=0.13333333333333333, comboBeforeGameEndGames=4, comboBeforeGameEndRate=0.13333333333333333, comboBeforeOrdinaryLethalGames=4, ordinaryLethalBeforeComboGames=26, infiniteLifeGames=0, nobleDeterministicDrainGames=1, hugeFeederGames=4, hugeFeederWithoutImmediateLethalGames=2, mulliganGames=7, totalMulligans=10, mulliganGameRate=0.23333333333333334, functionalWithoutComboGames=26, functionalWithoutComboRate=0.8666666666666667, exactlyOneRoleMissingGames=23, exactlyOneRoleMissingRate=0.7666666666666667, roleShortGamesByMissingRole={Carrion Feeder=7, Ivy Lane Denizen=14, Safehold Elite=4}, roleShortGamesByAttribution={GAME_ENDED_BEFORE_DEPLOYMENT=2, MANA_CONSTRAINED=13, NEVER_DRAWN=10, OTHER_UNDEPLOYED_OR_TRANSIENT=4, SACRIFICED=4, TUTORED_BUT_NOT_YET_CAST=5}, heraldContributionGames=9, witnessContributionGames=7, birchloreContributionGames=4, nettleContributionGames=16, quirionContributionGames=6, colorBottleneckGames=23, khalniGardenTempoGames=6, hauntedMireTempoGames=5, intentionallyHeldGames=12, birchloreManaAvailableGames=11, quirionSequenceAvailableGames=4, tappedLandConstraintGames=11, insufficientTotalManaGames=30)

