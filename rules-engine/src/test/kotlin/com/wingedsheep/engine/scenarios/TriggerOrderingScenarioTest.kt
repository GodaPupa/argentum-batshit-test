package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** CR 603.3b/d: player-selected APNAP placement order precedes each trigger's target choices. */
class TriggerOrderingScenarioTest : ScenarioTestBase() {
    private val entrant = card("Ordering Entrant") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 2; toughness = 4
    }
    private val observer = card("Ordering Observer") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 1; toughness = 4
        triggeredAbility { trigger = Triggers.entersBattlefield(GameObjectFilter.Creature, TriggerBinding.OTHER); effect = Effects.GainLife(2) }
        triggeredAbility { trigger = Triggers.entersBattlefield(GameObjectFilter.Creature, TriggerBinding.OTHER); effect = Effects.LoseLife(2, EffectTarget.Controller) }
    }
    private val targeted = card("Ordering Target Observer") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 1; toughness = 4
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            val t = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, t)
        }
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            val t = target("creature", Targets.Creature)
            effect = Effects.GrantKeyword(com.wingedsheep.sdk.core.Keyword.VIGILANCE, t)
        }
    }
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    private fun setup() = scenario().withPlayers("Active", "Nonactive").withRngSeed(0xFE000081)
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .withCardInHand(1, entrant.name)
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
    private fun TestGame.enter() {
        castSpell(1, entrant.name).error shouldBe null
        passPriority().error shouldBe null; passPriority().error shouldBe null
    }
    private fun TestGame.order(index: Int) {
        val question = state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        submitDecision(OptionChosenResponse(question.id, index)).error shouldBe null
    }
    private fun TestGame.stackAbilities() = state.stack.map {
        state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!
    }

    init {
        cardRegistry.register(listOf(entrant, observer, targeted))

        test("putting life loss below life gain permits surviving at one life") {
            val game = setup().withCardOnBattlefield(1, observer.name).withLifeTotal(1, 1).build()
            game.enter()
            val question = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            question.playerId shouldBe game.player1Id
            val loss = question.options.indexOfFirst { it.contains("lose", ignoreCase = true) }
            (loss >= 0) shouldBe true
            game.order(loss)
            game.stackAbilities().size shouldBe 2
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.gameOver shouldBe false
            game.state.lifeTotal(game.player1Id) shouldBe 1
        }

        test("the other legal ordering resolves life loss first and loses before the gain") {
            val game = setup().withCardOnBattlefield(1, observer.name).withLifeTotal(1, 1).build()
            game.enter()
            val question = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            val gain = question.options.indexOfFirst { it.contains("gain", ignoreCase = true) }
            (gain >= 0) shouldBe true
            game.order(gain)
            game.passPriority().error shouldBe null; game.passPriority().error shouldBe null
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player2Id
        }

        test("invalid ordering choices fail without altering the game or consuming a trigger") {
            val game = setup().withCardOnBattlefield(1, observer.name).build()
            game.enter()
            val question = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            val before = game.state
            game.submitDecision(OptionChosenResponse(question.id, question.options.size)).error.shouldNotBeNull()
            game.state shouldBe before
            game.order(1)
            game.stackAbilities().size shouldBe 2
        }

        test("ordering is preserved through serialization and two separate target decisions") {
            val game = setup().withCardOnBattlefield(1, targeted.name).build()
            game.enter()
            game.state = json.decodeFromString<GameState>(json.encodeToString(GameState.serializer(), game.state))
            game.order(1)
            val first = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            game.submitDecision(TargetsResponse(first.id, mapOf(0 to listOf(game.findPermanent(entrant.name)!!)))).error shouldBe null
            // A target pause must not reopen the already selected ordering question.
            val second = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            game.submitDecision(TargetsResponse(second.id, mapOf(0 to listOf(game.findPermanent(targeted.name)!!)))).error shouldBe null
            game.state.pendingDecision shouldBe null
            game.stackAbilities().size shouldBe 2
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.gameOver shouldBe false
        }

        test("active and nonactive controllers order their own groups in APNAP placement order") {
            val game = setup().withCardOnBattlefield(1, observer.name).withCardOnBattlefield(2, observer.name).build()
            game.enter()
            game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>().playerId shouldBe game.player1Id
            game.order(1)
            game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>().playerId shouldBe game.player2Id
            game.order(0)
            game.stackAbilities().map { it.controllerId } shouldBe
                listOf(game.player1Id, game.player1Id, game.player2Id, game.player2Id)
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.lifeTotal(game.player1Id) shouldBe 20
            game.state.lifeTotal(game.player2Id) shouldBe 20
        }

        test("two copies of a card retain all four source identities and trigger payloads") {
            val game = setup().withCardOnBattlefield(1, observer.name).withCardOnBattlefield(1, observer.name).build()
            game.enter()
            val sources = game.findAllPermanents(observer.name).toSet()
            repeat(3) { game.order(0) }
            val abilities = game.stackAbilities()
            abilities.size shouldBe 4
            abilities.groupBy { it.sourceId }.mapValues { it.value.size } shouldBe sources.associateWith { 2 }
            abilities.map { it.triggeringEntityId }.toSet() shouldBe setOf(game.findPermanent(entrant.name))
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.lifeTotal(game.player1Id) shouldBe 20
        }
    }
}
