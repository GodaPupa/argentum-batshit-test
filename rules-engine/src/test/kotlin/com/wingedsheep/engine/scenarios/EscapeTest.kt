package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Shared Escape regression independent of any one set card.
 *
 * Escape is a graveyard alternative cost, not Flashback: it grants no timing permission,
 * requires its own non-mana exile payment, excludes the escaping card from that payment,
 * and an escaped instant/sorcery returns to the graveyard normally after resolving.
 */
class EscapeTest : FunSpec({

    val escapeStudy = card("Escape Study") {
        manaCost = "{3}{U}"
        colorIdentity = "U"
        typeLine = "Sorcery"
        spell { effect = Effects.GainLife(3) }
        keywordAbility(KeywordAbility.escape("{1}{U}", exileOtherCards = 2))
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + escapeStudy)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        return driver
    }

    fun escapeActions(driver: GameTestDriver, player: com.wingedsheep.sdk.model.EntityId) =
        LegalActionEnumerator.create(driver.cardRegistry).enumerate(driver.state, player)
            .filter { (it.action as? CastSpell)?.alternativeCostType == AlternativeCostType.ESCAPE }

    test("Escape is offered from graveyard only when mana and other-card payment are available") {
        val driver = createDriver()
        val player = driver.activePlayer!!
        val spell = driver.putCardInGraveyard(player, "Escape Study")
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 1)

        val withoutFuel = escapeActions(driver, player).single { (it.action as CastSpell).cardId == spell }
        withoutFuel.affordable.shouldBeFalse()

        driver.putCardInGraveyard(player, "Island")
        driver.putCardInGraveyard(player, "Island")

        val withFuel = escapeActions(driver, player).single { (it.action as CastSpell).cardId == spell }
        withClue("two OTHER graveyard cards make the non-mana Escape payment affordable") {
            withFuel.affordable.shouldBeTrue()
        }
        withFuel.additionalCostInfo?.exileMinCount shouldBe 2
        withFuel.additionalCostInfo?.exileMaxCount shouldBe 2
        withFuel.additionalCostInfo?.validExileTargets?.contains(spell) shouldBe false
    }

    test("Escape grants no timing permission to a sorcery") {
        val driver = createDriver()
        val player = driver.activePlayer!!
        val spell = driver.putCardInGraveyard(player, "Escape Study")
        driver.putCardInGraveyard(player, "Island")
        driver.putCardInGraveyard(player, "Island")
        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 1)

        driver.passPriorityUntil(Step.END)

        escapeActions(driver, player).map { (it.action as CastSpell).cardId } shouldNotContain spell
    }

    test("Escape pays exact other-card exile cost and resolved sorcery returns to graveyard") {
        val driver = createDriver()
        val player = driver.activePlayer!!
        val spell = driver.putCardInGraveyard(player, "Escape Study")
        val fuelA = driver.putCardInGraveyard(player, "Island")
        val fuelB = driver.putCardInGraveyard(player, "Island")
        driver.putCardInGraveyard(player, "Island") // spare fuel must remain
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 1)
        val lifeBefore = driver.getLifeTotal(player)

        val result = driver.submit(
            CastSpell(
                playerId = player,
                cardId = spell,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.ESCAPE,
                paymentStrategy = PaymentStrategy.FromPool,
                additionalCostPayment = AdditionalCostPayment(exiledCards = listOf(fuelA, fuelB)),
            )
        )
        withClue("Escape cast should succeed: ${result.error}") {
            result.isSuccess.shouldBeTrue()
        }
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        driver.getExile(player) shouldContain fuelA
        driver.getExile(player) shouldContain fuelB
        driver.getExile(player) shouldNotContain spell
        driver.getGraveyard(player) shouldContain spell
        driver.getLifeTotal(player) shouldBe lifeBefore + 3
    }

    test("Escape rejects using the escaping card itself as payment") {
        val driver = createDriver()
        val player = driver.activePlayer!!
        val spell = driver.putCardInGraveyard(player, "Escape Study")
        val fuel = driver.putCardInGraveyard(player, "Island")
        driver.putCardInGraveyard(player, "Island")
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 1)

        val result = driver.submit(
            CastSpell(
                playerId = player,
                cardId = spell,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.ESCAPE,
                paymentStrategy = PaymentStrategy.FromPool,
                additionalCostPayment = AdditionalCostPayment(exiledCards = listOf(spell, fuel)),
            )
        )

        result.isSuccess.shouldBeFalse()
        result.error.orEmpty() shouldContain "cannot exile itself"
    }

    test("Escape rejects duplicate exile payment IDs") {
        val driver = createDriver()
        val player = driver.activePlayer!!
        val spell = driver.putCardInGraveyard(player, "Escape Study")
        val fuel = driver.putCardInGraveyard(player, "Island")
        driver.putCardInGraveyard(player, "Island")
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 1)

        val result = driver.submit(
            CastSpell(
                playerId = player,
                cardId = spell,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.ESCAPE,
                paymentStrategy = PaymentStrategy.FromPool,
                additionalCostPayment = AdditionalCostPayment(exiledCards = listOf(fuel, fuel)),
            )
        )

        result.isSuccess.shouldBeFalse()
        result.error.orEmpty() shouldContain "same card cannot be exiled more than once"
    }
})
