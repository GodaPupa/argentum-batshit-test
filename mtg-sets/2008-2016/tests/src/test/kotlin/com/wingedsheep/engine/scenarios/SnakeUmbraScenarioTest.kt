package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.mechanics.sba.creature.LethalDamageCheck
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.roe.cards.SnakeUmbra
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SnakeUmbraScenarioTest : ScenarioTestBase() {
    private val host = card("Snake Test Pinger") {
        manaCost = "{G}"
        typeLine = "Creature — Elf"
        power = 2
        toughness = 2
        activatedAbility {
            cost = Costs.Mana("{0}")
            val victim = target("any target", Targets.Any)
            effect = Effects.DealDamage(1, victim)
        }
    }
    init {
        cardRegistry.register(SnakeUmbra)
        cardRegistry.register(host)

        test("cast attaches and gives plus one plus one") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardInHand(1, SnakeUmbra.name).withLandsOnBattlefield(1, "Forest", 3).build()
            val c = g.findPermanent(host.name)!!
            g.castSpell(1, SnakeUmbra.name, c).error shouldBe null
            g.resolveStack()
            val a = g.findPermanent(SnakeUmbra.name)!!
            g.state.getEntity(a)?.get<AttachedToComponent>()?.targetId shouldBe c
            g.state.projectedState.getPower(c) shouldBe 3
            g.state.projectedState.getToughness(c) shouldBe 3
        }
        for (accept in listOf(true, false)) {
            test("noncombat opponent damage optional draw choice $accept") {
                val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                    .withCardAttachedTo(1, SnakeUmbra.name, host.name).withCardInLibrary(1, "Forest").build()
                val c = g.findPermanent(host.name)!!
                g.execute(ActivateAbility(g.player1Id, c, host.activatedAbilities.single().id,
                    targets = listOf(ChosenTarget.Player(g.player2Id)))).error shouldBe null
                g.resolveStack()
                g.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe g.player1Id
                g.handSize(1) shouldBe 0
                g.answerYesNo(accept).error shouldBe null
                g.resolveStack()
                g.handSize(1) shouldBe if (accept) 1 else 0
                g.getLifeTotal(2) shouldBe 19
            }
        }
        test("damage to the creature controller does not trigger") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, SnakeUmbra.name, host.name).withCardInLibrary(1, "Forest").build()
            g.execute(ActivateAbility(g.player1Id, g.findPermanent(host.name)!!, host.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Player(g.player1Id)))).error shouldBe null
            g.resolveStack()
            g.state.pendingDecision shouldBe null
            g.handSize(1) shouldBe 0
            g.getLifeTotal(1) shouldBe 19
        }
        test("damage to a creature does not trigger") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, SnakeUmbra.name, host.name).withCardOnBattlefield(2, "Centaur Courser")
                .withCardInLibrary(1, "Forest").build()
            g.execute(ActivateAbility(g.player1Id, g.findPermanent(host.name)!!, host.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(g.findPermanent("Centaur Courser")!!)))).error shouldBe null
            g.resolveStack()
            g.state.pendingDecision shouldBe null
            g.handSize(1) shouldBe 0
        }
        test("opponent controls enchanted creature and receives the optional draw") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(2, host.name)
                .withCardAttachedTo(1, SnakeUmbra.name, host.name).withCardInLibrary(2, "Forest")
                .withActivePlayer(2).build()
            g.execute(ActivateAbility(g.player2Id, g.findPermanent(host.name)!!, host.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Player(g.player1Id)))).error shouldBe null
            g.resolveStack()
            g.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe g.player2Id
            g.answerYesNo(true).error shouldBe null
            g.resolveStack()
            g.handSize(2) shouldBe 1
            g.handSize(1) shouldBe 0
        }
        test("combat damage to opponent offers draw") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, SnakeUmbra.name, host.name).withCardInLibrary(1, "Forest").build()
            g.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            g.declareAttackers(mapOf(host.name to 2)).error shouldBe null
            g.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
            if (g.state.pendingDecision is CombatResolutionDecision) g.submitDefaultCombatDamage()
            g.resolveStack()
            g.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe g.player1Id
            g.answerYesNo(true).error shouldBe null
            g.resolveStack()
            g.handSize(1) shouldBe 1
        }
        for (lethal in listOf(false, true)) {
            test("exact armor replaces destruction and clears damage lethal $lethal") {
                val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                    .withCardAttachedTo(1, SnakeUmbra.name, host.name).build()
                val c = g.findPermanent(host.name)!!
                val a = g.findPermanent(SnakeUmbra.name)!!
                val damaged = g.state.updateEntity(c) { it.with(DamageComponent(if (lethal) 3 else 1)) }
                val result = if (lethal) LethalDamageCheck().check(damaged)
                    else ZoneMovementUtils.destroyPermanent(damaged, c, canRegenerate = false).toExecutionResult()
                (c in result.state.getBattlefield()) shouldBe true
                (a in result.state.getBattlefield()) shouldBe false
                result.state.getEntity(c)?.get<DamageComponent>() shouldBe null
            }
        }
        test("exiling the host is not destruction and does not consume armor to save it") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, SnakeUmbra.name, host.name).build()
            val c = g.findPermanent(host.name)!!
            val result = ZoneMovementUtils.movePermanentToZone(g.state, c, Zone.EXILE)
            (c in result.state.getBattlefield()) shouldBe false
        }
    }
}
