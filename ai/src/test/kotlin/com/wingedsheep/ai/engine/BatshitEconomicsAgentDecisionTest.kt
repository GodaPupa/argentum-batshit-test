package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.CardsDrawnThisTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Deterministic decision probes for the lines that decide Batshit Economics vs Mono-Red Madness.
 *
 * These are deliberately positions, not matchup scripts: every assertion is about information the
 * agent is entitled to see in the current game state, and every position is replayed with a fixed
 * RNG seed. The deck lists are not fixtures here and remain frozen independently of this suite.
 */
class BatshitEconomicsAgentDecisionTest : ScenarioTestBase() {

    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING

    private fun ai(game: TestGame, player: EntityId = game.player1Id) =
        AIPlayer.create(cardRegistry, player, profile)

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun chosenTargetId(action: CastSpell): EntityId? = when (val target = action.targets.singleOrNull()) {
        is ChosenTarget.Permanent -> target.entityId
        is ChosenTarget.Player -> target.playerId
        is ChosenTarget.Card -> target.cardId
        is ChosenTarget.Spell -> target.spellEntityId
        null -> null
    }

    private fun seeded() = scenario().withPlayers().withRngSeed(0xBA75_117L)

    init {
        test("Batshit removes Guttersnipe before Flamebreather when the two-damage engine is lethal") {
            val game = seeded()
                .withLifeTotal(1, 2)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(1, "Lightning Bolt")
                .withCardOnBattlefield(2, "Guttersnipe")
                .withCardOnBattlefield(2, "Kessig Flamebreather")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, chosenTargetId(action)!!) shouldBe "Guttersnipe"
        }

        test("Batshit cashes a removal-targeted creature into Village Rites") {
            val game = seeded()
                .withActivePlayer(2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Village Rites")
                .withCardOnBattlefield(1, "Shambling Ghast")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withCardInHand(2, "Lightning Bolt")
                .build()
            val victim = game.findPermanent("Shambling Ghast")!!
            game.castSpell(2, "Lightning Bolt", victim).isSuccess.shouldBeTrue()
            game.execute(PassPriority(game.player2Id)).isSuccess.shouldBeTrue()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Village Rites"
            action.additionalCostPayment?.sacrificedPermanents shouldBe listOf(victim)
        }

        test("Batshit Unearths Glasswright over a lower-value creature") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Unearth")
                .withCardInGraveyard(1, "Shambling Ghast")
                .withCardInGraveyard(1, "Goblin Glasswright")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Unearth"
            cardName(game, chosenTargetId(action)!!) shouldBe "Goblin Glasswright"
        }

        test("Batshit reserves Treasure mana for Bolt instead of feeding it to Munitions") {
            val game = seeded()
                .withCardOnBattlefield(1, "Makeshift Munitions")
                .withCardOnBattlefield(1, "Treasure", isToken = true)
                .withCardOnBattlefield(1, "Shambling Ghast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(1, "Lightning Bolt")
                .withLifeTotal(2, 3)
                .build()

            val action = ai(game).chooseAction(game.state)
            action.shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Lightning Bolt"
            chosenTargetId(action) shouldBe game.player2Id
        }

        test("Batshit deploys Flamebreather before spending a prepared Craft") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInHand(1, "Goblin Glasswright")
                .withCardInHand(1, "Kessig Flamebreather")
                .withCardInHand(1, "Craw Wurm")
                .build()
            game.castSpell(1, "Goblin Glasswright").isSuccess.shouldBeTrue()
            game.resolveStack()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Kessig Flamebreather"
        }

        test("Red accepts a beneficial Fiery Temper madness cast") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInHand(1, "Tormenting Voice")
                .withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            val voice = game.findCardsInHand(1, "Tormenting Voice").single()
            val temper = game.findCardsInHand(1, "Fiery Temper").single()
            game.execute(
                CastSpell(
                    game.player1Id,
                    voice,
                    additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(temper)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            ).isSuccess.shouldBeTrue()
            while (game.state.pendingDecision == null && game.state.stack.isNotEmpty()) {
                game.execute(PassPriority(game.state.priorityPlayerId!!))
            }

            val decision = game.state.pendingDecision!!
            ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<com.wingedsheep.engine.core.YesNoResponse>()
                .choice shouldBe true
        }

        test("Red flashes back Lava Dart when sacrificing a Mountain is lethal") {
            val game = seeded()
                .withLifeTotal(2, 1)
                .withCardInGraveyard(1, "Lava Dart")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Lava Dart"
            chosenTargetId(action) shouldBe game.player2Id
            action.additionalCostPayment?.sacrificedPermanents?.size shouldBe 1
        }

        test("Red preserves Fireblast away from lethal and spends it for lethal") {
            val safe = seeded()
                .withLandsOnBattlefield(1, "Mountain", 6)
                .withCardInHand(1, "Fireblast")
                .withLifeTotal(2, 20)
                .build()
            (ai(safe).chooseAction(safe.state) is CastSpell) shouldBe false

            val lethal = seeded()
                .withLandsOnBattlefield(1, "Mountain", 6)
                .withCardInHand(1, "Fireblast")
                .withLifeTotal(2, 4)
                .build()
            val action = ai(lethal).chooseAction(lethal.state).shouldBeInstanceOf<CastSpell>()
            cardName(lethal, action.cardId) shouldBe "Fireblast"
            chosenTargetId(action) shouldBe lethal.player2Id
        }

        test("Red discards Sneaky Snacker when the draw spell will recur it") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Tormenting Voice")
                .withCardInHand(1, "Craw Wurm")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(CardsDrawnThisTurnComponent(1))
            }

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Tormenting Voice"
            val discarded = action.additionalCostPayment?.discardedCards?.single()!!
            cardName(game, discarded) shouldBe "Sneaky Snacker"
        }

        test("Red removes an active Flamebreather before a lower-impact creature") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(1, "Lightning Bolt")
                .withCardOnBattlefield(2, "Kessig Flamebreather")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, chosenTargetId(action)!!) shouldBe "Kessig Flamebreather"
        }
    }
}
