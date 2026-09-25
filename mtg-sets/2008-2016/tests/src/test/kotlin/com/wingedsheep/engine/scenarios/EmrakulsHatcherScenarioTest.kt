package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Synthetic exact-card scenarios; no frozen experimental deck or official seed is used. */
class EmrakulsHatcherScenarioTest : ScenarioTestBase() {
    private fun TestGame.settle() {
        resolveStack().forEach { it.error shouldBe null }
        state.stack.isEmpty() shouldBe true
        hasPendingDecision() shouldBe false
    }

    private fun TestGame.assertSpawns(playerNumber: Int) {
        val player = if (playerNumber == 1) player1Id else player2Id
        val tokens = findPermanents("Eldrazi Spawn")
        tokens shouldHaveSize 3
        tokens.forEach { token ->
            state.getEntity(token)?.get<TokenComponent>() shouldBe TokenComponent
            state.getEntity(token)?.get<CardComponent>()?.ownerId shouldBe player
            state.projectedState.getController(token) shouldBe player
            state.projectedState.getPower(token) shouldBe 0
            state.projectedState.getToughness(token) shouldBe 1
            state.projectedState.getColors(token) shouldBe emptySet()
            state.projectedState.getSubtypes(token) shouldBe setOf("Eldrazi", "Spawn")
        }
    }

    init {
        test("entry creates the exact colorless Spawn count for its controller") {
            val game = scenario().withRngSeed(0x48415443484552L).withPlayers("Opponent", "Caster")
                .withCardInHand(2, "Emrakul's Hatcher")
                .withLandsOnBattlefield(2, "Mountain", 5)
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(2, "Emrakul's Hatcher").error shouldBe null
            game.findPermanents("Eldrazi Spawn") shouldHaveSize 0
            game.settle()
            game.assertSpawns(2)
            val body = game.findPermanent("Emrakul's Hatcher")!!
            game.state.projectedState.getPower(body) shouldBe 3
            game.state.projectedState.getToughness(body) shouldBe 3
            game.state.projectedState.getColors(body) shouldBe setOf("RED")
        }

        test("newly created Spawn sacrifice immediately for usable colorless mana without using the stack") {
            val game = scenario().withRngSeed(0x48415443484552L).withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Emrakul's Hatcher")
                .withCardInHand(1, "Sol Ring")
                .withLandsOnBattlefield(1, "Mountain", 5)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Emrakul's Hatcher").error shouldBe null
            game.settle()
            game.assertSpawns(1)
            val tokens = game.findPermanents("Eldrazi Spawn").toList()
            val ability = cardRegistry.getCard("Eldrazi Spawn")!!.activatedAbilities.single().id
            tokens.forEachIndexed { index, token ->
                game.execute(ActivateAbility(game.player1Id, token, ability)).error shouldBe null
                game.state.stack.isEmpty() shouldBe true
                game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe index + 1
            }
            game.findPermanents("Eldrazi Spawn") shouldHaveSize 0
            game.castSpell(1, "Sol Ring").error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 2
            game.settle()
            game.findPermanent("Sol Ring") shouldNotBe null
        }

        test("the entry trigger still creates Spawn after its source dies in response") {
            val game = scenario().withRngSeed(0x48415443484552L).withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Emrakul's Hatcher")
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 5)
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Emrakul's Hatcher").error shouldBe null
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            val body = game.findPermanent("Emrakul's Hatcher")!!
            game.findPermanents("Eldrazi Spawn") shouldHaveSize 0
            game.state.stack.size shouldBe 1
            game.passPriority().error shouldBe null
            game.castSpell(2, "Lightning Bolt", body).error shouldBe null
            game.settle()
            game.isInGraveyard(1, "Emrakul's Hatcher") shouldBe true
            game.assertSpawns(1)
        }

        test("countering the creature spell creates no Spawn because it never enters") {
            val game = scenario().withRngSeed(0x48415443484552L).withPlayers("Caster", "Counterspell")
                .withCardInHand(1, "Emrakul's Hatcher")
                .withCardInHand(2, "Counterspell")
                .withLandsOnBattlefield(1, "Mountain", 5)
                .withLandsOnBattlefield(2, "Island", 2)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Emrakul's Hatcher").error shouldBe null
            val bodySpell = game.state.stack.single()
            game.passPriority().error shouldBe null
            val counter = game.state.getHand(game.player2Id).single {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Counterspell"
            }
            game.execute(CastSpell(game.player2Id, counter, listOf(ChosenTarget.Spell(bodySpell)))).error shouldBe null
            game.settle()
            game.findPermanent("Emrakul's Hatcher") shouldBe null
            game.isInGraveyard(1, "Emrakul's Hatcher") shouldBe true
            game.findPermanents("Eldrazi Spawn") shouldHaveSize 0
        }
    }
}
