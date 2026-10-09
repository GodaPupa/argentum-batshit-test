package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec

/** Qualification only: no seeds, GameEnvironment, initializer, action processor, or gameplay. */
class PlayFirstTimingJournalTest : FunSpec({
    PlayFirstTimingFixtures.cases.forEach { (name, verify) -> test(name) { verify() } }
})
