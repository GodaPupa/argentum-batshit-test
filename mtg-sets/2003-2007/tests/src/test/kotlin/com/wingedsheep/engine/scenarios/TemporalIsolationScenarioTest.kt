package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tsp.cards.TemporalIsolation
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TemporalIsolationScenarioTest : FunSpec({
    val pinger = card("Isolation Fixture Pinger") {
        manaCost = "{1}{U}"
        typeLine = "Creature — Wizard"
        power = 2
        toughness = 5
        activatedAbility {
            cost = Costs.Tap
            val victim = target("any target", Targets.Any)
            effect = Effects.DealDamage(1, victim)
        }
    }
    val removeAura = card("Isolation Fixture Dispel Aura") {
        manaCost = "{W}"
        typeLine = "Instant"
        spell {
            val aura = target("enchantment", Targets.Enchantment)
            effect = Effects.Destroy(aura)
        }
    }
    fun fixture(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + listOf(TemporalIsolation, pinger, removeAura))
        d.initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    test("flash attaches on opponent's turn, shadow excludes ordinary blockers, combat damage is prevented") {
        val d = fixture()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val creature = d.putCreatureOnBattlefield(me, pinger.name)
        d.removeSummoningSickness(creature)
        val blocker = d.putCreatureOnBattlefield(enemy, "Centaur Courser")
        val aura = d.putCardInHand(enemy, TemporalIsolation.name)
        d.giveMana(enemy, Color.WHITE, 1)
        d.giveColorlessMana(enemy, 1)
        d.passPriority(me)
        d.castSpell(enemy, aura, listOf(creature)).isSuccess shouldBe true
        d.bothPass()
        d.state.projectedState.hasKeyword(creature, Keyword.SHADOW) shouldBe true
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(me, listOf(creature), enemy).isSuccess shouldBe true
        d.bothPass()
        d.declareBlockers(enemy, mapOf(blocker to listOf(creature))).isSuccess shouldBe false
        d.declareBlockers(enemy, emptyMap()).isSuccess shouldBe true
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.getLifeTotal(enemy) shouldBe 20
    }
    test("prevents noncombat damage by enchanted creature but not damage to it") {
        val d = fixture()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val creature = d.putCreatureOnBattlefield(me, pinger.name)
        d.removeSummoningSickness(creature)
        d.giveMana(me, Color.WHITE, 1)
        d.giveColorlessMana(me, 1)
        val aura = d.putCardInHand(me, TemporalIsolation.name)
        d.castSpell(me, aura, listOf(creature)).isSuccess shouldBe true
        d.bothPass()
        d.submit(ActivateAbility(me, creature, pinger.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Player(enemy)))).isSuccess shouldBe true
        d.bothPass()
        d.getLifeTotal(enemy) shouldBe 20
        val bolt = d.putCardInHand(me, "Lightning Bolt")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, bolt, listOf(creature)).isSuccess shouldBe true
        d.bothPass()
        (d.state.getEntity(creature)!!.get<DamageComponent>()?.amount ?: 0) shouldBe 3
        val removal = d.putCardInHand(me, removeAura.name)
        d.giveMana(me, Color.WHITE, 1)
        d.castSpell(me, removal, listOf(aura)).isSuccess shouldBe true
        d.bothPass()
        d.state.projectedState.hasKeyword(creature, Keyword.SHADOW) shouldBe false
        d.untapPermanent(creature)
        d.submit(ActivateAbility(me, creature, pinger.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Player(enemy)))).isSuccess shouldBe true
        d.bothPass()
        d.getLifeTotal(enemy) shouldBe 19
    }
})
