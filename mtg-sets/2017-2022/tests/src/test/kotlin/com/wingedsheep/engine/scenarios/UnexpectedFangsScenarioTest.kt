package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CountersAddedEvent
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class UnexpectedFangsScenarioTest : ScenarioTestBase() {
    init {
        test("both counters persist through combat and cleanup and lifelink gains actual combat damage") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Unexpected Fangs")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bear = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Unexpected Fangs", bear).error shouldBe null
            val events = game.resolveStack().flatMap { it.events }.filterIsInstance<CountersAddedEvent>()
            events.filter { it.entityId == bear }.map { it.counterType to it.amount }.toSet() shouldBe
                setOf("+1/+1" to 1, "lifelink" to 1)
            game.state.projectedState.getPower(bear) shouldBe 3
            game.state.projectedState.getToughness(bear) shouldBe 3
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Grizzly Bears" to 2)).error shouldBe null
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.getLifeTotal(1) shouldBe 23
            game.getLifeTotal(2) shouldBe 17
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            val counters = game.state.getEntity(bear)!!.get<CountersComponent>()!!
            counters.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            counters.getCount(CounterType.LIFELINK) shouldBe 1
            game.state.projectedState.hasKeyword(bear, Keyword.LIFELINK) shouldBe true
            game.state.projectedState.getPower(bear) shouldBe 3
        }

        listOf(1, 2).forEach { creatureController ->
            test("lifelink on player $creatureController creature gains life for its controller on noncombat damage") {
                val game = scenario().withPlayers()
                    .withCardInHand(1, "Unexpected Fangs")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(creatureController, "Prodigal Pyromancer", summoningSickness = false)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val pinger = game.findPermanent("Prodigal Pyromancer")!!
                game.castSpell(1, "Unexpected Fangs", pinger).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.projectedState.hasKeyword(pinger, Keyword.LIFELINK) shouldBe true
                game.state.projectedState.getPower(pinger) shouldBe 2
                val controller = if (creatureController == 1) game.player1Id else game.player2Id
                val victim = if (creatureController == 1) game.player2Id else game.player1Id
                if (creatureController == 2) game.passPriority().error shouldBe null
                val ability = cardRegistry.getCard("Prodigal Pyromancer")!!.activatedAbilities.single()
                game.execute(ActivateAbility(controller, pinger, ability.id, listOf(ChosenTarget.Player(victim))))
                    .error shouldBe null
                val damage = game.resolveStack().flatMap { it.events }.filterIsInstance<DamageDealtEvent>().single()
                damage.sourceId shouldBe pinger
                damage.amount shouldBe 1
                damage.isCombatDamage shouldBe false
                game.getLifeTotal(creatureController) shouldBe 21
                game.getLifeTotal(if (creatureController == 1) 2 else 1) shouldBe 19
            }
        }

        test("a creature returned to hand in response receives neither counter") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Unexpected Fangs").withCardInHand(2, "Unsummon")
                .withLandsOnBattlefield(1, "Swamp", 2).withLandsOnBattlefield(2, "Island", 1)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bear = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Unexpected Fangs", bear).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Unsummon", bear).error shouldBe null
            val results = game.resolveStack()
            results.forEach { it.error shouldBe null }
            results.flatMap { it.events }.filterIsInstance<CountersAddedEvent>() shouldBe emptyList()
            game.isInHand(1, "Grizzly Bears") shouldBe true
            game.state.getEntity(bear)?.get<CountersComponent>() shouldBe null
        }

        test("a noncreature target is rejected without altering state") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Unexpected Fangs").withLandsOnBattlefield(1, "Swamp", 2)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.state
            game.castSpell(1, "Unexpected Fangs", game.findPermanent("Swamp")!!).error shouldNotBe null
            game.state shouldBe before
        }
    }
}
