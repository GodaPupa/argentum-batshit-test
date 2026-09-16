package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Paradox Gardens (Secrets of Strixhaven) — a Land.
 *
 * This land enters tapped.
 * {T}: Add {G} or {U}.
 * {2}{G}{U}, {T}: Surveil 1.
 *
 * Verifies the ETB-tapped replacement effect, both mana abilities individually, and the paid
 * surveil ability's full mana-payment-then-decision flow. Written to close a gap found while
 * correcting the Lilysplash experiment's decklist (docs/experiments/lilysplash/): this card had a
 * generated implementation with no scenario test yet, so its actual behavior was unverified before
 * being relied on as a Yavimaya Coast replacement.
 */
class ParadoxGardensScenarioTest : ScenarioTestBase() {

    private val greenAbilityId by lazy {
        // [0] {T}: add {G}, [1] {T}: add {U}, [2] {2}{G}{U},{T}: Surveil 1.
        cardRegistry.getCard("Paradox Gardens")!!.activatedAbilities[0].id
    }
    private val blueAbilityId by lazy {
        cardRegistry.getCard("Paradox Gardens")!!.activatedAbilities[1].id
    }
    private val surveilAbilityId by lazy {
        cardRegistry.getCard("Paradox Gardens")!!.activatedAbilities[2].id
    }

    init {
        context("Paradox Gardens") {

            test("enters tapped when played from hand") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Paradox Gardens")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.execute(
                    PlayLand(game.player1Id, game.findCardsInHand(1, "Paradox Gardens").single())
                ).error shouldBe null

                val gardens = game.findPermanent("Paradox Gardens")!!
                withClue("Paradox Gardens should enter tapped") {
                    game.state.getEntity(gardens)?.has<TappedComponent>() shouldBe true
                }
            }

            test("{T}: Add {G} taps for one green mana") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Paradox Gardens")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gardens = game.findPermanent("Paradox Gardens")!!
                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = gardens, abilityId = greenAbilityId)
                )
                withClue("Tapping for green should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Paradox Gardens produced one green mana") {
                    game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.green shouldBe 1
                }
            }

            test("{T}: Add {U} taps for one blue mana") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Paradox Gardens")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gardens = game.findPermanent("Paradox Gardens")!!
                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = gardens, abilityId = blueAbilityId)
                )
                withClue("Tapping for blue should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Paradox Gardens produced one blue mana") {
                    game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.blue shouldBe 1
                }
            }

            test("{2}{G}{U}, {T}: Surveil 1 pays its composite cost and raises a surveil decision") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Paradox Gardens")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gardens = game.findPermanent("Paradox Gardens")!!
                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = gardens, abilityId = surveilAbilityId)
                )
                withClue("Activating the surveil ability should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
                game.resolveStack()

                withClue("Paying {2}{G}{U} taps Paradox Gardens itself for the ability's own {T} cost") {
                    game.state.getEntity(gardens)?.has<TappedComponent>() shouldBe true
                }
                withClue("Surveil 1 should raise a card-selection decision") {
                    (game.getPendingDecision() is SelectCardsDecision) shouldBe true
                }
                game.skipSelection().error shouldBe null
            }
        }
    }
}
