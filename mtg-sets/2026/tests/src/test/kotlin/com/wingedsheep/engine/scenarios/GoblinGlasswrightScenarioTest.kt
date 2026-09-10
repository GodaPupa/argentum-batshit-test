package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Focused rules coverage for Goblin Glasswright // Craft with Pride. */
class GoblinGlasswrightScenarioTest : ScenarioTestBase() {

    private fun TestGame.preparedCopies(): List<EntityId> =
        state.getExile(player1Id).filter { id ->
            val entity = state.getEntity(id)
            entity?.get<CardComponent>()?.name == "Goblin Glasswright" &&
                entity.get<PreparedSpellCopyComponent>() != null
        }

    private fun TestGame.topTriggerSource(): String? = state.stack.lastOrNull()?.let { id ->
        state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()?.sourceName
    }

    private fun TestGame.topCardName(): String? = state.stack.lastOrNull()?.let { id ->
        state.getEntity(id)?.get<CardComponent>()?.name
    }

    private fun TestGame.resolveTop() {
        passPriority().error shouldBe null
        passPriority().error shouldBe null
    }

    init {
        context("Goblin Glasswright — prepare") {
            test("each battlefield incarnation has exactly one castable Craft with Pride") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Goblin Glasswright")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Glasswright").error shouldBe null
                game.resolveStack()

                val glasswright = game.findPermanent("Goblin Glasswright")!!
                game.state.getEntity(glasswright)?.get<PreparedComponent>() shouldNotBe null

                val copies = game.preparedCopies()
                withClue("prepared Glasswright should own exactly one spell copy in exile") {
                    copies.shouldHaveSize(1)
                }
                val copy = copies.single()
                val craftActions = game.getLegalActions(1).filter { legal ->
                    val action = legal.action
                    action is CastSpell && action.cardId == copy
                }
                withClue("that copy should have exactly one legal cast action") {
                    craftActions.shouldHaveSize(1)
                }
                withClue("Craft with Pride should be castable from exile for {R}") {
                    craftActions.single().sourceZone shouldBe "EXILE"
                    craftActions.single().manaCostString shouldBe "{R}"
                    craftActions.single().isAffordable shouldBe true
                }

                val cast = game.execute(craftActions.single().action as CastSpell)
                cast.error shouldBe null
                game.resolveStack()

                game.findPermanents("Treasure").shouldHaveSize(1)
                game.state.getEntity(glasswright)?.get<PreparedComponent>() shouldBe null
                game.preparedCopies().shouldHaveSize(0)
            }

