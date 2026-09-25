package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class NyleasDiscipleScenarioTest : ScenarioTestBase() {
    init {
        test("counts own green mana symbols including self and hybrid but not opponent or land text") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Nylea's Disciple")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardOnBattlefield(1, "Llanowar Elves")
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInHand(1, "Giant Growth")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Nylea's Disciple").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            // Disciple GG + Elves G + Elite G/W. Forest text, generic costs and other zones do not count.
            game.getLifeTotal(1) shouldBe 24
            game.getLifeTotal(2) shouldBe 20
            val disciple = game.findPermanent("Nylea's Disciple")!!
            game.state.projectedState.getPower(disciple) shouldBe 3
            game.state.projectedState.getToughness(disciple) shouldBe 3
        }

        listOf(false, true).forEach { otherGreenPermanent ->
            test("removing Disciple before its trigger resolves leaves devotion ${if (otherGreenPermanent) 1 else 0}") {
                val setup = scenario().withPlayers()
                    .withCardInHand(1, "Nylea's Disciple")
                    .withCardInHand(2, "Unsummon")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withLandsOnBattlefield(2, "Island", 1)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                if (otherGreenPermanent) setup.withCardOnBattlefield(1, "Llanowar Elves")
                val game = setup.build()
                game.castSpell(1, "Nylea's Disciple").error shouldBe null
                game.passPriority().error shouldBe null
                game.passPriority().error shouldBe null
                game.state.stack.size shouldBe 1
                game.getLifeTotal(1) shouldBe 20
                val disciple = game.findPermanent("Nylea's Disciple")!!
                game.passPriority().error shouldBe null
                game.castSpell(2, "Unsummon", disciple).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.isInHand(1, "Nylea's Disciple") shouldBe true
                game.getLifeTotal(1) shouldBe if (otherGreenPermanent) 21 else 20
                game.getLifeTotal(2) shouldBe 20
            }
        }
    }
}
