package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.shouldBe

/**
 * Mirkwood Bats — "Flying. Whenever you create or sacrifice a token, each opponent loses 1 life."
 *
 * Regression: the token-creation half never fired. `EventPattern.TokenCreationEvent` was treated as
 * a replacement-effect-only pattern (`matchesTrigger` returned false) and was never indexed as a
 * trigger, so creating a token under your control drained no life — e.g. Horn of Gondor's Soldier.
 *
 * The fix matches the trigger against each token-creation `ZoneChangeEvent` (`fromZone == null`),
 * one per token, so the ability fires once per token created (singular "a token" templating).
 */
class MirkwoodBatsTokenCreationTest : ScenarioTestBase() {

    init {
        val makeOneToken = card("Make One Token") {
            manaCost = "{0}"; typeLine = "Sorcery"
            spell {
                effect = Effects.CreateToken(
                    power = 1, toughness = 1, creatureTypes = setOf("Spirit"), count = 1
                )
            }
        }
        val makeThreeTokens = card("Make Three Tokens") {
            manaCost = "{0}"; typeLine = "Sorcery"
            spell {
                effect = Effects.CreateToken(
                    power = 1, toughness = 1, creatureTypes = setOf("Spirit"), count = 3
                )
            }
        }
        cardRegistry.register(listOf(makeOneToken, makeThreeTokens))

        test("creating one token under your control drains each opponent 1 life") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Mirkwood Bats")
                .withCardInHand(1, "Make One Token")
                .withCardInLibrary(1, "Make One Token")
                .build()

            game.castSpell(1, "Make One Token").error shouldBe null
            game.resolveStack()

            game.findPermanents("Spirit Token").size shouldBe 1
            game.getLifeTotal(2) shouldBe 19
            game.getLifeTotal(1) shouldBe 20
        }

        test("creating three tokens at once drains 3 (fires once per token)") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Mirkwood Bats")
                .withCardInHand(1, "Make Three Tokens")
                .withCardInLibrary(1, "Make One Token")
                .build()

            game.castSpell(1, "Make Three Tokens").error shouldBe null
            game.resolveStack()

            game.findPermanents("Spirit Token").size shouldBe 3
            game.getLifeTotal(2) shouldBe 17
        }

        test("a token created by an OPPONENT does not trigger your Mirkwood Bats") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Mirkwood Bats")
                .withCardInHand(2, "Make One Token")
                .withCardInLibrary(2, "Make One Token")
                .withActivePlayer(2)
                .withPriorityPlayer(2)
                .build()

            game.castSpell(2, "Make One Token").error shouldBe null
            game.resolveStack()

            game.findPermanents("Spirit Token").size shouldBe 1
            // "you create" is the Bats' controller (player 1); player 2's token doesn't trigger it,
            // so no one loses life.
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
        }

        test("no Mirkwood Bats: creating a token drains nothing") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Make One Token")
                .withCardInLibrary(1, "Make One Token")
                .build()

            game.castSpell(1, "Make One Token").error shouldBe null
            game.resolveStack()

            game.getLifeTotal(2) shouldBe 20
        }
    }
}