            test("the Batshit sacrifice loop exposes every cast, token, death, return, and draw transition") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kessig Flamebreather", summoningSickness = false)
                    .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                    .withCardInHand(1, "Goblin Glasswright")
                    .withCardInHand(1, "Not Dead After All")
                    .withCardInHand(1, "Village Rites")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Swamp")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Glasswright").error shouldBe null
                game.resolveStack()
                val glasswright = game.findPermanent("Goblin Glasswright")!!
                val firstCopy = game.preparedCopies().single()

                val craft = game.getLegalActions(1).single { legal ->
                    val action = legal.action
                    action is CastSpell && action.cardId == firstCopy
                }.action as CastSpell
                game.execute(craft).error shouldBe null
                withClue("Craft's cast trigger is above the unresolved spell") {
                    game.topTriggerSource() shouldBe "Kessig Flamebreather"
                    game.findPermanent("Treasure") shouldBe null
                }
                game.resolveTop()
                withClue("Flamebreather resolves for exactly one damage") {
                    game.getLifeTotal(2) shouldBe 19
                    game.findPermanent("Treasure") shouldBe null
                }

                withClue("Craft is now the top spell") { game.topCardName() shouldBe "Goblin Glasswright" }
                game.resolveTop()
                withClue("Craft creates the Treasure before Bats' trigger resolves") {
                    game.findPermanents("Treasure").shouldHaveSize(1)
                    game.getLifeTotal(2) shouldBe 19
                    game.topTriggerSource() shouldBe "Mirkwood Bats"
                }
                game.resolveTop()
                withClue("Bats drains exactly one for Treasure creation") {
                    game.getLifeTotal(2) shouldBe 18
                }

                game.castSpell(1, "Not Dead After All", glasswright).error shouldBe null
                withClue("Not Dead's cast creates its own Flamebreather trigger") {
                    game.topTriggerSource() shouldBe "Kessig Flamebreather"
                }
                game.resolveTop()
                game.getLifeTotal(2) shouldBe 17
                withClue("Not Dead remains beneath the resolved cast trigger") {
                    game.topCardName() shouldBe "Not Dead After All"
                }
                game.resolveTop()
                withClue("the temporary dies ability is now attached to Glasswright") {
                    game.state.grantedTriggeredAbilities.any { it.entityId == glasswright } shouldBe true
                }

                val treasure = game.findPermanent("Treasure")!!
                val treasureAbility = cardRegistry.getCard("Treasure")!!.script.activatedAbilities.single()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = treasure,
                        abilityId = treasureAbility.id,
                        manaColorChoice = Color.BLACK,
                    )
                ).error shouldBe null
                withClue("the Craft Treasure is sacrificed explicitly for Village Rites' black mana") {
                    game.findPermanent("Treasure") shouldBe null
                    game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.black shouldBe 1
                    game.getLifeTotal(2) shouldBe 17
                    game.topTriggerSource() shouldBe "Mirkwood Bats"
                }
                game.resolveStack()
                withClue("Bats drains exactly one for sacrificing the Treasure") {
                    game.getLifeTotal(2) shouldBe 16
                }

                val handBeforeRites = game.handSize(1)
                val libraryBeforeRites = game.librarySize(1)
                game.castSpellWithAdditionalSacrifice(1, "Village Rites", "Goblin Glasswright").error shouldBe null
                withClue("Village Rites sacrifices Glasswright as a cost and removes its old Craft") {
                    game.findPermanent("Goblin Glasswright") shouldBe null
                    game.preparedCopies().shouldHaveSize(0)
                    game.handSize(1) shouldBe handBeforeRites - 1
                }

                var flamebreatherResolved = false
                var returnResolved = false
                var wickedRoleDrainResolved = false
                val verifyReturnedGlasswright = {
                    withClue("Glasswright returns tapped, wearing Wicked, and prepared afresh") {
                        game.findPermanent("Goblin Glasswright") shouldBe glasswright
                        game.state.getEntity(glasswright)?.has<TappedComponent>() shouldBe true
                        val role = game.findPermanent("Wicked Role")!!
                        game.state.getEntity(role)?.get<AttachedToComponent>()?.targetId shouldBe glasswright
                        game.state.getEntity(glasswright)?.get<PreparedComponent>() shouldNotBe null
                        game.preparedCopies().single() shouldNotBe firstCopy
                    }
                }
                while (!flamebreatherResolved || !returnResolved || !wickedRoleDrainResolved) {
                    when (val source = game.topTriggerSource()) {
                        "Kessig Flamebreather" -> {
                            val lifeBefore = game.getLifeTotal(2)
                            game.resolveTop()
                            game.getLifeTotal(2) shouldBe lifeBefore - 1
                            flamebreatherResolved = true
                        }

                        "Mirkwood Bats" -> {
                            withClue("this Bats trigger is from creating the Wicked Role") {
                                game.findPermanent("Wicked Role") shouldNotBe null
                            }
                            if (!returnResolved) {
                                verifyReturnedGlasswright()
                                returnResolved = true
                            }
                            wickedRoleDrainResolved shouldBe false
                            val lifeBefore = game.getLifeTotal(2)
                            game.resolveTop()
                            game.getLifeTotal(2) shouldBe lifeBefore - 1
                            wickedRoleDrainResolved = true
                        }

                        null -> error("expected a triggered ability above Village Rites")
                        else -> {
                            withClue("the remaining trigger should be Not Dead's return ability, not $source") {
                                returnResolved shouldBe false
                            }
                            val lifeBefore = game.getLifeTotal(2)
                            game.resolveTop()
                            game.getLifeTotal(2) shouldBe lifeBefore
                            verifyReturnedGlasswright()
                            returnResolved = true
                        }
                    }
                }

                withClue("Rites' cast, Treasure sacrifice, and Role creation each caused one life loss") {
                    game.getLifeTotal(2) shouldBe 14
                }
                withClue("Village Rites is still waiting below those triggers") {
                    game.topCardName() shouldBe "Village Rites"
                }
                game.resolveTop()
                withClue("Village Rites alone draws two cards") {
                    game.librarySize(1) shouldBe libraryBeforeRites - 2
                    game.handSize(1) shouldBe handBeforeRites + 1
                }
            }
        }
    }
}
