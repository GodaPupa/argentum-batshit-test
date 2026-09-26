package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.bbd.cards.SpireGarden
import com.wingedsheep.sdk.core.Color

/** Separately named exact-card fixture suite; no official deck or sample is initialized. */
class SpireGardenScenarioTest : OpponentCountLandScenarioSpec(SpireGarden, Color.RED, Color.GREEN)
