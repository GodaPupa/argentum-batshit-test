package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsDiscardedEvent
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.ManaSpentEvent
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.TriggerOrderingContinuation
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Marauding Mako — "Whenever you discard one or more cards, put that many +1/+1 counters on this
 * creature." plus Cycling {2}.
 *
 * Batch semantics (CR 603.2c): one trigger per discard *event*, sized by that event. These cases pin
 * the two-card case (one counter per card of a single event) and the "cycling this card doesn't feed
 * its own trigger" edge, since the discard ability only functions on the battlefield.
 */
class MaraudingMakoScenarioTest : ScenarioTestBase() {

    init {
        test("a two-card discard event adds two +1/+1 counters") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Marauding Mako", summoningSickness = false)
                .withCardInHand(1, "Faithless Looting")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .stocked()
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val mako = game.findPermanent("Marauding Mako")!!

            // Faithless Looting: draw two, then discard two — a single discard event.
            val cast = game.castSpell(1, "Faithless Looting")
            withClue("Faithless Looting cast should succeed: ${cast.error}") { cast.error shouldBe null }
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()
            if (game.hasPendingDecision()) {
                game.selectCards(game.findCardsInHand(1, "Grizzly Bears").take(2))
            }
            game.resolveStack()

            withClue("\"That many\" reads the size of the one discard event: 2") {
                game.counters(mako) shouldBe 2
                game.state.projectedState.getPower(mako) shouldBe 3
                game.state.projectedState.getToughness(mako) shouldBe 3
            }
        }

        test("cycling another card is a one-card discard event") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Marauding Mako", summoningSickness = false)
                .withCardInHand(1, "Agonasaur Rex")
                .withLandsOnBattlefield(1, "Forest", 3)
                .stocked()
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val mako = game.findPermanent("Marauding Mako")!!
            val rex = game.findCardsInHand(1, "Agonasaur Rex").single()
            val forests = game.findPermanents("Forest")
            forests.size shouldBe 3
            val handBefore = game.handSize(1)

            val cycle = game.cycleCard(1, "Agonasaur Rex")
            withClue("Cycling should succeed: ${cycle.error}") { cycle.error shouldBe null }
            val discard = cycle.events.filterIsInstance<CardsDiscardedEvent>().single()
            discard.playerId shouldBe game.player1Id
            discard.cardIds shouldBe listOf(rex)
            discard.asCyclingCost shouldBe true
            val payment = cycle.events.filterIsInstance<ManaSpentEvent>().single()
            payment.playerId shouldBe game.player1Id
            payment.total shouldBe 3
            payment.green shouldBe 3
            forests.forEach { game.state.getEntity(it)!!.has<TappedComponent>() shouldBe true }
            game.isInGraveyard(1, "Agonasaur Rex") shouldBe true
            game.counters(mako) shouldBe 0

            // Both abilities are waiting for the same controller to choose their placement order.
            // Select Mako by its real source identity; the Rex then asks for its optional target.
            val ordering = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            ordering.playerId shouldBe game.player1Id
            ordering.context.sourceName shouldBe "Triggered abilities"
            ordering.context.phase shouldBe DecisionPhase.CASTING
            ordering.options.size shouldBe 2
            val suspension = game.state.continuationStack.last().shouldBeInstanceOf<Suspension>()
            suspension.question shouldBe ordering
            val group = suspension.answer.shouldBeInstanceOf<TriggerOrderingContinuation>()
            group.chosen shouldBe emptyList()
            group.remaining.size shouldBe 2
            group.remaining.map { it.sourceId }.toSet() shouldBe setOf(mako, rex)
            group.remaining.forEach { it.controllerId shouldBe game.player1Id }
            group.remaining.single { it.sourceId == mako }.triggerContext.discardedCardCount shouldBe 1
            val makoOption = group.remaining.indexOfFirst { it.sourceId == mako }
            (makoOption >= 0) shouldBe true
            game.submitDecision(OptionChosenResponse(ordering.id, makoOption)).error shouldBe null

            val targets = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            targets.playerId shouldBe game.player1Id
            targets.context.sourceId shouldBe rex
            targets.context.sourceName shouldBe "Agonasaur Rex"
            val requirement = targets.targetRequirements.single()
            requirement.minTargets shouldBe 0
            requirement.maxTargets shouldBe 1
            targets.legalTargets.getValue(requirement.index) shouldBe listOf(mako)
            game.submitDecision(TargetsResponse(targets.id, mapOf(requirement.index to emptyList()))).error shouldBe null
            game.state.pendingDecision shouldBe null

            val resolutions = game.resolveStack()
            resolutions.isNotEmpty() shouldBe true
            resolutions.forEach { it.error shouldBe null }
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            game.state.continuationStack shouldBe emptyList()
            game.isInGraveyard(1, "Agonasaur Rex") shouldBe true
            game.handSize(1) shouldBe handBefore

            withClue("One card discarded → one counter") { game.counters(mako) shouldBe 1 }
        }

        test("cycling the Mako itself doesn't trigger it — the ability only works on the battlefield") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Marauding Mako")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .stocked()
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            val cycle = game.cycleCard(1, "Marauding Mako")
            withClue("Cycling should succeed: ${cycle.error}") { cycle.error shouldBe null }
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            withClue("It went to the graveyard and replaced itself with a draw") {
                game.isInGraveyard(1, "Marauding Mako") shouldBe true
                game.handSize(1) shouldBe handBefore
            }
        }
    }

    private fun TestGame.counters(id: EntityId): Int =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    /** Both libraries stocked so nobody decks out on the draws these cases force. */
    private fun ScenarioBuilder.stocked(): ScenarioBuilder = apply {
        repeat(8) {
            withCardInLibrary(1, "Grizzly Bears")
            withCardInLibrary(2, "Grizzly Bears")
        }
    }
}
