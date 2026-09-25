package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.LibrarySearchedEvent
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.ShuffleCause
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.LibraryOrderingComponent
import com.wingedsheep.engine.state.components.player.LibraryOrderingPlan
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Real-card continuations on an excluded synthetic input, never the frozen R1 corpus. */
class IndustrialWasteV2OrderingIntegrationTest : FunSpec({
    val names = listOf("Expedition Map", "Crop Rotation", "Myr Kinsmith", "Myr Retriever", "Urza's Tower")
    val deck = Deck(cards = List(21) { "Forest" } + names)
    val labels = (1..21).map { "Forest#$it" } + names.map { "$it#1" }
    val opening = labels.take(4) + names.take(3).map { "$it#1" } + labels.drop(4).filterNot { it in names.take(3).map { name -> "$name#1" } }
    val plan = LibraryOrderingPlan("IW_V2_ORDERING_SEARCH_REGRESSION_ONLY", 999, List(4) { opening })
    // Independent Python hashlib reference for effect shuffle ordinal 1, before filtering absent copies.
    val rank = listOf(
        "Forest#16", "Myr Kinsmith#1", "Forest#7", "Forest#12", "Forest#18", "Urza's Tower#1",
        "Forest#4", "Forest#9", "Forest#3", "Forest#20", "Expedition Map#1", "Forest#5", "Forest#6",
        "Forest#11", "Forest#13", "Myr Retriever#1", "Forest#15", "Forest#21", "Forest#8", "Forest#14",
        "Crop Rotation#1", "Forest#10", "Forest#1", "Forest#2", "Forest#19", "Forest#17",
    )
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    fun fixture(): GameTestDriver = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
        initGame(deck, Deck.of("Forest" to 60), seed = 9_250_925_004L, libraryOrdering1 = plan)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun runSearch(sourceName: String): Pair<String, List<GameEvent>> {
        val driver = fixture()
        val player = driver.player1
        val advisors = CardAdvisorRegistry().also {
            IndustrialWasteV2PilotAdvisorModule.register(it)
        }
        val responder = DecisionResponder(GameSimulator(driver.cardRegistry), AIPlayer.defaultEvaluator(), advisors)
        // Four real land plays on four real own turns; no minted cards or replacement state.
        repeat(4) { index ->
            driver.activePlayer shouldBe player
            driver.playLand(player, driver.findCardInHand(player, "Forest")!!).error shouldBe null
            if (index < 3) {
                driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
                driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
                driver.activePlayer shouldBe driver.player2
                driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
                driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            }
        }
        var searchDecisions = 0
        fun resolve() {
            var steps = 0
            while (driver.state.stack.isNotEmpty() || driver.pendingDecision != null) {
                check(steps++ < 40)
                val decision = driver.pendingDecision
                if (decision != null) {
                    if (decision is SelectCardsDecision && decision.context.sourceName == sourceName) searchDecisions++
                    driver.submitDecision(decision.playerId, responder.respond(driver.state, decision, decision.playerId)).error shouldBe null
                } else driver.bothPass().error shouldBe null
            }
        }
        val source = driver.findCardInHand(player, sourceName)!!
        if (sourceName == "Crop Rotation") {
            val forest = driver.state.projectedState.getBattlefieldControlledBy(player).first {
                driver.state.getEntity(it)!!.get<CardComponent>()!!.name == "Forest"
            }
            driver.submit(CastSpell(player, source, additionalCostPayment = AdditionalCostPayment(
                sacrificedPermanents = listOf(forest),
            ))).error shouldBe null
        } else driver.castSpell(player, source).error shouldBe null
        resolve()
        if (sourceName == "Expedition Map") {
            val ability = driver.cardRegistry.requireCard(sourceName).activatedAbilities.single()
            driver.submit(ActivateAbility(player, source, ability.id)).error shouldBe null
            resolve()
        }
        searchDecisions shouldBe 1
        val ordered = driver.state.getEntity(player)!!.get<LibraryOrderingComponent>()!!
        ordered.effectShufflesUsed shouldBe 1
        ordered.mulligansUsed shouldBe 0
        val actual = driver.state.getLibrary(player).map { ordered.originalCopies.getValue(it) }
        actual shouldBe rank.filter { it in actual.toSet() }
        driver.events.filterIsInstance<LibraryShuffledEvent>().count {
            it.playerId == player && it.cause == ShuffleCause.SPELL_OR_ABILITY
        } shouldBe 1
        driver.events.filterIsInstance<LibrarySearchedEvent>().count { it.playerId == player } shouldBe 1
        val expectedName = if (sourceName == "Myr Kinsmith") "Myr Retriever" else "Urza's Tower"
        val expectedZone = if (sourceName == "Crop Rotation") Zone.BATTLEFIELD else Zone.HAND
        val found = ordered.originalCopies.entries.single { it.value == "$expectedName#1" }.key
        if (expectedZone == Zone.BATTLEFIELD) driver.state.getBattlefield().contains(found) shouldBe true
        else driver.state.getZone(player, expectedZone).contains(found) shouldBe true
        return json.encodeToString(GameState.serializer(), driver.state) to driver.events
    }

    names.take(3).forEach { source ->
        test("$source uses the frozen shuffle through its real continuation with deterministic replay") {
            val first = runSearch(source)
            val replay = runSearch(source)
            replay.first shouldBe first.first
            replay.second shouldBe first.second
        }
    }

    test("the public action policy cannot observe a changed future ordering plan or library order") {
        val driver = fixture()
        val player = driver.player1
        val simulator = GameSimulator(driver.cardRegistry)
        val original = driver.state
        val component = original.getEntity(player)!!.get<LibraryOrderingComponent>()!!
        val changed = original.updateEntity(player) {
            it.with(component.copy(plan = component.plan.copy(row = 1000, openingOrders = component.plan.openingOrders.map { order -> order.reversed() })))
        }.reorderZone(ZoneKey(player, Zone.LIBRARY), original.getLibrary(player).reversed())
        IndustrialWasteV2PublicActionPolicy.choose(original, player, simulator.getLegalActions(original, player)) shouldBe
            IndustrialWasteV2PublicActionPolicy.choose(changed, player, simulator.getLegalActions(changed, player))
    }
})
