package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Two fixed, seed-excluded receiving cases for the narrow multi-cast discriminator.
 *
 * MU1 permits a result only because two existing component calls disagree: Mental Note declines
 * to preserve visible Counterspell mana while Tolarian Terror proposes in its normal deployment
 * window. MU2 proves that two independently proposed casts remain fail-closed.
 */
class SphinxStageEMultiCastDiscriminatorReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_MULTICAST_UNIQUE_PROPOSAL_BUDGET_20260929.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private data class Prepared(
        val state: GameState,
        val seat: SphinxStageEInitializedSeat,
        val actor: EntityId,
        val epoch: ActorEpoch,
    )

    private fun name(state: GameState, id: EntityId): String =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    private fun prepare(
        identity: String,
        seed: Long,
        handNames: List<String>,
        graveyardNames: List<String>,
        battlefieldIslands: Int,
    ): Prepared {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val actor = EntityId.of("multicast-actor-$identity-${seed.toString(16)}")
        val opponent = EntityId.of("multicast-opponent-$identity-${seed.toString(16)}")
        val ownNames = own.cards.flatMap { (card, count) -> List(count) { card } }
        val openingEpoch = ActorEpoch("stage-e-multicast-unique-v1", identity, 0)
        val initialized = GameInitializer(cardRegistry).initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig("Frozen Sphinx actor", Deck(ownNames), playerId = actor),
                    PlayerConfig("Passive opponent", Deck(List(60) { "Island" }), playerId = opponent),
                ),
                startingHandSize = 7,
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = 0,
                seed = seed,
            )
        )
        val seat = SphinxStageEInitializedSeat.bindOpening(
            initialized, actor, bytes, openingEpoch,
        )

        var state = initialized.state
        fun advance(action: GameAction) {
            val result = actionProcessor.process(state, action).result
            result.error shouldBe null
            state = result.state
        }
        advance(KeepHand(actor))
        advance(KeepHand(opponent))

        val all = (state.getHand(actor) + state.getLibrary(actor)).sortedBy { it.value }
        all.size shouldBe 60
        val used = mutableSetOf<EntityId>()
        fun take(cardName: String): EntityId {
            val id = all.firstOrNull { it !in used && name(state, it) == cardName }
                ?: error("Missing physical $cardName in $identity")
            used += id
            return id
        }

        val hand = handNames.map(::take)
        val graveyard = graveyardNames.map(::take)
        val lands = List(battlefieldIslands) { take("Island") }
        val library = all.filter { it !in used }

        state = state.copy(
            zones = state.zones +
                (ZoneKey(actor, Zone.HAND) to hand) +
                (ZoneKey(actor, Zone.LIBRARY) to (graveyard + lands + library)),
        )
        graveyard.forEach {
            state = ZoneTransitionService.moveToZone(state, it, Zone.GRAVEYARD).state
        }
        lands.forEach {
            state = ZoneTransitionService.moveToZone(
                state, it, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor),
            ).state
        }
        state = state.copy(
            phase = Phase.PRECOMBAT_MAIN,
            step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor,
            priorityPlayerId = actor,
        )
        return Prepared(state, seat, actor, openingEpoch.copy(step = 1))
    }

    private fun input(prepared: Prepared, policyRng: Long): ActorInput =
        adapter.build(
            prepared.state,
            prepared.actor,
            completeActorLegalActions(prepared.state, prepared.actor, enumerator),
            prepared.epoch,
            policyRng,
        )

    private fun castIndex(input: ActorInput, state: GameState, cardName: String): Int =
        input.legalActions.indexOfFirst {
            (it.action as? CastSpell)?.let { cast -> name(state, cast.cardId) == cardName } == true
        }.also { require(it >= 0) { "No current $cardName cast offer" } }

    init {
        test("MU1 unique existing component proposal survives a real two-cast window") {
            val prepared = prepare(
                identity = "reconstructed-hybrid",
                seed = 0x53504D554C540001L,
                handNames = listOf("Mental Note", "Counterspell", "Tolarian Terror"),
                graveyardNames = List(5) { "Sphinx's Approach" },
                battlefieldIslands = 2,
            )
            val input = input(prepared, 0x53504D554C540101L)
            val castNames = input.legalActions.mapNotNull { legal ->
                (legal.action as? CastSpell)?.let { name(prepared.state, it.cardId) }
            }
            castNames.toSet() shouldBe setOf("Mental Note", "Tolarian Terror")

            val noteIndex = castIndex(input, prepared.state, "Mental Note")
            prepared.seat.decideCurrentCast(
                input, prepared.epoch, noteIndex, SphinxStageEComponentCall.SETUP_DRAW,
            ).shouldBeInstanceOf<SphinxStageEAdapterResult.Declined>()
                .reason shouldBe "preserve available interaction"

            val terrorIndex = castIndex(input, prepared.state, "Tolarian Terror")
            val direct = prepared.seat.decideCurrentCast(
                input, prepared.epoch, terrorIndex, SphinxStageEComponentCall.DEPLOYMENT,
            ).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()

            val whole = SphinxStageEWholeActor.decide(
                input, prepared.epoch, prepared.seat,
            ).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            whole.proposal shouldBe direct.proposal
            name(prepared.state, (whole.proposal.action as CastSpell).cardId) shouldBe "Tolarian Terror"
        }

        test("MU2 two accepted physical casts remain explicitly unqualified") {
            val prepared = prepare(
                identity = "reconstructed-v01",
                seed = 0x53504D554C540002L,
                handNames = listOf("Mental Note", "Mental Note"),
                graveyardNames = emptyList(),
                battlefieldIslands = 2,
            )
            val input = input(prepared, 0x53504D554C540102L)
            val noteIndices = input.legalActions.withIndex().filter {
                (it.value.action as? CastSpell)?.let { cast ->
                    name(prepared.state, cast.cardId) == "Mental Note"
                } == true
            }.map { it.index }
            noteIndices.size shouldBe 2
            noteIndices.forEach { index ->
                prepared.seat.decideCurrentCast(
                    input, prepared.epoch, index, SphinxStageEComponentCall.SETUP_DRAW,
                ).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            }

            SphinxStageEWholeActor.decide(
                input, prepared.epoch, prepared.seat,
            ).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
                .requirement shouldBe "Multiple accepted current cast offers require a reviewed ranking policy"
        }
    }
}
