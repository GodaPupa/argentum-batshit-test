package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.actions.decision.DecisionValidators
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.CanBlockAnyNumber
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Twelve fixed cases for the current combat division rules, with real damage application. */
class CombatAssignmentCurrentRulesTest : ScenarioTestBase() {
    private val attacker = card("Current Division Attacker") {
        manaCost = "{4}"; typeLine = "Creature — Soldier"; power = 4; toughness = 5
    }
    private val trampler = card("Current Division Trampler") {
        manaCost = "{4}"; typeLine = "Creature — Beast"; power = 4; toughness = 5
        keywords(Keyword.TRAMPLE)
    }
    private val deadly = card("Current Division Deadly Trampler") {
        manaCost = "{3}"; typeLine = "Creature — Beast"; power = 3; toughness = 5
        keywords(Keyword.TRAMPLE, Keyword.DEATHTOUCH)
    }
    private val deadlyPartner = card("Current Division Deadly Partner") {
        manaCost = "{1}"; typeLine = "Creature — Soldier"; power = 1; toughness = 3
        keywords(Keyword.DEATHTOUCH)
    }
    private val firstBlocker = card("Current Division First Blocker") {
        manaCost = "{1}"; typeLine = "Creature — Soldier"; power = 0; toughness = 3
    }
    private val secondBlocker = card("Current Division Second Blocker") {
        manaCost = "{1}"; typeLine = "Creature — Soldier"; power = 0; toughness = 3
    }
    private val bander = card("Current Division Banding Blocker") {
        manaCost = "{1}"; typeLine = "Creature — Soldier"; power = 0; toughness = 3
        keywords(Keyword.BANDING)
    }
    private val firstSmall = card("Current Division First Small") {
        manaCost = "{1}"; typeLine = "Creature — Soldier"; power = 1; toughness = 3
    }
    private val secondSmall = card("Current Division Second Small") {
        manaCost = "{1}"; typeLine = "Creature — Soldier"; power = 1; toughness = 3
    }
    private val multiBlocker = card("Current Division Multiple Blocker") {
        manaCost = "{4}"; typeLine = "Creature — Soldier"; power = 4; toughness = 5
        staticAbility { ability = CanBlockAnyNumber() }
    }
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    private fun combat(
        attackerNames: List<String> = listOf(attacker.name),
        blocks: Map<String, List<String>> = linkedMapOf(
            firstBlocker.name to attackerNames, secondBlocker.name to attackerNames,
        ),
        marked: Map<String, Int> = emptyMap(),
        beforeDamage: (TestGame) -> Unit = {},
    ): TestGame {
        val builder = scenario().withPlayers().withRngSeed(0xFEC510)
            .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island")
            .withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
        attackerNames.forEach { builder.withCardOnBattlefield(1, it, summoningSickness = false) }
        blocks.keys.forEach { builder.withCardOnBattlefield(2, it) }
        val game = builder.build()
        for ((name, amount) in marked) {
            game.state = game.state.updateEntity(game.findPermanent(name)!!) { it.with(DamageComponent(amount)) }
        }
        game.declareAttackers(attackerNames.associateWith { 2 }).error shouldBe null
        game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
        game.declareBlockers(blocks).error shouldBe null
        beforeDamage(game)
        game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
        game.state.step shouldBe Step.COMBAT_DAMAGE
        return game
    }

    private fun board(game: TestGame): CombatResolutionDecision =
        game.getPendingDecision().shouldBeInstanceOf<CombatResolutionDecision>()

    private fun response(
        question: CombatResolutionDecision,
        assignments: Map<Pair<EntityId, EntityId>, Int>,
    ): CombatResolutionResponse = CombatResolutionResponse(
        question.id,
        question.edges.filter { it.editableBy == question.playerId }.map { edge ->
            DamageEdgeAmount(edge.id, assignments[edge.sourceId to edge.targetId] ?: edge.amount)
        },
    )

    private fun reject(game: TestGame, answer: DecisionResponse) {
        val before = game.state
        val result = game.submitDecision(answer)
        result.error shouldNotBe null
        result.state shouldBe before
        result.events shouldBe emptyList()
        game.state shouldBe before
    }

