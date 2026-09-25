package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Fixed semantic setups, not R1 allocations. Minted fixture IDs are mapped before collection;
 * the extraction replay below replays one accepted transcript, not a new game initialization.
 */
class IndustrialWasteV2EventMetricsTest : FunSpec({
    fun driver(life: Int = 20) = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
        initMirrorMatch(Deck.of("Forest" to 40), startingLife = life, seed = 9_250_925_005L)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
        replaceState(state.copy(zones = state.zones.mapValues { (key, cards) -> if (key.zoneType == Zone.HAND) emptyList() else cards }))
    }
    fun loop(life: Int = 20): MetricsFixture {
        val game = driver(life)
        val player = game.player1
        game.putPermanentOnBattlefield(player, "Ashnod's Altar")
        game.putPermanentOnBattlefield(player, "Pactdoll Terror")
        game.putPermanentOnBattlefield(player, "Myr Retriever")
        game.putCardInGraveyard(player, "Myr Retriever")
        return MetricsFixture(game)
    }

    test("inventory is not a loop but a real neutral sacrifice return cast and entry is") {
        val fixture = loop()
        fixture.collector.snapshot().demonstratedLoopReadyTurn shouldBe null
        fixture.collector.snapshot().demonstratedLoopCycles shouldBe emptyList()
        fixture.submit(fixture.choose().shouldBeInstanceOf<ActivateAbility>())
        fixture.resolve()
        fixture.collector.snapshot().demonstratedLoopCycles shouldBe emptyList()
        fixture.submit(fixture.choose().shouldBeInstanceOf<CastSpell>())
        fixture.resolve()
        val metric = fixture.collector.snapshot()
        metric.demonstratedLoopReadyTurn shouldBe 1
        metric.artifactReturnsFollowedByRecast shouldBe 1
        val cycle = metric.demonstratedLoopCycles.single()
        cycle.sacrificedRetriever shouldNotBe cycle.returnedRetriever
        cycle.manaBefore shouldBe List(6) { 0 }
        cycle.manaAfter shouldBe cycle.manaBefore
        (cycle.sacrificeTransition < cycle.returnTransition && cycle.returnTransition < cycle.castTransition && cycle.castTransition < cycle.entryTransition) shouldBe true
        metric.actualLethalTurn shouldBe null
    }

    test("actual Pactdoll lethal is counted only after the engine ends the game") {
        val fixture = loop(life = 2)
        fixture.collector.snapshot().actualLethalTurn shouldBe null
        var actions = 0
        while (!fixture.driver.state.gameOver) {
            check(actions++ < 12)
            fixture.submit(fixture.choose())
            fixture.resolve()
        }
        fixture.driver.state.winnerId shouldBe fixture.player
        fixture.collector.snapshot().actualLethalTurn shouldBe 1
        fixture.collector.snapshot().demonstratedLoopCycles.size shouldBe 2
        fixture.collector.snapshot().artifactReturnsFollowedByRecast shouldBe 2
    }

    test("Blood Fountain return and recast is recursion without inventing an Altar loop") {
        val game = driver()
        repeat(6) { game.putPermanentOnBattlefield(game.player1, "Swamp") }
        game.putPermanentOnBattlefield(game.player1, "Blood Fountain")
        repeat(2) { game.putCardInGraveyard(game.player1, "Myr Retriever") }
        val fixture = MetricsFixture(game)
        fixture.submit(fixture.choose().shouldBeInstanceOf<ActivateAbility>())
        fixture.resolve()
        fixture.collector.snapshot().artifactReturnsFollowedByRecast shouldBe 0
        fixture.submit(fixture.choose().shouldBeInstanceOf<CastSpell>())
        fixture.resolve()
        fixture.collector.snapshot().artifactReturnsFollowedByRecast shouldBe 1
        fixture.collector.snapshot().demonstratedLoopReadyTurn shouldBe null
    }

    test("a development action spending loop resources abandons the partial cycle proof") {
        val game = driver()
        val player = game.player1
        game.putPermanentOnBattlefield(player, "Forest")
        game.putPermanentOnBattlefield(player, "Ashnod's Altar")
        game.putPermanentOnBattlefield(player, "Myr Retriever")
        game.putCardInGraveyard(player, "Myr Retriever")
        val candy = game.putCardInHand(player, "Candy Trail")
        val fixture = MetricsFixture(game)
        val altar = game.state.getBattlefield().single { game.state.getEntity(it)!!.get<CardComponent>()!!.name == "Ashnod's Altar" }
        val retriever = game.state.getBattlefield().single { game.state.getEntity(it)!!.get<CardComponent>()!!.name == "Myr Retriever" }
        val ability = game.cardRegistry.requireCard("Ashnod's Altar").activatedAbilities.single()
        fixture.submit(ActivateAbility(player, altar, ability.id, costPayment = com.wingedsheep.sdk.scripting.AdditionalCostPayment(sacrificedPermanents = listOf(retriever))))
        fixture.resolve()
        fixture.submit(CastSpell(player, candy))
        fixture.resolve()
        fixture.submit(CastSpell(player, game.findCardInHand(player, "Myr Retriever")!!))
        fixture.resolve()
        fixture.collector.snapshot().artifactReturnsFollowedByRecast shouldBe 1
        fixture.collector.snapshot().demonstratedLoopCycles shouldBe emptyList()
    }

    test("Scry and Rumble count only observed copies and distinguish seeing from land access") {
        val game = driver()
        val player = game.player1
        repeat(3) { game.putPermanentOnBattlefield(player, "Forest") }
        val candy = game.putCardInHand(player, "Candy Trail")
        val rumble = game.putCardInHand(player, "Malevolent Rumble")
        game.putCardOnTopOfLibrary(player, "Forest")
        val tower = game.putCardOnTopOfLibrary(player, "Urza's Tower")
        val plant = game.putCardOnTopOfLibrary(player, "Urza's Power Plant")
        val mine = game.putCardOnTopOfLibrary(player, "Urza's Mine")
        val fixture = MetricsFixture(game)
        val initiallySeen = fixture.collector.snapshot().firstSeenTurnByOriginalCopy.keys
        fixture.submit(CastSpell(player, candy))
        fixture.resolve { decision ->
            if (decision is SelectCardsDecision && mine in decision.options) CardsSelectedResponse(decision.id, listOf(mine)) else null
        }
        fixture.collector.snapshot().firstSeenTurnByOriginalCopy.keys shouldBe initiallySeen + fixture.copies.getValue(mine) + fixture.copies.getValue(plant)
        fixture.collector.snapshot().firstAccessTurnByTronName shouldBe emptyMap()
        val rumbleCopies = game.state.getLibrary(player).take(4).map(fixture.copies::getValue).toSet()
        fixture.submit(CastSpell(player, rumble))
        fixture.resolve { decision ->
            if (decision is SelectCardsDecision && tower in decision.options) CardsSelectedResponse(decision.id, listOf(tower)) else null
        }
        val metric = fixture.collector.snapshot()
        metric.firstSeenTurnByOriginalCopy.keys shouldBe initiallySeen + fixture.copies.getValue(mine) + rumbleCopies
        metric.firstAccessTurnByTronName shouldBe mapOf("Urza's Tower" to 1)
        game.state.getGraveyard(player).contains(plant) shouldBe true
        metric.threeTronPieceAccessTurn shouldBe null
        metric.fullTronTurn shouldBe null
    }

    test("all three controlled real Tron lands establish access and full Tron") {
        val game = driver()
        listOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower").forEach { game.putPermanentOnBattlefield(game.player1, it) }
        val metric = MetricsFixture(game).collector.snapshot()
        metric.threeTronPieceAccessTurn shouldBe 1
        metric.fullTronTurn shouldBe 1
    }

    test("duplicated missing or rejected transitions cannot enter the metric stream") {
        val fixture = loop()
        fixture.submit(fixture.choose())
        val accepted = fixture.transcript.single()
        val before = fixture.collector.snapshot()
        shouldThrow<IllegalArgumentException> { fixture.collector.record(accepted.before, accepted.action, accepted.result) }
        shouldThrow<IllegalArgumentException> {
            fixture.collector.record(fixture.driver.state, PassPriority(fixture.player), ExecutionResult.error(fixture.driver.state, "Synthetic rejected action"))
        }
        fixture.collector.snapshot() shouldBe before
    }

    test("replaying the same complete accepted transcript reproduces metric evidence") {
        val fixture = loop()
        repeat(2) {
            fixture.submit(fixture.choose()); fixture.resolve()
            fixture.submit(fixture.choose()); fixture.resolve()
        }
        val replay = IndustrialWasteV2EventCollector(fixture.initial, fixture.player, fixture.copies)
        fixture.transcript.forEach { replay.record(it.before, it.action, it.result) }
        replay.snapshot() shouldBe fixture.collector.snapshot()
        replay.snapshot().demonstratedLoopCycles.size shouldBe 2
    }
})

