package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.otj.cards.HighwayRobbery
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Eight fixed scenarios for Highway Robbery's resolution-time optional payment and Plot. */
class HighwayRobberyScenarioTest : ScenarioTestBase() {
    private val discardLabel = "Discard a card, then draw two cards"
    private val sacrificeLabel = "Sacrifice a land, then draw two cards"
    private val declineLabel = "Decline"

    private fun setup(vararg hand: String) = scenario().withPlayers()
        .withCardInHand(1, HighwayRobbery.name)
        .apply {
            hand.forEach { withCardInHand(1, it) }
            repeat(8) {
                withCardInLibrary(1, "Forest")
                withCardInLibrary(2, "Island")
            }
        }
        .withActivePlayer(1)
        .withRngSeed(0xFEC608)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.mana(
        player: EntityId = player1Id,
        color: Color = Color.RED,
        amount: Int = 2
    ) {
        state = state.updateEntity(player) {
            it.with((it.get<ManaPoolComponent>() ?: ManaPoolComponent()).add(color, amount))
        }
    }

    private fun TestGame.cast(): EntityId {
        mana()
        val source = state.getHand(player1Id).first()
        val offered = getLegalActions(1).filter { (it.action as? CastSpell)?.cardId == source }
        offered.size shouldBe 1
        offered.single().modalEnumeration shouldBe null
        val action = offered.single().action.shouldBeInstanceOf<CastSpell>()
        action.chosenModes shouldBe emptyList()
        val result = execute(action)
        result.error shouldBe null
        result.events.filterIsInstance<CardsDiscardedEvent>() shouldBe emptyList()
        result.events.filterIsInstance<PermanentsSacrificedEvent>() shouldBe emptyList()
        state.pendingDecision shouldBe null
        state.stack shouldBe listOf(source)
        state.getEntity(source)!!.get<SpellOnStackComponent>()!!.chosenModes shouldBe emptyList()
        return source
    }

    private fun TestGame.resolveToChoice(source: EntityId): ChooseOptionDecision {
        resolveStack().forEach { it.error shouldBe null }
        val choice = state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        choice.context.phase shouldBe DecisionPhase.RESOLUTION
        choice.context.sourceId shouldBe source
        choice.playerId shouldBe player1Id
        return choice
    }

    private fun TestGame.choose(choice: ChooseOptionDecision, label: String): ExecutionResult {
        choice.options shouldContain label
        val result = submitDecision(OptionChosenResponse(choice.id, choice.options.indexOf(label)))
        result.error shouldBe null
        return result
    }

    private fun TestGame.reject(answer: DecisionResponse) {
        val before = state
        val rejected = submitDecision(answer)
        rejected.error shouldNotBe null
        rejected.state shouldBe before
        rejected.events shouldBe emptyList()
        state shouldBe before
    }

    private fun TestGame.finished() {
        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        state.gameOver shouldBe false
        isInGraveyard(1, HighwayRobbery.name) shouldBe true
    }

