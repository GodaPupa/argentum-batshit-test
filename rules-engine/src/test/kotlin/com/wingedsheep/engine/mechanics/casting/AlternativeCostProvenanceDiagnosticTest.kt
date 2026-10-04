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

/** Failing requirements stay isolated; no alternative-cost card is admitted by this diagnostic. */
class AlternativeCostProvenanceDiagnosticTest : ScenarioTestBase() {
    private val witness = card("Alternative provenance witness") {
        manaCost = "{5}"; typeLine = "Creature — Goblin"; power = 1; toughness = 1
        selfAlternativeCost = SelfAlternativeCost(ManaCost.ZERO)
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(1) }
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
        test("the actually selected intrinsic alternative is recorded when legacy action omits discriminator") {
            val g = scenario().withPlayers().withCardInHand(1, witness.name).build()
            val id = g.state.getHand(g.player1Id).single()
            g.execute(CastSpell(g.player1Id, id, useAlternativeCost = true)).error shouldBe null
            g.state.getEntity(id)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
        }
        test("explicit paid alternative identity survives stack to permanent entry serialization") {
            val g = scenario().withPlayers().withCardInHand(1, witness.name).build()
            val id = g.state.getHand(g.player1Id).single()
            g.execute(CastSpell(g.player1Id, id, useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE)).error shouldBe null
            g.state.getEntity(id)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
            g.passPriority().error shouldBe null
            g.passPriority().error shouldBe null
            (id in g.state.getBattlefield()) shouldBe true
            val restored = json.decodeFromString<GameState>(json.encodeToString(g.state))
            // The permanent must retain the selected cost's identity, not merely mana spent.
            json.encodeToString(restored.getEntity(id)!!).contains("\"SELF_ALTERNATIVE\"") shouldBe true
        }
        for (leave in listOf(bounce, blink)) {
            test("pending ETB keeps old visit paid-cost truth after ${leave.name}") {
                val g = scenario().withPlayers().withCardInHand(1, witness.name).withCardInHand(1, leave.name).build()
                val id = g.state.getHand(g.player1Id).first { g.state.getEntity(it)
                    ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == witness.name }
                g.execute(CastSpell(g.player1Id, id, useAlternativeCost = true,
                    alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE)).error shouldBe null
                g.passPriority().error shouldBe null
                g.passPriority().error shouldBe null
                val oldRef = g.state.objectRef(id)!!
                val triggerId = g.state.stack.single()
                g.state.getEntity(triggerId)!!.get<TriggeredAbilityOnStackComponent>()!!.sourceId shouldBe id
                g.castSpell(1, leave.name, id).error shouldBe null
                g.passPriority().error shouldBe null
                g.passPriority().error shouldBe null
                g.state.isCurrentObject(oldRef) shouldBe false
                val restored = json.decodeFromString<GameState>(json.encodeToString(g.state))
                val oldTrigger = restored.getEntity(triggerId)!!.get<TriggeredAbilityOnStackComponent>()!!
                // A later source visit cannot replace the historical paid-cost truth of this ETB.
                json.encodeToString(oldTrigger).contains("\"SELF_ALTERNATIVE\"") shouldBe true
            }
        }
        test("normal mana payment does not manufacture intrinsic alternative provenance") {
            val g = scenario().withPlayers().withCardInHand(1, witness.name).build()
            g.state = g.state.updateEntity(g.player1Id) { it.with(ManaPoolComponent(colorless = 5)) }
            g.castSpell(1, witness.name).error shouldBe null
            val id = g.state.stack.last()
            g.state.getEntity(id)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe null
            g.passPriority().error shouldBe null
            g.passPriority().error shouldBe null
            json.encodeToString(g.state.getEntity(id)!!).contains("\"SELF_ALTERNATIVE\"") shouldBe false
        }
    }
}
