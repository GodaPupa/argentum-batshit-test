package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.thb.cards.SleepOfTheDead
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Cross-layer proof that production AI can materialize Sleep of the Dead's Escape payment.
 *
 * The legal-action enumerator advertises the three OTHER graveyard cards through the shared
 * ExileFromGraveyard contract; Strategist must turn that metadata into AdditionalCostPayment
 * before the CastSpell reaches the processor.
 */
class EscapeAutomaticPaymentAiTest : FunSpec({

    test("AI materializes all three other graveyard cards for a real Sleep Escape action") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + SleepOfTheDead)
        driver.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0,
        )

        val player = driver.player1
        val opponent = driver.player2
        val target = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val sleep = driver.putCardInGraveyard(player, "Sleep of the Dead")
        val fuel = listOf(
            driver.putCardInGraveyard(player, "Island"),
            driver.putCardInGraveyard(player, "Island"),
            driver.putCardInGraveyard(player, "Island"),
        )

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 2)

        val escape = LegalActionEnumerator.create(driver.cardRegistry)
            .enumerate(driver.state, player)
            .single {
                val cast = it.action as? CastSpell
                cast?.cardId == sleep && cast.alternativeCostType == AlternativeCostType.ESCAPE
            }

        val chosen = AIPlayer.create(driver.cardRegistry, player)
            .chooseFrom(driver.state, listOf(escape))
            .action as CastSpell

        chosen.alternativeCostType shouldBe AlternativeCostType.ESCAPE
        chosen.additionalCostPayment?.exiledCards.orEmpty() shouldContainAll fuel
        chosen.additionalCostPayment?.exiledCards.orEmpty() shouldNotContain sleep

        withClue("the production-materialized Escape action must be directly executable") {
            driver.submit(chosen).error shouldBe null
        }
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        driver.getExile(player) shouldContainAll fuel
        driver.getGraveyard(player).contains(sleep) shouldBe true
    }
})
