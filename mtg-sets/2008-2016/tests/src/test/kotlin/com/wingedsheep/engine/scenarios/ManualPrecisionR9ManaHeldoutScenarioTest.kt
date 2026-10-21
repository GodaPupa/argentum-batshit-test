package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * R9 INDEPENDENT held-out source qualification. No changes to R8 production paths.
 * Uses actual card definitions, ActionProcessor and ManaSolver, not fabricated events.
 */
class ManualPrecisionR9ManaHeldoutScenarioTest : ScenarioTestBase() {
    init {
        test("generic-only spell uses Confluence printed life cost exactly once") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mana Confluence")
                .withCardInHand(1, "Sol Ring")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val land = game.findPermanent("Mana Confluence")!!
            val life = game.getLifeTotal(1)
            game.castSpell(1, "Sol Ring").error shouldBe null
            game.getLifeTotal(1) shouldBe life - 1
            game.state.getEntity(land)?.has<TappedComponent>() shouldBe true
        }

        test("available Forest pays green creature spell without unnecessarily spending Confluence life") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mana Confluence")
                .withCardOnBattlefield(1, "Forest")
                .withCardInHand(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val confluence = game.findPermanent("Mana Confluence")!!
            val forest = game.findPermanent("Forest")!!
            val life = game.getLifeTotal(1)
            game.castSpell(1, "Llanowar Elves").error shouldBe null
            game.getLifeTotal(1) shouldBe life
            game.state.getEntity(forest)?.has<TappedComponent>() shouldBe true
            game.state.getEntity(confluence)?.has<TappedComponent>() shouldBe false
        }

        test("mixed-cost Starting Town chooses free colorless mana for generic spell") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Starting Town")
                .withCardInHand(1, "Sol Ring")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.getLifeTotal(1)
            game.castSpell(1, "Sol Ring").error shouldBe null
            game.getLifeTotal(1) shouldBe before
        }

        test("mixed-cost Starting Town pays one life when colored spell needs green") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Starting Town")
                .withCardInHand(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.getLifeTotal(1)
            game.castSpell(1, "Llanowar Elves").error shouldBe null
            game.getLifeTotal(1) shouldBe before - 1
        }

        test("explicit nonspell source selection can exclude available Confluence without life cost") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mana Confluence")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Mountain")
                .withCardOnBattlefield(1, "Temur Sabertooth")
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.getLifeTotal(1)
            val confluence = game.findPermanent("Mana Confluence")!!
            val forest = game.findPermanent("Forest")!!
            val mountain = game.findPermanent("Mountain")!!
            val sabertooth = game.findPermanent("Temur Sabertooth")!!
            val ability = cardRegistry.getCard("Temur Sabertooth")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(
                game.player1Id, sabertooth, ability,
                paymentStrategy = PaymentStrategy.Explicit(listOf(forest, mountain))
            )).error shouldBe null
            game.getLifeTotal(1) shouldBe before
            game.state.getEntity(confluence)?.has<TappedComponent>() shouldBe false
            game.state.getEntity(forest)?.has<TappedComponent>() shouldBe true
            game.state.getEntity(mountain)?.has<TappedComponent>() shouldBe true
        }

        test("two distinct Confluence mana-source activations each cost exactly one life") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withLandsOnBattlefield(1, "Mana Confluence", 2)
                .withCardOnBattlefield(1, "Temur Sabertooth")
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val sources = game.state.getBattlefield().filter {
                game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Mana Confluence"
            }
            sources.size shouldBe 2
            val before = game.getLifeTotal(1)
            val sabertooth = game.findPermanent("Temur Sabertooth")!!
            val ability = cardRegistry.getCard("Temur Sabertooth")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, sabertooth, ability)).error shouldBe null
            game.getLifeTotal(1) shouldBe before - 2
            sources.forEach { game.state.getEntity(it)?.has<TappedComponent>() shouldBe true }
        }

        test("one life cannot finance two Confluence payments on a single nonspell activation") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withLifeTotal(1, 1)
                .withLandsOnBattlefield(1, "Mana Confluence", 2)
                .withCardOnBattlefield(1, "Temur Sabertooth")
                .withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val sources = game.state.getBattlefield().filter {
                game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Mana Confluence"
            }
            val sabertooth = game.findPermanent("Temur Sabertooth")!!
            val ability = cardRegistry.getCard("Temur Sabertooth")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, sabertooth, ability)).error shouldNotBe null
            game.getLifeTotal(1) shouldBe 1
            sources.forEach { game.state.getEntity(it)?.has<TappedComponent>() shouldBe false }
        }
    }
}
