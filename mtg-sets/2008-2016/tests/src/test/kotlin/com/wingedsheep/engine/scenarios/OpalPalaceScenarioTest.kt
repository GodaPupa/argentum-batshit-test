package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
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
            designateCommander(game, commanderId)

            tapForCommanderMana(game)
            val cast = game.execute(CastSpell(game.player1Id, commanderId))
            withClue("Palace mana must pay for the commander spell: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()
            counters(game, commanderId) shouldBe 1
            game.state.getEntity(commanderId)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 1
        }

        test("a later hand cast uses only prior command-zone casts") {
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
            designateCommander(game, commanderId, priorCommandCasts = 2)
            tapForCommanderMana(game)
            val cast = game.castSpell(1, "Grizzly Bears")
            withClue("Hand cast paid with Palace mana: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()
            counters(game, commanderId) shouldBe 2
            game.state.getEntity(commanderId)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 2
        }

        test("Palace mana spent on a noncommander does not add counters") {
            val game = scenario()
                .withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInCommandZone(1, "Grizzly Bears")
                .withCardInHand(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val commanderId = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            designateCommander(game, commanderId)

            tapForCommanderMana(game)
            val cast = game.castSpell(1, "Llanowar Elves")
            withClue("Noncommander cast paid with Palace mana: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()
            counters(game, game.findPermanent("Llanowar Elves")!!) shouldBe 0
        }

        test("two Palace mana spent on a first command-zone cast add two counters") {
            val game = scenario()
                .withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Plains", 5)
                .withCardInCommandZone(1, "Serra Angel")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val commanderId = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            designateCommander(game, commanderId)
            val palaces = game.state.getBattlefield().filter { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Opal Palace"
            }
            palaces.size shouldBe 2
            palaces.forEach { tapForCommanderMana(game, it, Color.WHITE) }

            val cast = game.execute(CastSpell(game.player1Id, commanderId))
            withClue("Two Palace mana must pay for the commander: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()
            counters(game, commanderId) shouldBe 2
        }

        test("ordinary mana spent on a commander does not add Palace counters") {
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
            designateCommander(game, commanderId)

            val cast = game.execute(CastSpell(game.player1Id, commanderId))
            withClue("Commander cast paid from ordinary Forests: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()
            counters(game, commanderId) shouldBe 0
        }

        test("colorless commander identity makes the second Palace ability add no mana") {
            val game = scenario()
                .withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInCommandZone(1, "Ornithopter")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val commanderId = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            designateCommander(game, commanderId)

            val palace = game.findPermanent("Opal Palace")!!
            val activated = game.execute(ActivateAbility(
                playerId = game.player1Id,
                sourceId = palace,
                abilityId = OpalPalace.activatedAbilities[1].id,
            ))
            withClue("The ability may be activated even when it produces no mana: ${activated.error}") {
                activated.error shouldBe null
            }
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.getPendingDecision() shouldBe null
        }
    }

    private fun tapForCommanderMana(
        game: TestGame,
        palace: com.wingedsheep.sdk.model.EntityId = game.findPermanent("Opal Palace")!!,
        color: Color = Color.GREEN,
    ) {
        val result = game.execute(ActivateAbility(
            playerId = game.player1Id,
            sourceId = palace,
            abilityId = OpalPalace.activatedAbilities[1].id,
            manaColorChoice = color,
        ))
        withClue("Paid commander-color mana ability: ${result.error}") { result.error shouldBe null }
    }

    private fun designateCommander(
        game: TestGame,
        cardId: com.wingedsheep.sdk.model.EntityId,
        priorCommandCasts: Int = 0,
    ) {
        game.state = game.state.updateEntity(cardId) {
            it.with(CommanderComponent(ownerId = game.player1Id, castsFromCommandZone = priorCommandCasts))
        }.updateEntity(game.player1Id) {
            it.with(CommanderRegistryComponent(listOf(cardId)))
        }
    }

    private fun counters(game: TestGame, cardId: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(cardId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
}
