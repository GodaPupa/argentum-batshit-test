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

        test("countered Palace spell loses its rider before a hand recast with ordinary mana") {
            val game = scenario().withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInCommandZone(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            designateCommander(game, id)
            tapForCommanderMana(game)
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            val countered = com.wingedsheep.engine.mechanics.stack.StackResolver(cardRegistry).counterSpellToHand(game.state, id)
            countered.error shouldBe null
            game.state = countered.newState
            game.state.getHand(game.player1Id).contains(id) shouldBe true
            game.state.getEntity(id)!!.has<com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent>() shouldBe false
            counters(game, id) shouldBe 0
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            counters(game, id) shouldBe 0
            game.state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 1
        }

        test("pending Palace counters survive serialization and apply once on resolution") {
            val game = scenario().withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInCommandZone(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            designateCommander(game, id)
            tapForCommanderMana(game)
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            val json = kotlinx.serialization.json.Json {
                serializersModule = com.wingedsheep.engine.core.engineSerializersModule
                encodeDefaults = true
            }
            val before = game.state.getEntity(id)!!
            val encoded = json.encodeToString(com.wingedsheep.engine.state.ComponentContainer.serializer(), before)
            val restored = json.decodeFromString(com.wingedsheep.engine.state.ComponentContainer.serializer(), encoded)
            restored shouldBe before
            restored.get<com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent>()!!.count shouldBe 1
            game.state = game.state.updateEntity(id) { restored }
            val copied = com.wingedsheep.engine.mechanics.stack.StackResolver(cardRegistry).putSpellCopy(game.state, id)
            copied.error shouldBe null
            game.state = copied.newState
            val copyId = game.state.stack.single { it != id }
            game.state.getEntity(copyId)!!.has<com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent>() shouldBe false
            game.resolveStack().forEach { it.error shouldBe null }
            counters(game, copyId) shouldBe 0
            counters(game, id) shouldBe 1
            game.state.getEntity(id)!!.has<com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent>() shouldBe false
        }

        test("entry observers see Palace counters before checking entering power") {
            val game = scenario().withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withCardOnBattlefield(1, "Garruk's Packleader")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Forest")
                .withCardInCommandZone(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            designateCommander(game, id)
            tapForCommanderMana(game)
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            counters(game, id) shouldBe 1
            // Packleader's optional draw is offered only if the entering Bears already has power3.
            val decision = game.getPendingDecision()
            (decision is com.wingedsheep.engine.core.YesNoDecision) shouldBe true
            game.answerYesNo(true).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getHand(game.player1Id).size shouldBe 1
        }

        test("two Palace contributions are combined before an additive counter modifier") {
            val game = scenario().withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withCardOnBattlefield(1, "Opal Palace")
                .withCardOnBattlefield(1, "Hardened Scales")
                .withLandsOnBattlefield(1, "Plains", 5)
                .withCardInCommandZone(1, "Serra Angel")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.state.getZone(game.player1Id, Zone.COMMAND).single()
            designateCommander(game, id)
            game.state.getBattlefield().filter {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Opal Palace"
            }.forEach { tapForCommanderMana(game, it, Color.WHITE) }
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            counters(game, id) shouldBe 3
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
