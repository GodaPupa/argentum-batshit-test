package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/** Deterministic seed-free component fixtures; not Phase 2 sampled gameplay. */
class WrithingChrysalisScenarioTest : ScenarioTestBase() {
    private fun plusOne(game: TestGame, id: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        test("cast trigger creates two Spawn and each later Spawn sacrifice grows Chrysalis once") {
            val game = scenario()
                .withPlayers("Manual fixture", "Opponent")
                .withCardInHand(1, "Writhing Chrysalis")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLandsOnBattlefield(1, "Wastes", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Writhing Chrysalis").error shouldBe null
            game.resolveStack()

            val chrysalis = game.findPermanent("Writhing Chrysalis")!!
            game.state.projectedState.getPower(chrysalis) shouldBe 2
            game.state.projectedState.getToughness(chrysalis) shouldBe 3
            game.state.projectedState.getColors(chrysalis) shouldBe emptySet()
            game.state.projectedState.hasKeyword(chrysalis, Keyword.REACH.name) shouldBe true
            plusOne(game, chrysalis) shouldBe 0

            val spawns = game.findPermanents("Eldrazi Spawn")
            spawns shouldHaveSize 2
            val ability = PredefinedTokens.EldraziSpawn.activatedAbilities.single().id

            spawns.forEachIndexed { index, spawn ->
                game.execute(ActivateAbility(game.player1Id, spawn, ability)).error shouldBe null
                game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe index + 1
                game.resolveStack()
                plusOne(game, chrysalis) shouldBe index + 1
            }

            game.findPermanents("Eldrazi Spawn") shouldHaveSize 0
            game.state.projectedState.getPower(chrysalis) shouldBe 4
            game.state.projectedState.getToughness(chrysalis) shouldBe 5
        }

        test("the cast trigger survives countering the Chrysalis spell and still creates two Spawn") {
            val game = scenario()
                .withPlayers("Manual fixture", "Opponent")
                .withCardInHand(1, "Writhing Chrysalis")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLandsOnBattlefield(1, "Wastes", 2)
                .withCardInHand(2, "Counterspell")
                .withLandsOnBattlefield(2, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Writhing Chrysalis").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Writhing Chrysalis").error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Writhing Chrysalis") shouldBe true
            game.findPermanent("Writhing Chrysalis") shouldBe null
            game.findPermanents("Eldrazi Spawn") shouldHaveSize 2
        }
    }
}