private data class MetricsTransition(val before: GameState, val action: GameAction, val result: ExecutionResult)

private class MetricsFixture(val driver: GameTestDriver) {
    val player = driver.player1
    val initial = driver.state
    val copies: Map<EntityId, String> = initial.entities.entries
        .filter { (_, entity) -> entity.get<CardComponent>()?.ownerId == player && !entity.has<TokenComponent>() }
        .groupBy { it.value.get<CardComponent>()!!.name }
        .flatMap { (name, entries) -> entries.sortedBy { it.key.toString() }.mapIndexed { index, entry -> entry.key to "$name#${index + 1}" } }.toMap()
    val collector = IndustrialWasteV2EventCollector(initial, player, copies)
    val transcript = mutableListOf<MetricsTransition>()
    private val simulator = GameSimulator(driver.cardRegistry)
    private val responder = DecisionResponder(simulator, AIPlayer.defaultEvaluator(), CardAdvisorRegistry().also {
        IndustrialWasteV2PilotAdvisorModule.register(it)
    })
    fun choose() = IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, simulator.getLegalActions(driver.state, player))
    fun submit(action: GameAction) {
        val before = driver.state
        val result = driver.submit(action)
        result.error shouldBe null
        collector.record(before, action, result)
        transcript += MetricsTransition(before, action, result)
    }
    fun resolve(decisionOverride: (PendingDecision) -> DecisionResponse? = { null }) {
        var steps = 0
        while (!driver.state.gameOver && (driver.state.stack.isNotEmpty() || driver.pendingDecision != null)) {
            check(steps++ < 60)
            val decision = driver.pendingDecision
            if (decision != null) submit(SubmitDecision(decision.playerId, decisionOverride(decision) ?: responder.respond(driver.state, decision, decision.playerId)))
            else submit(PassPriority(driver.state.priorityPlayerId!!))
        }
    }
}
