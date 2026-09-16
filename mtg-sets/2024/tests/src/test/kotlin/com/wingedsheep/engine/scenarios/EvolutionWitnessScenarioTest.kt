package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.EvolutionWitness
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EvolutionWitnessScenarioTest : FunSpec({
    val putCounter = card("Put Counter") {
        manaCost = "{G}"
        typeLine = "Instant"
        spell {
            val target = target("target creature", Targets.Creature)
            effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, target)
        }
    }

    fun counters(game: GameTestDriver, permanent: EntityId): Int =
        game.state.getEntity(permanent)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    fun setup(): Triple<GameTestDriver, EntityId, EntityId> {
        val game = GameTestDriver().apply {
            registerCards(TestCards.all + listOf(EvolutionWitness, putCounter))
            initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = game.activePlayer!!
        val witness = game.putCreatureOnBattlefield(player, "Evolution Witness")
        val graveyardPermanent = game.putCardInGraveyard(player, "Grizzly Bears")
        return Triple(game, witness, graveyardPermanent)
    }

    test("Adapt 2 places one batch of counters and returns a permanent card") {
        val (game, witness, graveyardPermanent) = setup()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 1)
        game.giveMana(player, Color.GREEN, 1)

        game.submitSuccess(ActivateAbility(player, witness, EvolutionWitness.activatedAbilities.single().id))
        game.bothPass()

        game.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        game.submitTargetSelection(player, listOf(graveyardPermanent)).isSuccess shouldBe true
        game.bothPass()

        counters(game, witness) shouldBe 2
        game.getHand(player) shouldContain graveyardPermanent
    }

    test("Adapt checks again on resolution and adds nothing if a counter was added in response") {
        val (game, witness, graveyardPermanent) = setup()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 1)
        game.giveMana(player, Color.GREEN, 2)

        game.submitSuccess(ActivateAbility(player, witness, EvolutionWitness.activatedAbilities.single().id))
        val response = game.putCardInHand(player, "Put Counter")
        game.castSpell(player, response, listOf(witness)).isSuccess shouldBe true
        game.bothPass()
        game.submitTargetSelection(player, listOf(graveyardPermanent)).isSuccess shouldBe true
        game.bothPass()
        game.bothPass()

        counters(game, witness) shouldBe 1
    }

    test("a counter placed by another effect also triggers the Witness") {
        val (game, witness, graveyardPermanent) = setup()
        val player = game.activePlayer!!
        val spell = game.putCardInHand(player, "Put Counter")
        game.giveMana(player, Color.GREEN, 1)

        game.castSpell(player, spell, listOf(witness)).isSuccess shouldBe true
        game.bothPass()
        game.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        game.submitTargetSelection(player, listOf(graveyardPermanent)).isSuccess shouldBe true
        game.bothPass()

        counters(game, witness) shouldBe 1
        game.getHand(player) shouldContain graveyardPermanent
    }
})
