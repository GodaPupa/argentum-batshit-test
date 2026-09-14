package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OrderObjectsDecision
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.avr.cards.GhostlyFlicker
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GhostlyFlickerScenarioTest : FunSpec({
    fun setup(): Triple<GameTestDriver, EntityId, EntityId> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(GhostlyFlicker)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val creature = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.removeSummoningSickness(creature)
        val land = driver.putLandOnBattlefield(player, "Island")
        driver.addComponent(land, TappedComponent)
        return Triple(driver, creature, land)
    }

    fun GameTestDriver.totalMana(player: EntityId): Int {
        val pool = state.getEntity(player)?.get<ManaPoolComponent>() ?: return 0
        return pool.white + pool.blue + pool.black + pool.red + pool.green + pool.colorless
    }

    fun GameTestDriver.resolveFlickerTriggers(
        player: EntityId,
        opponent: EntityId,
        flicker: EntityId,
        lands: List<EntityId>,
    ) {
        var guard = 0
        while ((state.stack.isNotEmpty() || pendingDecision != null) && guard++ < 100) {
            when (val decision = pendingDecision) {
                is OrderObjectsDecision -> submitDecision(
                    decision.playerId,
                    OrderedResponse(decision.id, decision.objects),
                )
                is YesNoDecision -> submitYesNo(decision.playerId, true)
                is ChooseTargetsDecision -> {
                    val legal = decision.legalTargets.values.flatten().toSet()
                    val targets = when {
                        flicker in legal -> listOf(flicker)
                        opponent in legal -> listOf(opponent)
                        else -> lands.filter { it in legal }
                    }
                    val result = submitTargetSelection(decision.playerId, targets)
                    withClue(
                        "prompt=${decision.prompt}; requirements=${decision.targetRequirements}; " +
                            "legal=${legal.map { getCardName(it) ?: it }}; " +
                            "chosen=${targets.map { getCardName(it) ?: it }}; error=${result.error}",
                    ) {
                        result.isSuccess shouldBe true
                    }
                }
                null -> bothPass()
                else -> error("Unexpected combo decision: ${decision::class.simpleName}")
            }
        }
        guard shouldNotBe 100
        state.stack.isEmpty() shouldBe true
        pendingDecision shouldBe null
        findCardInHand(player, "Ghostly Flicker") shouldBe flicker
    }

    fun GameTestDriver.runFlickerCycle(
        player: EntityId,
        opponent: EntityId,
        recursionCreature: String,
        flicker: EntityId,
        lands: List<EntityId>,
    ) {
        lands.forEach { land ->
            submit(
                ActivateAbility(
                    playerId = player,
                    sourceId = land,
                    abilityId = AbilityId.intrinsicMana('U'),
                ),
            ).isSuccess shouldBe true
        }
        val drake = findPermanent(player, "Peregrine Drake")!!
        val recursion = findPermanent(player, recursionCreature)!!
        castSpell(player, flicker, targets = listOf(drake, recursion)).isSuccess shouldBe true
        bothPass()
        resolveFlickerTriggers(player, opponent, flicker, lands)
    }

    test("requires exactly two targets") {
        val (driver, creature, _) = setup()
        val player = driver.activePlayer!!
        val flicker = driver.putCardInHand(player, "Ghostly Flicker")
        driver.giveMana(player, Color.BLUE, 3)

        driver.castSpell(player, flicker, targets = listOf(creature)).isSuccess shouldBe false
        driver.findCardInHand(player, "Ghostly Flicker") shouldNotBe null
    }

    test("returns both targets, refreshing the creature and untapping the land") {
        val (driver, creature, land) = setup()
        val player = driver.activePlayer!!
        val flicker = driver.putCardInHand(player, "Ghostly Flicker")
        driver.giveMana(player, Color.BLUE, 3)

        driver.castSpell(player, flicker, targets = listOf(creature, land)).isSuccess shouldBe true
        driver.bothPass()

        val returnedCreature = driver.findPermanent(player, "Grizzly Bears")
        val returnedLand = driver.findPermanent(player, "Island")
        returnedCreature shouldNotBe null
        returnedLand shouldNotBe null
        driver.state.getEntity(returnedCreature!!)?.has<SummoningSicknessComponent>() shouldBe true
        driver.state.getEntity(returnedLand!!)?.has<TappedComponent>() shouldBe false
        driver.getExile(player).contains(creature) shouldBe false
        driver.getExile(player).contains(land) shouldBe false
    }

    test("Peregrine Drake and Archaeomancer loop into a Sage's Row Denizen win") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.putCreatureOnBattlefield(player, "Peregrine Drake")
        driver.putCreatureOnBattlefield(player, "Archaeomancer")
        driver.putCreatureOnBattlefield(player, "Sage's Row Denizen")
        val lands = List(5) { driver.putLandOnBattlefield(player, "Island") }
        val flicker = driver.putCardInHand(player, "Ghostly Flicker")
        val opponentLibrary = ZoneKey(opponent, Zone.LIBRARY)
        driver.replaceState(
            driver.state.copy(
                zones = driver.state.zones +
                    (opponentLibrary to driver.state.getZone(opponentLibrary).take(8)),
            ),
        )

        repeat(2) { iteration ->
            driver.runFlickerCycle(player, opponent, "Archaeomancer", flicker, lands)

            lands.count(driver::isTapped) shouldBe 0
            driver.totalMana(player) shouldBe (iteration + 1) * 2
            driver.state.getZone(opponentLibrary).size shouldBe 8 - ((iteration + 1) * 4)
        }

        var guard = 0
        while (!driver.state.gameOver && guard++ < 300) {
            if (driver.pendingDecision != null) {
                driver.autoResolveDecision()
            } else {
                driver.bothPass()
            }
        }
        driver.assertGameOver(expectedWinner = player)
    }

    test("Mnemonic Wall is a second recursion creature for the Drake loop") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.putCreatureOnBattlefield(player, "Peregrine Drake")
        driver.putCreatureOnBattlefield(player, "Mnemonic Wall")
        val lands = List(5) { driver.putLandOnBattlefield(player, "Island") }
        val flicker = driver.putCardInHand(player, "Ghostly Flicker")

        driver.runFlickerCycle(player, opponent, "Mnemonic Wall", flicker, lands)

        lands.count(driver::isTapped) shouldBe 0
        driver.totalMana(player) shouldBe 2
        driver.findCardInHand(player, "Ghostly Flicker") shouldBe flicker
    }
})
