package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class IndustrialWasteGate9C20ScenarioTest : ScenarioTestBase() {

    private val drawAbilityId =
        cardRegistry.getCard("Bonder's Ornament")!!.activatedAbilities
            .first { !it.isManaAbility }
            .id

    init {
        test("Bonder's Ornament draws only for players who control a Bonder's Ornament") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardOnBattlefield(1, "Bonder's Ornament", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(2, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val ornament = game.findPermanent("Bonder's Ornament")!!
            val activation = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = ornament,
                    abilityId = drawAbilityId
                )
            )
            withClue("Bonder's Ornament draw ability should activate: ${activation.error}") {
                activation.error shouldBe null
            }
            game.resolveStack()

            game.handSize(1) shouldBe 1
            game.handSize(2) shouldBe 0
        }

        test("Bonder's Ornament draws for both players when both control one") {
            val game = scenario()
                .withPlayers("Monster Tron", "Mirror")
                .withCardOnBattlefield(1, "Bonder's Ornament", summoningSickness = false)
                .withCardOnBattlefield(2, "Bonder's Ornament", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(2, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val ornament = game.state.getBattlefield(game.player1Id)
                .first { it == game.findPermanent("Bonder's Ornament") || true }
            val source = game.state.getBattlefield(game.player1Id).first { id ->
                game.state.getEntity(id)?.let { entity ->
                    entity.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Bonder's Ornament"
                } == true
            }

            val activation = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = source,
                    abilityId = drawAbilityId
                )
            )
            withClue("Bonder's Ornament draw ability should activate: ${activation.error}") {
                activation.error shouldBe null
            }
            game.resolveStack()

            game.handSize(1) shouldBe 1
            game.handSize(2) shouldBe 1
        }
    }
}
