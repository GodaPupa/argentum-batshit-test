package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.core.SpellCopiedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.stack.StormCopyEffectExecutor
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import com.wingedsheep.sdk.scripting.effects.StormCopyEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Isolated prerequisite probe for replicate: the reusable triggered-copy executor must
 * survive countering the original before its copy trigger resolves. This does not model
 * replicate payment, register Lose Focus, or claim that a fixed count is paid multiplicity.
 */
class ReplicateCopyLifetimeBoundaryTest : FunSpec({
    val registry = CardRegistry()
    val resolver = StackResolver(cardRegistry = registry)
    val executor = StormCopyEffectExecutor(registry)
    val effect = DrawCardsEffect(DynamicAmount.Fixed(1), EffectTarget.Controller)

    fun initial(player: EntityId, original: EntityId): GameState = GameState(
        activePlayerId = player, priorityPlayerId = player, turnOrder = listOf(player)
    ).withEntity(original, ComponentContainer.of(
        CardComponent(
            cardDefinitionId = "Copy Lifetime Probe", name = "Copy Lifetime Probe",
            manaCost = ManaCost.parse("{X}{U}"), typeLine = TypeLine.instant(),
            oracleText = "", ownerId = player, spellEffect = effect
        ),
        OwnerComponent(player), ControllerComponent(player),
        SpellOnStackComponent(casterId = player, xValue = 3)
    )).copy(stack = listOf(original))

    fun copy(state: GameState, player: EntityId, original: EntityId, count: Int) = executor.execute(
        state, StormCopyEffect(copyCount = count, spellEffect = effect, spellName = "Copy Lifetime Probe"),
        EffectContext(sourceId = original, controllerId = player)
    )

    fun copies(state: GameState) = state.stack.filter { state.getEntity(it)?.has<CopyOfComponent>() == true }

    test("control - zero copies leaves the ordinary spell unchanged") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val state = initial(player, original)
        val result = copy(state, player, original, 0)
        result.isSuccess shouldBe true
        result.state shouldBe state
        result.events shouldBe emptyList()
    }

    test("control - two live-source copies retain X and are spells without cast events") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val result = copy(initial(player, original), player, original, 2)
        result.isSuccess shouldBe true
        copies(result.state).size shouldBe 2
        copies(result.state).forEach {
            result.state.getEntity(it)!!.get<SpellOnStackComponent>()!!.xValue shouldBe 3
        }
        result.events.filterIsInstance<SpellCopiedEvent>().size shouldBe 2
        result.events.filterIsInstance<SpellCastEvent>().size shouldBe 0
    }

    test("countering the original before the copy trigger resolves must preserve two copies and X") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val countered = resolver.counterSpell(initial(player, original), original)
        countered.isSuccess shouldBe true
        countered.newState.stack shouldBe emptyList()
        val result = copy(countered.newState, player, original, 2)
        result.isSuccess shouldBe true
        copies(result.state).size shouldBe 2
        copies(result.state).forEach {
            result.state.getEntity(it)!!.get<SpellOnStackComponent>()!!.xValue shouldBe 3
        }
    }

    test("control - countering one already-created copy leaves original and sibling intact") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val copied = copy(initial(player, original), player, original, 2)
        copied.isSuccess shouldBe true
        val ids = copies(copied.state)
        val countered = resolver.counterSpell(copied.state, ids.first())
        countered.isSuccess shouldBe true
        countered.newState.stack.contains(original) shouldBe true
        copies(countered.newState) shouldBe listOf(ids.last())
    }
})
