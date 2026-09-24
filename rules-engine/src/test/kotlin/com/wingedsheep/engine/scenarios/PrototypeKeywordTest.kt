package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PrototypeComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

/**
 * Rules-engine coverage for Prototype independent of any printed card.
 */
class PrototypeKeywordTest : FunSpec({
    val prototypeGolem = card("Test Prototype Golem") {
        manaCost = "{6}"
        typeLine = "Artifact Creature — Golem"
        power = 6
        toughness = 6
        keywordAbility(
            KeywordAbility.Prototype(
                cost = ManaCost.parse("{2}{G}"),
                power = 3,
                toughness = 3,
            )
        )
    }

    val freeCastRitual = card("Test Prototype Free Cast Ritual") {
        manaCost = "{G}"
        typeLine = "Sorcery"
        spell {
            effect = Effects.GrantNextSpellFreeCast()
        }
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(prototypeGolem, freeCastRitual))
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("ordinary legal actions expose printed and Prototype casts independently") {
        val driver = createDriver()
        val player = driver.activePlayer!!
        val golem = driver.putCardInHand(player, "Test Prototype Golem")
        driver.giveMana(player, Color.GREEN, 6)

        val casts = LegalActionEnumerator.create(driver.cardRegistry)
            .enumerate(driver.state, player)
            .mapNotNull { it.action as? CastSpell }
            .filter { it.cardId == golem }

        casts.map { it.castForPrototype }.shouldContainExactlyInAnyOrder(false, true)
    }

    test("Prototype characteristics persist through stack, GameState serialization, and battlefield") {
        val driver = createDriver()
        val player = driver.activePlayer!!
        val golem = driver.putCardInHand(player, "Test Prototype Golem")
        driver.giveMana(player, Color.GREEN, 3)

        driver.submit(
            CastSpell(
                playerId = player,
                cardId = golem,
                castForPrototype = true,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).error shouldBe null

        val onStack = driver.state.getEntity(golem)?.get<CardComponent>()!!
        onStack.manaCost.toString() shouldBe "{2}{G}"
        onStack.manaValue shouldBe 3
        onStack.colors shouldBe setOf(Color.GREEN)
        onStack.baseStats?.basePower shouldBe 3
        onStack.baseStats?.baseToughness shouldBe 3
        driver.state.getEntity(golem)?.get<PrototypeComponent>() shouldNotBe null

        val json = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
        }
        val encoded = json.encodeToString(GameState.serializer(), driver.state)
        val decoded = json.decodeFromString(GameState.serializer(), encoded)
        decoded.getEntity(golem)?.get<PrototypeComponent>() shouldNotBe null
        decoded.getEntity(golem)?.get<CardComponent>()?.manaValue shouldBe 3

        driver.bothPass()
        val permanent = driver.findPermanent(player, "Test Prototype Golem")
        permanent shouldNotBe null
        val onBattlefield = driver.state.getEntity(permanent!!)?.get<CardComponent>()!!
        onBattlefield.manaValue shouldBe 3
        onBattlefield.colors shouldBe setOf(Color.GREEN)
        onBattlefield.baseStats?.basePower shouldBe 3
        onBattlefield.baseStats?.baseToughness shouldBe 3
    }

    test("without-paying alternative cost can coexist with Prototype") {
        val driver = createDriver()
        val player = driver.activePlayer!!

        driver.giveMana(player, Color.GREEN, 1)
        val ritual = driver.putCardInHand(player, "Test Prototype Free Cast Ritual")
        driver.castSpell(player, ritual).isSuccess shouldBe true
        driver.bothPass()

        val golem = driver.putCardInHand(player, "Test Prototype Golem")
        driver.submit(
            CastSpell(
                playerId = player,
                cardId = golem,
                castForPrototype = true,
                useWithoutPayingManaCost = true,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).error shouldBe null

        val onStack = driver.state.getEntity(golem)?.get<CardComponent>()!!
        onStack.manaCost.toString() shouldBe "{2}{G}"
        onStack.manaValue shouldBe 3
        onStack.baseStats?.basePower shouldBe 3
        onStack.baseStats?.baseToughness shouldBe 3
    }
})
