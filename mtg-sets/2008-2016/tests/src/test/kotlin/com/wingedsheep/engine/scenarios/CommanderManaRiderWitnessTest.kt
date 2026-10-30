package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.RestrictedManaEntry
import com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
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

import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.components.identity.CantBeCounteredComponent
import com.wingedsheep.engine.state.components.identity.CommanderZoneChoiceAskedComponent
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.Json

/** Test-only fixtures; every witness runs as a complete class, without test skips. */
abstract class OpalWitnessFixture : ScenarioTestBase() {
    protected fun taggedPalaceManaCount(game: TestGame): Int =
        game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.restrictedMana.count {
            it.riders.contains(ManaSpellRider.CommanderCastEntryCounters)
        }

    protected fun stageTwoPalaceManaForCast(game: TestGame) {
        val palaces = game.state.getBattlefield().filter {
            game.state.getEntity(it)?.get<CardComponent>()?.name == "Opal Palace"
        }
        palaces.size shouldBe 2
        val rider = RestrictedManaEntry(
            color = Color.WHITE,
            restriction = ManaRestriction.AnySpend,
            riders = setOf(ManaSpellRider.CommanderCastEntryCounters),
        )
        game.state = game.state.updateEntity(game.player1Id) { player ->
            val pool = player.get<ManaPoolComponent>() ?: ManaPoolComponent()
            player.with(pool.copy(restrictedMana = pool.restrictedMana + listOf(rider, rider)))
        }
    }

