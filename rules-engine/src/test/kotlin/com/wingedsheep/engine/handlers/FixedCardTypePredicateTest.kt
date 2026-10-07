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

class FixedCardTypePredicateTest : FunSpec({
    val player = EntityId.generate()
    val other = EntityId.generate()
    val subject = EntityId.generate()
    val evaluator = PredicateEvaluator()

    fun state(type: CardType, zone: Zone) = GameState(turnOrder = listOf(player, other))
        .withEntity(player, ComponentContainer())
        .withEntity(other, ComponentContainer())
        .withEntity(subject, ComponentContainer()
            .with(CardComponent(
                cardDefinitionId = "Fixed-type fixture",
                name = "Fixed-type fixture",
                manaCost = ManaCost(emptyList()),
                typeLine = TypeLine(cardTypes = setOf(type)),
                ownerId = player,
            ))
            .with(OwnerComponent(player))
            .with(ControllerComponent(player)))
        .addToZone(ZoneKey(player, zone), subject)

    fun matches(state: GameState, type: CardType) = evaluator.matches(
        state,
        state.projectedState,
        subject,
        GameObjectFilter.Any.withCardType(type),
        PredicateContext(controllerId = player),
    )

    test("fixed Battle filter matches a printed Battle and rejects a creature in the library") {
        matches(state(CardType.BATTLE, Zone.LIBRARY), CardType.BATTLE) shouldBe true
        matches(state(CardType.CREATURE, Zone.LIBRARY), CardType.BATTLE) shouldBe false
    }

    test("fixed card-type filter reads projected battlefield types") {
        val state = state(CardType.CREATURE, Zone.BATTLEFIELD).addFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.SetCardTypes(setOf("BATTLE")),
            affectedEntities = setOf(subject),
            duration = Duration.EndOfTurn,
            context = EffectContext(sourceId = null, controllerId = player),
        )
        matches(state, CardType.BATTLE) shouldBe true
        matches(state, CardType.CREATURE) shouldBe false
    }

    test("fixed card-type description is stable for Battle") {
        GameObjectFilter.Any.withCardType(CardType.BATTLE).description shouldBe "battle"
    }
})
