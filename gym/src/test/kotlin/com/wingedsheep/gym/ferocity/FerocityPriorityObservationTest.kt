package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.BlockTax
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Four prospective projection fixtures. The declaration actor is not a priority holder. */
class FerocityPriorityObservationTest : ScenarioTestBase() {
    private val builder = ObservationBuilder(cardRegistry)
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))
    private val tax = card("Priority Observation Block Tax") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility { ability = BlockTax(DynamicAmount.Fixed(1)) }
    }
    private val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }

    private fun fixture(seed: Long) = scenario().withRngSeed(seed)
        .withPlayers("First observer seat", "Second observer seat")
        .withActivePlayer(1).withPriorityPlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")

    private fun views(state: GameState, actor: EntityId, holders: Set<EntityId>, label: String): ActorInput {
        (state.pendingDecision?.playerId ?: state.priorityPlayerId) shouldBe actor
        val menu = fullMenu(state, actor, enumerator)
        val direct = builder.build(state, actor, menu).observation.shouldBeInstanceOf<TrainingObservation>()
        val input = adapter.build(state, actor, menu,
            ActorEpoch("priority-observation-v1", label, 0), 0xFE000100)
        val expected = state.turnOrder.associateWith { it in holders }
        state.turnOrder.associateWith(state::hasPriority) shouldBe expected
        direct.players.associate { it.id to it.hasPriority } shouldBe expected
        input.observation.players.associate { it.id to it.hasPriority } shouldBe expected
        direct.agentToAct shouldBe actor
        direct.priorityPlayerId shouldBe state.priorityPlayerId
        input.actorId shouldBe actor
        input.observation.priorityPlayerId shouldBe state.priorityPlayerId
        return input
    }

    private fun TestGame.reachBlocks(): Pair<EntityId, EntityId> {
        passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
        val attacker = findPermanent("Grizzly Bears")!!
        execute(DeclareAttackers(player1Id, mapOf(attacker to player2Id))).error shouldBe null
        passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
        state.step shouldBe Step.DECLARE_BLOCKERS
        state.priorityPlayerId shouldBe player2Id
        return attacker to findPermanent("Hill Giant")!!
    }

    init {
        cardRegistry.register(tax)

        test("HP01 ordinary priority and its actual pass agree in direct and actor observations") {
            val game = fixture(0xFE000101).build()
            val first = views(game.state, game.player1Id, setOf(game.player1Id), "HP01-first")
            first.legalActions.any { it.action is PassPriority } shouldBe true
            game.execute(PassPriority(game.player1Id)).error shouldBe null
            val second = views(game.state, game.player2Id, setOf(game.player2Id), "HP01-second")
            second.legalActions.any { it.action is PassPriority } shouldBe true
        }

        test("HP02 a real declaration baton has no priority until blockers finish") {
            val game = fixture(0xFE000102)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardOnBattlefield(2, "Hill Giant", summoningSickness = false).build()
            game.reachBlocks()
            val waiting = views(game.state, game.player2Id, emptySet(), "HP02-declaration")
            waiting.legalActions.map { it.action::class } shouldBe listOf(DeclareBlockers::class)
            val beforeIllegalPass = game.state
            game.execute(PassPriority(game.player2Id)).error.shouldNotBeNull()
            game.state shouldBe beforeIllegalPass
            game.execute(DeclareBlockers(game.player2Id, emptyMap())).error shouldBe null
            val settled = views(game.state, game.player1Id, setOf(game.player1Id), "HP02-settled")
            settled.legalActions.any { it.action is PassPriority } shouldBe true
        }

        test("HP03 a serialized real block-tax payment retains its actor without priority") {
            val game = fixture(0xFE000103)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardOnBattlefield(2, "Hill Giant", summoningSickness = false)
                .withCardOnBattlefield(1, tax.name).withCardOnBattlefield(2, "Forest").build()
            val (attacker, blocker) = game.reachBlocks()
            game.execute(DeclareBlockers(game.player2Id, mapOf(blocker to listOf(attacker)))).error shouldBe null
            val question = game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            question.playerId shouldBe game.player2Id
            game.state.priorityPlayerId shouldBe null
            val beforeRestore = views(game.state, game.player2Id, emptySet(), "HP03-payment")
            beforeRestore.legalActions.isNotEmpty() shouldBe true
            beforeRestore.legalActions.all { it.isManaAbility && it.action is ActivateAbility } shouldBe true
            val encoded = json.encodeToString(GameState.serializer(), game.state)
            val restored = json.decodeFromString(GameState.serializer(), encoded)
            restored shouldBe game.state
            game.state = restored
            views(game.state, game.player2Id, emptySet(), "HP03-payment").canonicalJson() shouldBe
                beforeRestore.canonicalJson()
            game.execute(SubmitDecision(game.player2Id,
                ManaSourcesSelectedResponse(question.id, emptyList(), autoPay = true))).error shouldBe null
            game.state.pendingDecision shouldBe null
            game.state.getEntity(blocker)!!.get<BlockingComponent>()!!.blockedAttackerIds shouldBe listOf(attacker)
            game.state.getEntity(game.findPermanent("Forest")!!)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            views(game.state, game.player1Id, setOf(game.player1Id), "HP03-paid")
        }

        test("HP04 actual shared-team priority is visible on both teammates") {
            val players = (1..4).map { EntityId.of("priority-observation-team-$it") }
            val deck = Deck(cards = List(40) { "Forest" })
            val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                format = Format.TwoHeadedGiant(),
                players = players.map { PlayerConfig(it.value, deck, playerId = it) },
                teams = listOf(listOf(0, 1), listOf(2, 3)),
                startingPlayerIndex = 0, skipMulligans = true, seed = 0xFE000104,
            ))
            initialized.playerIds shouldBe players
            val state = initialized.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
                .withPriority(players[0])
            views(state, players[0], setOf(players[0], players[1]), "HP04-first-team")
            val passed = actionProcessor.process(state, PassPriority(players[0])).result
            passed.error shouldBe null
            passed.state.priorityPlayerId shouldBe players[1]
            views(passed.state, players[1], setOf(players[0], players[1]), "HP04-teammate")
            val teamPassed = actionProcessor.process(passed.state, PassPriority(players[1])).result
            teamPassed.error shouldBe null
            teamPassed.state.priorityPlayerId shouldBe players[2]
            views(teamPassed.state, players[2], setOf(players[2], players[3]), "HP04-second-team")
        }
    }
}
