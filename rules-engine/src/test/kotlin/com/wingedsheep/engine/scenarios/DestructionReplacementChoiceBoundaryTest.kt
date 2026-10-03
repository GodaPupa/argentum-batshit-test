package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.handlers.effects.library.MoveCollectionExecutor
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.sba.creature.LethalDamageCheck
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.MoveType
import com.wingedsheep.sdk.scripting.references.Player
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Isolated diagnostic, not an Umbra implementation or admission test.
 * Existing, observably different replacements expose the missing destruction-choice seam
 * before adding another replacement family. Required-behavior probes intentionally fail
 * until destruction enters the resumable replacement pipeline.
 */
class DestructionReplacementChoiceBoundaryTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.initMirrorMatch(Deck.of("Plains" to 20, "Forest" to 20))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun addShield(driver: GameTestDriver, creature: EntityId, modification: SerializableModification) {
        val shield = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(Layer.ABILITY, modification = modification, affectedEntities = setOf(creature)),
            duration = Duration.EndOfTurn,
            sourceId = null,
            controllerId = driver.player1,
            timestamp = 1L
        )
        driver.replaceState(driver.state.copy(floatingEffects = driver.state.floatingEffects + shield))
    }

    fun markDamage(driver: GameTestDriver, creature: EntityId) {
        driver.replaceState(driver.state.updateEntity(creature) { it.with(DamageComponent(3)) })
    }

    test("control - lone regeneration clears damage and taps") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        markDamage(d, c)
        addShield(d, c, SerializableModification.RegenerationShield)
        val r = ZoneMovementUtils.destroyPermanent(d.state, c)
        (c in r.state.getBattlefield()) shouldBe true
        r.state.getEntity(c)?.get<DamageComponent>() shouldBe null
        r.state.getEntity(c)?.has<TappedComponent>() shouldBe true
        r.state.floatingEffects.size shouldBe 0
    }

    test("control - lone remove-damage replacement clears damage without tapping") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        markDamage(d, c)
        addShield(d, c, SerializableModification.RemoveDamageShield)
        val r = ZoneMovementUtils.destroyPermanent(d.state, c)
        (c in r.state.getBattlefield()) shouldBe true
        r.state.getEntity(c)?.get<DamageComponent>() shouldBe null
        r.state.getEntity(c)?.has<TappedComponent>() shouldBe false
        r.state.floatingEffects.size shouldBe 0
    }

    test("control - cannot regenerate still permits remove-damage replacement") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        addShield(d, c, SerializableModification.RegenerationShield)
        addShield(d, c, SerializableModification.RemoveDamageShield)
        val r = ZoneMovementUtils.destroyPermanent(d.state, c, canRegenerate = false)
        (c in r.state.getBattlefield()) shouldBe true
        r.state.getEntity(c)?.has<TappedComponent>() shouldBe false
        r.state.floatingEffects.single().effect.modification shouldBe SerializableModification.RegenerationShield
    }

    test("required - effect destruction offers the affected controller a replacement choice") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        addShield(d, c, SerializableModification.RegenerationShield)
        addShield(d, c, SerializableModification.RemoveDamageShield)
        val r = ZoneMovementUtils.destroyPermanent(d.state, c)
        r.state.pendingDecision.shouldNotBeNull().playerId shouldBe d.player1
        r.state.floatingEffects.size shouldBe 2
    }

    test("required - lethal SBA destruction offers the same replacement choice") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        markDamage(d, c)
        addShield(d, c, SerializableModification.RegenerationShield)
        addShield(d, c, SerializableModification.RemoveDamageShield)
        val r = LethalDamageCheck().check(d.state)
        r.state.pendingDecision.shouldNotBeNull().playerId shouldBe d.player1
        r.state.floatingEffects.size shouldBe 2
    }

    test("required - batch destruction suspends before consuming a competing replacement") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val other = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        addShield(d, c, SerializableModification.RegenerationShield)
        addShield(d, c, SerializableModification.RemoveDamageShield)
        val effect = MoveCollectionEffect(
            from = "victims",
            destination = CardDestination.ToZone(Zone.GRAVEYARD, Player.You),
            moveType = MoveType.Destroy
        )
        val context = EffectContext(
            sourceId = null,
            controllerId = d.player1,
            pipeline = PipelineState(storedCollections = mapOf("victims" to listOf(c, other)))
        )
        val r = MoveCollectionExecutor(d.cardRegistry).execute(d.state, effect, context)
        r.state.pendingDecision.shouldNotBeNull().playerId shouldBe d.player1
        r.state.floatingEffects.size shouldBe 2
        (other in r.state.getBattlefield()) shouldBe true
    }
})
