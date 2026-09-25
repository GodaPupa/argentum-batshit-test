package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mmq.cards.ChoMannosBlessing
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class ChoMannosBlessingScenarioTest : ScenarioTestBase() {
    init {
        test("chosen-color protection removes another matching Aura but preserves Cho-Manno's Blessing") {
            val game = scenario()
                .withPlayers("Veteran fixture", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")
                .withCardInHand(1, "Cho-Manno's Blessing")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val creature = game.findPermanent("Grizzly Bears")!!
            val cast = game.castSpell(1, "Cho-Manno's Blessing", creature)
            withClue("the Aura cast should be legal") { cast.error shouldBe null }

            game.resolveStack()
            val decision = game.state.pendingDecision as ChooseColorDecision
            game.execute(
                SubmitDecision(
                    game.player1Id,
                    ColorChosenResponse(decision.id, Color.WHITE)
                )
            ).error shouldBe null
            game.resolveStack()

            val blessing = game.findPermanent("Cho-Manno's Blessing")!!
            game.state.getEntity(blessing)!!.get<AttachedToComponent>()?.targetId shouldBe creature
            game.state.projectedState.hasKeyword(creature, "PROTECTION_FROM_WHITE") shouldBe true
            game.isOnBattlefield("Cho-Manno's Blessing") shouldBe true
            game.isOnBattlefield("Holy Strength") shouldBe false
            game.isInGraveyard(1, "Holy Strength") shouldBe true
        }

        test("definition retains flash for the accepted flash-Aura timing rail") {
            val game = scenario()
                .withPlayers("Veteran fixture", "Opponent")
                .withCardInHand(1, "Cho-Manno's Blessing")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            ChoMannosBlessing.keywords.contains(Keyword.FLASH) shouldBe true
        }
    }
}
