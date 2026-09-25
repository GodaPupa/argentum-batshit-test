package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChosenCardTypePredicateTest : FunSpec({
    val player = EntityId.generate()
    val other = EntityId.generate()
    val subject = EntityId.generate()
    val evaluator = PredicateEvaluator()
    val filter = GameObjectFilter.Any.withCardTypeFromVariable("chosenType")

    fun state(zone: Zone) = GameState(turnOrder = listOf(player, other))
        .withEntity(player, ComponentContainer())
        .withEntity(other, ComponentContainer())
        .withEntity(subject, ComponentContainer()
            .with(CardComponent(
                cardDefinitionId = "Chosen-type fixture",
                name = "Chosen-type fixture",
                manaCost = ManaCost(emptyList()),
                typeLine = TypeLine(cardTypes = setOf(CardType.CREATURE)),
                ownerId = player,
            ))
            .with(OwnerComponent(player))
            .with(ControllerComponent(player)))
        .addToZone(ZoneKey(player, zone), subject)

    fun matches(state: GameState, choice: String?) = evaluator.matches(
        state, state.projectedState, subject, filter,
        PredicateContext(controllerId = player, chosenValues = choice?.let { mapOf("chosenType" to it) } ?: emptyMap()),
    )

    test("library type selection reads printed card types and accepts case variation") {
        val state = state(Zone.LIBRARY)
        matches(state, "Creature") shouldBe true
        matches(state, "creature") shouldBe true
        matches(state, "Land") shouldBe false
    }

    test("missing unknown and supertype selections fail closed") {
        val state = state(Zone.LIBRARY)
        matches(state, null) shouldBe false
        matches(state, "Not a card type") shouldBe false
        matches(state, "Basic") shouldBe false
    }

    test("battlefield choice uses actual continuous-effect type rather than printed type") {
        val state = state(Zone.BATTLEFIELD).addFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.SetCardTypes(setOf("ARTIFACT")),
            affectedEntities = setOf(subject),
            duration = Duration.EndOfTurn,
            context = EffectContext(sourceId = null, controllerId = player),
        )
        state.getEntity(subject)?.get<CardComponent>()?.typeLine?.cardTypes shouldBe setOf(CardType.CREATURE)
        state.projectedState.getProjectedValues(subject)?.types?.contains("ARTIFACT") shouldBe true
        matches(state, "Artifact") shouldBe true
        matches(state, "Creature") shouldBe false
    }
})
