package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Focused regressions for the frozen Batshit Economics vs Grixis Affinity policy gate. */
class GrixisAffinityPolicyRegressionTest : ScenarioTestBase() {
    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId) = game.state.getEntity(id)?.get<CardComponent>()?.name
    private fun seeded() = scenario().withPlayers().withRngSeed(0xAFF1_9178L)

    init {
        test("reduced-rate conditional burn is not spent on speculative face damage") {
            val game = seeded().withLifeTotal(2, 6)
                .withCardInHand(1, "Galvanic Blast").withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(1, "Vault of Whispers")
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("reduced-rate conditional burn remains legal when conservative visible reach is lethal") {
            val game = seeded().withLifeTotal(2, 4)
                .withCardInHand(1, "Galvanic Blast").withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Galvanic Blast"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("generic creature presence alone does not make a graveyard worth a Spellbomb") {
            val game = seeded().withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInGraveyard(2, "Kessig Flamebreather")
                .withCardInGraveyard(2, "Shambling Ghast")
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("Spellbomb answers concrete opposing recursion already on the stack") {
            val game = seeded().withActivePlayer(2)
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInHand(2, "Unearth")
                .withCardInGraveyard(2, "Kessig Flamebreather")
                .withLandsOnBattlefield(2, "Swamp", 1)
                .build()

            game.castSpellTargetingGraveyardCard(2, "Unearth", 2, "Kessig Flamebreather").error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Nihil Spellbomb"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("Wellspring draw alone does not justify a self-only Shaman sweep") {
            val game = seeded().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(1, "Mons's Goblin Raiders")
                .withCardInLibrary(1, "Forest")
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("Shaman still cashes Wellspring when the sweep hits opposing creatures") {
            val game = seeded().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                .withCardOnBattlefield(2, "Greedy Freebooter")
                .withCardInLibrary(1, "Forest")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Krark-Clan Shaman"
            name(game, action.costPayment!!.sacrificedPermanents.single()) shouldBe "Ichor Wellspring"
        }
    }
}
