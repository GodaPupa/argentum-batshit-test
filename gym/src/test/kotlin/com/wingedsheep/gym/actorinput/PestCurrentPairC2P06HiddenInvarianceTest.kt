package com.wingedsheep.gym.actorinput

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.hidden.HiddenWorldMaterializationRequest
import com.wingedsheep.engine.hidden.HiddenWorldMaterializationResult
import com.wingedsheep.engine.hidden.HiddenWorldMaterializer
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import io.kotest.matchers.shouldBe

/**
 * Whole-pair hidden-state invariance probes for the exact current Pest/Monster policies and current
 * ObservationAdapter. All randomness is fixed test-only entropy; no official seed namespace,
 * allocation, claim, game, or outcome is created.
 */
class PestCurrentPairC2P06HiddenInvarianceTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val materializer = HiddenWorldMaterializer(cardRegistry)
    private val enumerator = LegalActionEnumerator(
        cardRegistry,
        ManaSolver(cardRegistry),
        CostCalculator(cardRegistry),
        PredicateEvaluator(),
        ConditionEvaluator(),
        TurnManager(cardRegistry),
    )
    private val epoch = ActorEpoch("pest-c2-p06-hidden-v1", "fixed-position", 0)

    private fun cardName(state: GameState, id: EntityId): String? =
        state.getEntity(id)?.get<CardComponent>()?.name

    private fun semanticAction(state: GameState, action: GameAction): String = when (action) {
        is CastSpell -> "CastSpell:" + cardName(state, action.cardId)
        is ActivateAbility -> "ActivateAbility:" + cardName(state, action.sourceId)
        is PlayLand -> "PlayLand:" + cardName(state, action.cardId)
        is PassPriority -> "PassPriority"
        else -> action::class.simpleName ?: action.toString()
    }

    private fun actorInput(state: GameState, actor: EntityId): String {
        val menu = completeActorLegalActions(state, actor, enumerator)
        return adapter.build(state, actor, menu, epoch, 0xC206_00A0L).canonicalJson()
    }

    private fun hiddenPermutation(
        source: GameState,
        viewer: EntityId,
        hiddenOwner: EntityId,
    ): GameState {
        val hand = source.getHand(hiddenOwner)
        require(hand.size >= 2)
        val firstName = requireNotNull(cardName(source, hand[0]))
        val secondName = requireNotNull(cardName(source, hand[1]))
        val normalized = source.copy(rng = GameRng.seeded(0xC206_0F00L))
        val materialized = materializer.materialize(
            normalized,
            HiddenWorldMaterializationRequest(
                slotAssignments = linkedMapOf(
                    hand[0] to cardRegistry.requireCard(secondName),
                    hand[1] to cardRegistry.requireCard(firstName),
                ),
                futureRng = GameRng.seeded(0xC206_0F00L),
            ),
        ) as HiddenWorldMaterializationResult.Materialized
        var result = materialized.state
        for (owner in result.turnOrder) {
            val key = ZoneKey(owner, Zone.LIBRARY)
            val library = result.getLibrary(owner)
            if (library.size > 1) result = result.copy(zones = result.zones + (key to library.reversed()))
        }
        // The permutation must be hidden to the tested viewer; the adapter check below is the
        // executable assertion. This parameter is kept explicit so a future fixture cannot silently
        // switch which player's hidden slots are being rewritten.
        require(viewer != hiddenOwner)
        return result
    }

    init {
        test("Pest policy and observation are invariant to Monster hidden hand identities and both hidden library orders") {
            val game = scenario().withPlayers("Pest", "Monster").withRngSeed(0xC206_0001L)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Carrier Thrall")
                .withCardOnBattlefield(2, "Bramble Wurm")
                .withCardInHand(2, "Rooftop Percher")
                .withCardInHand(2, "Boulderbranch Golem")
                .withCardInLibrary(2, "Urza's Tower")
                .withCardInLibrary(2, "Ancient Stirrings")
                .build()
            val base = game.state.copy(rng = GameRng.seeded(0xC206_0F00L))
            val permuted = hiddenPermutation(base, game.player1Id, game.player2Id)

            actorInput(base, game.player1Id) shouldBe actorInput(permuted, game.player1Id)
            val before = AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
                .chooseAction(base)
            val after = AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
                .chooseAction(permuted)
            semanticAction(base, before) shouldBe semanticAction(permuted, after)
            semanticAction(base, before) shouldBe "CastSpell:Cast Down"
        }

        test("Monster policy and observation are invariant to Pest hidden hand identities and both hidden library orders") {
            val game = scenario().withPlayers("Pest", "Monster").withRngSeed(0xC206_0002L)
                .withActivePlayer(2)
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Weather the Storm")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Pest Mascot")
                .withCardOnBattlefield(2, "Expedition Map", summoningSickness = false)
                .withLandsOnBattlefield(2, "Forest", 2)
                .withLandsOnBattlefield(2, "Urza's Mine", 1)
                .withLandsOnBattlefield(2, "Urza's Power Plant", 1)
                .withCardInLibrary(2, "Urza's Tower")
                .withCardInLibrary(2, "Bramble Wurm")
                .build()
            val base = game.state.copy(rng = GameRng.seeded(0xC206_0F00L))
            val permuted = hiddenPermutation(base, game.player2Id, game.player1Id)

            actorInput(base, game.player2Id) shouldBe actorInput(permuted, game.player2Id)
            val before = AIPlayer.create(cardRegistry, game.player2Id, PestMonsterTronPolicy.profile)
                .chooseAction(base)
            val after = AIPlayer.create(cardRegistry, game.player2Id, PestMonsterTronPolicy.profile)
                .chooseAction(permuted)
            semanticAction(base, before) shouldBe semanticAction(permuted, after)
            semanticAction(base, before) shouldBe "ActivateAbility:Expedition Map"
        }
    }
}
