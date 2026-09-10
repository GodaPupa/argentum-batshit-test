package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Cards revealed into a hand, or returned to a hand from a public zone, stay visible to
 * opponents until the hand's owner *plays* a card with the same name — the way MTGO/Arena
 * keeps showing a known card until it can no longer be told apart from a freshly played one.
 *
 * Exercises [com.wingedsheep.engine.mechanics.RevealedInHandTracker], which manages the
 * [RevealedToComponent] lifecycle for the [Zone.HAND] over each action's emitted events.
 */
class RevealedHandVisibilityScenarioTest : ScenarioTestBase() {

    /** Players whom [cardId] is currently revealed to. */
    private fun GameState.revealedTo(cardId: EntityId): Set<EntityId> =
        getEntity(cardId)?.get<RevealedToComponent>()?.playerIds ?: emptySet()

    /** The id of the (first) card named [name] in [playerId]'s hand. */
    private fun GameState.handCard(playerId: EntityId, name: String): EntityId =
        getHand(playerId).first { getEntity(it)?.get<CardComponent>()?.name == name }

    init {
        context("revealed / returned cards stay visible to opponents") {

            test("a creature bounced back to its owner's hand becomes visible to the opponent") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Unsummon")
                    .withCardInHand(1, "Forest") // an unrelated hand card to prove scoping
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val secretCard = game.state.handCard(game.player1Id, "Forest")
                val unsummonId = game.state.handCard(game.player1Id, "Unsummon")

                // Player1 casts Unsummon on their own Grizzly Bears, returning it to hand.
                val cast = game.execute(
                    CastSpell(game.player1Id, unsummonId, listOf(ChosenTarget.Permanent(bears)))
                )
                withClue("Unsummon should cast: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Grizzly Bears is back in Player1's hand") {
                    game.state.getHand(game.player1Id) shouldContain bears
                }
                withClue("the bounced creature is now known to the opponent (Player2)") {
                    game.state.revealedTo(bears) shouldContain game.player2Id
                }
                withClue("an unrelated card in the same hand stays hidden — only the bounced card is shown") {
                    (game.player2Id in game.state.revealedTo(secretCard)).shouldBeFalse()
                }

                // End to end: Player2's view of Player1's hand actually exposes the card face-up.
                val opponentView = game.getClientState(2)
                val p1Hand = opponentView.zones.first {
                    it.zoneId.ownerId == game.player1Id && it.zoneId.zoneType == Zone.HAND
                }
                withClue("opponent's masked view exposes the bounced card id and full card data") {
                    p1Hand.cardIds shouldContain bears
                    opponentView.cards.containsKey(bears).shouldBeTrue()
                    opponentView.cards[bears]!!.name shouldBe "Grizzly Bears"
                }
                withClue("opponent's view does NOT expose the unrelated hidden hand card") {
                    (secretCard in p1Hand.cardIds).shouldBeFalse()
                }
            }

            test("a creature returned from the graveyard to hand becomes visible to the opponent") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Raise Dead")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.state.getGraveyard(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Grizzly Bears"
                }
                val raiseDeadId = game.state.handCard(game.player1Id, "Raise Dead")
                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        raiseDeadId,
                        listOf(ChosenTarget.Card(bears, game.player1Id, Zone.GRAVEYARD)),
                    )
                )
                withClue("Raise Dead should cast: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Grizzly Bears returned to Player1's hand") {
                    game.state.getHand(game.player1Id) shouldContain bears
                }
                withClue("a card returned from the public graveyard stays known to the opponent") {
                    game.state.revealedTo(bears) shouldContain game.player2Id
                }
            }

            test("playing a same-named card from hand forgets the revealed copy") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Unsummon")
                    .withCardInHand(1, "Grizzly Bears")     // the card we will cast (never revealed)
                    .withCardOnBattlefield(1, "Grizzly Bears") // the card we will bounce (revealed)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castCopy = game.state.handCard(game.player1Id, "Grizzly Bears")
                val bouncedCopy = game.findPermanent("Grizzly Bears")!!
                val unsummonId = game.state.handCard(game.player1Id, "Unsummon")

                // Bounce one copy so it becomes a known card in hand.
                game.execute(CastSpell(game.player1Id, unsummonId, listOf(ChosenTarget.Permanent(bouncedCopy))))
                game.resolveStack()
                withClue("the bounced copy is known to the opponent before any same-named card is played") {
                    game.state.revealedTo(bouncedCopy) shouldContain game.player2Id
                }

                // Now play a DIFFERENT Grizzly Bears from hand. The known copy can no longer be told
                // apart from the one just cast, so the opponent forgets it.
                val cast = game.execute(CastSpell(game.player1Id, castCopy, emptyList()))
                withClue("casting Grizzly Bears should succeed: ${cast.error}") { cast.error shouldBe null }

                withClue("the still-in-hand revealed copy is forgotten once a same-named card is played") {
                    game.state.getHand(game.player1Id) shouldContain bouncedCopy
                    (game.player2Id in game.state.revealedTo(bouncedCopy)).shouldBeFalse()
                }
            }

            test("a card searched from the library and revealed into hand is visible to the opponent") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Lay of the Land")
                    .withCardInLibrary(1, "Forest")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fetched = game.state.getLibrary(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Forest"
                }

                val layId = game.state.handCard(game.player1Id, "Lay of the Land")
                game.execute(CastSpell(game.player1Id, layId, emptyList()))
                game.resolveStack()
                // Resolve the search selection (pick the Forest) if one is pending.
                if (game.getPendingDecision() != null) {
                    game.selectCards(listOf(fetched))
                    game.resolveStack()
                }

                withClue("the revealed land is now in Player1's hand") {
                    game.state.getHand(game.player1Id) shouldContain fetched
                }
                withClue("a card revealed as it entered the hand is known to the opponent") {
                    game.state.revealedTo(fetched) shouldContain game.player2Id
                }
            }
        }
    }
}
