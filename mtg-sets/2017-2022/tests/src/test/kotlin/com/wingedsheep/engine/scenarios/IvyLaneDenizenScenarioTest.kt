package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Focused rules coverage for Ivy Lane Denizen and its Project X counter interaction. */
class IvyLaneDenizenScenarioTest : FunSpec({

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.plusCounters(entityId: EntityId): Int =
        state.getEntity(entityId)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    fun GameTestDriver.castLlanowarElves(player: EntityId) {
        val elves = putCardInHand(player, "Llanowar Elves")
        giveMana(player, Color.GREEN, 1)
        castSpell(player, elves).isSuccess shouldBe true
        bothPass()
    }

    test("another green creature entering creates a targeted counter trigger") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(player, "Ivy Lane Denizen")
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")

        d.castLlanowarElves(player)

        val decision = d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        (bears in decision.legalTargets.flatten()) shouldBe true
        d.submitTargetSelection(decision.playerId, listOf(bears)).isSuccess shouldBe true
        d.bothPass()

        d.plusCounters(bears) shouldBe 1
    }

    test("Ivy Lane Denizen does not trigger from its own entry") {
        val d = driver()
        val player = d.activePlayer!!
        val denizenCard = d.putCardInHand(player, "Ivy Lane Denizen")
        d.giveMana(player, Color.GREEN, 1)
        d.giveColorlessMana(player, 3)

        d.castSpell(player, denizenCard).isSuccess shouldBe true
        d.bothPass()

        val denizen = d.findPermanent(player, "Ivy Lane Denizen")!!
        d.pendingDecision shouldBe null
        d.stackSize shouldBe 0
        d.plusCounters(denizen) shouldBe 0
    }

    test("the counter is not placed when the chosen creature is illegal on resolution") {
        val d = driver()
        val player = d.activePlayer!!
        d.putCreatureOnBattlefield(player, "Ivy Lane Denizen")
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")

        d.castLlanowarElves(player)
        val decision = d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        d.submitTargetSelection(decision.playerId, listOf(bears)).isSuccess shouldBe true

        d.moveToGraveyard(bears)
        d.bothPass()

        d.findPermanent(player, "Grizzly Bears") shouldBe null
        d.plusCounters(bears) shouldBe 0
    }
})
