package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Ten separately reported cases per real card. Miniature same-card libraries establish a
 * seeded public board for real PlayLand, mana and concession actions. They are neither the
 * frozen 100-card control nor sampled capability games. 9250925016L is an excluded fixture seed.
 */
abstract class OpponentCountLandScenarioSpec(
    definition: CardDefinition,
    firstColor: Color,
    secondColor: Color
) : FunSpec({
    data class Fixture(val driver: GameTestDriver, val players: List<EntityId>, val land: EntityId)

    fun setup(playerCount: Int = 4, teams: List<List<Int>>? = null): Fixture {
        val driver = GameTestDriver()
        driver.registerCard(definition)
        val initialized = GameInitializer(driver.cardRegistry).initializeGame(GameConfig(
            players = List(playerCount) { PlayerConfig("Land fixture seat $it", Deck.of(definition.name to 20)) },
            startingHandSize = 1,
            skipMulligans = true,
            startingPlayerIndex = 0,
            useHandSmoother = false,
            teams = teams,
            seed = 9250925016L
        ))
        initialized.seed shouldBe 9250925016L
        driver.replaceState(initialized.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN))
        val owner = initialized.playerIds.first()
        return Fixture(driver, initialized.playerIds, driver.state.getHand(owner).single())
    }

    fun Fixture.playAndCheck(tapped: Boolean) {
        val owner = players.first()
        driver.legalActions(owner).any { it.action == PlayLand(owner, land) } shouldBe true
        driver.playLand(owner, land).error shouldBe null
        driver.state.getBattlefield().contains(land) shouldBe true
        driver.state.getEntity(land)!!.has<TappedComponent>() shouldBe tapped
        driver.state.projectedState.getController(land) shouldBe owner
        driver.state.projectedState.getColors(land) shouldBe emptySet()
        for (subtype in listOf("Plains", "Island", "Swamp", "Mountain", "Forest")) {
            driver.state.projectedState.hasSubtype(land, subtype) shouldBe false
        }
        definition.colorIdentity shouldBe setOf(firstColor, secondColor)
        driver.state.stack.isEmpty() shouldBe true
        driver.state.pendingDecision shouldBe null
        driver.state.gameOver shouldBe false
    }

    for (players in 2..4) {
        test("${definition.name} enters with the correct tap state at $players live seats") {
            val fixture = setup(players)
            fixture.driver.state.getOpponents(fixture.players.first()).size shouldBe players - 1
            fixture.playAndCheck(tapped = players == 2)
            if (players == 2) {
                val action = ActivateAbility(fixture.players.first(), fixture.land, definition.activatedAbilities.first().id)
                fixture.driver.legalActions(fixture.players.first()).any { it.action == action && it.affordable } shouldBe false
                val before = fixture.driver.state
                fixture.driver.submit(action).error shouldNotBe null
                fixture.driver.state shouldBe before
            }
        }
    }

    for (departures in 1..2) {
        test("${definition.name} counts survivors after $departures opponents leave the original four-seat table") {
            val fixture = setup()
            fixture.players.takeLast(departures).forEach { fixture.driver.concede(it).error shouldBe null }
            fixture.driver.state.activePlayers.size shouldBe 4 - departures
            fixture.driver.state.getOpponents(fixture.players.first()).size shouldBe 3 - departures
            fixture.playAndCheck(tapped = departures == 2)
        }
    }

    test("${definition.name} does not count two teammates as opponents at a four-seat table") {
        val fixture = setup(teams = listOf(listOf(0, 1, 2), listOf(3)))
        fixture.driver.state.activePlayers.size shouldBe 4
        fixture.driver.state.getOpponents(fixture.players.first()) shouldBe listOf(fixture.players.last())
        fixture.playAndCheck(tapped = true)
    }

    for ((index, color) in listOf(firstColor, secondColor).withIndex()) {
        test("${definition.name} can immediately tap for exactly one $color mana without life loss or a stack object") {
            val fixture = setup()
            fixture.playAndCheck(tapped = false)
            val owner = fixture.players.first()
            val life = fixture.driver.getLifeTotal(owner)
            val action = ActivateAbility(owner, fixture.land, definition.activatedAbilities[index].id)
            fixture.driver.legalActions(owner).any { it.action == action && it.affordable } shouldBe true
            fixture.driver.submit(action).error shouldBe null
            val pool = fixture.driver.state.getEntity(owner)!!.get<ManaPoolComponent>()!!
            pool.total shouldBe 1
            pool.getAmount(color) shouldBe 1
            fixture.driver.getLifeTotal(owner) shouldBe life
            fixture.driver.state.getEntity(fixture.land)!!.has<TappedComponent>() shouldBe true
            fixture.driver.state.stack.isEmpty() shouldBe true
            fixture.driver.state.pendingDecision shouldBe null
        }
    }

    test("${definition.name} is offered as a land and a direct spell-cast attempt is rejected without state mutation") {
        val fixture = setup()
        val owner = fixture.players.first()
        fixture.driver.legalActions(owner).any { it.action is CastSpell && it.description.contains(definition.name) } shouldBe false
        val before = fixture.driver.state
        fixture.driver.submit(CastSpell(owner, fixture.land)).error shouldNotBe null
        fixture.driver.state shouldBe before
        fixture.playAndCheck(tapped = false)
    }

    test("${definition.name} replays a concession land play and mana activation identically through a serialized state") {
        fun replay(roundtrip: Boolean): Pair<String, String> {
            val fixture = setup()
            fixture.driver.concede(fixture.players.last()).error shouldBe null
            if (roundtrip) {
                fixture.driver.replaceState(SerializationTestSupport.roundTrip(fixture.driver.state))
            }
            fixture.playAndCheck(tapped = false)
            fixture.driver.submit(ActivateAbility(fixture.players.first(), fixture.land, definition.activatedAbilities.first().id)).error shouldBe null
            return SerializationTestSupport.encodeState(fixture.driver.state) to
                SerializationTestSupport.encodeEvents(fixture.driver.events)
        }
        replay(roundtrip = false) shouldBe replay(roundtrip = true)
    }
})
