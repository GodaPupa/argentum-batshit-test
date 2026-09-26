package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.GameRng
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Exact-card Stage-E readiness fixtures. No experimental allocation or outcome is created. */
class SphinxsApproachScenarioTest : ScenarioTestBase() {
    private fun position(
        copies: Int,
        library: List<String> = listOf("Island", "Island", "Goliath Sphinx", "Island"),
        activePlayer: Int = 1,
        endStep: Boolean = false,
    ): TestGame {
        val setup = scenario().withPlayers("Approach", "Opponent")
            .withCardInHand(1, "Sphinx's Approach")
            .withLandsOnBattlefield(1, "Island", 3)
            .withCardInLibrary(2, "Island")
            .withActivePlayer(activePlayer)
            .inPhase(if (endStep) Phase.ENDING else Phase.PRECOMBAT_MAIN,
                if (endStep) Step.END else Step.PRECOMBAT_MAIN)
        repeat(copies) { setup.withCardInGraveyard(1, "Sphinx's Approach") }
        library.forEach { setup.withCardInLibrary(1, it) }
        return setup.build().also { it.state = it.state.copy(rng = GameRng.seeded(0x535048L)) }
    }

    private fun resolveToMay(game: TestGame) {
        game.castSpell(1, "Sphinx's Approach").error shouldBe null
        game.resolveStack().forEach { it.error shouldBe null }
    }

    private fun findSphinx(game: TestGame) = (game.state.pendingDecision as SelectCardsDecision)
        .options.single { game.state.getEntity(it)?.get<CardComponent>()?.name == "Goliath Sphinx" }

    init {
        test("three graveyard copies cannot count the resolving spell as the fourth") {
            val game = position(3)
            resolveToMay(game)
            game.handSize(1) shouldBe 2
            game.hasPendingDecision() shouldBe false
            game.findCardsInGraveyard(1, "Sphinx's Approach").size shouldBe 4
            game.state.getExile(game.player1Id) shouldBe emptyList()
            game.isOnBattlefield("Goliath Sphinx") shouldBe false
        }

        test("mandatory draw precedes optional action with no intervening priority") {
            val game = position(4)
            game.castSpell(1, "Sphinx's Approach").error shouldBe null
            val events = game.resolveStack().flatMap { it.events }
            events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
            game.handSize(1) shouldBe 2
            game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            game.findCardsInGraveyard(1, "Sphinx's Approach").size shouldBe 4
            game.state.getExile(game.player1Id) shouldBe emptyList()
            game.execute(PassPriority(game.player1Id)).isSuccess shouldBe false
        }

        test("decline leaves all four graveyard copies and normal resolving-spell cleanup") {
            val game = position(4)
            resolveToMay(game)
            game.answerYesNo(false).error shouldBe null
            game.findCardsInGraveyard(1, "Sphinx's Approach").size shouldBe 5
            game.state.getExile(game.player1Id) shouldBe emptyList()
            game.isOnBattlefield("Goliath Sphinx") shouldBe false
            game.hasPendingDecision() shouldBe false
        }

        test("successful exact-four action exiles five cards and searches onto battlefield without casting") {
            val game = position(4)
            val before = game.state.zones.values.flatten().toSet()
            resolveToMay(game)
            val paid = game.answerYesNo(true)
            paid.error shouldBe null
            paid.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.EXILE } shouldBe 5
            game.state.getExile(game.player1Id).size shouldBe 5
            val found = game.selectCards(listOf(findSphinx(game)))
            found.error shouldBe null
            found.events.filterIsInstance<SpellCastEvent>() shouldBe emptyList()
            found.events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 1
            game.isOnBattlefield("Goliath Sphinx") shouldBe true
            game.findCardsInGraveyard(1, "Sphinx's Approach") shouldBe emptyList()
            game.state.stack shouldBe emptyList()
            val after = game.state.zones.values.flatten()
            after.toSet() shouldBe before
            after.size shouldBe after.toSet().size
            val sphinx = game.findPermanent("Goliath Sphinx")!!
            game.state.getEntity(sphinx)!!.has<SummoningSicknessComponent>() shouldBe true
        }

        test("five graveyard copies require exactly four and preserve the unselected copy") {
            val game = position(5)
            val copies = game.findCardsInGraveyard(1, "Sphinx's Approach")
            resolveToMay(game)
            game.answerYesNo(true).error shouldBe null
            val decision = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            decision.minSelections shouldBe 4
            decision.maxSelections shouldBe 4
            decision.options shouldContainExactlyInAnyOrder copies
            game.state.getExile(game.player1Id) shouldBe emptyList()
            game.selectCards(copies.take(4)).error shouldBe null
            game.selectCards(listOf(findSphinx(game))).error shouldBe null
            game.findCardsInGraveyard(1, "Sphinx's Approach") shouldBe copies.drop(4)
            game.state.getExile(game.player1Id).size shouldBe 5
        }

        test("serialized exact-card continuation replays the same selection and search") {
            val game = position(5)
            val copies = game.findCardsInGraveyard(1, "Sphinx's Approach").take(4)
            resolveToMay(game)
            game.answerYesNo(true).error shouldBe null
            val json = Json {
                serializersModule = engineSerializersModule
                allowStructuredMapKeys = true
                encodeDefaults = true
            }
            val paused = json.encodeToString(GameState.serializer(), game.state)
            game.selectCards(copies).error shouldBe null
            game.selectCards(listOf(findSphinx(game))).error shouldBe null
            val expected = json.encodeToString(GameState.serializer(), game.state)
            game.state = json.decodeFromString(GameState.serializer(), paused)
            game.selectCards(copies).error shouldBe null
            game.selectCards(listOf(findSphinx(game))).error shouldBe null
            json.encodeToString(GameState.serializer(), game.state) shouldBe expected
        }

