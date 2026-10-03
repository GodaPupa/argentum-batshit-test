package com.wingedsheep.engine.mechanics.casting

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CardDestination
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Actual cast, ETB condition, departure and serialized historical-source resolution. */
class AlternativeCostConditionScenarioTest : ScenarioTestBase() {
    private val witness = card("Alternative provenance witness") {
        manaCost = "{5}"; typeLine = "Creature — Goblin"; power = 1; toughness = 1
        selfAlternativeCost = SelfAlternativeCost(ManaCost.ZERO)
        triggeredAbility { trigger = Triggers.EntersBattlefield
            interveningIf = Conditions.CastChoiceIs(com.wingedsheep.sdk.scripting.ChoiceSlot.ALTERNATIVE_COST, "SELF_ALTERNATIVE")
            effect = Effects.GainLife(1) }
    }
    private val bounce = card("Provenance departure") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { target("creature", Targets.Creature); effect = Effects.ReturnToHand(EffectTarget.ContextTarget(0)) }
    }
    private val blink = card("Provenance blink") {
        manaCost = "{0}"; typeLine = "Instant"
        spell {
            target("creature", Targets.Creature)
            effect = Effects.Pipeline {
                val selected = gather(CardSource.ChosenTargets)
                val exiled = moveTracked(selected, CardDestination.ToZone(Zone.EXILE))
                move(exiled, CardDestination.ToZone(Zone.BATTLEFIELD))
            }
        }
    }
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    init {
        listOf(witness, bounce, blink).forEach(cardRegistry::register)
        for (leave in listOf(null, bounce, blink)) {
            test("paid alternative condition resolves exactly once after serialized ${leave?.name ?: "live entry"}") {
                var setup = scenario().withPlayers().withCardInHand(1, witness.name)
                if (leave != null) setup = setup.withCardInHand(1, leave.name)
                val g = setup.build()
                val id = g.state.getHand(g.player1Id).first { g.state.getEntity(it)
                    ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == witness.name }
                val initialLife = g.state.lifeTotal(g.player1Id)
                g.execute(CastSpell(g.player1Id, id, useAlternativeCost = true)).error shouldBe null
                g.passPriority().error shouldBe null
                g.passPriority().error shouldBe null
                val triggerId = g.state.stack.single()
                if (leave != null) {
                    g.castSpell(1, leave.name, id).error shouldBe null
                    g.passPriority().error shouldBe null
                    g.passPriority().error shouldBe null
                }
                g.state = json.decodeFromString<GameState>(json.encodeToString(g.state))
                // A blink's new noncast visit must not trigger this intervening-if ability.
                g.state.stack shouldBe listOf(triggerId)
                val ability = g.state.getEntity(triggerId)!!.get<TriggeredAbilityOnStackComponent>()!!
                val context = com.wingedsheep.engine.handlers.EffectContext.forTriggeredAbility(ability)
                val evaluator = com.wingedsheep.engine.handlers.ConditionEvaluator()
                val slot = com.wingedsheep.sdk.scripting.ChoiceSlot.ALTERNATIVE_COST
                evaluator.evaluate(g.state, Conditions.CastChoiceMade(slot), context) shouldBe true
                evaluator.evaluate(g.state, Conditions.CastChoiceIs(slot, "DASH"), context) shouldBe false
                if (leave != null) {
                    evaluator.evaluate(g.state, Conditions.CastChoiceMade(slot),
                        context.copy(lastKnownSourceSnapshot = null)) shouldBe false
                    val mismatched = ability.lastKnownSourceSnapshot!!.copy(objectRef = null)
                    evaluator.evaluate(g.state, Conditions.CastChoiceMade(slot),
                        context.copy(lastKnownSourceSnapshot = mismatched)) shouldBe false
                }
                g.passPriority().error shouldBe null
                g.passPriority().error shouldBe null
                g.state.lifeTotal(g.player1Id) shouldBe initialLife + 1
                g.state.stack.isEmpty() shouldBe true
            }
        }
    }
}
