package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ogw.cards.WorldBreaker
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Exact-card qualification for World Breaker (OGW #126). */
class WorldBreakerScenarioTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(WorldBreaker)
        return driver
    }

    test("cast trigger exiles a target land before World Breaker resolves") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val caster = driver.activePlayer!!
        val opponent = driver.getOpponent(caster)
        val worldBreaker = driver.putCardInHand(caster, "World Breaker")
        val targetLand = driver.putLandOnBattlefield(opponent, "Forest")
        driver.giveMana(caster, Color.GREEN, 7)

        val cast = driver.submit(
            CastSpell(
                playerId = caster,
                cardId = worldBreaker,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        )
        cast.error shouldBe null

        driver.submitTargetSelection(caster, listOf(targetLand)).error shouldBe null

        // The cast trigger is above the creature spell and resolves independently.
        driver.bothPass()
        driver.getExile(opponent).contains(targetLand) shouldBe true
        driver.findPermanent(caster, "World Breaker") shouldBe null

        driver.bothPass()
        driver.findPermanent(caster, "World Breaker") shouldNotBe null
        WorldBreaker.colors shouldBe emptySet()
        WorldBreaker.colorIdentity shouldBe setOf(Color.GREEN)
    }

    test("graveyard ability pays two generic plus one true colorless and sacrifices a land") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val player = driver.activePlayer!!
        val worldBreaker = driver.putCardInGraveyard(player, "World Breaker")
        val sacrificedLand = driver.putLandOnBattlefield(player, "Forest")
        driver.giveMana(player, Color.GREEN, 2)
        driver.giveColorlessMana(player, 1)

        val ability = WorldBreaker.script.activatedAbilities.single()
        val result = driver.submit(
            ActivateAbility(
                playerId = player,
                sourceId = worldBreaker,
                abilityId = ability.id,
                costPayment = AdditionalCostPayment(
                    sacrificedPermanents = listOf(sacrificedLand)
                ),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        )
        result.error shouldBe null
        driver.state.getGraveyard(player).contains(sacrificedLand) shouldBe true

        driver.bothPass()
        driver.state.getHand(player).contains(worldBreaker) shouldBe true
        driver.state.getZone(com.wingedsheep.engine.state.ZoneKey(player, Zone.GRAVEYARD))
            .contains(worldBreaker) shouldBe false
    }
})
