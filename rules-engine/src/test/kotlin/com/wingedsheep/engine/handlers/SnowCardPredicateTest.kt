package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SnowCardPredicateTest : FunSpec({
    val player = EntityId.generate()
    val other = EntityId.generate()
    val snow = EntityId.generate()
    val ordinary = EntityId.generate()
    val evaluator = PredicateEvaluator()
    val filter = GameObjectFilter.Any.withCardPredicate(CardPredicate.IsSnow)

    fun card(name: String, isSnow: Boolean) = ComponentContainer()
        .with(CardComponent(
            cardDefinitionId = name,
            name = name,
            manaCost = ManaCost(emptyList()),
            typeLine = TypeLine(
                supertypes = if (isSnow) setOf(Supertype.SNOW) else emptySet(),
                cardTypes = setOf(CardType.CREATURE),
            ),
            ownerId = player,
        ))
        .with(OwnerComponent(player))
        .with(ControllerComponent(player))

    fun state(zone: Zone) = GameState(turnOrder = listOf(player, other))
        .withEntity(player, ComponentContainer())
        .withEntity(other, ComponentContainer())
        .withEntity(snow, card("Snow fixture", true))
        .withEntity(ordinary, card("Ordinary fixture", false))
        .addToZone(ZoneKey(player, zone), snow)
        .addToZone(ZoneKey(player, zone), ordinary)

    fun matches(state: GameState, id: EntityId) = evaluator.matches(
        state,
        state.projectedState,
        id,
        filter,
        PredicateContext(controllerId = player),
    )

    test("printed Snow supertype matches in a non-battlefield zone") {
        val state = state(Zone.LIBRARY)
        matches(state, snow) shouldBe true
        matches(state, ordinary) shouldBe false
    }

    test("projected battlefield types retain Snow and drive the same predicate") {
        val state = state(Zone.BATTLEFIELD)
        state.projectedState.getProjectedValues(snow)?.types?.contains("SNOW") shouldBe true
        state.projectedState.getProjectedValues(ordinary)?.types?.contains("SNOW") shouldBe false
        matches(state, snow) shouldBe true
        matches(state, ordinary) shouldBe false
    }
})