    init {
        // Register the canonical definition explicitly after the shared test-card corpus.
        cardRegistry.register(HighwayRobbery)

        test("discard choice at resolution discards the selected card then draws two") {
            val game = setup("Grizzly Bears", "Lightning Bolt")
                .withCardOnBattlefield(1, "Mountain")
                .build()
            val fodder = game.state.getHand(game.player1Id)[1]
            val kept = game.state.getHand(game.player1Id)[2]
            val handBefore = game.handSize(1)
            val source = game.cast()
            game.state.getHand(game.player1Id) shouldContain fodder

            // Casting has completed. The opponent can respond before any payment choice.
            game.passPriority().error shouldBe null
            game.state.priorityPlayerId shouldBe game.player2Id
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe listOf(source)
            val choice = game.resolveToChoice(source)
            choice.options shouldBe listOf(discardLabel, sacrificeLabel, declineLabel)
            game.choose(choice, discardLabel)
            val select = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            select.context.phase shouldBe DecisionPhase.RESOLUTION
            select.playerId shouldBe game.player1Id
            select.options.toSet() shouldBe setOf(fodder, kept)
            select.minSelections shouldBe 1
            select.maxSelections shouldBe 1
            game.reject(CardsSelectedResponse(select.id, emptyList()))

            val result = game.submitDecision(CardsSelectedResponse(select.id, listOf(fodder)))
            result.error shouldBe null
            game.finished()
            game.state.getGraveyard(game.player1Id) shouldContain fodder
            game.state.getHand(game.player1Id) shouldContain kept
            game.handSize(1) shouldBe handBefore
            result.events.filterIsInstance<CardsDiscardedEvent>().flatMap { it.cardIds } shouldBe listOf(fodder)
            result.events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
        }

        test("sacrifice choice at resolution pays with exactly the selected controlled land") {
            val game = setup("Lightning Bolt")
                .withCardOnBattlefield(1, "Mountain")
                .withCardOnBattlefield(1, "Island")
                .withCardOnBattlefield(2, "Swamp")
                .build()
            val chosenLand = game.findPermanent("Island")!!
            val keptLand = game.findPermanent("Mountain")!!
            val enemyLand = game.findPermanent("Swamp")!!
            val handBefore = game.handSize(1)
            val source = game.cast()
            val choice = game.resolveToChoice(source)
            game.choose(choice, sacrificeLabel)
            val select = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            select.context.phase shouldBe DecisionPhase.RESOLUTION
            select.playerId shouldBe game.player1Id
            select.options.toSet() shouldBe setOf(chosenLand, keptLand)
            select.minSelections shouldBe 1
            select.maxSelections shouldBe 1
            game.reject(CardsSelectedResponse(select.id, emptyList()))
            game.reject(CardsSelectedResponse(select.id, listOf(enemyLand)))

            val result = game.submitDecision(CardsSelectedResponse(select.id, listOf(chosenLand)))
            result.error shouldBe null
            game.finished()
            game.state.getGraveyard(game.player1Id) shouldContain chosenLand
            game.state.getBattlefield(game.player1Id) shouldContain keptLand
            game.state.getBattlefield(game.player2Id) shouldContain enemyLand
            game.handSize(1) shouldBe handBefore + 1
            result.events.filterIsInstance<ZoneChangeEvent>().filter { it.wasSacrificed }
                .map { it.entityId } shouldBe listOf(chosenLand)
            result.events.filterIsInstance<PermanentsSacrificedEvent>().flatMap { it.permanentIds } shouldBe
                listOf(chosenLand)
            result.events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
        }

        test("Decline completes the resolution choice once without discard sacrifice or draw") {
            val game = setup("Lightning Bolt").withCardOnBattlefield(1, "Mountain").build()
            val bolt = game.state.getHand(game.player1Id)[1]
            val land = game.findPermanent("Mountain")!!
            val source = game.cast()
            val choice = game.resolveToChoice(source)
            val result = game.choose(choice, declineLabel)

            result.events.filterIsInstance<DecisionSubmittedEvent>().single().description shouldBe
                "(Highway Robbery) Chose Decline"
            game.finished()
            game.state.getHand(game.player1Id) shouldContain bolt
            game.state.getBattlefield(game.player1Id) shouldContain land
            result.events.filterIsInstance<CardsDiscardedEvent>() shouldBe emptyList()
            result.events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
            result.events.filterIsInstance<PermanentsSacrificedEvent>() shouldBe emptyList()
            result.events.filterIsInstance<ZoneChangeEvent>().count { it.wasSacrificed } shouldBe 0
        }

        test("empty hand omits discard but still permits sacrificing an actual land") {
            val game = setup().withCardOnBattlefield(1, "Mountain").build()
            val land = game.findPermanent("Mountain")!!
            val source = game.cast()
            game.handSize(1) shouldBe 0
            val choice = game.resolveToChoice(source)
            choice.options shouldBe listOf(sacrificeLabel, declineLabel)
            val result = game.choose(choice, sacrificeLabel)

            // Exactly one eligible land needs no separate selection prompt.
            game.finished()
            game.state.getGraveyard(game.player1Id) shouldContain land
            game.handSize(1) shouldBe 2
            result.events.filterIsInstance<PermanentsSacrificedEvent>().flatMap { it.permanentIds } shouldBe
                listOf(land)
            result.events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
        }

        test("empty hand and no controlled land cannot manufacture a payment or draw") {
            val game = setup().withCardOnBattlefield(2, "Mountain").build()
            val enemyLand = game.findPermanent("Mountain")!!
            game.cast()
            val results = game.resolveStack()
            results.forEach { it.error shouldBe null }
            val events = results.flatMap { it.events }

            game.finished()
            game.handSize(1) shouldBe 0
            game.state.getBattlefield(game.player2Id) shouldContain enemyLand
            events.filterIsInstance<CardsDiscardedEvent>() shouldBe emptyList()
            events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
            events.filterIsInstance<PermanentsSacrificedEvent>() shouldBe emptyList()
            events.filterIsInstance<ZoneChangeEvent>().count { it.wasSacrificed } shouldBe 0
        }

        test("opponent can counter before the resolution payment choice") {
            val game = setup("Grizzly Bears")
                .withCardOnBattlefield(1, "Mountain")
                .withCardInHand(2, "Counterspell")
                .build()
            val land = game.findPermanent("Mountain")!!
            val source = game.cast()
            game.passPriority().error shouldBe null
            game.state.priorityPlayerId shouldBe game.player2Id
            game.state.pendingDecision shouldBe null
            game.mana(game.player2Id, Color.BLUE)
            val counter = game.state.getHand(game.player2Id).single()
            val response = game.execute(CastSpell(game.player2Id, counter, listOf(ChosenTarget.Spell(source))))
            response.error shouldBe null
            val results = game.resolveStack()
            results.forEach { it.error shouldBe null }
            val events = response.events + results.flatMap { it.events }

            game.finished()
            game.handSize(1) shouldBe 1
            game.state.getBattlefield(game.player1Id) shouldContain land
            events.filterIsInstance<CardsDiscardedEvent>() shouldBe emptyList()
            events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
            events.filterIsInstance<PermanentsSacrificedEvent>() shouldBe emptyList()
        }

        test("madness discard to exile still draws two before its real madness continuation") {
            val game = setup("Fiery Temper", "Grizzly Bears")
                .withCardOnBattlefield(1, "Mountain")
                .build()
            val temper = game.state.getHand(game.player1Id)[1]
            val source = game.cast()
            game.choose(game.resolveToChoice(source), discardLabel)
            val select = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val paid = game.submitDecision(CardsSelectedResponse(select.id, listOf(temper)))
            paid.error shouldBe null
            game.state.getExile(game.player1Id) shouldContain temper
            game.isInGraveyard(1, HighwayRobbery.name) shouldBe true
            game.handSize(1) shouldBe 3
            paid.events.filterIsInstance<CardsDiscardedEvent>().flatMap { it.cardIds } shouldBe listOf(temper)
            paid.events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
            game.state.pendingDecision shouldBe null
            game.state.stack.isNotEmpty() shouldBe true

            game.resolveStack().forEach { it.error shouldBe null }
            val madness = game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            madness.playerId shouldBe game.player1Id
            game.handSize(1) shouldBe 3
            val declined = game.submitDecision(YesNoResponse(madness.id, false))
            declined.error shouldBe null
            game.finished()
            game.state.getGraveyard(game.player1Id) shouldContain temper
        }

        test("actual Plot waits until a later turn then makes its free cast choice at resolution") {
            val game = setup()
                .withCardOnBattlefield(1, "Mountain")
                .withCardOnBattlefield(1, "Island")
                .build()
            val source = game.state.getHand(game.player1Id).single()
            game.mana()
            val plotted = game.execute(PlotCard(game.player1Id, source))
            plotted.error shouldBe null
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            game.state.getExile(game.player1Id) shouldContain source
            game.getLegalActions(1).any { (it.action as? CastSpell)?.cardId == source } shouldBe false
            val beforeEarlyCast = game.state
            val tooEarly = game.execute(CastSpell(game.player1Id, source, useWithoutPayingManaCost = true))
            tooEarly.error shouldNotBe null
            tooEarly.state shouldBe beforeEarlyCast
            tooEarly.events shouldBe emptyList()
            game.state shouldBe beforeEarlyCast

            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            game.state.activePlayerId shouldBe game.player2Id
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            game.state.activePlayerId shouldBe game.player1Id
            val offered = game.getLegalActions(1).single { (it.action as? CastSpell)?.cardId == source }
            offered.modalEnumeration shouldBe null
            offered.isAffordable shouldBe true
            offered.manaCostString shouldBe "{0}"
            val freeCast = offered.action.shouldBeInstanceOf<CastSpell>()
            freeCast.chosenModes shouldBe emptyList()
            val manaBefore = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()
            val tapsBefore = game.state.getBattlefield(game.player1Id).associateWith {
                game.state.getEntity(it)!!.has<TappedComponent>()
            }
            // Plot's persistent permission supplies the free cost. Execute the offered template
            // unchanged and verify payment, rather than require a different free-cast action flag.
            val castResult = game.execute(freeCast)
            castResult.error shouldBe null
            castResult.events.filterIsInstance<SpellCastEvent>().single {
                it.spellEntityId == source
            }.totalManaSpent shouldBe 0
            castResult.events.filterIsInstance<TappedEvent>() shouldBe emptyList()
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>() shouldBe manaBefore
            game.state.getBattlefield(game.player1Id).associateWith {
                game.state.getEntity(it)!!.has<TappedComponent>()
            } shouldBe tapsBefore
            game.state.pendingDecision shouldBe null
            game.choose(game.resolveToChoice(source), sacrificeLabel)
            val select = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val land = game.findPermanent("Island")!!
            select.options shouldContain land
            val paid = game.submitDecision(CardsSelectedResponse(select.id, listOf(land)))
            paid.error shouldBe null
            paid.events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
            game.state.getGraveyard(game.player1Id) shouldContain land
            game.finished()
        }
    }
}
