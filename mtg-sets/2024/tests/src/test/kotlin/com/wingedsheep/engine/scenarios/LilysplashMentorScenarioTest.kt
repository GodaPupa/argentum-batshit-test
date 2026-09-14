package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.blb.cards.LilysplashMentor
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.TimingRule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

/** Deterministic coverage for Lilysplash Mentor's blink activation and restrictions. */
class LilysplashMentorScenarioTest : FunSpec({

    val abilityId = LilysplashMentor.activatedAbilities.single().id

    fun driver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(
            deck = Deck.of("Forest" to 20, "Island" to 20),
            startingPlayer = 0,
            skipMulligans = true,
        )
        return driver
    }

    fun GameTestDriver.fundActivation(player: EntityId) {
        giveMana(player, Color.GREEN, 1)
        giveMana(player, Color.BLUE, 2)
    }

    fun GameTestDriver.activate(source: EntityId, target: EntityId) = submit(
        ActivateAbility(
            playerId = activePlayer!!,
            sourceId = source,
            abilityId = abilityId,
            targets = listOf(entityIdToChosenTarget(state, target)),
        ),
    )

    fun GameTestDriver.createMouseToken(player: EntityId): EntityId {
        val tokenId = EntityId.generate()
        val card = CardComponent(
            cardDefinitionId = "token:Mouse",
            name = "Mouse Token",
            manaCost = ManaCost.ZERO,
            typeLine = TypeLine.parse("Creature — Mouse"),
            baseStats = CreatureStats(1, 1),
            colors = setOf(Color.WHITE),
            ownerId = player,
        )
        replaceState(
            state
                .withEntity(
                    tokenId,
                    ComponentContainer.of(
                        card,
                        TokenComponent,
                        ControllerComponent(player),
                        SummoningSicknessComponent,
                    ),
                )
                .addToZone(ZoneKey(player, Zone.BATTLEFIELD), tokenId),
        )
        return tokenId
    }

    test("the activation is sorcery-speed") {
        LilysplashMentor.activatedAbilities.single().timing shouldBe TimingRule.SorcerySpeed

        val driver = driver()
        val player = driver.activePlayer!!
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val bears = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.passPriorityUntil(Step.UPKEEP)
        driver.fundActivation(player)

        val result = driver.activate(mentor, bears)

        result.isSuccess shouldBe false
        result.error.orEmpty() shouldContain "sorcery"
    }

    test("it blinks another creature you control and returns it with exactly one counter") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val bears = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.addComponent(
            bears,
            CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)),
        )
        driver.fundActivation(player)

        driver.activate(mentor, bears).isSuccess shouldBe true
        driver.bothPass()

        val returned = driver.findPermanent(player, "Grizzly Bears")!!
        driver.state.getEntity(returned)
            ?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
    }

    test("it cannot target Lilysplash Mentor itself") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        driver.fundActivation(player)

        val result = driver.activate(mentor, mentor)

        result.isSuccess shouldBe false
        result.error.shouldNotBeNull()
    }

    test("it cannot target a creature an opponent controls") {
        val driver = driver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val bears = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        driver.fundActivation(player)

        val result = driver.activate(mentor, bears)

        result.isSuccess shouldBe false
        result.error.shouldNotBeNull()
    }

    test("an exiled token does not return or receive a counter") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val token = driver.createMouseToken(player)
        driver.fundActivation(player)

        driver.activate(mentor, token).isSuccess shouldBe true
        driver.bothPass()

        driver.state.getBattlefield(player).contains(token) shouldBe false
        driver.findPermanent(player, "Mouse Token") shouldBe null
    }
})
