package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.mechanics.sba.creature.LethalDamageCheck
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Keyword
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
        keyword(Keyword.UMBRA_ARMOR)
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
})
