package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Prospective receiving bank frozen by STAGE_E_INTERACTION_COUNTER_RECEIVING_BUDGET_20260928.json.
 * This qualifies only the actor's current real-engine Spell Pierce / Dispel cast + offered target.
 * Spell Pierce resolution intentionally stops before the opponent's unless-pay choice.
 */
class SphinxStageEInteractionCounterReceivingTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val epoch = ActorEpoch("stage-e-interaction-counter-receiving-v1", "fixed-adapter-data", 0)
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_INTERACTION_COUNTER_RECEIVING_BUDGET_20260928.json")) }
    private val identities = listOf(
        "reconstructed-v01",
        "reconstructed-hybrid",
        "closest-no-approach-v01",
        "serpico-terror-benchmark",
    )

    private fun actorInput(game: TestGame): ActorInput {
        val actor = game.state.priorityPlayerId!!
        return adapter.build(
            game.state,
            actor,
            completeActorLegalActions(game.state, actor, enumerator),
            epoch,
            202609280401L,
        )
    }

    private fun castOffer(input: ActorInput, card: EntityId): Int =
        input.legalActions.indexOfFirst {
            (it.action as? CastSpell)?.let { cast ->
                cast.cardId == card && cast.faceIndex == null && !cast.castFaceDown
            } == true
        }.also { require(it >= 0) }

    init {
        identities.forEach { identity ->
            val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
            val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
            val interaction = when {
                (own.cards["Spell Pierce"] ?: 0) > 0 -> "Spell Pierce"
                (own.cards["Dispel"] ?: 0) > 0 -> "Dispel"
                else -> error("$identity has neither prospectively admitted interaction identity")
            }
            val opposingSpell = if (interaction == "Spell Pierce") "Ponder" else "Mental Note"
            val basic = if ((own.cards["Island"] ?: 0) > 0) "Island" else "Snow-Covered Island"

            test("IC1 $identity real $interaction receives only the actually offered public opposing spell") {
                val builder = scenario().withPlayers().withRngSeed(202609280402L)
                    .withCardInHand(1, interaction)
                    .withLandsOnBattlefield(1, basic, 1)
                    .withCardInHand(2, opposingSpell)
                    .withLandsOnBattlefield(2, "Island", 1)
                    .withActivePlayer(2).withPriorityPlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                own.cards.forEach { (name, count) ->
                    val removed = (if (name == interaction) 1 else 0) + (if (name == basic) 1 else 0)
                    val remaining = count - removed
                    require(remaining >= 0)
                    repeat(remaining) { builder.withCardInLibrary(1, name) }
                }

                val game = builder.build()
                (game.state.getHand(game.player1Id) + game.state.getBattlefield(game.player1Id) +
                    game.state.getLibrary(game.player1Id)).size shouldBe 60

                val opposing = game.findCardsInHand(2, opposingSpell).single()
                game.execute(CastSpell(game.player2Id, opposing)).error shouldBe null
                game.execute(PassPriority(game.player2Id)).error shouldBe null

                val interactionId = game.findCardsInHand(1, interaction).single()
                val input = actorInput(game)
                input.actorId shouldBe game.player1Id
                val index = castOffer(input, interactionId)
                val legal = input.legalActions[index]
                legal.basicBluePayment!!.availableBlueMana shouldBe 1
                legal.basicBluePayment!!.totalMana shouldBe 1
                val offeredTargets = legal.targetRequirements.orEmpty().flatMap { it.validTargets }.ifEmpty {
                    legal.validTargets.orEmpty()
                }
                offeredTargets shouldContain opposing

                val proposed = SphinxStageEActorAdapter.decideCurrentCast(
                    input,
                    input.epoch,
                    input.actorId,
                    own,
                    index,
                    SphinxStageEComponentCall.COUNTERSPELL,
                ).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()

                proposed.proposal.action shouldBe CastSpell(
                    game.player1Id,
                    interactionId,
                    listOf(ChosenTarget.Spell(opposing)),
                )
                game.execute(proposed.proposal.action).error shouldBe null
                game.state.stack shouldContain interactionId
                game.state.stack shouldContain opposing
            }
        }
    }
}
