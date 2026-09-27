package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.c13.cards.OpalPalace
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Opal Palace's mana is scoped to the exact commander spell on which it was spent. */
class OpalPalaceScenarioTest : ScenarioTestBase() {
    init {
        test("first command-zone cast enters with one counter when paid with Palace mana") {
            val game = scenario()
                .withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInCommandZone(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val commanderId = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            game.state = game.state.updateEntity(commanderId) {
                it.with(CommanderComponent(ownerId = game.player1Id))
            }

            tapForCommanderMana(game)
            val cast = game.execute(CastSpell(game.player1Id, commanderId))
            withClue("Palace mana must pay for the commander spell: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()
            counters(game, commanderId) shouldBe 1
            game.state.getEntity(commanderId)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 1
        }

        test("a later hand cast uses only prior command-zone casts, and ordinary mana carries no rider") {
            val game = scenario()
                .withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val commanderId = game.state.getHand(game.player1Id).single()
            game.state = game.state.updateEntity(commanderId) {
                it.with(CommanderComponent(ownerId = game.player1Id, castsFromCommandZone = 2))
            }
            tapForCommanderMana(game)
            val cast = game.castSpell(1, "Grizzly Bears")
            withClue("Hand cast paid with Palace mana: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()
            counters(game, commanderId) shouldBe 2
            game.state.getEntity(commanderId)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 2
        }
    }

    private fun tapForCommanderMana(game: TestGame) {
        val palace = game.findPermanent("Opal Palace")!!
        val result = game.execute(ActivateAbility(
            playerId = game.player1Id,
            sourceId = palace,
            abilityId = OpalPalace.activatedAbilities[1].id,
            manaColorChoice = Color.GREEN,
        ))
        withClue("Paid commander-color mana ability: ${result.error}") { result.error shouldBe null }
    }

    private fun counters(game: TestGame, cardId: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(cardId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
}
