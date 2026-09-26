package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.research.ferocity.FerocityOfTheHuntPrerelease as ferocityResearchFixture
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** CR 117.3c/117.5/704.3: completed activations settle SBAs before priority, never during payment. */
class ActivationPriorityScenarioTest : ScenarioTestBase() {
    private val outlet = card("Activation Boundary Outlet") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 2; toughness = 3
        oracleText = "Sacrifice a creature: You gain 3 life."
        activatedAbility { cost = Costs.Sacrifice(GameObjectFilter.Creature); effect = Effects.GainLife(3) }
    }
    private val lord = card("Activation Boundary Lord") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        oracleText = "Creatures get +0/+1.\nWhen this creature dies, draw a card."
        staticAbility { ability = ModifyStats(0, 1, GroupFilter.AllCreatures) }
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.DrawCards(1) }
    }
    private val fragile = card("Activation Boundary Fragile") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 0; toughness = 0
        oracleText = "When this creature dies, draw a card."
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.DrawCards(1) }
    }
    private val targetingLord = card("Activation Boundary Targeting Lord") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        oracleText = "Creatures get +0/+1.\nWhen this creature dies, it deals 1 damage to target creature."
        staticAbility { ability = ModifyStats(0, 1, GroupFilter.AllCreatures) }
        triggeredAbility {
            trigger = Triggers.Dies
            val target = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, target)
        }
    }
    private val selfSacrifice = card("Activation Boundary Self Sacrifice") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 2
        oracleText = "Sacrifice this creature: You gain 3 life."
        activatedAbility { cost = Costs.SacrificeSelf; effect = Effects.GainLife(3) }
    }
    private val lifeOutlet = card("Activation Boundary Life Outlet") {
        manaCost = "{0}"; typeLine = "Artifact"
        oracleText = "Pay 2 life: You gain 3 life."
        activatedAbility { cost = Costs.PayLife(2); effect = Effects.GainLife(3) }
    }
    private val warded = card("Activation Boundary Warded Bear") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
        keywordAbility(KeywordAbility.ward("{2}"))
    }

    private fun setup() = scenario().withPlayers("Active player", "Nonactive player")
        .withCardInLibrary(1, "Island").withCardInLibrary(1, "Island")
        .withCardInLibrary(2, "Plains").withCardInLibrary(2, "Plains")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
    private fun TestGame.fixed() = apply { state = state.copy(rng = GameRng.seeded(0xFE000080)) }
    private fun TestGame.resolveOne() {
        passPriority().error shouldBe null
        passPriority().error shouldBe null
    }
    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack.isEmpty() shouldBe true
        state.gameOver shouldBe false
    }
    private fun sacrifice(game: TestGame, actor: EntityId, source: EntityId, victim: EntityId) =
        game.execute(ActivateAbility(actor, source, outlet.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(victim))))
    private fun mana(game: TestGame, actor: EntityId, source: EntityId, definition: CardDefinition,
                     color: Color? = null) = game.execute(ActivateAbility(actor, source,
        definition.activatedAbilities.single().id, manaColorChoice = color))

    init {
        cardRegistry.register(listOf(outlet, lord, fragile, targetingLord, selfSacrifice, lifeOutlet, warded,
            ferocityResearchFixture))

        test("nonactive activation removes its sacrificed token before returning priority") {
            val game = setup().withCardOnBattlefield(2, outlet.name)
                .withCardOnBattlefield(2, "Grizzly Bears", isToken = true).withPriorityPlayer(2).build().fixed()
            val token = game.findPermanent("Grizzly Bears")!!
            sacrifice(game, game.player2Id, game.findPermanent(outlet.name)!!, token).error shouldBe null
            game.state.getEntity(token) shouldBe null
            game.state.priorityPlayerId shouldBe game.player2Id
            game.state.pendingCastPriority shouldBe null
            game.state.stack.size shouldBe 1
            game.getLifeTotal(2) shouldBe 20
            game.finish()
            game.getLifeTotal(2) shouldBe 23
        }

        test("cost and SBA deaths join one APNAP batch above a nonactive player's activation") {
            val game = setup().withCardOnBattlefield(2, outlet.name).withCardOnBattlefield(2, lord.name)
                .withCardOnBattlefield(1, fragile.name).withPriorityPlayer(2).build().fixed()
            val lordId = game.findPermanent(lord.name)!!
            val fragileId = game.findPermanent(fragile.name)!!
            sacrifice(game, game.player2Id, game.findPermanent(outlet.name)!!, lordId).error shouldBe null
            game.isInGraveyard(2, lord.name) shouldBe true
            game.isInGraveyard(1, fragile.name) shouldBe true
            val triggers = game.state.stack.mapNotNull {
                game.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
            }
            triggers.map { it.controllerId } shouldBe listOf(game.player1Id, game.player2Id)
            triggers.map { it.sourceId } shouldBe listOf(fragileId, lordId)
            game.state.stack.size shouldBe 3
            game.state.priorityPlayerId shouldBe game.player2Id
            game.state.pendingCastPriority shouldBe null
        }

        test("SBA death occurs before choosing targets for a cost-triggered ability") {
            val game = setup().withCardOnBattlefield(1, outlet.name)
                .withCardOnBattlefield(1, targetingLord.name).withCardOnBattlefield(1, fragile.name)
                .withCardOnBattlefield(1, lifeOutlet.name)
                .withCardOnBattlefield(2, "Grizzly Bears").build().fixed()
            val lordId = game.findPermanent(targetingLord.name)!!
            val fragileId = game.findPermanent(fragile.name)!!
            val bears = game.findPermanent("Grizzly Bears")!!
            val result = sacrifice(game, game.player1Id, game.findPermanent(outlet.name)!!, lordId)
            result.error shouldBe null
            // Both same-controller deaths are complete before their placement order is chosen.
            val ordering = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            ordering.playerId shouldBe game.player1Id
            ordering.options.size shouldBe 2
            game.isInGraveyard(1, targetingLord.name) shouldBe true
            game.isInGraveyard(1, fragile.name) shouldBe true
            val lordFirst = ordering.options.indexOfFirst { it.contains(targetingLord.name) }
            (lordFirst >= 0) shouldBe true
            val ordered = game.submitDecision(OptionChosenResponse(ordering.id, lordFirst))
            ordered.error shouldBe null
            val choice = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            ordered.events.any { it is DecisionRequestedEvent && it.decisionId == choice.id } shouldBe true
            choice.legalTargets.values.flatten().contains(lordId) shouldBe false
            choice.legalTargets.values.flatten().contains(fragileId) shouldBe false
            game.isInGraveyard(1, fragile.name) shouldBe true
            val deathIndex = result.events.indexOfFirst {
                it is ZoneChangeEvent && it.entityId == fragileId && it.toZone == Zone.GRAVEYARD
            }
            val choiceIndex = result.events.indexOfFirst { it is DecisionRequestedEvent }
            (deathIndex >= 0 && deathIndex < choiceIndex) shouldBe true
            val beforeInterrupt = game.state
            game.execute(ActivateAbility(game.player1Id, game.findPermanent(lifeOutlet.name)!!,
                lifeOutlet.activatedAbilities.single().id)).error shouldBe "You don't have priority"
            game.state shouldBe beforeInterrupt
            game.state.priorityPlayerId shouldBe null
            game.selectTargets(listOf(bears)).error shouldBe null
            game.state.pendingCastPriority shouldBe null
            game.state.priorityPlayerId shouldBe game.player1Id
            game.state.stack.size shouldBe 3
        }

        test("Ferocity's cost-death return waits above the activated ability after Aura cleanup") {
            val game = setup().withCardOnBattlefield(1, selfSacrifice.name)
                .withCardOnBattlefield(1, ferocityResearchFixture.name)
                .withCardAttachedTo(1, ferocityResearchFixture.name, selfSacrifice.name).build().fixed()
            val source = game.findPermanent(selfSacrifice.name)!!
            val origin = game.state.objectRef(source)
            game.execute(ActivateAbility(game.player1Id, source,
                selfSacrifice.activatedAbilities.single().id)).error shouldBe null
            game.isInGraveyard(1, selfSacrifice.name) shouldBe true
            game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            game.state.stack.size shouldBe 2
            game.state.priorityPlayerId shouldBe game.player1Id
            game.getLifeTotal(1) shouldBe 20
            game.resolveOne()
            game.findPermanent(selfSacrifice.name) shouldBe source
            (game.state.objectRef(source) != origin) shouldBe true
            game.state.getEntity(source)!!.has<TappedComponent>() shouldBe true
            game.state.projectedState.hasKeyword(source, Keyword.DEATHTOUCH) shouldBe false
            game.state.stack.size shouldBe 1
            game.getLifeTotal(1) shouldBe 20
            game.finish()
            game.getLifeTotal(1) shouldBe 23
        }

        test("paying the last life loses before an activated life gain can resolve") {
            val game = setup().withCardOnBattlefield(1, lifeOutlet.name).withLifeTotal(1, 2).build().fixed()
            val result = game.execute(ActivateAbility(game.player1Id, game.findPermanent(lifeOutlet.name)!!,
                lifeOutlet.activatedAbilities.single().id))
            result.error shouldBe null
            game.getLifeTotal(1) shouldBe 0
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player2Id
            game.state.priorityPlayerId shouldBe null
            game.state.pendingDecision shouldBe null
            result.events.filterIsInstance<ResolvedEvent>().isEmpty() shouldBe true
        }

        test("standalone Spawn mana removes the token before nonactive player receives priority") {
            val game = setup().withCardOnBattlefield(2, "Eldrazi Spawn", isToken = true)
                .withPriorityPlayer(2).build().fixed()
            val token = game.findPermanent("Eldrazi Spawn")!!
            mana(game, game.player2Id, token, PredefinedTokens.EldraziSpawn).error shouldBe null
            game.state.getEntity(token) shouldBe null
            game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.colorless shouldBe 1
            game.state.stack.isEmpty() shouldBe true
            game.state.priorityPlayerId shouldBe game.player2Id
            game.state.pendingCastPriority shouldBe null
        }

        test("standalone Treasure with explicit color settles token cleanup before priority") {
            val game = setup().withCardOnBattlefield(2, "Treasure", isToken = true)
                .withPriorityPlayer(2).build().fixed()
            val token = game.findPermanent("Treasure")!!
            mana(game, game.player2Id, token, PredefinedTokens.Treasure, Color.BLACK).error shouldBe null
            game.state.getEntity(token) shouldBe null
            game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.black shouldBe 1
            game.state.stack.isEmpty() shouldBe true
            game.state.priorityPlayerId shouldBe game.player2Id
        }

        test("Treasure color-choice pause completes its mana effect before SBA and restores activator priority") {
            val game = setup().withCardOnBattlefield(2, "Treasure", isToken = true)
                .withCardOnBattlefield(2, lifeOutlet.name)
                .withPriorityPlayer(2).build().fixed()
            val token = game.findPermanent("Treasure")!!
            mana(game, game.player2Id, token, PredefinedTokens.Treasure).error shouldBe null
            val choice = game.state.pendingDecision.shouldBeInstanceOf<ChooseColorDecision>()
            game.isInGraveyard(2, "Treasure") shouldBe true
            val beforeInterrupt = game.state
            game.execute(ActivateAbility(game.player2Id, game.findPermanent(lifeOutlet.name)!!,
                lifeOutlet.activatedAbilities.single().id)).error shouldBe "You don't have priority"
            game.state shouldBe beforeInterrupt
            game.state.priorityPlayerId shouldBe null
            game.submitDecision(ColorChosenResponse(choice.id, Color.BLACK)).error shouldBe null
            game.state.getEntity(token) shouldBe null
            game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.black shouldBe 1
            game.state.pendingDecision shouldBe null
            game.state.pendingCastPriority shouldBe null
            game.state.priorityPlayerId shouldBe game.player2Id
        }

        test("Spawn activation inside ward payment does not run SBA until the outer payment completes") {
            val game = setup().withCardOnBattlefield(1, "Mountain")
                .withCardOnBattlefield(1, "Eldrazi Spawn", isToken = true)
                .withCardOnBattlefield(1, "Eldrazi Spawn", isToken = true)
                .withCardOnBattlefield(2, lifeOutlet.name)
                .withCardOnBattlefield(2, warded.name).withCardInHand(1, "Lightning Bolt").build().fixed()
            val tokens = game.state.getBattlefield().filter {
                game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Eldrazi Spawn"
            }
            tokens.size shouldBe 2
            val bear = game.findPermanent(warded.name)!!
            game.castSpell(1, "Lightning Bolt", bear).error shouldBe null
            game.resolveOne()
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            for (token in tokens) {
                mana(game, game.player1Id, token, PredefinedTokens.EldraziSpawn).error shouldBe null
                game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
                val beforeInterrupt = game.state
                game.execute(ActivateAbility(game.player2Id, game.findPermanent(lifeOutlet.name)!!,
                    lifeOutlet.activatedAbilities.single().id)).error shouldBe "You don't have priority"
                game.state shouldBe beforeInterrupt
                game.state.priorityPlayerId shouldBe null
                game.state.pendingCastPriority shouldBe null
                (game.state.getEntity(token) != null) shouldBe true
                (token in game.state.getGraveyard(game.player1Id)) shouldBe true
            }
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.colorless shouldBe 2
            game.submitManaSourcesDecision(selectedSources = emptyList(), autoPay = false).error shouldBe null
            tokens.forEach { game.state.getEntity(it) shouldBe null }
            game.state.pendingDecision shouldBe null
            game.findPermanent(warded.name) shouldBe bear
            game.state.stack.size shouldBe 1
            game.finish()
            game.isInGraveyard(2, warded.name) shouldBe true
        }
    }
}
