package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Exact-card qualification for Jack-o'-Lantern (MID #254). */
class JackOLanternScenarioTest : ScenarioTestBase() {

    init {
        test("battlefield ability exiles up to one graveyard card and draws") {
            val game = scenario()
                .withPlayers("Lantern controller", "Opponent")
                .withCardOnBattlefield(1, "Jack-o'-Lantern")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Hill Giant")
                .withCardInGraveyard(2, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val source = game.findPermanent("Jack-o'-Lantern")!!
            val target = game.findCardsInGraveyard(2, "Grizzly Bears").single()
            val ability = cardRegistry.getCard("Jack-o'-Lantern")!!.activatedAbilities[0]
            val handBefore = game.handSize(1)

            val result = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = source,
                    abilityId = ability.id,
                    targets = listOf(ChosenTarget.Card(target, game.player2Id, Zone.GRAVEYARD)),
                )
            )
            result.error shouldBe null

            // Sacrifice is a cost; the source is in the graveyard before resolution.
            game.isOnBattlefield("Jack-o'-Lantern") shouldBe false
            game.isInGraveyard(1, "Jack-o'-Lantern") shouldBe true

            game.resolveStack()

            game.isInGraveyard(2, "Grizzly Bears") shouldBe false
            game.isInExile(2, "Grizzly Bears") shouldBe true
            game.handSize(1) shouldBe handBefore + 1
        }

        test("battlefield ability may decline its optional graveyard target and still draws") {
            val game = scenario()
                .withPlayers("Lantern controller", "Opponent")
                .withCardOnBattlefield(1, "Jack-o'-Lantern")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val source = game.findPermanent("Jack-o'-Lantern")!!
            val ability = cardRegistry.getCard("Jack-o'-Lantern")!!.activatedAbilities[0]
            val handBefore = game.handSize(1)

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = source,
                    abilityId = ability.id,
                    targets = emptyList(),
                )
            ).error shouldBe null
            game.resolveStack()

            game.handSize(1) shouldBe handBefore + 1
            game.isInGraveyard(1, "Jack-o'-Lantern") shouldBe true
        }

        test("graveyard mana ability exiles Jack-o'-Lantern as a cost and adds a chosen color") {
            val game = scenario()
                .withPlayers("Lantern controller", "Opponent")
                .withCardInGraveyard(1, "Jack-o'-Lantern")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val source = game.findCardsInGraveyard(1, "Jack-o'-Lantern").single()
            val ability = cardRegistry.getCard("Jack-o'-Lantern")!!.activatedAbilities[1]

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = source,
                    abilityId = ability.id,
                )
            ).error shouldBe null

            game.isInGraveyard(1, "Jack-o'-Lantern") shouldBe false
            game.isInExile(1, "Jack-o'-Lantern") shouldBe true

            val decision = game.getPendingDecision().shouldNotBeNull()
            game.submitDecision(ColorChosenResponse(decision.id, Color.BLUE)).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
            pool.shouldNotBeNull()
            pool.blue shouldBe 1
            pool.total shouldBe 1
        }
    }
}
