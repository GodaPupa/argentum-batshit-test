package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.conditions.CastChoiceMade
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SelfAlternativePaidChoiceScenarioTest : ScenarioTestBase() {
    init {
        for ((name, cost) in listOf("Paid Choice Probe" to "{3}", "Free Choice Probe" to "{0}")) {
            cardRegistry.register(card(name) {
                manaCost = cost
                typeLine = "Creature — Construct"
                power = 2
                toughness = 2
                selfAlternativeCost = SelfAlternativeCost(ManaCost.parse("{0}"))
            })
        }
        cardRegistry.register(card("Choice Discount Probe") {
            manaCost = "{0}"
            typeLine = "Artifact"
            staticAbility {
                ability = ModifySpellCost(
                    target = SpellCostTarget.YouCast(GameObjectFilter.Any),
                    modification = CostModification.ReduceGeneric(3)
                )
            }
        })
        cardRegistry.register(card("Entry Choice Probe") {
            manaCost = "{3}"
            typeLine = "Creature — Construct"
            power = 2
            toughness = 2
            selfAlternativeCost = SelfAlternativeCost(ManaCost.parse("{0}"))
            replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        })
        test("SA01 explicit self alternative writes the durable flag") {
            val game = board("Paid Choice Probe")
            val action = game.getLegalActions(1).mapNotNull { it.action as? CastSpell }.first {
                game.state.getEntity(it.cardId)?.get<CardComponent>()?.name == "Paid Choice Probe" &&
                    it.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
            }
            game.execute(action).error shouldBe null
            game.resolveStack()
            val id = game.findPermanent("Paid Choice Probe")
            id shouldNotBe null
            game.state.getEntity(id!!)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe ChoiceValue.Flag
        }
        test("SA02 normal casting on the same eligible board stays unmarked") {
            val game = board("Paid Choice Probe")
            game.castSpell(1, "Paid Choice Probe").error shouldBe null
            game.resolveStack()
            val id = game.findPermanent("Paid Choice Probe")
            id shouldNotBe null
            game.state.getEntity(id!!)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
        }
        test("SA03 zero printed cost does not imply a self alternative choice") {
            val game = board("Free Choice Probe")
            game.castSpell(1, "Free Choice Probe").error shouldBe null
            game.resolveStack()
            val id = game.findPermanent("Free Choice Probe")
            id shouldNotBe null
            game.state.getEntity(id!!)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
        }

        test("SA04 explicit choice survives whole-state stack and battlefield serialization") {
            val game = board("Paid Choice Probe")
            val action = explicit(game, "Paid Choice Probe")
            game.execute(action).error shouldBe null
            game.state.getEntity(action.cardId)?.get<SpellOnStackComponent>()?.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
            roundTrip(game)
            game.resolveStack().forEach { it.error shouldBe null }
            game.findPermanent("Paid Choice Probe") shouldBe action.cardId
            roundTrip(game)
            game.state.getEntity(action.cardId)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe ChoiceValue.Flag
        }
        test("SA05 entry pause serialization merges color with selected casting choice") {
            val game = board("Entry Choice Probe")
            val action = explicit(game, "Entry Choice Probe")
            game.execute(action).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.getPendingDecision() shouldNotBe null
            roundTrip(game)
            game.submitDecision(ColorChosenResponse(game.getPendingDecision()!!.id, Color.BLUE)).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.findPermanent("Entry Choice Probe") shouldBe action.cardId
            roundTrip(game)
            val bag = game.state.getEntity(action.cardId)?.get<CastChoicesComponent>()
            bag?.chosen?.get(ChoiceSlot.COLOR) shouldBe ChoiceValue.ColorChoice(Color.BLUE)
            bag?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe ChoiceValue.Flag
        }
        test("SA06 generic condition reader observes explicit choice while source exists") {
            val game = board("Paid Choice Probe")
            val action = explicit(game, "Paid Choice Probe")
            game.execute(action).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE),
                EffectContext(sourceId = action.cardId, controllerId = game.player1Id)) shouldBe true
        }
        test("SA07 generic condition reader rejects normal eligible casting") {
            val game = board("Paid Choice Probe")
            game.castSpell(1, "Paid Choice Probe").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            val id = game.findPermanent("Paid Choice Probe")
            id shouldNotBe null
            ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE),
                EffectContext(sourceId = id, controllerId = game.player1Id)) shouldBe false
        }
        // This is a transport isolation test: only the stored discriminator is varied.
        // It does not claim that each synthetic alternative is legally selectable.
        for (kind in AlternativeCostType.entries.filter { it != AlternativeCostType.SELF_ALTERNATIVE }) {
            test("SA08 transport rejects other discriminator " + kind.name) {
                val game = board("Paid Choice Probe")
                val action = explicit(game, "Paid Choice Probe")
                game.execute(action).error shouldBe null
                game.state = game.state.updateEntity(action.cardId) { entity ->
                    entity.with(entity.get<SpellOnStackComponent>()!!.copy(alternativeCost = kind))
                }
                roundTrip(game)
                game.resolveStack().forEach { it.error shouldBe null }
                game.findPermanent("Paid Choice Probe") shouldBe action.cardId
                game.state.getEntity(action.cardId)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
            }
        }
        test("SA09 legacy null transport remains unmarked") {
            val game = board("Paid Choice Probe")
            val action = explicit(game, "Paid Choice Probe")
            game.execute(action).error shouldBe null
            game.state = game.state.updateEntity(action.cardId) { entity ->
                entity.with(entity.get<SpellOnStackComponent>()!!.copy(alternativeCost = null))
            }
            roundTrip(game)
            game.resolveStack().forEach { it.error shouldBe null }
            game.findPermanent("Paid Choice Probe") shouldBe action.cardId
            game.state.getEntity(action.cardId)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
        }

        test("SA10 battlefield exit removes choice before normal recast") {
            val game = board("Paid Choice Probe")
            val action = explicit(game, "Paid Choice Probe")
            game.execute(action).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(action.cardId)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe ChoiceValue.Flag
            val firstVisit = game.state.objectRef(action.cardId)
            firstVisit shouldNotBe null
            game.castSpell(1, "Unsummon", action.cardId).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getHand(game.player1Id).contains(action.cardId) shouldBe true
            game.state.getEntity(action.cardId)?.get<CastChoicesComponent>() shouldBe null
            val handVisit = game.state.objectRef(action.cardId)
            handVisit shouldNotBe null
            handVisit shouldNotBe firstVisit
            game.state.isCurrentObject(firstVisit!!) shouldBe false
            roundTrip(game)
            game.state.objectRef(action.cardId) shouldBe handVisit
            game.castSpell(1, "Paid Choice Probe").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.findPermanent("Paid Choice Probe") shouldBe action.cardId
            val secondVisit = game.state.objectRef(action.cardId)
            secondVisit shouldNotBe null
            secondVisit shouldNotBe firstVisit
            secondVisit shouldNotBe handVisit
            game.state.isCurrentObject(firstVisit) shouldBe false
            game.state.isCurrentObject(handVisit!!) shouldBe false
            roundTrip(game)
            game.state.objectRef(action.cardId) shouldBe secondVisit
            game.state.getEntity(action.cardId)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
        }
        test("SA11 normal cast reduced to zero remains unmarked") {
            var builder = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Paid Choice Probe")
                .withCardOnBattlefield(1, "Choice Discount Probe")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(5) { builder = builder.withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain") }
            val game = builder.build()
            game.castSpell(1, "Paid Choice Probe").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            val id = game.findPermanent("Paid Choice Probe")
            id shouldNotBe null
            game.state.getEntity(id!!)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
        }
    }
    private fun roundTrip(game: TestGame) {
        val before = game.state
        game.state = SerializationTestSupport.roundTrip(before)
        game.state shouldBe before
    }
    private fun explicit(game: TestGame, name: String): CastSpell =
        game.getLegalActions(1).mapNotNull { it.action as? CastSpell }.first {
            game.state.getEntity(it.cardId)?.get<CardComponent>()?.name == name &&
                it.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
        }
    private fun board(name: String): TestGame {
        var builder = scenario().withPlayers("Player", "Opponent")
            .withCardInHand(1, name).withCardInHand(1, "Unsummon")
            .withLandsOnBattlefield(1, "Mountain", 3).withLandsOnBattlefield(1, "Island", 1)
            .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(5) {
            builder = builder.withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
        }
        return builder.build()
    }
}

