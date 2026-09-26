package com.wingedsheep.gym

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.gym.matchup.IZZET_SCIENCE_COMMANDER
import com.wingedsheep.gym.matchup.IzzetSciencePosition1Bootstrap
import com.wingedsheep.gym.matchup.IzzetScienceVeteranBeastriderEngineReadiness
import com.wingedsheep.gym.matchup.VETERAN_BEASTRIDER_COMMANDER
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/** Production bootstrap boundaries plus named public-coordinate fixtures, never official games. */
class IzzetSciencePosition1BootstrapTest : FunSpec({
    val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("izzet-science/v0.7-control.md")) }
    val control = Files.readAllBytes(root.resolve("izzet-science/v0.7-control.md"))
    val opponent = Files.readAllBytes(root.resolve("izzet-science/opponents/veteran-beastrider-commander-clash-2025.txt"))
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    fun config(assignment: String) = IzzetSciencePosition1Bootstrap.configuration(control, opponent, assignment)

    // No missing card gets a counterfeit definition. For the independent initializer/mulligan
    // fixtures only, substitute 99 existing basics per library after checking the exact full config.
    // This cannot enter Bootstrap.initialize and is not frozen-deck card or gameplay qualification.
    fun fixture(assignment: String) = config(assignment).let { frozen ->
        frozen.copy(
            players = frozen.players.mapIndexed { index, player ->
                player.copy(deck = if (index == 0) Deck.of("Snow-Covered Island" to 60, "Snow-Covered Mountain" to 39)
                    else Deck.of("Forest" to 60, "Plains" to 39))
            },
            seed = 1L,
        )
    }

    test("frozen byte identities and translated lists produce an unseeded unsmoothed PDH config") {
        val frozen = config("play")
        frozen.seed shouldBe null
        frozen.skipMulligans shouldBe false
        frozen.useHandSmoother shouldBe false
        frozen.startingHandSize shouldBe 7
        frozen.format shouldBe IzzetSciencePosition1Bootstrap.pdhFormat
        (frozen.format as Format.Commander).startingLife shouldBe 30
        (frozen.format as Format.Commander).commanderDamageThreshold shouldBe 16
        frozen.players.map { it.deck.cards.size }.shouldContainExactly(99, 99)
        frozen.players.map { it.commanderCardName }.shouldContainExactly(
            IZZET_SCIENCE_COMMANDER, VETERAN_BEASTRIDER_COMMANDER,
        )
        frozen.players.map { it.startingLife }.shouldContainExactly(30, 30)
    }

    test("play and draw change the starting player without swapping stable deck seats") {
        val play = config("play")
        val draw = config("draw")
        play.startingPlayerIndex shouldBe 0
        draw.startingPlayerIndex shouldBe 1
        play.players shouldBe draw.players
    }

    test("changed control or opponent bytes and unknown assignments reject before seed access") {
        listOf(
            Triple(control + 10.toByte(), opponent, "play"),
            Triple(control, opponent + 10.toByte(), "play"),
            Triple(control, opponent, "PLAY"),
            Triple(control, opponent, ""),
        ).forEach { (candidateControl, candidateOpponent, assignment) ->
            var seedReads = 0
            shouldThrow<IllegalArgumentException> {
                IzzetSciencePosition1Bootstrap.initialize(
                    registry, candidateControl, candidateOpponent, assignment,
                ) { seedReads++; error("seed supplier must remain untouched") }
            }
            seedReads shouldBe 0
        }
    }

    test("all three unresolved identities block the real initializer without touching its seed supplier") {
        val expected = listOf(
            "unresolved Veteran Beastrider card: Benevolent Blessing",
            "unresolved Veteran Beastrider card: Opal Palace",
            "unresolved Veteran Beastrider card: Snake Umbra",
        )
        IzzetScienceVeteranBeastriderEngineReadiness.unresolvedCardIdentities(registry).shouldContainExactly(expected)
        var seedReads = 0
        val rejected = shouldThrow<IllegalArgumentException> {
            IzzetSciencePosition1Bootstrap.initialize(registry, control, opponent, "play") {
                seedReads++
                error("the frozen pair must reject before seed access")
            }
        }
        expected.forEach { rejected.message.orEmpty() shouldContain it }
        seedReads shouldBe 0
    }

    for (assignment in listOf("play", "draw")) {
        test("$assignment fixture reuses the real configuration with exact command zones and deterministic initialization") {
            val fixtureConfig = fixture(assignment)
            val first = GameInitializer(registry).initializeGame(fixtureConfig)
            val replay = GameInitializer(registry).initializeGame(fixtureConfig)
            first shouldBe replay
            first.seed shouldBe 1L
            first.state.format shouldBe IzzetSciencePosition1Bootstrap.pdhFormat
            first.state.activePlayerId shouldBe first.playerIds[fixtureConfig.startingPlayerIndex!!]
            for ((seat, owner) in first.playerIds.withIndex()) {
                first.state.getEntity(owner)!!.get<LifeTotalComponent>()!!.life shouldBe 30
                val commanderId = first.state.getEntity(owner)!!.get<CommanderRegistryComponent>()!!.commanderIds.single()
                first.state.getZone(ZoneKey(owner, Zone.COMMAND)) shouldBe listOf(commanderId)
                val commander = first.state.getEntity(commanderId)!!
                commander.get<CardComponent>()!!.name shouldBe fixtureConfig.players[seat].commanderCardName
                commander.get<CardComponent>()!!.typeLine.isLegendary shouldBe false
                commander.get<CommanderComponent>()!!.ownerId shouldBe owner
                commander.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 0
                first.state.getZone(ZoneKey(owner, Zone.HAND)).size shouldBe 7
                first.state.getZone(ZoneKey(owner, Zone.LIBRARY)).size shouldBe 92
                val mulligan = first.state.getEntity(owner)!!.get<MulliganStateComponent>()!!
                mulligan.hasKept shouldBe false
                mulligan.freeMulligan shouldBe false
            }
            val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), first.state))
            restored shouldBe first.state
        }
    }

    test("a charged London mulligan survives serialized state and action replay without automatic decisions") {
        val initial = GameInitializer(registry).initializeGame(fixture("play"))
        val owner = initial.playerIds[0]
        val other = initial.playerIds[1]
        val processor = ActionProcessor(registry)
        var state = initial.state
        fun submit(action: GameAction) {
            val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
            val restoredAction = json.decodeFromString(GameAction.serializer(), json.encodeToString(GameAction.serializer(), action))
            val actual = processor.process(state, action).result
            val replay = processor.process(restored, restoredAction).result
            actual.error shouldBe null
            actual shouldBe replay
            state = actual.state
        }
        submit(TakeMulligan(owner))
        state.getEntity(owner)!!.get<MulliganStateComponent>()!!.mulligansTaken shouldBe 1
        state.getZone(ZoneKey(owner, Zone.HAND)).size shouldBe 7
        submit(KeepHand(owner))
        submit(KeepHand(other))
        val bottom = state.getZone(ZoneKey(owner, Zone.HAND)).first()
        submit(BottomCards(owner, listOf(bottom)))
        state.getZone(ZoneKey(owner, Zone.HAND)).size shouldBe 6
        state.getZone(ZoneKey(owner, Zone.LIBRARY)).last() shouldBe bottom
    }
})
