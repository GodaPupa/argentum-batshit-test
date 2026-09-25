package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.fra.cards.TwinnedVision
import com.wingedsheep.mtg.sets.definitions.gpt.cards.IzzetGuildmage
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TwinnedVisionScenarioTest : FunSpec({

    val copyInstantAbility = IzzetGuildmage.activatedAbilities[0].id

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(TwinnedVision, IzzetGuildmage))
        d.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("hand cast draws exactly one card") {
        val d = driver()
        val you = d.activePlayer!!
        val before = d.state.getLibrary(you).size

        val vision = d.putCardInHand(you, TwinnedVision.name)
        d.giveMana(you, Color.BLUE, 2)
        d.castSpell(you, vision).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.state.getLibrary(you).size shouldBe before - 1
        d.state.getGraveyard(you).contains(vision) shouldBe true
    }

    test("Izzet Guildmage copy of a hand-cast Twinned Vision draws two while original draws one") {
        val d = driver()
        val you = d.activePlayer!!
        val before = d.state.getLibrary(you).size
        val guildmage = d.putCreatureOnBattlefield(you, IzzetGuildmage.name)

        val vision = d.putCardInHand(you, TwinnedVision.name)
        d.giveMana(you, Color.BLUE, 2)
        d.castSpell(you, vision).isSuccess shouldBe true

        d.giveMana(you, Color.BLUE, 3)
        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = guildmage,
                abilityId = copyInstantAbility,
                targets = listOf(ChosenTarget.Spell(vision))
            )
        ).isSuccess shouldBe true

        d.bothPass().isSuccess shouldBe true // Guildmage ability creates the spell copy.
        d.bothPass().isSuccess shouldBe true // Copy was not cast from hand: draw two.
        d.bothPass().isSuccess shouldBe true // Original was cast from hand: draw one.

        d.state.getLibrary(you).size shouldBe before - 3
        d.state.getGraveyard(you).contains(vision) shouldBe true
    }

    test("flashback pays its discard cost, draws two, and exiles Twinned Vision") {
        val d = driver()
        val you = d.activePlayer!!
        val before = d.state.getLibrary(you).size
        val vision = d.putCardInGraveyard(you, TwinnedVision.name)
        val fodder = d.putCardInHand(you, "Island")

        d.giveMana(you, Color.BLUE, 3)
        d.submit(
            CastSpell(
                playerId = you,
                cardId = vision,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.FLASHBACK,
                additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(fodder)),
                paymentStrategy = PaymentStrategy.AutoPay
            )
        ).isSuccess shouldBe true

        d.state.getGraveyard(you).contains(fodder) shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.state.getLibrary(you).size shouldBe before - 2
        d.state.getZone(ZoneKey(you, Zone.EXILE)).contains(vision) shouldBe true
        d.state.getGraveyard(you).contains(vision) shouldBe false
    }

    test("flashback plus Izzet Guildmage copy draws four total and keeps flashback exile") {
        val d = driver()
        val you = d.activePlayer!!
        val before = d.state.getLibrary(you).size
        val guildmage = d.putCreatureOnBattlefield(you, IzzetGuildmage.name)
        val vision = d.putCardInGraveyard(you, TwinnedVision.name)
        val fodder = d.putCardInHand(you, "Island")

        d.giveMana(you, Color.BLUE, 3)
        d.submit(
            CastSpell(
                playerId = you,
                cardId = vision,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.FLASHBACK,
                additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(fodder)),
                paymentStrategy = PaymentStrategy.AutoPay
            )
        ).isSuccess shouldBe true

        d.giveMana(you, Color.BLUE, 3)
        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = guildmage,
                abilityId = copyInstantAbility,
                targets = listOf(ChosenTarget.Spell(vision))
            )
        ).isSuccess shouldBe true

        d.bothPass().isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.state.getLibrary(you).size shouldBe before - 4
        d.state.getZone(ZoneKey(you, Zone.EXILE)).contains(vision) shouldBe true
    }

    test("flashback without the required discard is rejected atomically") {
        val d = driver()
        val you = d.activePlayer!!
        val vision = d.putCardInGraveyard(you, TwinnedVision.name)
        d.giveMana(you, Color.BLUE, 3)
        val before = d.state

        d.submit(
            CastSpell(
                playerId = you,
                cardId = vision,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.FLASHBACK,
                additionalCostPayment = AdditionalCostPayment.NONE,
                paymentStrategy = PaymentStrategy.AutoPay
            )
        ).isSuccess shouldBe false

        d.state shouldBe before
    }
})
