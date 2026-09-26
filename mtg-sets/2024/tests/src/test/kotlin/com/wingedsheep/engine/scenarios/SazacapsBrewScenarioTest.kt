package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Actual canonical Brew through casting/priority/resolution. Fixed mechanics, not matchup games. */
class SazacapsBrewScenarioTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    private fun setup(discardName: String? = "Forest") = scenario()
        .withPlayers("Brewer", "Opponent")
        .withCardInHand(1, "Sazacap's Brew")
        .withLandsOnBattlefield(1, "Mountain", 2)
        .apply {
            if (discardName != null) withCardInHand(1, discardName)
            repeat(8) { withCardInLibrary(1, "Forest"); withCardInLibrary(2, "Island") }
        }
        .withRngSeed(0xFEC601).withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.brewAction(
        gift: Boolean = false,
        creature: EntityId? = null,
        drawPlayer: EntityId = player1Id,
        discardName: String? = "Forest"
    ) = CastSpell(
        player1Id, findCardsInHand(1, "Sazacap's Brew").single(),
        listOfNotNull(ChosenTarget.Player(drawPlayer), creature?.let { ChosenTarget.Permanent(it) }),
        giftRecipient = if (gift) player2Id else null,
        additionalCostPayment = AdditionalCostPayment(
            discardedCards = discardName?.let { listOf(findCardsInHand(1, it).single()) } ?: emptyList()
        )
    )

    private fun TestGame.fish(): List<EntityId> = state.getBattlefield().filter {
        state.getEntity(it)?.get<CardComponent>()?.typeLine?.subtypes?.any { subtype -> subtype.value == "Fish" } == true
    }

    private fun TestGame.finish(): List<GameEvent> {
        val results = resolveStack()
        results.forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        state.gameOver shouldBe false
        return results.flatMap { it.events }
    }

    private fun TestGame.reject(action: CastSpell) {
        val before = state
        val result = execute(action)
        result.error.shouldNotBeNull()
        result.state shouldBe before
        result.events shouldBe emptyList()
        state shouldBe before
    }

    init {
        test("no creature leaves a legal nongift cast and no gift variant") {
            val game = setup().build()
            val action = game.brewAction()
            val choices = game.getLegalActions(1).filter { (it.action as? CastSpell)?.cardId == action.cardId }
            choices.count { it.actionType == "CastSpell" } shouldBe 1
            choices.count { it.actionType == "CastWithGift" } shouldBe 0
            game.execute(action).error shouldBe null
            game.finish()
            game.handSize(1) shouldBe 2
            game.fish() shouldBe emptyList()
            game.isInGraveyard(1, "Forest") shouldBe true
        }

        test("gift menu exposes player then own creature and preserves the exact discard cost") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant").build()
            val action = game.brewAction()
            val gift = game.getLegalActions(1).single {
                it.actionType == "CastWithGift" && (it.action as? CastSpell)?.cardId == action.cardId
            }
            val giftCast = gift.action as CastSpell
            giftCast.giftRecipient shouldBe game.player2Id
            giftCast.chosenModes shouldBe emptyList()
            val requirements = gift.targetRequirements.shouldNotBeNull()
            requirements.size shouldBe 2
            requirements[0].validTargets.toSet() shouldBe setOf(game.player1Id, game.player2Id)
            requirements[1].validTargets shouldBe listOf(game.findPermanent("Grizzly Bears")!!)
            gift.additionalCostInfo.shouldNotBeNull().discardCount shouldBe 1
            gift.additionalCostInfo!!.validDiscardTargets shouldBe game.findCardsInHand(1, "Forest")
        }

        test("promised Fish enters tapped before the draw and the temporary bonus") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.execute(game.brewAction(true, bears)).error shouldBe null
            game.fish() shouldBe emptyList()
            val events = game.finish()
            val fish = game.fish().single()
            game.state.projectedState.getController(fish) shouldBe game.player2Id
            game.state.getEntity(fish)!!.has<TokenComponent>() shouldBe true
            game.state.getEntity(fish)!!.has<TappedComponent>() shouldBe true
            game.state.projectedState.getPower(fish) shouldBe 1
            game.state.projectedState.getToughness(fish) shouldBe 1
            game.state.getEntity(fish)!!.get<CardComponent>()!!.colors shouldBe setOf(Color.BLUE)
            game.state.projectedState.getPower(bears) shouldBe 4
            game.state.projectedState.getToughness(bears) shouldBe 2
            game.handSize(1) shouldBe 2
            val entry = events.indexOfFirst { it is ZoneChangeEvent && it.entityId == fish && it.toZone == Zone.BATTLEFIELD }
            val draw = events.indexOfFirst { it is CardsDrawnEvent && it.playerId == game.player1Id }
            (entry >= 0 && draw > entry) shouldBe true
            events.filterIsInstance<GiftGivenEvent>().size shouldBe 1
            (events.indexOfFirst { it is GiftGivenEvent } > draw) shouldBe true
        }

        test("nongift may target the opponent without giving a Fish or a creature bonus") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.execute(game.brewAction(drawPlayer = game.player2Id)).error shouldBe null
            val events = game.finish()
            game.handSize(1) shouldBe 0
            game.handSize(2) shouldBe 2
            game.state.projectedState.getPower(bears) shouldBe 2
            game.fish() shouldBe emptyList()
            events.filterIsInstance<GiftGivenEvent>() shouldBe emptyList()
        }

        test("a forged gift missing its conditional creature target fails before paying any cost") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            game.reject(game.brewAction(gift = true))
        }

        test("the conditional gift target cannot be an opposing creature") {
            val game = setup().withCardOnBattlefield(2, "Grizzly Bears").build()
            game.reject(game.brewAction(true, game.findPermanent("Grizzly Bears")!!))
        }

        test("a nongift cast cannot include the conditional creature target") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            game.reject(game.brewAction(false, game.findPermanent("Grizzly Bears")!!))
        }

        test("a gift cannot be promised to its caster") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            game.reject(game.brewAction(true, game.findPermanent("Grizzly Bears")!!).copy(giftRecipient = game.player1Id))
        }

        test("Brew cannot be cast with no other card available to discard") {
            val game = setup(null).build()
            game.reject(game.brewAction(discardName = null))
        }

        test("the spell being cast cannot pay its own discard cost") {
            val game = setup().build()
            val action = game.brewAction()
            game.reject(action.copy(additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(action.cardId))))
        }

        test("discard and both target declarations are complete before any response priority") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            val action = game.brewAction(true, game.findPermanent("Grizzly Bears")!!)
            val discard = action.additionalCostPayment!!.discardedCards.single()
            val result = game.execute(action)
            result.error shouldBe null
            game.state.pendingDecision shouldBe null
            game.state.getGraveyard(game.player1Id).contains(discard) shouldBe true
            game.state.stack shouldBe listOf(action.cardId)
            game.state.getEntity(action.cardId)!!.get<TargetsComponent>()!!.targets shouldBe action.targets
            game.state.getEntity(action.cardId)!!.get<SpellOnStackComponent>()!!.giftRecipient shouldBe game.player2Id
            game.fish() shouldBe emptyList()
            game.handSize(1) shouldBe 0
            result.events.filterIsInstance<CardsDiscardedEvent>().sumOf { it.cardIds.size } shouldBe 1
        }

        test("countering a promised Brew gives no gift and does not refund the discard") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2).build()
            val action = game.brewAction(true, game.findPermanent("Grizzly Bears")!!)
            game.execute(action).error shouldBe null
            game.passPriority().error shouldBe null
            game.execute(CastSpell(game.player2Id, game.findCardsInHand(2, "Counterspell").single(),
                listOf(ChosenTarget.Spell(action.cardId)))).error shouldBe null
            val events = game.finish()
            game.fish() shouldBe emptyList()
            game.handSize(1) shouldBe 0
            game.isInGraveyard(1, "Forest") shouldBe true
            game.isInGraveyard(1, "Sazacap's Brew") shouldBe true
            events.filterIsInstance<GiftGivenEvent>() shouldBe emptyList()
        }

        test("removing the creature target still gives the Fish and draws for the legal player") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Lightning Bolt").withLandsOnBattlefield(1, "Mountain", 1).build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.execute(game.brewAction(true, bears)).error shouldBe null
            game.castSpell(1, "Lightning Bolt", bears).error shouldBe null
            game.finish()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.handSize(1) shouldBe 2
            game.fish().size shouldBe 1
        }

        test("an illegal player target does not shift or prevent the remaining creature bonus") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Gilded Light").withLandsOnBattlefield(1, "Plains", 2).build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.execute(game.brewAction(true, bears)).error shouldBe null
            game.castSpell(1, "Gilded Light").error shouldBe null
            game.finish()
            game.handSize(1) shouldBe 0
            game.fish().size shouldBe 1
            game.state.projectedState.getPower(bears) shouldBe 4
        }

        test("when both targets become illegal the entire spell fails including its gift") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Gilded Light").withLandsOnBattlefield(1, "Plains", 2)
                .withCardInHand(1, "Lightning Bolt").withLandsOnBattlefield(1, "Mountain", 1).build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.execute(game.brewAction(true, bears)).error shouldBe null
            game.castSpell(1, "Gilded Light").error shouldBe null
            game.castSpell(1, "Lightning Bolt", bears).error shouldBe null
            val events = game.finish()
            game.handSize(1) shouldBe 0
            game.fish() shouldBe emptyList()
            events.filterIsInstance<GiftGivenEvent>() shouldBe emptyList()
            game.isInGraveyard(1, "Sazacap's Brew") shouldBe true
        }

        test("the power bonus ends at cleanup while the Fish remains") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.execute(game.brewAction(true, bears)).error shouldBe null
            game.finish()
            game.state.projectedState.getPower(bears) shouldBe 4
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.state.projectedState.getPower(bears) shouldBe 2
            game.fish().size shouldBe 1
            game.state.gameOver shouldBe false
        }

        test("a spell copy retains the promised recipient and targets without another discard") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            val bears = game.findPermanent("Grizzly Bears")!!
            val action = game.brewAction(true, bears)
            game.execute(action).error shouldBe null
            // Exercise the real spell-copy operation; a copy is not cast and pays no costs.
            val copied = EngineServices(cardRegistry).stackResolver.putSpellCopy(game.state, action.cardId)
            copied.error shouldBe null
            copied.events.filterIsInstance<SpellCastEvent>() shouldBe emptyList()
            game.state = copied.state
            val copy = game.state.stack.last()
            copy shouldNotBe action.cardId
            game.state.getEntity(copy)!!.get<SpellOnStackComponent>()!!.giftRecipient shouldBe game.player2Id
            game.finish()
            game.handSize(1) shouldBe 4
            game.fish().size shouldBe 2
            game.state.projectedState.getPower(bears) shouldBe 6
            game.state.getGraveyard(game.player1Id).size shouldBe 2
        }

        test("discarded Fiery Temper resolves through madness before Brew gives its gift") {
            val game = setup("Fiery Temper").withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Mountain", 1).build()
            val action = game.brewAction(true, game.findPermanent("Grizzly Bears")!!, discardName = "Fiery Temper")
            val temper = action.additionalCostPayment!!.discardedCards.single()
            game.execute(action).error shouldBe null
            game.state.getZone(game.player1Id, Zone.EXILE).contains(temper) shouldBe true
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            game.state.stack.contains(action.cardId) shouldBe true
            game.fish() shouldBe emptyList()
            game.answerYesNo(true).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            game.selectTargets(listOf(game.player2Id)).error shouldBe null
            game.state.stack.last() shouldBe temper
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            game.getLifeTotal(2) shouldBe 17
            game.fish() shouldBe emptyList()
            game.state.stack.contains(action.cardId) shouldBe true
            game.finish()
            game.handSize(1) shouldBe 2
            game.fish().size shouldBe 1
            game.isInGraveyard(1, "Fiery Temper") shouldBe true
        }

        test("declining madness keeps the paid discard and then permits Brew to resolve") {
            val game = setup("Fiery Temper").withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Mountain", 1).build()
            game.execute(game.brewAction(true, game.findPermanent("Grizzly Bears")!!, discardName = "Fiery Temper")).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(false).error shouldBe null
            game.isInGraveyard(1, "Fiery Temper") shouldBe true
            game.fish() shouldBe emptyList()
            game.finish()
            game.getLifeTotal(2) shouldBe 20
            game.handSize(1) shouldBe 2
            game.fish().size shouldBe 1
        }

        test("public promise and exact targets survive action and paid-state serialization") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            val action = game.brewAction(true, game.findPermanent("Grizzly Bears")!!)
            val replayAction = json.decodeFromString(CastSpell.serializer(), json.encodeToString(CastSpell.serializer(), action))
            replayAction shouldBe action
            game.execute(replayAction).error shouldBe null
            val encoded = json.encodeToString(GameState.serializer(), game.state)
            game.state = json.decodeFromString(GameState.serializer(), encoded)
            game.getClientState(2).cards[action.cardId]!!.giftPromised shouldBe true
            game.state.getEntity(action.cardId)!!.get<TargetsComponent>()!!.targets shouldBe action.targets
            game.finish()
            game.handSize(1) shouldBe 2
            game.fish().size shouldBe 1
        }
    }
}
