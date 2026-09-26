package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.LegendRuleContinuation
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.BoundaryFailure
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.ObservationBoundaryException
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Five prospectively fixed mechanical checks for each exact input identity (20 total).
 * The unchanged policy component receives no identity label and is not tuned by these checks.
 * Own fixture cards partition its actual frozen 60 without shuffling or experimental seeds.
 */
class SphinxStageEActorAdapterTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val epoch = ActorEpoch("stage-e-actor-receiving-v1", "fixed-adapter-data", 0)
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val identities = listOf("reconstructed-v01", "reconstructed-hybrid",
        "closest-no-approach-v01", "serpico-terror-benchmark")

    private fun fixture(own: SphinxStageEOwnDeck, active: Int = 1): TestGame {
        val builder = scenario().withPlayers().withRngSeed(202609260302L)
            .withCardInHand(1, "Mental Note").withCardInHand(1, "Mental Note")
            .withCardInHand(1, "Counterspell").withCardInHand(1, "Island")
            .withLandsOnBattlefield(1, "Island", 3)
            .withCardInHand(2, "Sphinx's Approach").withCardInHand(2, "Island")
            .withLandsOnBattlefield(2, "Island", 3)
            .withCardInLibrary(2, "Island").withCardInLibrary(2, "Mental Note")
            .withActivePlayer(active).withPriorityPlayer(active)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        val removed = mapOf("Island" to 4, "Mental Note" to 2, "Counterspell" to 1)
        own.cards.forEach { (name, count) ->
            val remaining = count - (removed[name] ?: 0)
            require(remaining >= 0)
            repeat(remaining) { builder.withCardInLibrary(1, name) }
        }
        return builder.build().also { game ->
            (game.state.getHand(game.player1Id) + game.state.getBattlefield(game.player1Id) +
                game.state.getLibrary(game.player1Id)).size shouldBe 60
        }
    }

    private fun input(state: GameState, current: ActorEpoch = epoch): ActorInput {
        val actor = state.pendingDecision?.playerId ?: state.priorityPlayerId!!
        return adapter.build(state, actor, completeActorLegalActions(state, actor, enumerator), current, 910302L)
    }

    private fun offer(input: ActorInput, id: EntityId): Int = input.legalActions.indexOfFirst {
        (it.action as? CastSpell)?.let { cast -> cast.cardId == id && cast.faceIndex == null && !cast.castFaceDown } == true
    }.also { require(it >= 0) }

    private fun decide(input: ActorInput, own: SphinxStageEOwnDeck, index: Int,
                       call: SphinxStageEComponentCall = SphinxStageEComponentCall.SETUP_DRAW) =
        SphinxStageEActorAdapter.decideCurrentCast(input, input.epoch, input.actorId, own, index, call)

    init {
        identities.forEach { identity ->
            val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
            val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)

            test("AR1 $identity binds current payment and converts the unchanged setup component into a real cast") {
                val game = fixture(own)
                val id = game.findCardsInHand(1, "Mental Note").first()
                val before = game.state
                val input = input(before)
                val index = offer(input, id)
                val projection = input.legalActions[index].basicBluePayment!!
                projection.availableBlueMana shouldBe 3
                projection.totalMana shouldBe 1
                projection.blueRemainingAfterPayment shouldBe 2
                val proposed = decide(input, own, index).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
                proposed.proposal.inputBindingHash shouldBe input.bindingHash
                proposed.proposal.nextPolicyRngState shouldBe input.policyRngState
                proposed.proposal.action shouldBe CastSpell(game.player1Id, id)
                game.state shouldBe before
                game.execute(proposed.proposal.action).error shouldBe null
                game.state.stack shouldBe listOf(id)
                game.state.getHand(game.player1Id).contains(id) shouldBe false
                input(game.state, epoch.copy(step = 1)).legalActions
                    .single { (it.action as? CastSpell)?.cardId == game.findCardsInHand(1, "Mental Note").single() }
                    .basicBluePayment!!.availableBlueMana shouldBe 2
            }

            test("AR2 $identity rejects a stale input and respects actual newly spent mana for the next offer") {
                val game = fixture(own)
                val notes = game.findCardsInHand(1, "Mental Note")
                val old = input(game.state)
                val accepted = decide(old, own, offer(old, notes.first())).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
                game.execute(accepted.proposal.action).error shouldBe null
                val nextEpoch = epoch.copy(step = 1)
                shouldThrow<ObservationBoundaryException> {
                    SphinxStageEActorAdapter.decideCurrentCast(old, nextEpoch, game.player1Id, own,
                        offer(old, notes.last()), SphinxStageEComponentCall.SETUP_DRAW)
                }.failure shouldBe BoundaryFailure.STALE_INPUT
                val current = input(game.state, nextEpoch)
                val index = offer(current, notes.last())
                current.legalActions[index].basicBluePayment!!.availableBlueMana shouldBe 2
                current.legalActions[index].basicBluePayment!!.blueRemainingAfterPayment shouldBe 1
                decide(current, own, index).shouldBeInstanceOf<SphinxStageEAdapterResult.Declined>()
                    .reason shouldBe "preserve available interaction"
                game.state.stack shouldBe listOf(notes.first())
            }

            test("AR3 $identity converts only the current public opposing spell target and the real counter resolves") {
                val game = fixture(own, active = 2)
                val opposing = game.findCardsInHand(2, "Sphinx's Approach").single()
                game.execute(CastSpell(game.player2Id, opposing)).error shouldBe null
                game.execute(PassPriority(game.player2Id)).error shouldBe null
                val counter = game.findCardsInHand(1, "Counterspell").single()
                val current = input(game.state)
                current.actorId shouldBe game.player1Id
                val index = offer(current, counter)
                val stack = current.observation.stack.single()
                stack.spell!!.manaValue shouldBe 3
                stack.spell!!.counterable shouldBe true
                val proposed = decide(current, own, index, SphinxStageEComponentCall.COUNTERSPELL)
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
                proposed.proposal.action shouldBe CastSpell(game.player1Id, counter, listOf(ChosenTarget.Spell(opposing)))
                game.execute(proposed.proposal.action).error shouldBe null
                val results = game.resolveStack()
                results.forEach { it.error shouldBe null }
                game.state.stack shouldBe emptyList()
                game.state.getGraveyard(game.player2Id) shouldBe listOf(opposing)
                game.state.getGraveyard(game.player1Id) shouldBe listOf(counter)
            }

            test("AR4 $identity keeps actor input and proposal invariant to unseen libraries and opponent hand identity") {
                val game = fixture(own)
                val base = game.state
                var changed = base
                listOf(game.player1Id, game.player2Id).forEach { player ->
                    changed = changed.copy(zones = changed.zones +
                        (ZoneKey(player, Zone.LIBRARY) to base.getLibrary(player).reversed()))
                }
                val hidden = base.getHand(game.player2Id).first()
                changed = changed.updateEntity(hidden) { entity ->
                    entity.with(entity.get<CardComponent>()!!.copy(cardDefinitionId = "Grizzly Bears", name = "Grizzly Bears"))
                }
                val before = input(base)
                val after = input(changed)
                after.canonicalJson() shouldBe before.canonicalJson()
                val id = game.findCardsInHand(1, "Mental Note").first()
                decide(after, own, offer(after, id)) shouldBe decide(before, own, offer(before, id))
                before.observation.zones.single { it.ownerId == game.player1Id && it.zoneType == Zone.LIBRARY }
                    .cards shouldBe emptyList()
            }

            test("AR5 $identity leaves other actions and a typed unresolved question explicitly unqualified") {
                val game = fixture(own)
                val before = game.state
                val ordinary = input(before)
                val pass = ordinary.legalActions.indexOfFirst { it.action is PassPriority }
                require(pass >= 0)
                decide(ordinary, own, pass).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
                // A projection-only typed boundary fixture. This inert continuation is never resumed;
                // it does not certify a real May effect, target or whole-game choice policy.
                val asked = before.suspendForDecision({ id ->
                    YesNoDecision(id, game.player1Id, "Fixed unresolved question", DecisionContext())
                }, LegendRuleContinuation(game.player1Id, emptyList())).state
                val pending = input(asked)
                pending.decision.shouldBeInstanceOf<YesNoDecision>()
                pending.legalActions shouldBe emptyList()
                decide(pending, own, 0).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
                game.state shouldBe before
            }
        }
    }
}
