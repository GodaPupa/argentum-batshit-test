package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/** Stored-replay boundary fixtures only; synthetic sentinel cards do not qualify real deck mechanics. */
class ManualStoredFixtureReplayIntegrationTest : FunSpec({
    val source = "1f4b927a54aab72ffc0ebecfeaab2c91dafe9735"
    // Fixed prospective fixture input, excluded from future official sampling.
    val seed = -202610010218L
    val players = (0..3).map { EntityId("manual-integration-$it") }
    val commanders = listOf("Animar, Soul of Elements", "Fixture Commander B", "Fixture Commander C", "Fixture Commander D")
    fun registry() = CardRegistry().apply {
        register(CardDefinition.basicLand("Forest", Subtype.FOREST))
        // The adapter binds Animar by designated name. These vanilla sentinels intentionally
        // test roster/lifecycle/serialization only, not Animar abilities or any opponent pilot.
        commanders.forEach { name -> register(CardDefinition.creature(name, ManaCost.parse("{1}"),
            emptySet(), 1, 1, supertypes = setOf(Supertype.LEGENDARY))) }
    }
    fun identity(label: String) = ManualFixtureIdentity("manual-fixture-integration-$label", source, "CRUISE",
        List(4) { "1".repeat(64) }, List(4) { "2".repeat(64) }, "3".repeat(64))
    // Identity pins are explicit synthetic placeholders, not authentication of a submitted deck.
    fun runner(registry: CardRegistry): ManualPhaseTwoFixtureRunner {
        val config = GameConfig(players = players.mapIndexed { i, id ->
            PlayerConfig("Fixture-$i", Deck.of("Forest" to 99), playerId = id, commanderCardName = commanders[i])
        }, format = Format.Commander(), seed = seed, startingPlayerIndex = 0,
            skipMulligans = false, useHandSmoother = false)
        val initial = GameInitializer(registry).initializeGame(config)
        val telemetry = PhaseTwoTelemetryAdapter(initial.state, ActionProcessor(registry), source, initial.playerIds, 0)
        val policies = initial.playerIds.associateWith { PhaseTwoPilotPolicy { _, prompt ->
            check(prompt is PhaseTwoPilotPrompt.Mulligan) { "Fixture must stop before ordinary policy decisions" }
            PhaseTwoPilotChoice.Keep
        } }
        return ManualPhaseTwoFixtureRunner(telemetry, registry, policies, ManualFixtureStepBudget(1, 60_000))
    }
    fun root() = Files.createTempDirectory("manual-engine-integration-").toRealPath()

    test("one real masked keep is stored and replayed without repeating initialization or pilot decisions") {
        val root = root(); val id = identity("keep"); val registry = registry(); var factories = 0
        val trace = ManualPhaseTwoStoredFixtureReplay.runNewAndReplay(root, id, registry) {
            factories++
            Files.readString(root.resolve(id.fixtureId).resolve("initialization-intent.txt")) shouldBe "INITIALIZE_ONCE\n"
            runner(registry)
        }
        factories shouldBe 1
        trace.playerIds.size shouldBe 4
        trace.steps.size shouldBe 1
        trace.steps.single().accepted shouldBe true
        trace.stopObservation?.kind shouldBe "RESOURCE_CAP"
        val directory = root.resolve(id.fixtureId)
        val before = Files.readAllBytes(directory.resolve("result.bin"))
        ManualPhaseTwoStoredFixtureReplay.verify(directory, id, registry) shouldBe trace
        factories shouldBe 1
        Files.readAllBytes(directory.resolve("result.bin")).contentEquals(before) shouldBe true
    }

    test("corrupt stored real trace is rejected without changing the preserved bytes") {
        val root = root(); val id = identity("corrupt"); val registry = registry()
        ManualPhaseTwoStoredFixtureReplay.runNewAndReplay(root, id, registry) { runner(registry) }
        val file = root.resolve(id.fixtureId).resolve("result.bin")
        val damaged = Files.readAllBytes(file) + byteArrayOf(32)
        Files.write(file, damaged) // Deliberate corruption of this disposable fixture only.
        shouldThrowAny { ManualPhaseTwoStoredFixtureReplay.verify(root.resolve(id.fixtureId), id, registry) }
        Files.readAllBytes(file).contentEquals(damaged) shouldBe true
    }

    test("completed identity cannot initialize a second real fixture or call its replacement factory") {
        val root = root(); val id = identity("duplicate"); val registry = registry()
        ManualPhaseTwoStoredFixtureReplay.runNewAndReplay(root, id, registry) { runner(registry) }
        var replacementCalled = false
        shouldThrowAny {
            ManualPhaseTwoStoredFixtureReplay.runNewAndReplay(root, id, registry) {
                replacementCalled = true
                runner(registry)
            }
        }
        replacementCalled shouldBe false
    }

    test("failed factory cannot be promoted to a completed replayable fixture") {
        val root = root(); val id = identity("failure"); val registry = registry()
        shouldThrowAny { ManualPhaseTwoStoredFixtureReplay.runNewAndReplay(root, id, registry) { error("injected pre-engine factory failure") } }
        Files.exists(root.resolve(id.fixtureId).resolve("fault.txt")) shouldBe true
        shouldThrowAny { ManualPhaseTwoStoredFixtureReplay.verify(root.resolve(id.fixtureId), id, registry) }
    }
})
