package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Per-step / per-phase mana emptying — CR 500.5 / 703.4q: "As a step or phase ends ... any unspent
 * mana left in a player's mana pool empties." It's a turn-based action applied at *every* step and
 * phase transition (`TurnManager.advanceStep` → `CleanupPhaseManager.emptyManaPools`), not only at
 * end of turn. Upwelling (`PreventManaPoolEmptying`) suppresses it for everyone; the colour/convert
 * statics (The Last Agni Kai, Ozai) are proven in their own scenario tests.
 */
class ManaEmptyingPerStepTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        return driver
    }

    fun GameTestDriver.pool(playerId: EntityId): ManaPoolComponent =
        state.getEntity(playerId)?.get<ManaPoolComponent>() ?: ManaPoolComponent()

    test("unspent mana empties as a step or phase ends") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        val active = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.giveMana(active, Color.GREEN, 3)
        driver.pool(active).total shouldBe 3

        // Crossing one boundary (precombat main → beginning of combat) empties the unspent mana.
        driver.passPriorityUntil(Step.BEGIN_COMBAT)
        driver.pool(active).total shouldBe 0
    }

    test("Upwelling keeps unspent mana across step and phase boundaries") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        val active = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.putPermanentOnBattlefield(active, "Upwelling")
        driver.giveMana(active, Color.GREEN, 3)

        // Upwelling ("Players don't lose unspent mana as steps and phases end") suppresses the
        // per-step emptying, so the mana survives the boundary.
        driver.passPriorityUntil(Step.BEGIN_COMBAT)
        driver.pool(active).total shouldBe 3
    }
})
