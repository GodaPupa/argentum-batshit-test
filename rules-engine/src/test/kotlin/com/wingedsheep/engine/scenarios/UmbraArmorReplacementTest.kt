package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.handlers.effects.library.MoveCollectionExecutor
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.MoveType
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.engine.mechanics.sba.creature.LethalDamageCheck
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UmbraArmorReplacementTest : FunSpec({
    val armor = card("Synthetic Umbra") {
        manaCost = "{G}"
        typeLine = "Enchantment — Aura"
        auraTarget = Targets.Creature
        keywords(Keyword.UMBRA_ARMOR)
    }
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.registerCard(armor)
        it.initMirrorMatch(Deck.of("Plains" to 20, "Forest" to 20))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun attach(d: GameTestDriver, host: EntityId): EntityId {
        val a = d.putPermanentOnBattlefield(d.player1, armor.name)
        d.replaceState(d.state.updateEntity(a) { it.with(AttachedToComponent(host)) }
            .updateEntity(host) { it.with(AttachmentsComponent((it.get<AttachmentsComponent>()?.attachedIds ?: emptyList()) + a)) })
        return a
    }
    test("effect destruction destroys armor and clears host damage") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val a = attach(d, c)
        d.replaceState(d.state.updateEntity(c) { it.with(DamageComponent(2)) })
        val result = ZoneMovementUtils.destroyPermanent(d.state, c, canRegenerate = false)
        (c in result.state.getBattlefield()) shouldBe true
        (a in result.state.getBattlefield()) shouldBe false
        result.state.getEntity(c)?.get<DamageComponent>() shouldBe null
    }
    test("lethal damage is replaced by armor destruction") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val a = attach(d, c)
        d.replaceState(d.state.updateEntity(c) { it.with(DamageComponent(3)) })
        val result = LethalDamageCheck().check(d.state)
        (c in result.state.getBattlefield()) shouldBe true
        (a in result.state.getBattlefield()) shouldBe false
        result.state.getEntity(c)?.get<DamageComponent>() shouldBe null
    }
    test("multiple armors offer a choice and destroy exactly the selected aura") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val a = attach(d, c)
        val b = attach(d, c)
        val result = ZoneMovementUtils.destroyPermanent(d.state, c)
        d.replaceState(result.state)
        val choice = d.state.pendingDecision as ChooseOptionDecision
        choice.options.size shouldBe 2
        d.submitDecision(choice.playerId, OptionChosenResponse(choice.id, 1)).error shouldBe null
        (c in d.state.getBattlefield()) shouldBe true
        (a in d.state.getBattlefield()) shouldBe true
        (b in d.state.getBattlefield()) shouldBe false
        d.state.pendingDecision shouldBe null
    }

    for (auraFirst in listOf(true, false)) {
        test("simultaneous aura and host destruction preserves host regardless of collection order $auraFirst") {
            val d = driver()
            val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
            val a = attach(d, c)
            d.replaceState(d.state.updateEntity(c) { it.with(DamageComponent(2)) })
            val cards = if (auraFirst) listOf(a, c) else listOf(c, a)
            val result = MoveCollectionExecutor(d.cardRegistry).execute(d.state,
                MoveCollectionEffect(from = "victims", destination = CardDestination.ToZone(Zone.GRAVEYARD, Player.You), moveType = MoveType.Destroy),
                EffectContext(sourceId = null, controllerId = d.player1,
                    pipeline = PipelineState(storedCollections = mapOf("victims" to cards))))
            result.error shouldBe null
            (c in result.state.getBattlefield()) shouldBe true
            (a in result.state.getBattlefield()) shouldBe false
            result.state.getEntity(c)?.get<DamageComponent>() shouldBe null
        }
    }

    for (byEffect in listOf(true, false)) {
        test("armor replacement retains destruction cause for aura shield counter $byEffect") {
            val d = driver()
            val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
            val a = attach(d, c)
            d.replaceState(d.state.updateEntity(a) { it.with(CountersComponent().withAdded(CounterType.SHIELD, 1)) }
                .updateEntity(c) { it.with(DamageComponent(3)) })
            val result = if (byEffect) ZoneMovementUtils.destroyPermanent(d.state, c).toExecutionResult()
                else LethalDamageCheck().check(d.state)
            (c in result.state.getBattlefield()) shouldBe true
            (a in result.state.getBattlefield()) shouldBe byEffect
            result.state.getEntity(c)?.get<DamageComponent>() shouldBe null
        }
    }

    for (auraFirst in listOf(true, false)) {
        test("simultaneous duplicate destruction consumes one aura shield counter $auraFirst") {
            val d = driver()
            val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
            val a = attach(d, c)
            d.replaceState(d.state.updateEntity(a) { it.with(CountersComponent().withAdded(CounterType.SHIELD, 1)) })
            val cards = if (auraFirst) listOf(a, c) else listOf(c, a)
            val result = MoveCollectionExecutor(d.cardRegistry).execute(d.state,
                MoveCollectionEffect(from = "victims", destination = CardDestination.ToZone(Zone.GRAVEYARD, Player.You), moveType = MoveType.Destroy),
                EffectContext(sourceId = null, controllerId = d.player1,
                    pipeline = PipelineState(storedCollections = mapOf("victims" to cards))))
            result.error shouldBe null
            (c in result.state.getBattlefield()) shouldBe true
            (a in result.state.getBattlefield()) shouldBe true
            result.state.getEntity(a)?.get<CountersComponent>()?.getCount(CounterType.SHIELD) shouldBe 0
        }
    }

    test("nested aura destruction replacement pauses and consumes exactly one chosen shield") {
        val d = driver()
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val a = attach(d, c)
        val b = attach(d, c)
        val shields = listOf(SerializableModification.RegenerationShield, SerializableModification.RemoveDamageShield).map {
            ActiveFloatingEffect(id = EntityId.generate(),
                effect = FloatingEffectData(Layer.ABILITY, modification = it, affectedEntities = setOf(a)),
                duration = Duration.EndOfTurn, sourceId = null, controllerId = d.player1, timestamp = 1L)
        }
        d.replaceState(d.state.copy(floatingEffects = d.state.floatingEffects + shields)
            .updateEntity(c) { it.with(DamageComponent(2)) })
        d.replaceState(ZoneMovementUtils.destroyPermanent(d.state, c).state)
        val outer = d.state.pendingDecision as ChooseOptionDecision
        d.submitDecision(outer.playerId, OptionChosenResponse(outer.id, 0)).error shouldBe null
        val nested = d.state.pendingDecision as ChooseOptionDecision
        (outer.id == nested.id) shouldBe false
        d.state.getEntity(c)?.get<DamageComponent>() shouldBe null
        d.state.floatingEffects.size shouldBe 2
        d.submitDecision(nested.playerId, OptionChosenResponse(nested.id, 1)).error shouldBe null
        (c in d.state.getBattlefield()) shouldBe true
        (a in d.state.getBattlefield()) shouldBe true
        (b in d.state.getBattlefield()) shouldBe true
        d.state.floatingEffects.single().effect.modification shouldBe SerializableModification.RegenerationShield
        d.state.continuationStack shouldBe emptyList()
    }
})
