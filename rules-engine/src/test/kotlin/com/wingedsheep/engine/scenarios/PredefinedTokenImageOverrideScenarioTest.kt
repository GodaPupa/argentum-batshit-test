package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * A predefined token (Treasure, Map, …) carries one canonical printing shared engine-wide, but each
 * set prints its own art. `CreateTreasure`/`CreateMapToken` (and `CreatePredefinedTokenEffect`) take
 * an optional `imageUri` so a card can mint the in-set version; `CreatePredefinedTokenExecutor`
 * resolves `effect.imageUri ?: cardDef.metadata.imageUri`.
 */
class PredefinedTokenImageOverrideScenarioTest : ScenarioTestBase() {

    private val overrideArt = "https://cards.scryfall.io/normal/front/3/d/3dfaedeb-f8ec-4f0e-b243-c850770a86f2.jpg?1783913602"

    private fun tokenImage(game: TestGame, name: String): String? =
        game.findPermanent(name)?.let { game.state.getEntity(it)?.get<CardComponent>()?.imageUri }

    init {
        val defaultTreasure = card("Default Treasure Maker") {
            manaCost = "{0}"; typeLine = "Sorcery"
            spell { effect = Effects.CreateTreasure() }
        }
        val overrideTreasure = card("Override Treasure Maker") {
            manaCost = "{0}"; typeLine = "Sorcery"
            spell { effect = Effects.CreateTreasure(imageUri = overrideArt) }
        }
        cardRegistry.register(listOf(defaultTreasure, overrideTreasure))

        test("no imageUri falls back to the predefined token's own art") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Default Treasure Maker")
                .withCardInLibrary(1, "Default Treasure Maker")
                .build()

            game.castSpell(1, "Default Treasure Maker").error shouldBe null
            game.resolveStack()

            // The predefined Treasure printing in PredefinedTokens.kt.
            tokenImage(game, "Treasure") shouldContain "4837a3f1"
        }

        test("imageUri override wins over the predefined token's art") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Override Treasure Maker")
                .withCardInLibrary(1, "Override Treasure Maker")
                .build()

            game.castSpell(1, "Override Treasure Maker").error shouldBe null
            game.resolveStack()

            tokenImage(game, "Treasure") shouldBe overrideArt
        }
    }
}
