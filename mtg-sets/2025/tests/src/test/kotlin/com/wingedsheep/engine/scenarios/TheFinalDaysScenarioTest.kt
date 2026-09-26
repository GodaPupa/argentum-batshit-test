package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * The Final Days ({2}{B}{B} Sorcery, Flashback {4}{B}{B}):
 *   "Create two tapped 2/2 black Horror creature tokens. If this spell was cast from a graveyard,
 *    instead create X of those tokens, where X is the number of creature cards in your graveyard."
 *
 * Proves the [com.wingedsheep.sdk.scripting.values.DynamicAmount.Conditional] token count gated on
 * [com.wingedsheep.sdk.dsl.Conditions.WasCastFromGraveyard] (flat 2 on a normal cast, the graveyard
 * creature count on a flashback cast) and that the dynamic-count `CreateToken` honors `tapped = true`.
 */
class TheFinalDaysScenarioTest : ScenarioTestBase() {
    init {
        test("normal cast from hand creates exactly two tapped 2/2 black Horror tokens") {
            val game = scenario()
                .withPlayers()
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withCardInHand(1, "The Final Days")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .build()

            game.castSpell(1, "The Final Days").error shouldBe null
            game.resolveStack()

            val tokens = game.findPermanents("Horror Token")
            tokens shouldHaveSize 2
            tokens.forEach { id ->
                game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
            }
        }

        test("flashback cast from graveyard creates one token per creature card in the graveyard") {
            val game = scenario()
                .withPlayers()
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withCardInGraveyard(1, "The Final Days")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 6)
                .build()

            val spell = game.findCardsInGraveyard(1, "The Final Days").single()
            val cast = game.execute(CastSpell(
                playerId = game.player1Id,
                cardId = spell,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.FLASHBACK
            ))
            cast.error shouldBe null
            game.state.getEntity(spell)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe
                AlternativeCostType.FLASHBACK
            game.findAllPermanents("Swamp").count {
                game.state.getEntity(it)!!.has<TappedComponent>()
            } shouldBe 6
            game.resolveStack()

            // Three creature cards remained in the graveyard while The Final Days was on the stack.
            val tokens = game.findPermanents("Horror Token")
            tokens shouldHaveSize 3
            tokens.forEach { id ->
                game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
            }

            // Flashback exiles the spell after resolution.
            game.isInExile(1, "The Final Days") shouldBe true
        }
    }
}
