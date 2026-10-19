package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Safety gate for the exact opt-in v0.8 deck challenger, not a modification of the
 * frozen deck or accepted R5 policy. The AI is evaluated with only decision-time
 * visible library size, without peeking at unknown card identities.
 */
class ManualV08WanSafetyTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }

        for (librarySize in listOf(0, 1, 2, 3)) {
            test("R5 policy must not voluntarily cast Wan with more mandatory ETB draws than " +
                librarySize + " available library cards") {
                val builder = scenario().withPlayers("Manual", "Opponent")
                    .withCardOnBattlefield(1, "Animar, Soul of Elements")
                    .withCardInHand(1, "Wan Shi Tong, Librarian")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(librarySize) { builder.withCardInLibrary(1, "Forest") }
                repeat(20) { builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()
                val animar = requireNotNull(game.findPermanent("Animar, Soul of Elements"))
                game.state = game.state.updateEntity(animar) {
                    it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 6))
                }
                val wan = game.state.getZone(ZoneKey(game.player1Id, Zone.HAND)).single {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Wan Shi Tong, Librarian"
                }
                val raw = LegalActionEnumerator.create(cardRegistry).enumerate(game.state, game.player1Id)
                    .single { (it.action as? CastSpell)?.cardId == wan }
                val choice = AIPlayer.create(cardRegistry, game.player1Id,
                    ManualTransmissionR5Candidate.profile).chooseAction(game.state)
                val declaredX = (choice as? CastSpell)?.takeIf { it.cardId == wan }?.xValue
                val record = buildJsonObject {
                    put("librarySize", librarySize)
                    put("legalWanAffordable", raw.affordable)
                    put("maxAffordableWanX", raw.maxAffordableX)
                    put("selectedAction", choice.toString())
                    put("selectedWanX", declaredX)
                    put("selectedWanMandatoryDraws", declaredX?.div(2))
                    put("isVoluntaryOverdraw", declaredX != null && declaredX / 2 > librarySize)
                    put("source", "R5 production policy unchanged; Wan inserted only into constructed decision state")
                }
                val output = Path.of(System.getenv("MANUAL_V08_WAN_SAFETY_OUTPUT")
                    ?: "build/manual-v08-wan-safety")
                Files.createDirectories(output)
                Files.writeString(output.resolve("wan-library-" + librarySize + ".json"), record.toString())
                println("WAN_V08_R5_SAFETY=" + record)
                // A loss on forced opponent-search triggers is not controllable by this
                // decision guard; this checks only voluntary X selection at casting.
                check(declaredX == null || declaredX / 2 <= librarySize) {
                    "R5 policy voluntarily selected unsafe mandatory Wan draw: " + record
                }
            }
        }
        test("unchanged v0.7 source, exact v0.8 one-card swap and isolated opt-in profile") {
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first {
                Files.exists(it.resolve("settings.gradle.kts"))
            }
            fun read(path: String): List<String> =
                Files.readAllLines(root.resolve(path)).flatMap { line ->
                    Regex("^(\\d+) (.+)$").matchEntire(line)?.let { m ->
                        List(m.groupValues[1].toInt()) { m.groupValues[2] }
                    } ?: emptyList()
                }
            val baseline = read("experiments/manual-transmission/control/v0.7.decklist.txt")
            val challenger = read("gameplay/manual-v08-wan/CHALLENGER.decklist.txt")
            baseline.size shouldBe 100
            challenger.size shouldBe 100
            val delta = baseline.toMutableList()
            check(delta.remove("Lifecrafter’s Bestiary"))
            delta.add("Wan Shi Tong, Librarian")
            challenger.sorted() shouldBe delta.sorted()
            ManualTransmissionV08WanCandidate.profile.id shouldBe "manual-v08-wan-shi-tong-opt-in"
            ManualTransmissionV08WanCandidate.profile.advisorModules shouldBe
                ManualTransmissionR5Candidate.profile.advisorModules
            AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
        }
    }
}
