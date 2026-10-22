package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.StateBasedActionChecker
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockedComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Excluded fixed engine fixtures. No experimental deck, pilot, seed allocation or game outcome. */
class RemoveCardTypeCapabilityTest : FunSpec({
    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all)
        registerCards(listOf(
            card("Type Loss Shrine") {
                manaCost = "{R}"
                typeLine = "Legendary Enchantment Creature — God Shrine"
                power = 6
                toughness = 5
            },
            card("Type Loss Kindred") {
                manaCost = "{R}"
                typeLine = "Kindred Enchantment Creature — God Shrine"
                power = 6
                toughness = 5
            },
        ))
        val deck = Deck.of("Forest" to 40)
        val initial = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Fixture 1", deck), PlayerConfig("Fixture 2", deck)),
            skipMulligans = true, startingPlayerIndex = 0, seed = 0x545950454c4f5353L,
        ))
        replaceState(initial.state)
    }

    fun removeType(state: GameState, target: EntityId, type: String) = state.addFloatingEffect(
        layer = Layer.TYPE,
        modification = SerializableModification.RemoveType(type),
        affectedEntities = setOf(target),
        duration = Duration.EndOfTurn,
        context = EffectContext(sourceId = target, controllerId = state.projectedState.getController(target)!!),
    )

    fun checkSba(d: GameTestDriver, before: GameState): GameState {
        val checker = StateBasedActionChecker(cardRegistry = d.cardRegistry)
        val actual = checker.checkAndApply(before)
        val replay = checker.checkAndApply(SerializationTestSupport.roundTrip(before))
        SerializationTestSupport.encodeState(actual.newState) shouldBe SerializationTestSupport.encodeState(replay.newState)
        SerializationTestSupport.encodeEvents(actual.events) shouldBe SerializationTestSupport.encodeEvents(replay.events)
        return actual.newState
    }

    test("losing creature removes God while retaining enchantment Shrine and legendary") {
        val d = game()
        val id = d.putCreatureOnBattlefield(d.state.turnOrder[0], "Type Loss Shrine")
        val changed = removeType(d.state, id, "CREATURE")
        changed.projectedState.isCreature(id) shouldBe false
        changed.projectedState.getSubtypes(id) shouldBe setOf("Shrine")
        changed.projectedState.hasType(id, "God") shouldBe false
        changed.projectedState.hasType(id, "ENCHANTMENT") shouldBe true
        changed.projectedState.isLegendary(id) shouldBe true
        SerializationTestSupport.roundTrip(changed).projectedState.getSubtypes(id) shouldBe setOf("Shrine")
    }

    test("retained kindred preserves creature subtypes after creature type is removed") {
        val d = game()
        val id = d.putCreatureOnBattlefield(d.state.turnOrder[0], "Type Loss Kindred")
        val changed = removeType(d.state, id, "CREATURE")
        changed.projectedState.isCreature(id) shouldBe false
        changed.projectedState.hasType(id, "KINDRED") shouldBe true
        changed.projectedState.getSubtypes(id) shouldBe setOf("God", "Shrine")
    }

    test("retained creature preserves creature subtypes after kindred type is removed") {
        val d = game()
        val id = d.putCreatureOnBattlefield(d.state.turnOrder[0], "Type Loss Kindred")
        val changed = removeType(d.state, id, "KINDRED")
        changed.projectedState.isCreature(id) shouldBe true
        changed.projectedState.hasType(id, "KINDRED") shouldBe false
        changed.projectedState.getSubtypes(id) shouldBe setOf("God", "Shrine")
    }

    test("losing the last type that supports creature subtypes removes God in either order") {
        for (first in listOf("CREATURE", "KINDRED")) {
            val d = game()
            val id = d.putCreatureOnBattlefield(d.state.turnOrder[0], "Type Loss Kindred")
            val second = if (first == "CREATURE") "KINDRED" else "CREATURE"
            val changed = removeType(removeType(d.state, id, first), id, second)
            changed.projectedState.isCreature(id) shouldBe false
            changed.projectedState.hasType(id, "KINDRED") shouldBe false
            changed.projectedState.getSubtypes(id) shouldBe setOf("Shrine")
            changed.projectedState.hasType(id, "God") shouldBe false
        }
    }

    test("noncreature attacker leaves combat and removes reciprocal blocker links") {
        val d = game()
        val p1 = d.state.turnOrder[0]
        val p2 = d.state.turnOrder[1]
        val attacker = d.putCreatureOnBattlefield(p1, "Type Loss Shrine")
        val blocker = d.putCreatureOnBattlefield(p2, "Grizzly Bears")
        val combat = d.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_BLOCKERS)
            .updateEntity(attacker) { it.with(AttackingComponent(p2)).with(BlockedComponent(listOf(blocker))) }
            .updateEntity(blocker) { it.with(BlockingComponent(listOf(attacker))) }
        val ordinary = checkSba(d, combat)
        ordinary.getEntity(attacker)!!.has<AttackingComponent>() shouldBe true
        ordinary.getEntity(blocker)!!.has<BlockingComponent>() shouldBe true
        val changed = removeType(ordinary, attacker, "CREATURE")
        changed.projectedState.isCreature(attacker) shouldBe false
        val settled = checkSba(d, changed)
        settled.getEntity(attacker)!!.has<AttackingComponent>() shouldBe false
        settled.getEntity(attacker)!!.has<BlockedComponent>() shouldBe false
        settled.getEntity(blocker)!!.has<BlockingComponent>() shouldBe false
        settled.getBattlefield().containsAll(listOf(attacker, blocker)) shouldBe true
    }

    test("noncreature blocker leaves combat while its attacker remains blocked") {
        val d = game()
        val p1 = d.state.turnOrder[0]
        val p2 = d.state.turnOrder[1]
        val attacker = d.putCreatureOnBattlefield(p1, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(p2, "Type Loss Shrine")
        val combat = d.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_BLOCKERS)
            .updateEntity(attacker) { it.with(AttackingComponent(p2)).with(BlockedComponent(listOf(blocker))) }
            .updateEntity(blocker) { it.with(BlockingComponent(listOf(attacker))) }
        val settled = checkSba(d, removeType(combat, blocker, "CREATURE"))
        settled.getEntity(blocker)!!.has<BlockingComponent>() shouldBe false
        settled.getEntity(attacker)!!.has<AttackingComponent>() shouldBe true
        settled.getEntity(attacker)!!.get<BlockedComponent>()!!.blockerIds shouldBe emptyList()
    }

    test("becoming a creature again does not restore combat participation") {
        val d = game()
        val p1 = d.state.turnOrder[0]
        val p2 = d.state.turnOrder[1]
        val attacker = d.putCreatureOnBattlefield(p1, "Type Loss Shrine")
        val combat = d.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_BLOCKERS)
            .updateEntity(attacker) { it.with(AttackingComponent(p2)) }
        val settled = checkSba(d, removeType(combat, attacker, "CREATURE"))
        settled.getEntity(attacker)!!.has<AttackingComponent>() shouldBe false
        val creatureAgain = checkSba(d, settled.copy(floatingEffects = emptyList()))
        creatureAgain.projectedState.isCreature(attacker) shouldBe true
        creatureAgain.getEntity(attacker)!!.has<AttackingComponent>() shouldBe false
    }
})
