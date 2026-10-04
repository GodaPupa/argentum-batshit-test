package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.stack.TargetingEvents
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TargetGroupProducerIntegrationTest : ScenarioTestBase() {
    private val spell = card("Announced Groups Spell") {
        manaCost = "{0}"; typeLine = "Instant"
        spell {
            target("first", com.wingedsheep.sdk.scripting.targets.TargetPlayer(optional = true))
            target("second", com.wingedsheep.sdk.scripting.targets.TargetPlayer(optional = true))
            effect = Effects.DrawCards(1)
        }
    }
    private val activated = card("Announced Groups Activator") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        activatedAbility {
            cost = Costs.Mana("{0}")
            target("first", com.wingedsheep.sdk.scripting.targets.TargetPlayer(optional = true))
            target("second", com.wingedsheep.sdk.scripting.targets.TargetPlayer(optional = true))
            effect = Effects.DrawCards(1)
        }
    }
    private val triggered = card("Announced Groups Trigger") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            target("first", com.wingedsheep.sdk.scripting.targets.TargetPlayer(optional = true))
            target("second", com.wingedsheep.sdk.scripting.targets.TargetPlayer(optional = true))
            effect = Effects.DrawCards(1)
        }
    }
    init {
        listOf(spell, activated, triggered).forEach(cardRegistry::register)
        test("cast producer stores omitted optional group and resolution names stay aligned") {
            val g = scenario().withPlayers().withCardInHand(1, spell.name).build()
            val id = g.state.getHand(g.player1Id).single()
            g.execute(CastSpell(g.player1Id, id, listOf(ChosenTarget.Player(g.player2Id)), announcedTargetCounts = listOf(0, 1))).error shouldBe null
            val c = g.state.getEntity(id)!!.get<TargetsComponent>()!!
            c.announcedTargetCounts shouldBe listOf(0, 1)
            c.targetRequirements.map { it.count } shouldBe listOf(0, 1)
            EffectContext.buildNamedTargets(c.targetRequirements, c.targets).keys shouldBe setOf("second")
        }
        test("activated producer retains ambiguous chosen group") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, activated.name).build()
            g.execute(ActivateAbility(g.player1Id, g.findPermanent(activated.name)!!, activated.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Player(g.player2Id)), announcedTargetCounts = listOf(0, 1))).error shouldBe null
            g.state.getEntity(g.state.stack.last())!!.get<TargetsComponent>()!!.announcedTargetCounts shouldBe listOf(0, 1)
        }
        test("interactive triggered producer retains empty requirement rather than deleting its identity") {
            val g = scenario().withPlayers().withCardInHand(1, triggered.name).withCardInLibrary(1, "Island").build()
            g.castSpell(1, triggered.name).error shouldBe null
            g.resolveStack()
            val question = g.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            g.submitDecision(TargetsResponse(question.id, mapOf(0 to emptyList(), 1 to listOf(g.player2Id)))).error shouldBe null
            val c = g.state.getEntity(g.state.stack.last())!!.get<TargetsComponent>()!!
            c.announcedTargetCounts shouldBe listOf(0, 1)
            c.targetRequirements.map { it.id } shouldBe listOf("first", "second")
            g.resolveStack()
            g.handSize(1) shouldBe 1
        }
        test("spell copying preserves selected group") {
            val g = scenario().withPlayers().withCardInHand(1, spell.name).build()
            val id = g.state.getHand(g.player1Id).single()
            g.execute(CastSpell(g.player1Id, id, listOf(ChosenTarget.Player(g.player2Id)), announcedTargetCounts = listOf(0, 1))).error shouldBe null
            val result = EngineServices(cardRegistry).stackResolver.putSpellCopy(g.state, id)
            result.error shouldBe null
            result.state.getEntity(result.state.stack.last())!!.get<TargetsComponent>()!!.announcedTargetCounts shouldBe listOf(0, 1)
        }
        test("atomic retarget leaves declared cardinalities and named group identity intact") {
            val g = scenario().withPlayers().withCardInHand(1, spell.name).build()
            val id = g.state.getHand(g.player1Id).single()
            g.execute(CastSpell(g.player1Id, id, listOf(ChosenTarget.Player(g.player2Id)), announcedTargetCounts = listOf(0, 1))).error shouldBe null
            val result = TargetingEvents.replaceTargets(g.state, id, listOf(ChosenTarget.Player(g.player1Id)))
            result.error shouldBe null
            val c = result.state.getEntity(id)!!.get<TargetsComponent>()!!
            c.announcedTargetCounts shouldBe listOf(0, 1)
            EffectContext.buildNamedTargets(c.targetRequirements, c.targets) shouldBe mapOf("second" to ChosenTarget.Player(g.player1Id))
            result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
        }
        test("ambiguous undeclared cast fails before it moves the card") {
            val g = scenario().withPlayers().withCardInHand(1, spell.name).build()
            val id = g.state.getHand(g.player1Id).single()
            g.execute(CastSpell(g.player1Id, id, listOf(ChosenTarget.Player(g.player2Id)))).error.isNullOrBlank() shouldBe false
            (id in g.state.getHand(g.player1Id)) shouldBe true
            g.state.stack shouldBe emptyList()
        }
    }
}