        test("Terror reduction immediately reflects the four spells exiled by Approach") {
            val setup = scenario().withPlayers("Approach", "Opponent")
                .withCardInHand(1, "Sphinx's Approach").withCardInHand(1, "Tolarian Terror")
                .withLandsOnBattlefield(1, "Island", 4)
                .withCardInGraveyard(1, "Mental Note").withCardInGraveyard(1, "Thought Scour")
                .withCardInLibrary(1, "Island").withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Goliath Sphinx").withCardInLibrary(1, "Island")
            repeat(4) { setup.withCardInGraveyard(1, "Sphinx's Approach") }
            val game = setup.build()
            val before = game.state
            game.castSpell(1, "Tolarian Terror").error shouldBe null
            game.state = before
            resolveToMay(game)
            game.answerYesNo(true).error shouldBe null
            game.selectCards(listOf(findSphinx(game))).error shouldBe null
            // Only two instant/sorcery cards now remain; the lone untapped Island cannot pay {4}{U}.
            game.castSpell(1, "Tolarian Terror").isSuccess shouldBe false
        }

        test("search may fail to find an existing Sphinx after the exile action") {
            val game = position(4)
            resolveToMay(game)
            game.answerYesNo(true).error shouldBe null
            val skipped = game.selectCards(emptyList())
            skipped.error shouldBe null
            skipped.events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 1
            game.isOnBattlefield("Goliath Sphinx") shouldBe false
            game.state.getExile(game.player1Id).size shouldBe 5
        }

        test("drawing the last Sphinx does not undo a legal exile action or suppress shuffle") {
            val game = position(4, listOf("Goliath Sphinx", "Island", "Island", "Island"))
            resolveToMay(game)
            game.isInHand(1, "Goliath Sphinx") shouldBe true
            val paid = game.answerYesNo(true)
            paid.error shouldBe null
            game.hasPendingDecision() shouldBe false
            paid.events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 1
            game.state.getExile(game.player1Id).size shouldBe 5
            game.isOnBattlefield("Goliath Sphinx") shouldBe false
        }

        test("no Sphinx in library still permits exile and successful empty search") {
            val game = position(4, List(4) { "Island" })
            resolveToMay(game)
            val paid = game.answerYesNo(true)
            paid.error shouldBe null
            paid.events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 1
            game.state.getExile(game.player1Id).size shouldBe 5
            game.hasPendingDecision() shouldBe false
        }

        test("countered Approach draws nothing and never starts the optional action") {
            val setup = scenario().withPlayers("Approach", "Opponent")
                .withCardInHand(1, "Sphinx's Approach").withCardInHand(2, "Counterspell")
                .withLandsOnBattlefield(1, "Island", 3).withLandsOnBattlefield(2, "Island", 2)
                .withCardInLibrary(1, "Island").withCardInLibrary(1, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(4) { setup.withCardInGraveyard(1, "Sphinx's Approach") }
            val game = setup.build()
            game.castSpell(1, "Sphinx's Approach").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Sphinx's Approach").error shouldBe null
            val events = game.resolveStack().flatMap { it.events }
            events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
            game.handSize(1) shouldBe 0
            game.findCardsInGraveyard(1, "Sphinx's Approach").size shouldBe 5
            game.state.getExile(game.player1Id) shouldBe emptyList()
            game.hasPendingDecision() shouldBe false
        }

        test("graveyard removal before resolution makes the optional action unavailable") {
            val game = position(4)
            game.castSpell(1, "Sphinx's Approach").error shouldBe null
            val removed = game.findCardsInGraveyard(1, "Sphinx's Approach").first()
            game.state = ZoneTransitionService.moveToZone(game.state, removed, Zone.EXILE).state
            game.resolveStack().forEach { it.error shouldBe null }
            game.handSize(1) shouldBe 2
            game.hasPendingDecision() shouldBe false
            game.state.getExile(game.player1Id) shouldBe listOf(removed)
            game.findCardsInGraveyard(1, "Sphinx's Approach").size shouldBe 4
        }

        test("only the controller's named graveyard cards qualify") {
            val setup = scenario().withPlayers("Approach", "Opponent")
                .withCardInHand(1, "Sphinx's Approach")
                .withCardInGraveyard(1, "Mental Note")
                .withCardInGraveyard(2, "Sphinx's Approach")
                .withCardInLibrary(1, "Island").withCardInLibrary(1, "Island")
                .withLandsOnBattlefield(1, "Island", 3)
            repeat(3) { setup.withCardInGraveyard(1, "Sphinx's Approach") }
            val scoped = setup.build()
            resolveToMay(scoped)
            scoped.hasPendingDecision() shouldBe false
            scoped.findCardsInGraveyard(2, "Sphinx's Approach").size shouldBe 1
            scoped.findCardsInGraveyard(1, "Mental Note").size shouldBe 1
            scoped.state.getExile(scoped.player1Id) shouldBe emptyList()
        }

        test("opponent end-step deployment removes sickness on the controller's next turn") {
            val game = position(4, activePlayer = 2, endStep = true)
            game.passPriority().error shouldBe null
            resolveToMay(game)
            game.answerYesNo(true).error shouldBe null
            game.selectCards(listOf(findSphinx(game))).error shouldBe null
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            game.state.activePlayerId shouldBe game.player1Id
            val sphinx = game.findPermanent("Goliath Sphinx")!!
            game.state.getEntity(sphinx)!!.has<SummoningSicknessComponent>() shouldBe false
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Goliath Sphinx" to 2)).error shouldBe null
        }
    }
}
