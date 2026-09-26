package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.cmr.cards.RejuvenatingSprings
import com.wingedsheep.sdk.core.Color

/** Separately named exact-card fixture suite; no official deck or sample is initialized. */
class RejuvenatingSpringsScenarioTest : OpponentCountLandScenarioSpec(RejuvenatingSprings, Color.GREEN, Color.BLUE)
