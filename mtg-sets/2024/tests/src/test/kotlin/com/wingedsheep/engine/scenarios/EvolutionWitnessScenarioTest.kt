package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.EvolutionWitness
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.CantReceiveCounters
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvolutionWitnessScenarioTest : FunSpec({
    val counterWard = card("Test Counter Ward") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility {
            ability = CantReceiveCounters(GroupFilter(GameObjectFilter.Permanent.named("Evolution Witness")))
        }
    }
    fun driver() = GameTestDriver().apply { registerCards(TestCards.all + EvolutionWitness + counterWard) }
    fun GameTestDriver.counters(id: EntityId) =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    fun GameTestDriver.activateAdapt(player: EntityId, witness: EntityId) = submit(
        ActivateAbility(playerId = player, sourceId = witness, abilityId = EvolutionWitness.activatedAbilities.single().id)
    )

    test("Adapt checks for +1/+1 counters on resolution and places two") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val witness = d.putCreatureOnBattlefield(player, "Evolution Witness")
        d.giveMana(player, Color.GREEN, 2)

        d.activateAdapt(player, witness).isSuccess shouldBe true
        d.bothPass()
        d.counters(witness) shouldBe 2
    }

    test("Adapt remains activatable with counters but resolves without adding more") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val witness = d.putCreatureOnBattlefield(player, "Evolution Witness")
        d.addComponent(witness, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        d.giveMana(player, Color.GREEN, 2)

        d.activateAdapt(player, witness).isSuccess shouldBe true
        d.bothPass()
        d.counters(witness) shouldBe 1
    }

    test("two stacked Adapt activations add counters only once") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val witness = d.putCreatureOnBattlefield(player, "Evolution Witness")
        d.giveMana(player, Color.GREEN, 4)

        d.activateAdapt(player, witness).isSuccess shouldBe true
        d.activateAdapt(player, witness).isSuccess shouldBe true
        repeat(4) { if (d.pendingDecision == null) d.bothPass() }
        d.counters(witness) shouldBe 2
    }

    test("counters placed before Adapt resolves cause it to do nothing") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val witness = d.putCreatureOnBattlefield(player, "Evolution Witness")
        d.giveMana(player, Color.GREEN, 2)

        d.activateAdapt(player, witness).isSuccess shouldBe true
        d.addComponent(witness, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        d.bothPass()
        d.counters(witness) shouldBe 1
    }

    test("Adapt does nothing when its source has left the battlefield") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val witness = d.putCreatureOnBattlefield(player, "Evolution Witness")
        d.giveMana(player, Color.GREEN, 2)

        d.activateAdapt(player, witness).isSuccess shouldBe true
        d.moveToGraveyard(witness)
        d.bothPass()
        d.counters(witness) shouldBe 0
    }

    test("Adapt uses normal counter placement prohibitions") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val witness = d.putCreatureOnBattlefield(player, "Evolution Witness")
        d.putPermanentOnBattlefield(player, "Test Counter Ward")
        d.giveMana(player, Color.GREEN, 2)

        d.activateAdapt(player, witness).isSuccess shouldBe true
        d.bothPass()
        d.counters(witness) shouldBe 0
    }
})