    protected fun tapForCommanderMana(
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

    protected fun designateCommander(
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

    protected fun counters(game: TestGame, cardId: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(cardId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    protected fun commanderGame(name: String = "Grizzly Bears", lands: Int = 10): TestGame =
        scenario().withPlayers("Commander", "Opponent").withFormat(Format.Commander())
            .withCardOnBattlefield(1, "Opal Palace")
            .withLandsOnBattlefield(1, "Forest", lands)
            .withCardInCommandZone(1, name)
            .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()

    protected fun commander(game: TestGame, prior: Int = 0): EntityId {
        val id = game.state.getZone(game.player1Id, Zone.COMMAND).single()
        designateCommander(game, id, prior)
        return id
    }

    protected fun stage(game: TestGame, count: Int = 1, color: Color = Color.GREEN) {
        val unit = RestrictedManaEntry(color = color, restriction = ManaRestriction.AnySpend,
            riders = setOf(ManaSpellRider.CommanderCastEntryCounters))
        game.state = game.state.updateEntity(game.player1Id) { c ->
            val pool = c.get<ManaPoolComponent>() ?: ManaPoolComponent()
            c.with(pool.copy(restrictedMana = pool.restrictedMana + List(count) { unit }))
        }
    }

    protected fun paid(game: TestGame, id: EntityId) {
        stage(game)
        game.execute(CastSpell(game.player1Id, id)).error shouldBe null
    }

    protected fun settle(game: TestGame) {
        game.resolveStack().forEach { it.error shouldBe null }
    }

    protected fun rider(game: TestGame, id: EntityId) =
        game.state.getEntity(id)!!.get<CommanderManaEntryCountersComponent>()

}

class OpalWitnessOP01Test : OpalWitnessFixture() {
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
    }
}

class OpalWitnessOP05Test : OpalWitnessFixture() {
    init {
        test("scoped repair: a later hand cast uses only prior command-zone casts") {
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
            // The fixture designated a commander already in hand. The next SBA offers its
            // real command-zone choice before priority returns; decline through the game API.
            val zoneChoice = game.state.pendingDecision as? YesNoDecision
                ?: error("Expected the commander's state-based zone choice")
            zoneChoice.playerId shouldBe game.player1Id
            zoneChoice.context.sourceId shouldBe commanderId
            zoneChoice.context.phase shouldBe DecisionPhase.STATE_BASED
            zoneChoice.yesText shouldBe "Command zone"
            zoneChoice.noText shouldBe "Leave in your hand"
            game.answerYesNo(false).error shouldBe null
            game.state.pendingDecision shouldBe null
            game.state.hasPriority(game.player1Id) shouldBe true
            (commanderId in game.state.getHand(game.player1Id)) shouldBe true
            val cast = game.castSpell(1, "Grizzly Bears")
            withClue("Hand cast paid with Palace mana: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()
            counters(game, commanderId) shouldBe 2
            game.state.getEntity(commanderId)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 2
        }
    }
}

class OpalWitnessOP06Test : OpalWitnessFixture() {
    init {
        test("scoped repair: two Palace contributions are combined before an additive counter modifier") {
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
            stageTwoPalaceManaForCast(game)
            taggedPalaceManaCount(game) shouldBe 2
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            taggedPalaceManaCount(game) shouldBe 0
            (id in game.state.stack) shouldBe true
            game.state.getEntity(id)!!.get<CommanderManaEntryCountersComponent>()!!.count shouldBe 2
            game.resolveStack().forEach { it.error shouldBe null }
            counters(game, id) shouldBe 3
        }
    }
}

class OpalWitnessOP07Test : OpalWitnessFixture() {
    init {
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
    }
}

class OpalWitnessOP09Test : OpalWitnessFixture() {
    init {
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
    }
}

class OpalWitnessOP11Test : OpalWitnessFixture() {
    init {
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
    }
}

class OpalWitnessOP13Test : OpalWitnessFixture() {
    init {
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
    }
}

class OpalWitnessOP02Test : OpalWitnessFixture() {
    init {
        test("Palace counts the current command-zone cast") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            settle(game)
            counters(game, id) shouldBe 1
            game.state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 1
        }
    }
}

class OpalWitnessOP03Test : OpalWitnessFixture() {
    init {
        test("Palace does not mark a foreign commander") {
            val game = scenario().withPlayers("Caster", "Owner").withFormat(Format.Commander())
                .withLandsOnBattlefield(1, "Forest", 2).withCardInHand(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.state.getHand(game.player1Id).single()
            game.state = game.state.updateEntity(id) {
                it.with(CommanderComponent(ownerId = game.player2Id, castsFromCommandZone = 2))
                    .with(CommanderZoneChoiceAskedComponent)
            }
            paid(game, id)
            (rider(game, id) == null) shouldBe true
            settle(game)
            counters(game, id) shouldBe 0
        }
    }
}

class OpalWitnessOP04Test : OpalWitnessFixture() {
    init {
        test("Palace preserves each consumed mana contribution") {
            val game = commanderGame()
            val id = commander(game, prior = 2)
            stage(game, 2)
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            taggedPalaceManaCount(game) shouldBe 0
            rider(game, id)!!.count shouldBe 6
            settle(game)
            counters(game, id) shouldBe 6
            game.state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 3
        }
    }
}

class OpalWitnessOP08Test : OpalWitnessFixture() {
    init {
        test("Palace pending rider serialization succeeds and preserves exact spell") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true }
            val before = game.state.getEntity(id)!!
            val roundTrip = runCatching {
                json.decodeFromString(ComponentContainer.serializer(),
                    json.encodeToString(ComponentContainer.serializer(), before))
            }
            roundTrip.isSuccess shouldBe true
            roundTrip.getOrThrow() shouldBe before
            roundTrip.getOrThrow().get<CommanderManaEntryCountersComponent>()!!.spellRef shouldBe game.state.objectRef(id)
        }
    }
}

class OpalWitnessOP10Test : OpalWitnessFixture() {
    init {
        test("Palace generic stack exit clears pending rider") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val originalRef = rider(game, id)!!.spellRef
            game.state = ZoneTransitionService.moveToZone(game.state, id, Zone.HAND).state
            (rider(game, id) == null) shouldBe true
            game.state.isCurrentObject(originalRef) shouldBe false
            counters(game, id) shouldBe 0
        }
    }
}

class OpalWitnessOP12Test : OpalWitnessFixture() {
    init {
        test("Palace entry consumes only its matching spell rider") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val originalRef = rider(game, id)!!.spellRef
            settle(game)
            (rider(game, id) == null) shouldBe true
            counters(game, id) shouldBe 1
            game.state.isCurrentObject(originalRef) shouldBe false
        }
    }
}

class OpalWitnessOP14Test : OpalWitnessFixture() {
    init {
        test("Palace unspent tagged mana does not mark a spell") {
            val game = commanderGame("Ornithopter")
            val id = commander(game)
            stage(game)
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            taggedPalaceManaCount(game) shouldBe 1
            (rider(game, id) == null) shouldBe true
            settle(game)
            counters(game, id) shouldBe 0
        }
    }
}

