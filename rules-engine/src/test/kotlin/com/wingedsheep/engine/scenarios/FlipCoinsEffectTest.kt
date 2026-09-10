package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CoinFlipEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.composite.FlipCoinsExecutor
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.effects.FlipCoinsEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [FlipCoinsExecutor] — the "flip N coins, count the heads" primitive behind Ral Zarek's ultimate.
 * Verifies it emits one [CoinFlipEvent] per flip and publishes the heads tally into the pipeline
 * under the requested key. Seeded so the count assertion is deterministic.
 */
class FlipCoinsEffectTest : FunSpec({

    test("flips exactly N coins and stores the number of heads (= won flips)") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40))
        val player = driver.activePlayer!!

        // Seed the RNG so the run is reproducible.
        driver.replaceState(driver.state.copy(rng = GameRng.seeded(12345L)))

        val executor = FlipCoinsExecutor(driver.cardRegistry)
        val context = EffectContext(sourceId = null, controllerId = player)
        val result = executor.execute(driver.state, FlipCoinsEffect(5, "heads"), context)

        result.error shouldBe null
        // One coin-flip event per flip.
        result.events.filterIsInstance<CoinFlipEvent>().size shouldBe 5
        // Stored heads equals the number of won flips actually emitted.
        val wonCount = result.events.filterIsInstance<CoinFlipEvent>().count { it.won }
        result.updatedStoredNumbers["heads"] shouldBe wonCount
    }

    test("flipping zero coins stores zero heads and emits no events") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40))
        val player = driver.activePlayer!!

        val executor = FlipCoinsExecutor(driver.cardRegistry)
        val context = EffectContext(sourceId = null, controllerId = player)
        val result = executor.execute(driver.state, FlipCoinsEffect(0, "heads"), context)

        result.error shouldBe null
        result.events.filterIsInstance<CoinFlipEvent>().size shouldBe 0
        result.updatedStoredNumbers["heads"] shouldBe 0
    }
})
