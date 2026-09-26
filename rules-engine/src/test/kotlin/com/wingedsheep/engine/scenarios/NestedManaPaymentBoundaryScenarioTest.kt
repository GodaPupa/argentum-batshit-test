package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import com.wingedsheep.sdk.scripting.effects.AddManaOfChoiceEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.values.ManaColorSet
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed CR 605.3a/117.5/603.3 scenarios; neither training games nor random outcomes. */
class NestedManaPaymentBoundaryScenarioTest : ScenarioTestBase() {
    private val altar = card("Nested Payment Altar") {
        manaCost = "{0}"; typeLine = "Artifact"
        activatedAbility { manaAbility = true; cost = Costs.Sacrifice(GameObjectFilter.Creature); effect = AddColorlessManaEffect(2) }
    }
    private val warded = card("Nested Payment Warded Bear") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
        keywordAbility(KeywordAbility.ward("{2}"))
    }
    private val lord = card("Nested Payment Targeting Lord") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        staticAbility { ability = ModifyStats(0, 1, GroupFilter.AllCreatures) }
        triggeredAbility {
            trigger = Triggers.Dies
            val target = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, target)
        }
    }
    private val fragile = card("Nested Payment Fragile") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 0; toughness = 0
    }
    private val drawFodder = card("Nested Payment Draw Fodder") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 1; toughness = 1
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.DrawCards(1) }
    }
    private val colorSource = card("Nested Payment Color Source") {
        manaCost = "{0}"; typeLine = "Artifact Creature — Construct"; power = 1; toughness = 1
        activatedAbility {
            manaAbility = true
            cost = Costs.SacrificeSelf
            effect = AddManaOfChoiceEffect(ManaColorSet.AnyColor, 2)
        }
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.DrawCards(1) }
    }
    private fun setup(actor: Int) = scenario().withPlayers("Active seat", "Other seat")
        .withRngSeed(0xFE000084 + actor.toLong()).withActivePlayer(1).withPriorityPlayer(actor)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .withCardOnBattlefield(actor, "Mountain").withCardOnBattlefield(actor, altar.name)
        .withCardOnBattlefield(3 - actor, warded.name).withCardInHand(actor, "Lightning Bolt")
        .withCardInLibrary(actor, "Island").withCardInLibrary(actor, "Plains")
        .withCardInLibrary(3 - actor, "Island")
    private fun TestGame.actor(seat: Int) = if (seat == 1) player1Id else player2Id
    private fun TestGame.openWard(seat: Int): String {
        altar.activatedAbilities.single().isManaAbility shouldBe true
        colorSource.activatedAbilities.single().isManaAbility shouldBe true
        castSpell(seat, "Lightning Bolt", findPermanent(warded.name)!!).error shouldBe null
        state.stack.size shouldBe 2
        deathTriggers().single().sourceId shouldBe findPermanent(warded.name)!!
        passPriority().error shouldBe null
        passPriority().error shouldBe null
        val payment = state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        payment.playerId shouldBe actor(seat)
        state.priorityPlayerId shouldBe null
        state.stack.size shouldBe 1
        return payment.id
    }
    private fun TestGame.activateAltar(seat: Int, victim: EntityId? = null) = execute(
        ActivateAbility(actor(seat), findPermanent(altar.name)!!, altar.activatedAbilities.single().id,
            costPayment = victim?.let { AdditionalCostPayment(sacrificedPermanents = listOf(it)) })
    )
    private fun TestGame.assertStillPaying(id: String) {
        state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().id shouldBe id
        state.priorityPlayerId shouldBe null
        state.pendingCastPriority shouldBe null
        state.stack.size shouldBe 1
    }
    private fun TestGame.deathTriggers() = state.stack.mapNotNull {
        state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
    }
    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        state.gameOver shouldBe false
    }
    init {
        cardRegistry.register(listOf(altar, warded, lord, fragile, drawFodder, colorSource))
        for (seat in 1..2) {
            test("seat $seat nested sacrifice choice restores the same payment without priority") {
                val game = setup(seat).withCardOnBattlefield(seat, "Grizzly Bears")
                    .withCardOnBattlefield(seat, "Gray Ogre").build()
                val id = game.openWard(seat)
                val victim = game.findPermanent("Grizzly Bears")!!
                game.activateAltar(seat).error shouldBe null
                game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                game.state.priorityPlayerId shouldBe null
                game.selectCards(listOf(victim)).error shouldBe null
                game.assertStillPaying(id)
                game.state.getEntity(game.actor(seat))!!.get<ManaPoolComponent>()!!.colorless shouldBe 2
                game.submitManaSourcesDecision().error shouldBe null
                game.state.priorityPlayerId shouldBe game.player1Id
                game.state.stack.size shouldBe 1
                game.finish()
                game.isInGraveyard(3 - seat, warded.name) shouldBe true
            }
            test("seat $seat deferred death target is chosen only after payment and lethal toughness SBA") {
                val game = setup(seat).withCardOnBattlefield(seat, lord.name)
                    .withCardOnBattlefield(seat, fragile.name).withCardOnBattlefield(seat, "Grizzly Bears").build()
                val id = game.openWard(seat)
                val fragileId = game.findPermanent(fragile.name)!!
                game.activateAltar(seat).error shouldBe null
                game.selectCards(listOf(game.findPermanent(lord.name)!!)).error shouldBe null
                game.assertStillPaying(id)
                game.findPermanent(fragile.name) shouldBe fragileId
                game.submitManaSourcesDecision().error shouldBe null
                val target = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                game.isInGraveyard(seat, fragile.name) shouldBe true
                target.legalTargets.values.flatten().contains(fragileId) shouldBe false
                game.state.priorityPlayerId shouldBe null
                game.selectTargets(listOf(game.findPermanent("Grizzly Bears")!!)).error shouldBe null
                game.deathTriggers().size shouldBe 1
                game.state.priorityPlayerId shouldBe game.player1Id
                game.finish()
            }
            test("seat $seat preselected sacrifice defers its death trigger exactly once") {
                val game = setup(seat).withCardOnBattlefield(seat, drawFodder.name).build()
                val id = game.openWard(seat)
                val handBefore = game.state.getHand(game.actor(seat)).size
                val victim = game.findPermanent(drawFodder.name)!!
                game.activateAltar(seat, victim).error shouldBe null
                game.assertStillPaying(id)
                game.state.getHand(game.actor(seat)).size shouldBe handBefore
                game.submitManaSourcesDecision().error shouldBe null
                game.deathTriggers().map { it.sourceId } shouldBe listOf(victim)
                game.finish()
                game.state.getHand(game.actor(seat)).size shouldBe handBefore + 1
            }
            test("seat $seat mana color pause preserves cost events and defers the draw") {
                val game = setup(seat).withCardOnBattlefield(seat, colorSource.name).build()
                val id = game.openWard(seat)
                val source = game.findPermanent(colorSource.name)!!
                val handBefore = game.state.getHand(game.actor(seat)).size
                game.execute(ActivateAbility(game.actor(seat), source, colorSource.activatedAbilities.single().id))
                    .error shouldBe null
                val color = game.state.pendingDecision.shouldBeInstanceOf<ChooseColorDecision>()
                game.state.priorityPlayerId shouldBe null
                game.state.stack.size shouldBe 1
                game.submitDecision(ColorChosenResponse(color.id, Color.BLACK)).error shouldBe null
                game.assertStillPaying(id)
                game.state.getHand(game.actor(seat)).size shouldBe handBefore
                game.submitManaSourcesDecision().error shouldBe null
                game.deathTriggers().map { it.sourceId } shouldBe listOf(source)
                game.finish()
                game.state.getHand(game.actor(seat)).size shouldBe handBefore + 1
            }
            test("seat $seat declined outer payment retains the already paid death trigger") {
                val game = setup(seat).withCardOnBattlefield(seat, drawFodder.name).build()
                val id = game.openWard(seat)
                val victim = game.findPermanent(drawFodder.name)!!
                val handBefore = game.state.getHand(game.actor(seat)).size
                game.activateAltar(seat, victim).error shouldBe null
                game.assertStillPaying(id)
                game.submitDecision(ManaSourcesSelectedResponse(id, declined = true)).error shouldBe null
                game.isInGraveyard(seat, "Lightning Bolt") shouldBe true
                game.deathTriggers().map { it.sourceId } shouldBe listOf(victim)
                game.findPermanent(warded.name) shouldBe game.state.getBattlefield().single {
                    game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == warded.name
                }
                game.finish()
                game.state.getHand(game.actor(seat)).size shouldBe handBefore + 1
            }
            test("seat $seat illegal interrupt during nested sacrifice is atomic and does not pay a cost") {
                val game = setup(seat).withCardOnBattlefield(seat, drawFodder.name)
                    .withCardOnBattlefield(seat, "Grizzly Bears").build()
                val id = game.openWard(seat)
                game.activateAltar(seat).error shouldBe null
                val before = game.state
                game.execute(PassPriority(game.actor(3 - seat))).error?.isNotEmpty() shouldBe true
                game.state shouldBe before
                game.selectCards(listOf(game.findPermanent(drawFodder.name)!!)).error shouldBe null
                game.assertStillPaying(id)
                game.submitManaSourcesDecision().error shouldBe null
                game.deathTriggers().size shouldBe 1
                game.finish()
            }
        }
    }
}
