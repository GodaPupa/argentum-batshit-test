package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Scenario tests for Not Dead After All. */
class NotDeadAfterAllScenarioTest : ScenarioTestBase() {

    init {
        context("Not Dead After All — return tapped, then crown with a Wicked Role") {
            test("the granted creature dies and comes back tapped wearing a Wicked Role") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Not Dead After All")
                    .withCardInHand(1, "Doom Blade")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                game.castSpell(1, "Not Dead After All", bears)
                game.resolveStack()

                game.castSpell(1, "Doom Blade", bears)
                game.resolveStack()

                withClue("same entity is back on the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                    game.findPermanent("Grizzly Bears") shouldBe bears
                }
                withClue("…tapped") {
                    game.state.getEntity(bears)?.has<TappedComponent>() shouldBe true
                }
                val role = game.findPermanent("Wicked Role")
                withClue("…wearing a Wicked Role") {
                    role shouldNotBe null
                    game.state.getEntity(role!!)?.get<AttachedToComponent>()?.targetId shouldBe bears
                }
                withClue("2/2 Bears + the Wicked Role's +1/+1 = 3/3") {
                    game.state.projectedState.getPower(bears) shouldBe 3
                    game.state.projectedState.getToughness(bears) shouldBe 3
                }
            }

            test("the grant is until end of turn only — a later death is permanent (CR 400.7)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Not Dead After All")
                    .withCardInHand(1, "Doom Blade")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Not Dead After All", bears)
                game.resolveStack()

                withClue("while it is live the grant rides the Bears") {
                    game.state.grantedTriggeredAbilities.any { it.entityId == bears } shouldBe true
                }

                game.castSpell(1, "Doom Blade", bears)
                game.resolveStack()

                withClue("the returned Bears is a new object — the grant did not follow it") {
                    game.state.grantedTriggeredAbilities.any { it.entityId == bears } shouldBe false
                }
            }

            test("Village Rites returns Glasswright as a new prepared permanent with a fresh Craft") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Goblin Glasswright")
                    .withCardInHand(1, "Not Dead After All")
                    .withCardInHand(1, "Village Rites")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Glasswright").error shouldBe null
                game.resolveStack()
                val glasswright = game.findPermanent("Goblin Glasswright")!!
                val originalCopy = game.state.getExile(game.player1Id).single { id ->
                    game.state.getEntity(id)?.get<PreparedSpellCopyComponent>() != null
                }

                game.castSpell(1, "Not Dead After All", glasswright).error shouldBe null
                game.resolveStack()
                game.castSpellWithAdditionalSacrifice(1, "Village Rites", "Goblin Glasswright").error shouldBe null
                withClue("Glasswright dies immediately as the legal additional cost") {
                    game.findPermanent("Goblin Glasswright") shouldBe null
                }
                game.resolveStack()

                withClue("the returned permanent is the new battlefield incarnation") {
                    game.findPermanent("Goblin Glasswright") shouldBe glasswright
                    game.state.grantedTriggeredAbilities.any { it.entityId == glasswright } shouldBe false
                }
                withClue("Not Dead returns it tapped and creates an attached Wicked Role") {
                    game.state.getEntity(glasswright).shouldNotBeNull().has<TappedComponent>() shouldBe true
                    val role = game.findPermanent("Wicked Role").shouldNotBeNull()
                    game.state.getEntity(role)?.get<AttachedToComponent>()?.targetId shouldBe glasswright
                }
                game.state.getEntity(glasswright)?.get<PreparedComponent>() shouldNotBe null
                val freshCopies = game.state.getExile(game.player1Id).filter { id ->
                    val entity = game.state.getEntity(id)
                    entity?.get<CardComponent>()?.name == "Goblin Glasswright" &&
                        entity.get<PreparedSpellCopyComponent>() != null
                }
                withClue("the old incarnation's copy is replaced by exactly one fresh Craft") {
                    freshCopies.size shouldBe 1
                    freshCopies.single() shouldNotBe originalCopy
                }
            }
        }
    }
}
