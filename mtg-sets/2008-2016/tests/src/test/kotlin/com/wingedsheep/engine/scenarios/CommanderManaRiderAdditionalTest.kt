package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.identity.CommanderZoneChoiceAskedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.RestrictedManaEntry
import com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent
import com.wingedsheep.mtg.sets.definitions.c13.cards.OpalPalace
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.CantReceiveCounters
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
import kotlinx.serialization.json.Json
import io.kotest.matchers.shouldBe

/** Isolated draft; no claim that these cases or their mutants have compiled or passed. */
class OpalWitnessOP17Test : OpalWitnessFixture() {
    init {
        test("Palace rejects another generation of the same card") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val pending = rider(game, id)!!
            game.state = game.state.updateEntity(id) {
                it.with(pending.copy(spellRef = ObjectRef(id, pending.spellRef.generation - 1)))
            }
            settle(game)
            counters(game, id) shouldBe 0
        }
        test("Palace rejects a rider belonging to a different entity") {
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
    }
}

class OpalRiderAdditionalTest : OpalWitnessFixture() {
    init {
        test("Palace with no commander produces no colored or colorless mana") {
            val game = scenario().withPlayers("Caster", "Opponent").withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace").withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Opal Palace")!!,
                OpalPalace.activatedAbilities[1].id)).error shouldBe null
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.getPendingDecision() shouldBe null
        }

        test("Palace uses the combined identity of both commanders") {
            for (color in listOf(Color.GREEN, Color.WHITE)) {
                val game = scenario().withPlayers("Commander", "Opponent").withFormat(Format.Commander())
                    .withCardOnBattlefield(1, "Opal Palace").withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInCommandZone(1, "Grizzly Bears").withCardInCommandZone(1, "Serra Angel")
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val ids = game.state.getZone(game.player1Id, Zone.COMMAND)
                for (id in ids) game.state = game.state.updateEntity(id) { it.with(CommanderComponent(game.player1Id)) }
                game.state = game.state.updateEntity(game.player1Id) { it.with(CommanderRegistryComponent(ids)) }
                tapForCommanderMana(game, color = color)
                val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
                pool.total shouldBe 1
                pool.restrictedMana.single().color shouldBe color
                taggedPalaceManaCount(game) shouldBe 1
            }
        }

        test("Palace mana spent activating another Palace is not a second spell contribution") {
            val game = scenario().withPlayers("Commander", "Opponent").withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace").withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Forest", 3).withCardInCommandZone(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = commander(game)
            val palaces = game.state.getBattlefield().filter { game.state.getEntity(it)?.get<CardComponent>()?.name == "Opal Palace" }
            tapForCommanderMana(game, palace = palaces[0])
            taggedPalaceManaCount(game) shouldBe 1
            tapForCommanderMana(game, palace = palaces[1])
            taggedPalaceManaCount(game) shouldBe 1
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            rider(game, id)!!.count shouldBe 1
            settle(game)
            counters(game, id) shouldBe 1
        }

        test("Palace exile cast uses prior command count without increment") {
            val game = commanderGame()
            val id = commander(game, 2)
            game.state = game.state.updateEntity(id) { it.with(CommanderZoneChoiceAskedComponent) }
            game.state = ZoneTransitionService.moveToZone(game.state, id, Zone.EXILE).state
                .addMayPlayPermission(MayPlayPermission(EntityId.generate(), setOf(id), game.player1Id, timestamp = game.state.timestamp))
            paid(game, id)
            rider(game, id)!!.count shouldBe 2
            settle(game)
            counters(game, id) shouldBe 2
            game.state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 2
        }

        test("Palace full game state round trip preserves pending spell and consumed mana") {
            val game = commanderGame()
            val id = commander(game, 1)
            paid(game, id)
            val pending = rider(game, id)!!
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            val encoded = json.encodeToString(GameState.serializer(), game.state)
            val restored = json.decodeFromString(GameState.serializer(), encoded)
            json.encodeToString(GameState.serializer(), restored) shouldBe encoded
            game.state = restored
            rider(game, id) shouldBe pending
            taggedPalaceManaCount(game) shouldBe 0
            settle(game)
            counters(game, id) shouldBe 2
            (rider(game, id) == null) shouldBe true
        }

        test("Palace two real activations preserve both units with explicit source selection") {
            val game = scenario().withPlayers("Commander", "Opponent").withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace").withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Plains", 5).withCardInCommandZone(1, "Serra Angel")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = commander(game)
            val palaces = game.state.getBattlefield().filter { game.state.getEntity(it)?.get<CardComponent>()?.name == "Opal Palace" }
            val plains = game.state.getBattlefield().filter { game.state.getEntity(it)?.get<CardComponent>()?.name == "Plains" }
            val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true }
            for (i in 0..1) {
                val action: GameAction = ActivateAbility(game.player1Id, palaces[i], OpalPalace.activatedAbilities[1].id,
                    manaColorChoice = Color.WHITE, paymentStrategy = PaymentStrategy.Explicit(listOf(plains[i])))
                val restored = json.decodeFromString(GameAction.serializer(), json.encodeToString(GameAction.serializer(), action))
                restored shouldBe action
                game.execute(restored).error shouldBe null
                taggedPalaceManaCount(game) shouldBe i + 1
                game.state.getEntity(plains[i])!!.has<TappedComponent>() shouldBe true
            }
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            taggedPalaceManaCount(game) shouldBe 0
            rider(game, id)!!.count shouldBe 2
            settle(game)
            counters(game, id) shouldBe 2
        }

        test("Palace uses only the cast commander's history with two commanders") {
            val game = scenario().withPlayers("Commander", "Opponent").withFormat(Format.Commander())
                .withLandsOnBattlefield(1, "Forest", 8)
                .withCardInCommandZone(1, "Grizzly Bears").withCardInCommandZone(1, "Llanowar Elves")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val ids = game.state.getZone(game.player1Id, Zone.COMMAND)
            val bear = ids.single { game.state.getEntity(it)!!.get<CardComponent>()!!.name == "Grizzly Bears" }
            val elf = ids.single { it != bear }
            game.state = game.state.updateEntity(bear) { it.with(CommanderComponent(game.player1Id, 2)) }
                .updateEntity(elf) { it.with(CommanderComponent(game.player1Id, 7)) }
                .updateEntity(game.player1Id) { it.with(CommanderRegistryComponent(listOf(bear, elf))) }
            paid(game, bear)
            settle(game)
            counters(game, bear) shouldBe 3
            counters(game, elf) shouldBe 0
            game.state.getEntity(elf)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 7
        }

        test("Palace hand cast with zero previous command casts adds no counters") {
            val game = scenario().withPlayers("Commander", "Opponent").withFormat(Format.Commander())
                .withLandsOnBattlefield(1, "Forest", 2).withCardInHand(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = game.state.getHand(game.player1Id).single()
            designateCommander(game, id)
            game.state = game.state.updateEntity(id) { it.with(CommanderZoneChoiceAskedComponent) }
            paid(game, id)
            settle(game)
            counters(game, id) shouldBe 0
            game.state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 0
        }

        test("Palace counters do not return after generic noncast reentry") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            settle(game)
            counters(game, id) shouldBe 1
            game.state = ZoneTransitionService.moveToZone(game.state, id, Zone.HAND).state
            game.state = ZoneTransitionService.moveToZone(game.state, id, Zone.BATTLEFIELD).state
            counters(game, id) shouldBe 0
            (rider(game, id) == null) shouldBe true
        }

        test("Palace a replayed cast cannot consume mana or apply the rider twice") {
            val game = commanderGame()
            val id = commander(game)
            paid(game, id)
            val before = game.state
            val replay = game.execute(CastSpell(game.player1Id, id))
            (replay.error != null) shouldBe true
            replay.newState shouldBe before
            rider(game, id)!!.count shouldBe 1
            settle(game)
            counters(game, id) shouldBe 1
        }
    }
}