    private fun marked(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<DamageComponent>()?.amount ?: 0

    /** A persisted legacy question at the same legal combat pause, using its real resumer. */
    private fun installLegacyQuestion(game: TestGame): AssignDamageDecision {
        val current = board(game)
        val source = current.attackers.single()
        val targetEdges = current.edges.filter { it.sourceId == source.id && !it.isTrampleDrain }
        game.state = game.state.popContinuation().second.suspendForDecision(
            question = { id -> AssignDamageDecision(
                id, current.playerId, "Legacy combat assignment", current.context,
                source.id, targetEdges.maxOf { it.maximum }, targetEdges.map { it.targetId },
                source.attackedDefenderId, targetEdges.associate { it.targetId to it.lethal },
                current.edges.filter { it.sourceId == source.id }.associate { it.targetId to it.amount },
                source.hasTrample, source.hasDeathtouch,
            ) },
            answer = DamageAssignmentContinuation(source.id, source.attackedDefenderId, current.firstStrike),
        ).state
        val encoded = json.encodeToString(GameState.serializer(), game.state)
        game.state = json.decodeFromString(GameState.serializer(), encoded)
        return game.getPendingDecision().shouldBeInstanceOf<AssignDamageDecision>()
    }

    init {
        cardRegistry.register(listOf(attacker, trampler, deadly, deadlyPartner, firstBlocker, secondBlocker,
            bander, firstSmall, secondSmall, multiBlocker))

        test("attacker freely divides four damage as two each among three-toughness blockers") {
            val game = combat()
            val source = game.findPermanent(attacker.name)!!
            val first = game.findPermanent(firstBlocker.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            val result = game.submitDecision(response(board(game), mapOf((source to first) to 2, (source to second) to 2)))
            result.error shouldBe null
            result.events.filterIsInstance<DamageDealtEvent>().filter { it.sourceId == source }
                .associate { it.targetId to it.amount } shouldBe mapOf(first to 2, second to 2)
            marked(game, first) shouldBe 2
            marked(game, second) shouldBe 2
            game.isOnBattlefield(firstBlocker.name) shouldBe true
            game.isOnBattlefield(secondBlocker.name) shouldBe true
            game.getPendingDecision() shouldBe null
        }

        test("blocker freely divides four damage as two each among three-toughness attackers") {
            val names = listOf(firstSmall.name, secondSmall.name)
            val game = combat(names, mapOf(multiBlocker.name to names))
            val attackingChoice = board(game)
            game.submitDecision(response(attackingChoice, emptyMap())).error shouldBe null
            val blockingChoice = board(game)
            blockingChoice.playerId shouldBe game.player2Id
            val source = game.findPermanent(multiBlocker.name)!!
            val first = game.findPermanent(firstSmall.name)!!
            val second = game.findPermanent(secondSmall.name)!!
            game.submitDecision(response(blockingChoice, mapOf((source to first) to 2, (source to second) to 2))).error shouldBe null
            marked(game, first) shouldBe 2
            marked(game, second) shouldBe 2
            game.isOnBattlefield(firstSmall.name) shouldBe true
            game.isOnBattlefield(secondSmall.name) shouldBe true
            game.getPendingDecision() shouldBe null
        }

        test("all damage may be assigned to one creature even beyond lethal") {
            val game = combat()
            val source = game.findPermanent(attacker.name)!!
            val first = game.findPermanent(firstBlocker.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            game.submitDecision(response(board(game), mapOf((source to first) to 4, (source to second) to 0))).error shouldBe null
            game.isOnBattlefield(firstBlocker.name) shouldBe false
            game.isOnBattlefield(secondBlocker.name) shouldBe true
            marked(game, second) shouldBe 0
        }

        test("board underassignment overassignment and negative assignments reject atomically") {
            val game = combat()
            val source = game.findPermanent(attacker.name)!!
            val first = game.findPermanent(firstBlocker.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            for ((a, b) in listOf(2 to 1, 0 to 0, 4 to 1, -1 to 5)) {
                reject(game, response(board(game), mapOf((source to first) to a, (source to second) to b)))
            }
        }

        test("serialized legacy assignment accepts a nonlethal split without imposing target order") {
            val game = combat()
            val first = game.findPermanent(firstBlocker.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            val legacy = installLegacyQuestion(game)
            game.submitDecision(DamageAssignmentResponse(legacy.id, mapOf(first to 2, second to 2))).error shouldBe null
            marked(game, first) shouldBe 2
            marked(game, second) shouldBe 2
            game.getPendingDecision() shouldBe null
        }

        test("legacy underassignment overassignment and negative amounts reject atomically") {
            val game = combat()
            val first = game.findPermanent(firstBlocker.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            val legacy = installLegacyQuestion(game)
            for ((a, b) in listOf(2 to 1, 0 to 0, 4 to 1, -1 to 5)) {
                reject(game, DamageAssignmentResponse(legacy.id, mapOf(first to a, second to b)))
            }
        }

        test("trample cannot assign to the defender before every blocker has lethal damage") {
            val game = combat(listOf(trampler.name))
            val source = game.findPermanent(trampler.name)!!
            val first = game.findPermanent(firstBlocker.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            reject(game, response(board(game), mapOf(
                (source to first) to 1, (source to second) to 1, (source to game.player2Id) to 2,
            )))
            val legacy = installLegacyQuestion(game)
            reject(game, DamageAssignmentResponse(legacy.id, mapOf(first to 1, second to 1, game.player2Id to 2)))
        }

        test("marked and concurrent lethal damage permit trample and preserve the other chooser's plan") {
            val names = listOf(trampler.name, firstSmall.name)
            val game = combat(names, mapOf(multiBlocker.name to names), marked = mapOf(multiBlocker.name to 2))
            val source = game.findPermanent(trampler.name)!!
            val partner = game.findPermanent(firstSmall.name)!!
            val blocker = game.findPermanent(multiBlocker.name)!!
            game.submitDecision(response(board(game), mapOf(
                (source to blocker) to 2, (source to game.player2Id) to 2, (partner to blocker) to 1,
            ))).error shouldBe null
            val defending = board(game)
            defending.playerId shouldBe game.player2Id
            defending.edges.single { it.sourceId == source && it.targetId == game.player2Id }.amount shouldBe 2
            game.submitDecision(response(defending, mapOf((blocker to source) to 0, (blocker to partner) to 4))).error shouldBe null
            game.getLifeTotal(2) shouldBe 18
            game.isOnBattlefield(multiBlocker.name) shouldBe false
            game.isOnBattlefield(firstSmall.name) shouldBe false
            game.isOnBattlefield(trampler.name) shouldBe true
            marked(game, source) shouldBe 0
        }

        test("deathtouch trample assigns one lethal damage per blocker and the remainder to the defender") {
            val game = combat(listOf(deadly.name))
            val source = game.findPermanent(deadly.name)!!
            val first = game.findPermanent(firstBlocker.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            board(game).edges.filter { !it.isTrampleDrain }.forEach { it.lethal shouldBe 1 }
            game.submitDecision(response(board(game), mapOf(
                (source to first) to 1, (source to second) to 1, (source to game.player2Id) to 1,
            ))).error shouldBe null
            game.getLifeTotal(2) shouldBe 19
            game.isOnBattlefield(firstBlocker.name) shouldBe false
            game.isOnBattlefield(secondBlocker.name) shouldBe false

            // Fixed amendment A: zero damage from a deathtouch trampler cannot turn a
            // non-deathtouch partner's one damage into lethal damage to a five-toughness blocker.
            val zeroNames = listOf(deadly.name, firstSmall.name)
            val zero = combat(zeroNames, mapOf(multiBlocker.name to zeroNames))
            val zeroSource = zero.findPermanent(deadly.name)!!
            val zeroPartner = zero.findPermanent(firstSmall.name)!!
            val zeroBlocker = zero.findPermanent(multiBlocker.name)!!
            reject(zero, response(board(zero), mapOf(
                (zeroSource to zeroBlocker) to 0, (zeroSource to zero.player2Id) to 3,
                (zeroPartner to zeroBlocker) to 1,
            )))

            // Fixed amendment B: a different attacker's positive deathtouch assignment does
            // supply lethal damage, allowing the non-deathtouch trampler's full defender drain.
            val positiveNames = listOf(trampler.name, deadlyPartner.name)
            val positive = combat(positiveNames, mapOf(multiBlocker.name to positiveNames))
            val positiveSource = positive.findPermanent(trampler.name)!!
            val positivePartner = positive.findPermanent(deadlyPartner.name)!!
            val positiveBlocker = positive.findPermanent(multiBlocker.name)!!
            positive.submitDecision(response(board(positive), mapOf(
                (positiveSource to positiveBlocker) to 0, (positiveSource to positive.player2Id) to 4,
                (positivePartner to positiveBlocker) to 1,
            ))).error shouldBe null
            val defending = board(positive)
            defending.playerId shouldBe positive.player2Id
            defending.edges.single { it.sourceId == positiveSource && it.targetId == positive.player2Id }.amount shouldBe 4
            positive.submitDecision(response(defending, mapOf(
                (positiveBlocker to positiveSource) to 4, (positiveBlocker to positivePartner) to 0,
            ))).error shouldBe null
            positive.getLifeTotal(2) shouldBe 16
            positive.isOnBattlefield(multiBlocker.name) shouldBe false
            positive.isOnBattlefield(trampler.name) shouldBe true
            positive.isOnBattlefield(deadlyPartner.name) shouldBe true
            marked(positive, positiveSource) shouldBe 4
            positive.getPendingDecision() shouldBe null
        }

        test("banding changes the chooser while preserving the same free nonlethal division") {
            val game = combat(blocks = mapOf(bander.name to listOf(attacker.name), secondBlocker.name to listOf(attacker.name)))
            val question = board(game)
            question.playerId shouldBe game.player2Id
            val source = game.findPermanent(attacker.name)!!
            val first = game.findPermanent(bander.name)!!
            val second = game.findPermanent(secondBlocker.name)!!
            val answer = response(question, mapOf((source to first) to 2, (source to second) to 2))
            val before = game.state
            val wrongPlayer = game.execute(SubmitDecision(game.player1Id, answer))
            wrongPlayer.error shouldNotBe null
            wrongPlayer.state shouldBe before
            game.submitDecision(answer).error shouldBe null
            marked(game, first) shouldBe 2
            marked(game, second) shouldBe 2
            game.getPendingDecision() shouldBe null
        }

        test("changed nonowned unknown and duplicate edges reject while unchanged full-board echoes remain valid") {
            val names = listOf(firstSmall.name, secondSmall.name)
            val game = combat(names, mapOf(multiBlocker.name to names))
            val question = board(game)
            val other = question.edges.first { it.editableBy != question.playerId }
            val altered = if (other.amount == 0) 1 else other.amount - 1
            reject(game, CombatResolutionResponse(question.id, listOf(DamageEdgeAmount(other.id, altered))))
            reject(game, CombatResolutionResponse(question.id, listOf(DamageEdgeAmount("unknown-edge", 0))))
            val owned = question.edges.first { it.editableBy == question.playerId }
            reject(game, CombatResolutionResponse(question.id,
                listOf(DamageEdgeAmount(owned.id, owned.amount), DamageEdgeAmount(owned.id, owned.amount))))
            game.submitDecision(CombatResolutionResponse(question.id,
                question.edges.map { DamageEdgeAmount(it.id, it.amount) })).error shouldBe null
            board(game).playerId shouldBe game.player2Id
            game.submitDecision(response(board(game), emptyMap())).error shouldBe null
            game.getPendingDecision() shouldBe null
        }

        test("a blocked source with no remaining recipients assigns zero and cannot invent targets") {
            val game = combat(beforeDamage = { current ->
                for (name in listOf(firstBlocker.name, secondBlocker.name)) {
                    val move = ZoneTransitionService.moveToZone(current.state, current.findPermanent(name)!!, Zone.EXILE)
                    current.state = move.state
                }
            })
            game.getPendingDecision() shouldBe null
            game.getLifeTotal(2) shouldBe 20
            val source = game.findPermanent(attacker.name)!!
            val empty = AssignDamageDecision("empty-legacy", game.player1Id, "No recipients",
                DecisionContext(phase = DecisionPhase.COMBAT), source, 4, emptyList(), game.player2Id,
                emptyMap(), emptyMap(), hasTrample = false, hasDeathtouch = false)
            DecisionValidators.validate(empty, DamageAssignmentResponse(empty.id, emptyMap())) shouldBe null
            DecisionValidators.validate(empty, DamageAssignmentResponse(empty.id, mapOf(game.player2Id to 4))) shouldNotBe null
            val emptyBoard = CombatResolutionDecision("empty-board", game.player1Id, "No recipients",
                empty.context, false, emptyList(), emptyList(), emptyList(), emptyList())
            DecisionValidators.validate(emptyBoard, CombatResolutionResponse(emptyBoard.id, emptyList())) shouldBe null
            DecisionValidators.validate(emptyBoard, CombatResolutionResponse(emptyBoard.id,
                listOf(DamageEdgeAmount("invented-edge", 4)))) shouldNotBe null
        }
    }
}