class OpalWitnessOP15Test : OpalWitnessFixture() {
    init {
        test("Palace mana rider survives producer departure") {
            val game = commanderGame()
            val id = commander(game)
            tapForCommanderMana(game)
            val palace = game.findPermanent("Opal Palace")!!
            game.state = ZoneTransitionService.moveToZone(game.state, palace, Zone.GRAVEYARD).state
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            settle(game)
            counters(game, id) shouldBe 1
        }
    }
}

class OpalWitnessControlsTest : OpalWitnessFixture() {
    init {
        test("Palace rejects a stale generation before entry") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val pending = rider(game, id)!!
            game.state = game.state.updateEntity(id) {
                it.with(pending.copy(spellRef = ObjectRef(id, pending.spellRef.generation - 1)))
            }
            settle(game)
            counters(game, id) shouldBe 0
            (rider(game, id) == null) shouldBe true
        }

        test("Palace rejects a different spell entity before entry") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val pending = rider(game, id)!!
            game.state = game.state.updateEntity(id) {
                it.with(pending.copy(spellRef = ObjectRef(game.player2Id, pending.spellRef.generation)))
            }
            settle(game)
            counters(game, id) shouldBe 0
        }

        test("Palace failed counter keeps its live rider") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val pending = rider(game, id)!!
            game.state = game.state.updateEntity(id) { it.with(CantBeCounteredComponent) }
            val countered = StackResolver(cardRegistry).counterSpell(game.state, id)
            countered.error shouldBe null
            game.state = countered.newState
            rider(game, id) shouldBe pending
            (id in game.state.stack) shouldBe true
            settle(game)
            counters(game, id) shouldBe 1
        }

        test("Palace direct counter destinations clear the exact rider") {
            for (destination in listOf("graveyard", "library", "exile")) {
                val game = commanderGame()
                val id = commander(game)
                paid(game, id)
                val resolver = StackResolver(cardRegistry)
                val result = when (destination) {
                    "graveyard" -> resolver.counterSpell(game.state, id)
                    "library" -> resolver.counterSpellToLibraryTop(game.state, id)
                    else -> resolver.counterSpellToExile(game.state, id, false, game.player1Id)
                }
                result.error shouldBe null
                game.state = result.newState
                withClue(destination) { (rider(game, id) == null) shouldBe true }
                (id in game.state.stack) shouldBe false
            }
        }

        test("Palace failed casting preserves mana and creates no rider") {
            val game = commanderGame("Serra Angel", lands = 0)
            val id = commander(game)
            stage(game, color = Color.WHITE)
            val before = game.state
            val result = game.execute(CastSpell(game.player1Id, id))
            (result.error != null) shouldBe true
            result.newState shouldBe before
            (rider(game, id) == null) shouldBe true
        }

        test("Palace first ability produces ordinary colorless mana") {
            val game = commanderGame()
            commander(game)
            val palace = game.findPermanent("Opal Palace")!!
            game.execute(ActivateAbility(game.player1Id, palace, OpalPalace.activatedAbilities[0].id)).error shouldBe null
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.colorless shouldBe 1
            taggedPalaceManaCount(game) shouldBe 0
        }

        test("Palace mana spent on another Palace activation cannot ride a later spell") {
            val game = commanderGame()
            val id = commander(game)
            stage(game)
            // The only available floating unit pays the real {1} activation cost. Colorless
            // commander identity then produces nothing, so no consumed tag can leak to casting.
            game.state = game.state.updateEntity(game.player1Id) { it.with(CommanderRegistryComponent(emptyList())) }
            val palace = game.findPermanent("Opal Palace")!!
            game.execute(ActivateAbility(game.player1Id, palace, OpalPalace.activatedAbilities[1].id)).error shouldBe null
            taggedPalaceManaCount(game) shouldBe 0
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            settle(game)
            counters(game, id) shouldBe 0
        }

        test("Palace mana rider round trips as SDK data") {
            val rider: ManaSpellRider = ManaSpellRider.CommanderCastEntryCounters
            val json = Json { encodeDefaults = true }
            json.decodeFromString(ManaSpellRider.serializer(),
                json.encodeToString(ManaSpellRider.serializer(), rider)) shouldBe rider
        }
    }
}
