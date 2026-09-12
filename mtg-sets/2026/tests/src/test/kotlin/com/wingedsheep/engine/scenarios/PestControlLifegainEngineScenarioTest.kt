package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Deterministic cross-card coverage for the Project Pest Control lifegain engine. */
class PestControlLifegainEngineScenarioTest : ScenarioTestBase() {
    private fun counters(game: TestGame, entity: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(entity)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        test("Warden and Lumaret create separate events for Researcher Mascot and Blight-Priest") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Bogwater Lumaret")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(1, "Marauding Blight-Priest")
                .withCardInHand(1, "Carrier Thrall")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val researcher = game.findPermanent("Blood Researcher")!!
            val mascot = game.findPermanent("Pest Mascot")!!
            val life = game.getLifeTotal(1)
            val opponentLife = game.getLifeTotal(2)
            game.castSpell(1, "Carrier Thrall").error shouldBe null
            game.resolveStack()

            game.getLifeTotal(1) shouldBe life + 2
            counters(game, researcher) shouldBe 2
            counters(game, mascot) shouldBe 2
            game.getLifeTotal(2) shouldBe opponentLife - 2
        }

        test("multiple Wardens and Lumarets produce four independent life-gain events") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Bogwater Lumaret")
                .withCardOnBattlefield(1, "Bogwater Lumaret")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardInHand(1, "Ornithopter")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val researcher = game.findPermanent("Blood Researcher")!!
            val life = game.getLifeTotal(1)
            game.castSpell(1, "Ornithopter").error shouldBe null
            game.resolveStack()

            game.getLifeTotal(1) shouldBe life + 4
            counters(game, researcher) shouldBe 4
        }

        test("Essence Warden sees an opponent creature entry while Lumaret does not") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Bogwater Lumaret")
                .withCardInHand(2, "Ornithopter")
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val life = game.getLifeTotal(1)
            game.castSpell(2, "Ornithopter").error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe life + 1
        }

        test("each Weather the Storm copy is a separate life-gain event") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(1, "Marauding Blight-Priest")
                .withCardInHand(1, "Ornithopter")
                .withCardInHand(1, "Ornithopter")
                .withCardInHand(1, "Weather the Storm")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Ornithopter").error shouldBe null
            game.resolveStack()
            game.castSpell(1, "Ornithopter").error shouldBe null
            game.resolveStack()
            val researcher = game.findPermanent("Blood Researcher")!!
            val mascot = game.findPermanent("Pest Mascot")!!
            val life = game.getLifeTotal(1)
            val opponentLife = game.getLifeTotal(2)

            game.castSpell(1, "Weather the Storm").error shouldBe null
            game.resolveStack()

            game.getLifeTotal(1) shouldBe life + 9
            counters(game, researcher) shouldBe 3
            counters(game, mascot) shouldBe 3
            game.getLifeTotal(2) shouldBe opponentLife - 3
        }

        test("Carrier death makes one Scion whose entry gains life, but its mana sacrifice does not") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Bogwater Lumaret")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val thrall = game.findPermanent("Carrier Thrall")!!
            val researcher = game.findPermanent("Blood Researcher")!!
            val life = game.getLifeTotal(1)
            game.castSpell(1, "Lightning Bolt", thrall).error shouldBe null
            game.resolveStack()

            val scion = game.findPermanent("Eldrazi Scion")!!
            game.findPermanents("Eldrazi Scion").size shouldBe 1
            game.getLifeTotal(1) shouldBe life + 2
            counters(game, researcher) shouldBe 2

            val scionAbility = cardRegistry.getCard("Eldrazi Scion")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, scion, scionAbility)).error shouldBe null
            game.resolveStack()

            game.findPermanent("Eldrazi Scion") shouldBe null
            game.getLifeTotal(1) shouldBe life + 2
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 1
        }
    }
}
