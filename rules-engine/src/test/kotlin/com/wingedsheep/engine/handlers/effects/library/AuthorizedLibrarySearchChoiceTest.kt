package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.handlers.effects.composite.CompositeEffectExecutor
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.LibrarySearchChoiceOrder
import com.wingedsheep.sdk.scripting.effects.LibrarySearchPortion
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthorizedLibrarySearchChoiceTest : FunSpec({
    val player = EntityId("searcher")
    val opponent = EntityId("opponent")
    val source = EntityId("instrument")
    val a = EntityId("land-a")
    val b = EntityId("land-b")
    val c = EntityId("land-c")

    fun card(name: String, type: CardType = CardType.LAND) = CardComponent(
        cardDefinitionId = name,
        name = name,
        manaCost = ManaCost(emptyList()),
        typeLine = TypeLine(cardTypes = setOf(type)),
        ownerId = player,
    )

    fun state(order: List<EntityId>): GameState {
        var result = GameState(turnOrder = listOf(player, opponent))
            .withEntity(player, ComponentContainer())
            .withEntity(opponent, ComponentContainer())
            .withEntity(source, ComponentContainer().with(card("Instrument", CardType.ARTIFACT)))
            .addToZone(ZoneKey(player, Zone.BATTLEFIELD), source)
        for (id in listOf(a, b, c)) {
            result = result.withEntity(id, ComponentContainer()
                .with(card("Basic Land"))
                .with(OwnerComponent(player)))
        }
        for (id in order) result = result.addToZone(ZoneKey(player, Zone.LIBRARY), id)
        return result
    }

    fun context(state: GameState): EffectContext {
        val origin = requireNotNull(state.objectRef(source))
        return EffectContext(
            sourceId = source,
            controllerId = player,
            objectReferences = ObjectReferenceEnvironment(
                captured = true,
                origin = origin,
                source = origin,
                resolutionKey = "instrument:resolution-1",
            ),
        )
    }

    fun run(
        state: GameState,
        sourceSpec: CardSource = CardSource.AuthorizedLibrarySearch(),
        between: Effect? = null,
    ): SelectCardsDecision {
        val gather = GatherCardsEffect(source = sourceSpec, storeAs = "pool")
        val select = SelectFromCollectionEffect(
            from = "pool",
            selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(1)),
            storeSelected = "found",
            librarySearchChoiceOrder = LibrarySearchChoiceOrder.CurrentAuthorizedSearch,
        )
        val steps = listOfNotNull(gather, between, select)
        val registry = CardRegistry()
        val composite = CompositeEffectExecutor { current, effect, ctx ->
            when (effect) {
                is GatherCardsEffect -> GatherCardsExecutor().execute(current, effect, ctx)
                is SelectFromCollectionEffect -> SelectFromCollectionExecutor(registry).execute(current, effect, ctx)
                else -> EffectResult.error(current, "Unexpected test effect")
            }
        }
        val result = composite.execute(state, CompositeEffect(steps), context(state))
        result.isPaused shouldBe true
        return result.state.pendingDecision as SelectCardsDecision
    }

    test("only the current direct search carries the offered physical order") {
        val first = run(state(listOf(a, b, c)))
        val opposite = run(state(listOf(b, a, c)))
        first.options shouldBe listOf(a, b, c)
        first.authorizedLibrarySearch?.offeredHandles shouldBe first.options
        first.authorizedLibrarySearch?.decisionId shouldBe first.id
        opposite.options shouldBe listOf(b, a, c)
        opposite.authorizedLibrarySearch?.offeredHandles shouldBe opposite.options
        opposite.authorizedLibrarySearch?.sourceOrigin?.entityId shouldBe source
    }

    test("restricted portion and duplicate names expose only eligible physical handles") {
        val decision = run(
            state(listOf(b, a, c)),
            CardSource.AuthorizedLibrarySearch(
                filter = GameObjectFilter.Land,
                portion = LibrarySearchPortion.Top(DynamicAmount.Fixed(2)),
            ),
        )
        decision.options shouldBe listOf(b, a)
        decision.authorizedLibrarySearch?.offeredHandles shouldBe listOf(b, a)
        decision.authorizedLibrarySearch?.libraryOwner shouldBe player
    }

    test("ordinary library gather and overwritten direct collection never grant order") {
        run(state(listOf(b, a, c)), CardSource.FromZone(Zone.LIBRARY)).authorizedLibrarySearch shouldBe null
        val overwrite = GatherCardsEffect(
            source = CardSource.FromZone(Zone.LIBRARY),
            storeAs = "pool",
        )
        run(state(listOf(b, a, c)), between = overwrite).authorizedLibrarySearch shouldBe null
    }
})