class OpalWitnessOP18Test : OpalWitnessFixture() {
    init {
        val prohibition = card("Palace counter prohibition fixture") {
            manaCost = "{2}{W}"
            typeLine = "Enchantment"
            staticAbility { ability = CantReceiveCounters(GroupFilter(GameObjectFilter.Creature)) }
        }
        cardRegistry.register(listOf(prohibition))
        test("Palace respects Solemnity shaped counter prevention before entry observers") {
            val game = scenario().withPlayers("Commander", "Opponent").withFormat(Format.Commander())
                .withCardOnBattlefield(1, prohibition.name).withLandsOnBattlefield(1, "Forest", 3)
                .withCardInCommandZone(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = commander(game)
            paid(game, id)
            settle(game)
            game.state.projectedState.canReceiveCounters(id) shouldBe false
            counters(game, id) shouldBe 0
            (rider(game, id) == null) shouldBe true
        }
    }
}

class OpalRiderBaselineTest : OpalWitnessFixture() {
    init {
        fun fixture(): Pair<TestGame, com.wingedsheep.sdk.model.EntityId> {
            val game = scenario().withPlayers("Caster", "Opponent").withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace").withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Grizzly Bears").withCardInCommandZone(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            commander(game)
            return game to game.state.getHand(game.player1Id).single()
        }

        test("existing uncounterable mana rider remains effective") {
            val (game, id) = fixture()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(ManaPoolComponent(restrictedMana = listOf(RestrictedManaEntry(Color.GREEN,
                    ManaRestriction.AnySpend, riders = setOf(ManaSpellRider.MakesSpellUncounterable)))))
            }
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            val countered = StackResolver(cardRegistry).counterSpell(game.state, id)
            countered.error shouldBe null
            (id in countered.newState.stack) shouldBe true
            (rider(game, id) == null) shouldBe true
        }

        test("existing shared commander type rider queues its own trigger") {
            val (game, id) = fixture()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(ManaPoolComponent(restrictedMana = listOf(RestrictedManaEntry(Color.GREEN,
                    ManaRestriction.AnySpend, riders = setOf(ManaSpellRider.ScryOnSharedTypeWithCommander())))))
            }
            game.execute(CastSpell(game.player1Id, id)).error shouldBe null
            game.state.stack.size shouldBe 2
            (rider(game, id) == null) shouldBe true
        }
    }
}
