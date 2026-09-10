package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MaskedVandalScenarioTest : ScenarioTestBase() {
    init {
        test("changeling makes Masked Vandal every creature type") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Masked Vandal")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val vandal = game.findPermanent("Masked Vandal")!!
            game.state.projectedState.hasKeyword(vandal, Keyword.CHANGELING) shouldBe true
            game.state.projectedState.hasSubtype(vandal, "Elf") shouldBe true
            game.state.projectedState.hasSubtype(vandal, "Goblin") shouldBe true
        }

        test("its ETB may exile a creature card to exile an opponent artifact") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Masked Vandal")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Sol Ring")
                .withCardOnBattlefield(2, "Glorious Anthem")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val artifact = game.findPermanent("Sol Ring")!!
            val enchantment = game.findPermanent("Glorious Anthem")!!
            game.castSpell(1, "Masked Vandal").error shouldBe null
            game.resolveStack()

            val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
            val legalTargets = targetDecision.legalTargets.flatten()
            (artifact in legalTargets) shouldBe true
            (enchantment in legalTargets) shouldBe true
            game.selectTargets(listOf(artifact)).error shouldBe null

            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(game.findCardsInGraveyard(1, "Grizzly Bears")).error shouldBe null
            game.resolveStack()

            game.isInExile(2, "Sol Ring") shouldBe true
            game.isInExile(1, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Glorious Anthem") shouldBe true
        }

        test("without a creature card in its controller's graveyard the target is not exiled") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Masked Vandal")
                .withCardOnBattlefield(2, "Sol Ring")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val artifact = game.findPermanent("Sol Ring")!!
            game.castSpell(1, "Masked Vandal").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
            game.selectTargets(listOf(artifact)).error shouldBe null
            game.resolveStack()

            game.hasPendingDecision() shouldBe false
            game.isOnBattlefield("Sol Ring") shouldBe true
            game.isInExile(2, "Sol Ring") shouldBe false
        }
    }
}
