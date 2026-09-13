package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.ai.insight.FriendlyRemovalAudit
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.gym.telemetry.ActionableManaBottleneck
import com.wingedsheep.gym.telemetry.ActionableManaBottleneckTracker
import com.wingedsheep.gym.telemetry.ManaConstraint
import com.wingedsheep.gym.telemetry.LandUnlockedSpell
import com.wingedsheep.gym.telemetry.PreSpellSetupClassification
import com.wingedsheep.gym.telemetry.PreSpellSetupTelemetry
import com.wingedsheep.gym.telemetry.PreSpellSetupEvaluation
import com.wingedsheep.gym.telemetry.SacrificeManaTrace
import com.wingedsheep.gym.telemetry.SacrificeManaUse
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes

private const val PEST_GOLDFISH_HORIZON = 20
private const val PEST_FRESH_SEED_SHA256 = "50d831076ff08c5df70aaa21e6edf7deaf9c269eeb74f0b5f9981b8be51caad8"
private const val PEST_PERFORMANCE_SEED_SHA256 = "c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513"
private const val PEST_UNTOUCHED_SEED_SHA256 = "f7012b5807621453692699055c90037bcf44526b4bc59edb37205c221aff9365"
private const val PEST_SAMPLE_2_SEED_SHA256 = "1e4247fceaa9a7d2f438ab8fa29733782f624cbcde383ec858153de1367e522d"
private const val PEST_SAMPLE_2_TAKE_2_SEED_SHA256 = "1db3fb1fcf4b969d9229bb2a060491a556ff5e1c8c80d327caf37698ca1c3cb7"
private const val PEST_SAMPLE_2_TAKE_3_SEED_SHA256 = "341fc7936a8a415d19d198662d1f6f2b2120ecb70d055a3a7a6fb76dd1ec8c47"
private const val PEST_SAMPLE_2_TAKE_4_SEED_SHA256 = "9f53ae30fd233bc8a980163aff9a7df1d6b41095356072e520a2cad16f97a7ad"
private const val PEST_SAMPLE_2_TAKE_5_SEED_SHA256 = "4e239b8b76df587ec3e14f05f564a288480de42abcd681670c7330323631c1f0"
private const val PEST_SAMPLE_2_TAKE_6_SEED_SHA256 = "79659bf0a8823c94e288b2df46f246385d02dba236ce3b70f9a4c6dc477eb79b"
private const val PEST_CONTROL_V10_SHA256 = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"

/** SHARED ARGENTUM CHANGE: yes — mandatory audit fields are never default-elided. */
internal val PEST_ARTIFACT_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
}

/** Pest Control-owned development goldfish. It is opt-in and never runs in ordinary CI. */
class PestControlGoldfishTest : FunSpec({
    test("frozen Pest Control v1.0 and Sample 1 seed vector are exact") {
        pestControlDeck().cards.groupingBy { it }.eachCount() shouldBe PEST_CONTROL_V10
        val retired = readPestSeeds()
        val fresh = readFreshPestSeeds()
        val performance = readPerformancePestSeeds()
        val untouched = readUntouchedPestSeeds()
        val sample2 = readSample2PestSeeds()
        val sample2Take2 = readSample2Take2PestSeeds()
        val sample2Take3 = readSample2Take3PestSeeds()
        val sample2Take4 = readSample2Take4PestSeeds()
        val sample2Take5 = readSample2Take5PestSeeds()
        val sample2Take6 = readSample2Take6PestSeeds()
        pestControlDeckSha256() shouldBe PEST_CONTROL_V10_SHA256
        retired.size shouldBe 30
        retired.distinct().size shouldBe 30
        fresh.size shouldBe 30
        fresh.distinct().size shouldBe 30
        fresh.intersect(retired.toSet()) shouldBe emptySet()
        seedVectorSha256(fresh) shouldBe PEST_FRESH_SEED_SHA256
        performance.size shouldBe 30
        performance.distinct().size shouldBe 30
        performance.intersect(retired.toSet()) shouldBe emptySet()
        performance.intersect(fresh.toSet()) shouldBe emptySet()
        seedVectorSha256(performance) shouldBe PEST_PERFORMANCE_SEED_SHA256
        untouched.size shouldBe 30
        untouched.distinct().size shouldBe 30
        untouched.intersect(retired.toSet()) shouldBe emptySet()
        untouched.intersect(fresh.toSet()) shouldBe emptySet()
        untouched.intersect(performance.toSet()) shouldBe emptySet()
        seedVectorSha256(untouched) shouldBe PEST_UNTOUCHED_SEED_SHA256
        sample2.size shouldBe 30
        sample2.distinct().size shouldBe 30
        sample2.intersect(retired.toSet()) shouldBe emptySet()
        sample2.intersect(fresh.toSet()) shouldBe emptySet()
        sample2.intersect(performance.toSet()) shouldBe emptySet()
        sample2.intersect(untouched.toSet()) shouldBe emptySet()
        seedVectorSha256(sample2) shouldBe PEST_SAMPLE_2_SEED_SHA256
        sample2Take2.size shouldBe 30
        sample2Take2.distinct().size shouldBe 30
        sample2Take2.intersect(retired.toSet()) shouldBe emptySet()
        sample2Take2.intersect(fresh.toSet()) shouldBe emptySet()
        sample2Take2.intersect(performance.toSet()) shouldBe emptySet()
        sample2Take2.intersect(untouched.toSet()) shouldBe emptySet()
        sample2Take2.intersect(sample2.toSet()) shouldBe emptySet()
        seedVectorSha256(sample2Take2) shouldBe PEST_SAMPLE_2_TAKE_2_SEED_SHA256
        sample2Take3.size shouldBe 30
        sample2Take3.distinct().size shouldBe 30
        sample2Take3.intersect(retired.toSet()) shouldBe emptySet()
        sample2Take3.intersect(fresh.toSet()) shouldBe emptySet()
        sample2Take3.intersect(performance.toSet()) shouldBe emptySet()
        sample2Take3.intersect(untouched.toSet()) shouldBe emptySet()
        sample2Take3.intersect(sample2.toSet()) shouldBe emptySet()
        sample2Take3.intersect(sample2Take2.toSet()) shouldBe emptySet()
        seedVectorSha256(sample2Take3) shouldBe PEST_SAMPLE_2_TAKE_3_SEED_SHA256
        sample2Take4.size shouldBe 30
        sample2Take4.distinct().size shouldBe 30
        sample2Take4.intersect(retired.toSet()) shouldBe emptySet()
        sample2Take4.intersect(fresh.toSet()) shouldBe emptySet()
        sample2Take4.intersect(performance.toSet()) shouldBe emptySet()
        sample2Take4.intersect(untouched.toSet()) shouldBe emptySet()
        sample2Take4.intersect(sample2.toSet()) shouldBe emptySet()
        sample2Take4.intersect(sample2Take2.toSet()) shouldBe emptySet()
        sample2Take4.intersect(sample2Take3.toSet()) shouldBe emptySet()
        seedVectorSha256(sample2Take4) shouldBe PEST_SAMPLE_2_TAKE_4_SEED_SHA256
        sample2Take5.size shouldBe 30
        sample2Take5.distinct().size shouldBe 30
        sample2Take5.intersect(retired.toSet()) shouldBe emptySet()
        sample2Take5.intersect(fresh.toSet()) shouldBe emptySet()
        sample2Take5.intersect(performance.toSet()) shouldBe emptySet()
        sample2Take5.intersect(untouched.toSet()) shouldBe emptySet()
        sample2Take5.intersect(sample2.toSet()) shouldBe emptySet()
        sample2Take5.intersect(sample2Take2.toSet()) shouldBe emptySet()
        sample2Take5.intersect(sample2Take3.toSet()) shouldBe emptySet()
        sample2Take5.intersect(sample2Take4.toSet()) shouldBe emptySet()
        seedVectorSha256(sample2Take5) shouldBe PEST_SAMPLE_2_TAKE_5_SEED_SHA256
        sample2Take6.size shouldBe 30
        sample2Take6.distinct().size shouldBe 30
        sample2Take6.intersect(retired.toSet()) shouldBe emptySet()
        sample2Take6.intersect(fresh.toSet()) shouldBe emptySet()
        sample2Take6.intersect(performance.toSet()) shouldBe emptySet()
        sample2Take6.intersect(untouched.toSet()) shouldBe emptySet()
        sample2Take6.intersect(sample2.toSet()) shouldBe emptySet()
        sample2Take6.intersect(sample2Take2.toSet()) shouldBe emptySet()
        sample2Take6.intersect(sample2Take3.toSet()) shouldBe emptySet()
        sample2Take6.intersect(sample2Take4.toSet()) shouldBe emptySet()
        sample2Take6.intersect(sample2Take5.toSet()) shouldBe emptySet()
        seedVectorSha256(sample2Take6) shouldBe PEST_SAMPLE_2_TAKE_6_SEED_SHA256
    }

    test("reproduce rejected Pest Control Game 25 Scion provenance").config(
        enabled = false, // Permanently retired vector; preserved as historical executable documentation only.
        timeout = 10.minutes,
    ) {
        val game = runPestGoldfish(pestRegistry(), readPestSeeds()[24], 25)
        println(PEST_ARTIFACT_JSON.encodeToString(game))
    }

    test("Pest Control v1.0 rejected Sample 1 regression replay").config(
        enabled = false, // Permanently retired vector; must never be replayed.
        timeout = 60.minutes,
    ) {
        val seeds = readPestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed ->
            runPestGoldfish(registry, seed, index + 1)
        }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("build", "reports", "pest-control-goldfish")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("pest-control-v10-goldfish-sample-1-regression-replay.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(block)
        Files.writeString(reportDir.resolve("pest-control-v10-goldfish-sample-1-regression-replay.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 fresh Goldfish Sample 1").config(
        enabled = false, // Rejected execution and accepted replay are both preserved; vector is retired.
        timeout = 60.minutes,
    ) {
        val seeds = readFreshPestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed -> runPestGoldfish(registry, seed, index + 1) }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("build", "reports", "pest-control-goldfish")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("pest-control-v10-goldfish-sample-1-fresh.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(block, freshPerformanceSample = true)
        Files.writeString(reportDir.resolve("pest-control-v10-goldfish-sample-1-fresh.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 1 fresh performance sample").config(
        enabled = false, // Rejected original and replay; vector is permanently retired from execution.
        timeout = 60.minutes,
    ) {
        val seeds = readPerformancePestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed -> runPestGoldfish(registry, seed, index + 1) }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("build", "reports", "pest-control-goldfish")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("pest-control-v10-goldfish-sample-1-performance.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(block, freshPerformanceSample = true)
        Files.writeString(reportDir.resolve("pest-control-v10-goldfish-sample-1-performance.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 1 untouched performance vector").config(
        enabled = false, // Accepted Sample #1; frozen vector must never be executed again.
        timeout = 60.minutes,
    ) {
        val seeds = readUntouchedPestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed -> runPestGoldfish(registry, seed, index + 1) }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("..", "docs", "experiments", "pest-control")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("goldfish-sample-1-untouched-raw.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(block, freshPerformanceSample = true)
        Files.writeString(reportDir.resolve("goldfish-sample-1-untouched-report.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 2 independent replication").config(
        enabled = false, // Rejected Sample #2; vector is permanently retired from execution.
        timeout = 60.minutes,
    ) {
        val seeds = readSample2PestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed -> runPestGoldfish(registry, seed, index + 1) }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("..", "docs", "experiments", "pest-control")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("goldfish-sample-2-raw.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(
            block,
            freshPerformanceSample = true,
            sampleLabel = "Goldfish Sample #2 — Independent Replication",
        )
        Files.writeString(reportDir.resolve("goldfish-sample-2-report.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 2 Take 2 independent replication").config(
        enabled = false, // Rejected Take 2; vector is permanently retired from execution.
        timeout = 60.minutes,
    ) {
        val seeds = readSample2Take2PestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed -> runPestGoldfish(registry, seed, index + 1) }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("..", "docs", "experiments", "pest-control")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("goldfish-sample-2-take-2-raw.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(
            block,
            freshPerformanceSample = true,
            sampleLabel = "Goldfish Sample #2 — Take 2 Independent Replication",
        )
        Files.writeString(reportDir.resolve("goldfish-sample-2-take-2-report.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 2 Take 3 independent replication").config(
        enabled = false, // Rejected Take 3; vector is permanently retired and must never be replayed.
        timeout = 60.minutes,
    ) {
        val seeds = readSample2Take3PestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed -> runPestGoldfish(registry, seed, index + 1) }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("..", "docs", "experiments", "pest-control")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("goldfish-sample-2-take-3-raw.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(
            block,
            freshPerformanceSample = true,
            sampleLabel = "Goldfish Sample #2 — Take 3 Independent Replication",
        )
        Files.writeString(reportDir.resolve("goldfish-sample-2-take-3-report.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 2 Take 4 independent replication").config(
        enabled = false, // Rejected Take 4; vector is permanently retired and must never be replayed.
        timeout = 60.minutes,
    ) {
        val seeds = readSample2Take4PestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed ->
            runPestGoldfish(registry, seed, index + 1).let { game ->
                game.copy(auditErrors = game.auditErrors + pestAuditCompletenessErrors(game))
            }
        }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("..", "docs", "experiments", "pest-control")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("goldfish-sample-2-take-4-raw.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(
            block,
            freshPerformanceSample = true,
            sampleLabel = "Goldfish Sample #2 — Take 4 Independent Replication",
        )
        Files.writeString(reportDir.resolve("goldfish-sample-2-take-4-report.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 2 Take 5 independent replication").config(
        enabled = false, // Take 5 executed exactly once; vector is permanently retired and must never be replayed.
        timeout = 60.minutes,
    ) {
        val seeds = readSample2Take5PestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed ->
            runPestGoldfish(registry, seed, index + 1).let { game ->
                game.copy(auditErrors = game.auditErrors + pestAuditCompletenessErrors(game))
            }
        }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("..", "docs", "experiments", "pest-control")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("goldfish-sample-2-take-5-raw.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(
            block,
            freshPerformanceSample = true,
            sampleLabel = "Goldfish Sample #2 — Take 5 Independent Replication",
        )
        Files.writeString(reportDir.resolve("goldfish-sample-2-take-5-report.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }

    test("Pest Control v1.0 Goldfish Sample 2 Take 6 independent replication").config(
        enabled = false, // Frozen but unexecuted; enable only for the single authorized invocation after remote-green freeze validation.
        timeout = 60.minutes,
    ) {
        val seeds = readSample2Take6PestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed ->
            runPestGoldfish(registry, seed, index + 1).let { game ->
                game.copy(auditErrors = game.auditErrors + pestAuditCompletenessErrors(game))
            }
        }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("..", "docs", "experiments", "pest-control")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("goldfish-sample-2-take-6-raw.json"),
            PEST_ARTIFACT_JSON.encodeToString(block),
        )
        val markdown = renderPestMarkdown(
            block,
            freshPerformanceSample = true,
            sampleLabel = "Goldfish Sample #2 — Take 6 Independent Replication",
        )
        Files.writeString(reportDir.resolve("goldfish-sample-2-take-6-report.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }
})

private val PEST_CONTROL_V10 = linkedMapOf(
    "Essence Warden" to 4,
    "Carrier Thrall" to 4,
    "Blood Researcher" to 4,
    "Pest Mascot" to 4,
    "Fierce Witchstalker" to 4,
    "Generous Ent" to 3,
    "Follow the Lumarets" to 4,
    "Weather the Storm" to 4,
    "Cast Down" to 4,
    "Bone Shards" to 2,
    "Chainer's Edict" to 2,
    "Forest" to 10,
    "Swamp" to 7,
    "Jungle Hollow" to 4,
)

private fun pestControlDeck(): Deck = Deck.of(*PEST_CONTROL_V10.map { it.key to it.value }.toTypedArray())

private fun readPestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-1-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.substringAfterLast(',').toLong() }

private fun readFreshPestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-1-fresh-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readPerformancePestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-1-performance-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readUntouchedPestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-1-untouched-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readSample2PestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-2-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readSample2Take2PestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-2-take-2-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readSample2Take3PestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-2-take-3-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readSample2Take4PestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-2-take-4-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readSample2Take5PestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-2-take-5-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun readSample2Take6PestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-2-take-6-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.split(',')[1].toLong() }

private fun seedVectorSha256(seeds: List<Long>): String = MessageDigest.getInstance("SHA-256")
    .digest((seeds.joinToString("\n") + "\n").toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun pestControlDeckSha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(PEST_CONTROL_V10.entries.joinToString(separator = "\n", postfix = "\n") { (name, count) -> "$name,$count" }.toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun pestRegistry(): CardRegistry = CardRegistry().apply {
    register(PredefinedTokens.allTokens)
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}

@Serializable
internal data class PestGoldfishBlock(
    val deckVersion: String,
    val agentProfile: String,
    val horizon: Int,
    val seeds: List<Long>,
    val games: List<PestGoldfishGame>,
    val summary: PestGoldfishSummary,
)

@Serializable
internal data class PestOpeningAccess(
    val keptHand: List<String>,
    val lands: List<String>,
    val green: Boolean,
    val black: Boolean,
    val untappedGreen: Boolean,
    val untappedBlack: Boolean,
    val entForestcyclingAvailable: Boolean,
)

@Serializable
internal data class PestWeatherCast(
    val turn: Int,
    val stormCount: Int,
    val expectedCopies: Int,
    val observedCopies: Int,
    val lifeBeforeCast: Int,
    val availableManaBeforeCast: Int,
    val handBeforeCast: List<String>,
    val actionsBeforeCastThisTurn: List<String>,
    val researcherPresent: Boolean,
    val mascotPresent: Boolean,
    val followAvailable: Boolean,
    val survivalRequired: Boolean,
    val pendingStackSources: List<String>,
    val currentlyExecutablePreWeatherSpells: List<String>,
    val spellsExecutableAfterLegalLandPlay: List<LandUnlockedSpell>,
    val bestValidatedPreWeatherSetupSequence: List<String>?,
    val weatherCastBeforeSuperiorSetup: Boolean,
    val usefulSpellCastLaterThisTurn: String?,
    val stillUnexecutableAfterLegalLandPlay: List<LandUnlockedSpell> = emptyList(),
    val executableButNotMateriallySuperior: List<List<String>> = emptyList(),
    val evaluatedSetupSequences: List<PreSpellSetupEvaluation> = emptyList(),
)

@Serializable
internal data class PestLifeEvent(
    val turn: Int,
    val amount: Int,
    val source: String,
    val researcherPresent: Boolean,
    val mascotPresent: Boolean,
)

@Serializable
internal data class PestFollowCast(
    val turn: Int,
    val mode: String,
    val lifeEventsBeforeCastThisTurn: Int,
    val handBeforeCast: List<String>,
    val actionsBeforeCastThisTurn: List<String>,
    val availableLifeGainResources: List<String>,
    val pendingStackSources: List<String>,
)

@Serializable
internal data class PestPureLifeGainActivation(
    val turn: Int,
    val source: String,
    val lifeBeforeActivation: Int,
    val researcherPresent: Boolean,
    val mascotPresent: Boolean,
    val survivalRequired: Boolean,
    val pendingStackSources: List<String>,
)

@Serializable
internal data class PestTurnAction(val turn: Int, val description: String)

@Serializable
internal data class PestCreatureEntry(
    val turn: Int,
    val creature: String,
    val wardensAlreadyPresent: Int,
    val researchersPresent: Int,
    val mascotsPresent: Int,
)

@Serializable
internal data class PestCoexistence(
    val wardenResearcher: Boolean,
    val wardenMascot: Boolean,
    val researcherMascot: Boolean,
    val wardenResearcherMascot: Boolean,
    val payoffWhenWeatherResolved: Boolean,
    val payoffDuringMultipleCreatureEntryLifeEvents: Boolean,
)

@Serializable
internal data class PestGoldfishGame(
    val game: Int,
    val seed: Long,
    val seedHex: String,
    val mulligans: Int,
    val opening: PestOpeningAccess,
    val t1Development: List<String>,
    val firstMeaningfulPermanentTurn: Int?,
    val actualWinningTurn: Int?,
    val terminalMechanism: String?,
    val essenceWardenCastTurns: List<Int>,
    val bloodResearcherCastTurns: List<Int>,
    val pestMascotCastTurns: List<Int>,
    val carrierThrallCastTurns: List<Int>,
    val carrierThrallDeaths: Int,
    val scionsCreated: Int,
    val scionsSacrificedForMana: Int,
    val scionFundedSpells: List<String>,
    val scionManaUses: List<SacrificeManaUse>,
    val fierceWitchstalkerCastTurns: List<Int>,
    val generousEntCycleTurns: List<Int>,
    val generousEntCastTurns: List<Int>,
    val followCasts: List<PestFollowCast>,
    val weatherCasts: List<PestWeatherCast>,
    val friendlyRemovalAudits: List<FriendlyRemovalAudit> = emptyList(),
    val pureLifeGainActivations: List<PestPureLifeGainActivation>,
    val turnActions: List<PestTurnAction>,
    val creatureEntries: List<PestCreatureEntry>,
    val payoffWithoutWardenTurns: List<Int>,
    val wardenWithoutPayoffTurns: List<Int>,
    val lifeEvents: List<PestLifeEvent>,
    val totalLifeGained: Int,
    val researcherCounterTriggers: Int,
    val researcherCountersAdded: Int,
    val mascotCounterTriggers: Int,
    val mascotCountersAdded: Int,
    val maximumResearcherPower: Int?,
    val maximumResearcherToughness: Int?,
    val maximumMascotPower: Int?,
    val maximumMascotToughness: Int?,
    val largestCreatureBattlefield: Int,
    val largestPermanentBattlefield: Int,
    val solitaireStrandedInteraction: List<String>,
    val genuineManaBottlenecks: List<ActionableManaBottleneck>,
    val jungleHollowTempoEvents: List<String>,
    val coexistence: PestCoexistence,
    val functionalState: String,
    val actions: Int,
    val stopReason: String,
    val auditErrors: List<String>,
)

/**
 * Take 4's predeclared observability gate. It validates the semantic completeness of every
 * sequencing evaluation and friendly-removal valuation before any gameplay result is interpreted.
 */
internal fun pestAuditCompletenessErrors(game: PestGoldfishGame): List<String> = buildList {
    fun fail(subject: String, detail: String) {
        add("audit completeness Game ${game.game} $subject: $detail")
    }

    fun validateResources(subject: String, resources: com.wingedsheep.gym.telemetry.SetupResourceState) {
        val colors = setOf("white", "blue", "black", "red", "green", "colorless")
        if (resources.floatingMana.keys != colors) fail(subject, "floating-mana colors are incomplete")
        if (resources.floatingMana.values.any { it < 0 }) fail(subject, "floating mana contains a negative value")
        if (resources.untappedManaSourceCount != resources.manaSources.count { !it.tapped }) {
            fail(subject, "untapped mana-source count does not match source records")
        }
        if (resources.manaSources.any { it.name.isBlank() }) fail(subject, "mana source lacks an identity")
        if (resources.handSize < 0 || resources.spellsCastThisTurn < 0) fail(subject, "resource count is negative")
    }

    game.weatherCasts.forEachIndexed { weatherIndex, weather ->
        val subject = "Weather ${weatherIndex + 1} on T${weather.turn}"
        val evaluations = weather.evaluatedSetupSequences
        weather.currentlyExecutablePreWeatherSpells.forEach { action ->
            if (evaluations.none {
                    it.relevantAction == action &&
                        PreSpellSetupClassification.CURRENTLY_EXECUTABLE in it.classifications
                }) fail(subject, "currently-executable action '$action' lacks a structured evaluation")
        }
        weather.spellsExecutableAfterLegalLandPlay.forEach { opportunity ->
            if (evaluations.none {
                    it.relevantAction == opportunity.spell && it.proposedLandPlay == opportunity.land &&
                        PreSpellSetupClassification.LAND_UNLOCKED in it.classifications &&
                        PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE in it.classifications
                }) fail(subject, "land-unlocked action '${opportunity.land} -> ${opportunity.spell}' lacks a structured evaluation")
        }
        weather.stillUnexecutableAfterLegalLandPlay.forEach { opportunity ->
            if (evaluations.none {
                    it.relevantAction == opportunity.spell && it.proposedLandPlay == opportunity.land &&
                        it.classifications == listOf(PreSpellSetupClassification.STILL_UNEXECUTABLE_AFTER_LAND)
                }) fail(subject, "still-unexecutable action '${opportunity.land} -> ${opportunity.spell}' lacks a structured evaluation")
        }
        weather.executableButNotMateriallySuperior.forEach { sequence ->
            if (evaluations.none {
                    it.setupPrefix == sequence &&
                        PreSpellSetupClassification.EXECUTABLE_BUT_NOT_MATERIALLY_SUPERIOR in it.classifications
                }) fail(subject, "non-superior sequence '$sequence' lacks a structured evaluation")
        }
        weather.bestValidatedPreWeatherSetupSequence?.let { sequence ->
            if (evaluations.none {
                    it.completeSetupContinuation == sequence &&
                        PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE in it.classifications
                }) fail(subject, "best missed-superior sequence lacks a structured evaluation")
        }

        evaluations.forEachIndexed { evaluationIndex, evaluation ->
            val evaluationSubject = "$subject evaluation ${evaluationIndex + 1}"
            if (evaluation.turn != weather.turn) fail(evaluationSubject, "turn is absent or inconsistent")
            if (evaluation.relevantAction.isBlank()) fail(evaluationSubject, "relevant action is absent")
            if (evaluation.classifications.isEmpty() || evaluation.classifications.distinct().size != evaluation.classifications.size) {
                fail(evaluationSubject, "classification is absent or duplicated")
            }
            val positionClasses = evaluation.classifications.count {
                it in setOf(
                    PreSpellSetupClassification.CURRENTLY_EXECUTABLE,
                    PreSpellSetupClassification.LAND_UNLOCKED,
                    PreSpellSetupClassification.STILL_UNEXECUTABLE_AFTER_LAND,
                )
            }
            if (positionClasses != 1) fail(evaluationSubject, "does not preserve exactly one executability classification")
            val stillUnexecutable = PreSpellSetupClassification.STILL_UNEXECUTABLE_AFTER_LAND in evaluation.classifications
            val superiorityClasses = evaluation.classifications.count {
                it in setOf(
                    PreSpellSetupClassification.EXECUTABLE_BUT_NOT_MATERIALLY_SUPERIOR,
                    PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE,
                )
            }
            if (superiorityClasses != if (stillUnexecutable) 0 else 1) {
                fail(evaluationSubject, "material-superiority classification is incomplete")
            }
            if (evaluation.materiallySuperior !=
                (PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE in evaluation.classifications)
            ) fail(evaluationSubject, "material-superiority result disagrees with classification")
            if (PreSpellSetupClassification.LAND_UNLOCKED in evaluation.classifications || stillUnexecutable) {
                if (evaluation.proposedLandPlay.isNullOrBlank()) fail(evaluationSubject, "proposed land is absent")
                if (evaluation.landEntersTapped == null) fail(evaluationSubject, "land tapped/untapped result is absent")
            }
            if (evaluation.setupPrefix.isEmpty()) fail(evaluationSubject, "setup prefix is absent")
            if (evaluation.proposedActionOrder != if (stillUnexecutable) evaluation.setupPrefix else evaluation.completeSetupContinuation) {
                fail(evaluationSubject, "proposed action order does not identify its equivalent complete sequence")
            }
            if (!stillUnexecutable && evaluation.completeSetupContinuation.isEmpty()) {
                fail(evaluationSubject, "complete setup continuation is absent")
            }
            if (stillUnexecutable && evaluation.completeSetupContinuation.isNotEmpty()) {
                fail(evaluationSubject, "unexecutable setup invents a complete continuation")
            }
            if (evaluation.actualLineTaken.isEmpty()) fail(evaluationSubject, "actual line is absent")
            if (!stillUnexecutable && evaluation.counterfactualLineEvaluated != evaluation.completeSetupContinuation) {
                fail(evaluationSubject, "counterfactual is not the complete setup continuation")
            }
            if (!stillUnexecutable && evaluation.comparisonLineEvaluated.isEmpty()) {
                fail(evaluationSubject, "complete comparison line is absent")
            }
            if (evaluation.steps.isEmpty()) fail(evaluationSubject, "structured action steps are absent")
            if (evaluation.reason.isBlank()) fail(evaluationSubject, "classification reason is absent")
            if (!stillUnexecutable) {
                if (evaluation.completedLineScore?.isFinite() != true) fail(evaluationSubject, "completed-line score is absent")
                if (evaluation.reorderedLineScore?.isFinite() != true) fail(evaluationSubject, "actual/reordered-line score is absent")
            }
            evaluation.steps.forEachIndexed { stepIndex, step ->
                val stepSubject = "$evaluationSubject step ${stepIndex + 1}"
                if (step.action.isBlank()) fail(stepSubject, "action is absent")
                validateResources("$stepSubject before", step.resourcesBefore)
                step.resourcesAfter?.let { validateResources("$stepSubject after", it) }
                if (step.action.startsWith("cast ")) {
                    if (step.manaCost.isNullOrBlank()) fail(stepSubject, "mana cost is absent")
                    if (step.coloredRequirements.isNullOrBlank()) fail(stepSubject, "colored requirements are absent")
                    val payable = step.paymentSources.isNotEmpty() || step.resourcesBefore.floatingMana.values.sum() > 0
                    if (!stillUnexecutable && !payable) fail(stepSubject, "source-specific payment plan is absent")
                    if (!stillUnexecutable && step.resourcesAfter == null) fail(stepSubject, "remaining resources are absent")
                    if (step.targets.size != step.targetDetails.size ||
                        step.targetDetails.map { it.id } != step.targets
                    ) fail(stepSubject, "target identities are incomplete")
                    if (step.additionalCostMode == "none" && step.additionalCosts.isNotEmpty()) {
                        fail(stepSubject, "additional-cost mode disagrees with payment")
                    }
                    step.additionalCosts.forEach { cost ->
                        if (cost.kind.isBlank()) fail(stepSubject, "additional-cost kind is absent")
                        if (cost.entities.isEmpty() && cost.amount == null) fail(stepSubject, "additional-cost payment is absent")
                        if (cost.entities.any { it.name.isBlank() }) fail(stepSubject, "additional-cost identity is absent")
                    }
                }
            }
        }
    }

    game.friendlyRemovalAudits.forEachIndexed { auditIndex, audit ->
        val subject = "friendly-removal evaluation ${auditIndex + 1} on engine turn ${audit.turnNumber}"
        if (audit.removalAction.isBlank()) fail(subject, "removal action is absent")
        if (audit.targetName.isBlank()) fail(subject, "target identity is absent")
        if (audit.manaCost.isNullOrBlank()) fail(subject, "mana cost is absent")
        if (audit.manaSources.any { it.name.isBlank() }) fail(subject, "mana-source identity is absent")
        if (audit.lifePaid < 0) fail(subject, "life cost is invalid")
        if (audit.additionalCostMode.isBlank()) fail(subject, "additional-cost mode is absent")
        audit.additionalCosts.forEach { cost ->
            if (cost.kind.isBlank()) fail(subject, "additional-cost kind is absent")
            if (cost.entities.isEmpty() && cost.amount == null) fail(subject, "additional-cost payment is absent")
            if (cost.entities.any { it.name.isBlank() }) fail(subject, "additional-cost entity identity is absent")
        }
        val values = listOf(
            audit.targetBattlefieldValueBefore,
            audit.removalResourceValueConsumed,
            audit.futureInteractionOpportunityCost,
            audit.resultingBoardValue,
            audit.passHoldValue,
            audit.resolvedLineValue,
            audit.netVersusHold,
            audit.requiredFairTradeMargin,
            audit.fairTradeSurplus,
        )
        if (values.any { !it.isFinite() }) fail(subject, "valuation contains a non-finite value")
        if (audit.removalResourceValueConsumed < 0.0 || audit.futureInteractionOpportunityCost < 0.0) {
            fail(subject, "resource or future-interaction cost is invalid")
        }
        if (audit.deathTriggersCreated.any(String::isBlank)) fail(subject, "death-trigger identity is absent")
        if (audit.resourcesCreated.any { it.name.isBlank() }) fail(subject, "created-resource identity is absent")
        if (audit.immediateEngineEffects.any(String::isBlank)) fail(subject, "engine-effect identity is absent")
        if (audit.opposingTargetAlternatives.any { it.name.isBlank() || it.controllerId == null || it.battlefieldValue == null }) {
            fail(subject, "opposing-target alternative is incomplete")
        }
        if (audit.friendlyTargetAlternatives.any { it.name.isBlank() || it.controllerId == null || it.battlefieldValue == null }) {
            fail(subject, "friendly-target alternative is incomplete")
        }
        if (audit.resultingBoardState.any { it.name.isBlank() || it.controllerId == null || it.battlefieldValue == null }) {
            fail(subject, "resulting board state is incomplete")
        }
        if (audit.policyDisposition.isBlank()) fail(subject, "fair-trade disposition is absent")
        if (audit.selectionReason.isNullOrBlank()) fail(subject, "selection/rejection reason is absent")
    }
}

@Serializable
internal data class PestGoldfishSummary(
    val games: Int,
    val openingColorAccessDistribution: Map<String, Int>,
    val mulliganGames: Int,
    val totalMulligans: Int,
    val mulliganRate: Double,
    val meaningfulDevelopmentByT1: Int,
    val meaningfulDevelopmentByT2: Int,
    val meaningfulDevelopmentByT3: Int,
    val medianFirstWarden: Double?,
    val medianFirstPayoff: Double?,
    val medianActualWinningTurn: Double?,
    val winsByT4: Int,
    val winsByT5: Int,
    val winsByT6: Int,
    val winsByT7: Int,
    val totalLifeEvents: Int,
    val totalLifeGained: Int,
    val averageLifeEvents: Double,
    val averageLifeGained: Double,
    val researcherCounterTriggers: Int,
    val researcherCountersAdded: Int,
    val mascotCounterTriggers: Int,
    val mascotCountersAdded: Int,
    val weatherStormCountDistribution: Map<Int, Int>,
    val researcherMaximumSizeDistribution: Map<String, Int>,
    val mascotMaximumSizeDistribution: Map<String, Int>,
    val enginePayoffCoexistenceGames: Int,
    val enginePayoffCoexistenceRate: Double,
    val allThreeCoexistenceGames: Int,
    val payoffWhenWeatherResolvedGames: Int,
    val carrierDeaths: Int,
    val scionsCreated: Int,
    val scionsSacrificedForMana: Int,
    val scionFundedSpells: Map<String, Int>,
    val entCycles: Int,
    val entCreatureCasts: Int,
    val entCyclingRate: Double?,
    val followNormalCasts: Int,
    val followEnhancedCasts: Int,
    val followEnhancedRate: Double?,
    val manaBottleneckGames: Int,
    val manaBottleneckObservations: Int,
    val manaConstraintDistribution: Map<String, Int>,
    val hollowTempoGames: Int,
    val hollowTempoEvents: Int,
    val hollowProximateDelayGames: Int,
    val hollowProximateDelayEvents: Int,
    val solitaireInteractionConstrainedGames: Int,
    val strandedInteractionObservations: Int,
    val functionalStateDistribution: Map<String, Int>,
    val lifeEventsWithPayoffPresent: Int,
    val payoffWithoutWardenGames: Int,
    val payoffWithoutWardenTurns: Int,
    val wardenWithoutPayoffGames: Int,
    val wardenWithoutPayoffTurns: Int,
    val additionalWardenOpportunityGames: Int,
    val additionalWardenOpportunityEntries: Int,
    val additionalWardenPotentialResearcherCounters: Int,
    val additionalWardenPotentialMascotCounters: Int,
    val weatherStormZeroCasts: Int,
    val weatherStormZeroWithLaterUsefulSpell: Int,
    val followNormalWithoutPriorLifeGain: Int,
)

internal data class PestWardenOpportunity(
    val entries: Int,
    val potentialResearcherCounters: Int,
    val potentialMascotCounters: Int,
)

internal fun wardenCounterfactualOpportunity(entries: List<PestCreatureEntry>): PestWardenOpportunity {
    val qualifying = entries.filter {
        it.wardensAlreadyPresent == 0 && it.researchersPresent + it.mascotsPresent > 0
    }
    return PestWardenOpportunity(
        entries = qualifying.size,
        potentialResearcherCounters = qualifying.sumOf(PestCreatureEntry::researchersPresent),
        potentialMascotCounters = qualifying.sumOf(PestCreatureEntry::mascotsPresent),
    )
}

private data class MutableWeather(
    val turn: Int,
    val stormCount: Int,
    val expectedCopies: Int,
    val lifeBeforeCast: Int,
    val availableManaBeforeCast: Int,
    val handBeforeCast: List<String>,
    val actionsBeforeCastThisTurn: List<String>,
    val researcherPresent: Boolean,
    val mascotPresent: Boolean,
    val followAvailable: Boolean,
    val survivalRequired: Boolean,
    val pendingStackSources: List<String>,
    val currentlyExecutablePreWeatherSpells: List<String>,
    val spellsExecutableAfterLegalLandPlay: List<LandUnlockedSpell>,
    val bestValidatedPreWeatherSetupSequence: List<String>?,
    val weatherCastBeforeSuperiorSetup: Boolean,
    val stillUnexecutableAfterLegalLandPlay: List<LandUnlockedSpell>,
    val executableButNotMateriallySuperior: List<List<String>>,
    val evaluatedSetupSequences: List<PreSpellSetupEvaluation>,
    val actionIndex: Int,
    var observedCopies: Int = 0,
)

internal fun runPestGoldfish(registry: CardRegistry, seed: Long, gameNumber: Int): PestGoldfishGame {
    val processor = ActionProcessor(registry)
    val init = GameInitializer(registry).initializeGame(
        GameConfig(
            players = listOf(
                PlayerConfig("Pest Control", pestControlDeck()),
                PlayerConfig("Blank Goldfish", Deck.of("Plains" to 60)),
            ),
            skipMulligans = false,
            useHandSmoother = false,
            startingPlayerIndex = 0,
            seed = seed,
        )
    )
    val pestId = init.playerIds[0]
    val blankId = init.playerIds[1]
    var state = init.state

    fun name(gameState: GameState, id: EntityId): String =
        gameState.getEntity(id)?.get<CardComponent>()?.name ?: id.toString()

    fun summaries(playerId: EntityId): Map<EntityId, CardSummary> = state.getHand(playerId).associateWith { id ->
        val card = state.getEntity(id)?.get<CardComponent>()
        CardSummary(
            name = card?.name ?: id.toString(),
            manaCost = card?.manaCost?.toString(),
            typeLine = card?.typeLine?.toString(),
            power = card?.baseStats?.basePower,
            toughness = card?.baseStats?.baseToughness,
            oracleText = card?.oracleText,
        )
    }

    val mulliganController = EngineAiPlayerController(registry, pestId, gameStateProvider = { state })
    for (playerId in state.turnOrder) {
        while (true) {
            val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
            val keep = if (playerId == pestId) {
                mulliganController.decideMulligan(
                    MulliganInfo(
                        hand = state.getHand(playerId),
                        mulliganCount = mulligan.mulligansTaken,
                        cardsToPutOnBottom = mulligan.cardsToBottom,
                        cards = summaries(playerId),
                        isOnThePlay = true,
                    )
                )
            } else true
            val processed = processor.process(state, if (keep) KeepHand(playerId) else TakeMulligan(playerId)).result
            check(processed.error == null) { "Mulligan rejected: ${processed.error}" }
            state = processed.state
            if (keep) break
        }
    }
    val mulligans = state.getEntity(pestId)!!.get<MulliganStateComponent>()!!.mulligansTaken
    for (playerId in state.turnOrder) {
        val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
        if (mulligan.cardsToBottom <= 0) continue
        val bottom = if (playerId == pestId) {
            mulliganController.chooseBottomCards(
                BottomCardsInfo(state.getHand(playerId), mulligan.cardsToBottom, summaries(playerId))
            )
        } else state.getHand(playerId).take(mulligan.cardsToBottom)
        val processed = processor.process(state, BottomCards(playerId, bottom)).result
        check(processed.error == null) { "Bottom cards rejected: ${processed.error}" }
        state = processed.state
    }

    val keptHand = state.getHand(pestId).map { name(state, it) }
    val openingLands = keptHand.filter { it in setOf("Forest", "Swamp", "Jungle Hollow") }
    val opening = PestOpeningAccess(
        keptHand = keptHand,
        lands = openingLands,
        green = openingLands.any { it == "Forest" || it == "Jungle Hollow" },
        black = openingLands.any { it == "Swamp" || it == "Jungle Hollow" },
        untappedGreen = "Forest" in openingLands,
        untappedBlack = "Swamp" in openingLands,
        entForestcyclingAvailable = "Generous Ent" in keptHand,
    )

    val environment = GameEnvironment.create(registry).also { it.restore(state, init.playerIds) }
    val friendlyRemovalAudits = mutableListOf<FriendlyRemovalAudit>()
    val pest = AIPlayer.create(
        registry, pestId, AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
        insightSink = { _, insight ->
            insight.options.mapNotNullTo(friendlyRemovalAudits) { it.friendlyRemovalAudit }
        },
    )
    val blank = AIPlayer.create(registry, blankId, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
    val bottleneckTracker = ActionableManaBottleneckTracker(registry)
    val sacrificeManaTrace = SacrificeManaTrace()
    val preSpellSetupTelemetry = PreSpellSetupTelemetry(registry)
    val manaSolver = ManaSolver(registry)
    val t1 = mutableListOf<String>()
    val wardenCasts = mutableListOf<Int>()
    val researcherCasts = mutableListOf<Int>()
    val mascotCasts = mutableListOf<Int>()
    val thrallCasts = mutableListOf<Int>()
    val witchCasts = mutableListOf<Int>()
    val entCycles = mutableListOf<Int>()
    val entCasts = mutableListOf<Int>()
    val follows = mutableListOf<PestFollowCast>()
    val weathers = mutableListOf<MutableWeather>()
    val pureLifeGainActivations = mutableListOf<PestPureLifeGainActivation>()
    val turnActions = mutableListOf<PestTurnAction>()
    val creatureEntries = mutableListOf<PestCreatureEntry>()
    val payoffWithoutWardenTurns = linkedSetOf<Int>()
    val wardenWithoutPayoffTurns = linkedSetOf<Int>()
    val lifeEvents = mutableListOf<PestLifeEvent>()
    val stranded = linkedSetOf<String>()
    val bottlenecks = mutableListOf<ActionableManaBottleneck>()
    val hollows = mutableListOf<String>()
    val audit = mutableListOf<String>()
    var thrallDeaths = 0
    var scionsCreated = 0
    var scionActivations = 0
    var scionSacrifices = 0
    var scionManaEvents = 0
    var researcherTriggers = 0
    var researcherCounters = 0
    var mascotTriggers = 0
    var mascotCounters = 0
    var maxResearcherPower: Int? = null
    var maxResearcherToughness: Int? = null
    var maxMascotPower: Int? = null
    var maxMascotToughness: Int? = null
    var maxCreatures = 0
    var maxPermanents = 0
    var firstPermanent: Int? = null
    var wardenResearcher = false
    var wardenMascot = false
    var researcherMascot = false
    var triple = false
    var payoffAtWeather = false
    var entryLifeEventsWithPayoff = 0
    var actions = 0
    var lastEngineTurn = -1
    var actionsThisEngineTurn = 0
    var stopReason = "T${PEST_GOLDFISH_HORIZON}_HORIZON"
    var winningTurn: Int? = null
    var terminal: String? = null

    fun pestTurn(gameState: GameState): Int = (gameState.turnNumber + 1) / 2

    fun battlefieldNames(gameState: GameState): List<String> =
        gameState.controlledBattlefield(pestId).map { name(gameState, it) }

    fun stackSourceName(gameState: GameState): String? {
        val top = gameState.stack.lastOrNull()?.let(gameState::getEntity) ?: return null
        return top.get<CardComponent>()?.name
            ?: top.get<ActivatedAbilityOnStackComponent>()?.sourceName
            ?: top.get<TriggeredAbilityOnStackComponent>()?.sourceName
    }

    fun stackSourceNames(gameState: GameState): List<String> = gameState.stack.mapNotNull { id ->
        val entity = gameState.getEntity(id) ?: return@mapNotNull null
        entity.get<CardComponent>()?.name
            ?: entity.get<ActivatedAbilityOnStackComponent>()?.sourceName
            ?: entity.get<TriggeredAbilityOnStackComponent>()?.sourceName
    }

    fun survivalRequiresLife(gameState: GameState): Boolean {
        val opposingPower = gameState.controlledBattlefield(blankId).sumOf { id ->
            val card = gameState.getEntity(id)?.get<CardComponent>()
            if (card?.isCreature == true) maxOf(0, gameState.projectedState.getPower(id) ?: 0) else 0
        }
        return opposingPower >= gameState.lifeTotal(pestId)
    }

    fun lifeGainResources(gameState: GameState): List<String> = buildList {
        gameState.getHand(pestId).forEach { id ->
            if (name(gameState, id) == "Weather the Storm") add("Weather the Storm in hand")
        }
        gameState.controlledBattlefield(pestId).forEach { id ->
            if (name(gameState, id) == "Food") add("Food on battlefield")
        }
    }

    fun observe(gameState: GameState) {
        val names = battlefieldNames(gameState)
        val hasWarden = "Essence Warden" in names
        val hasResearcher = "Blood Researcher" in names
        val hasMascot = "Pest Mascot" in names
        val presenceTurn = pestTurn(gameState)
        if ((hasResearcher || hasMascot) && !hasWarden) payoffWithoutWardenTurns += presenceTurn
        if (hasWarden && !hasResearcher && !hasMascot) wardenWithoutPayoffTurns += presenceTurn
        wardenResearcher = wardenResearcher || (hasWarden && hasResearcher)
        wardenMascot = wardenMascot || (hasWarden && hasMascot)
        researcherMascot = researcherMascot || (hasResearcher && hasMascot)
        triple = triple || (hasWarden && hasResearcher && hasMascot)
        val permanents = gameState.controlledBattlefield(pestId)
        maxPermanents = maxOf(maxPermanents, permanents.size)
        maxCreatures = maxOf(maxCreatures, permanents.count { gameState.getEntity(it)?.get<CardComponent>()?.isCreature == true })
        permanents.forEach { id ->
            when (name(gameState, id)) {
                "Blood Researcher" -> {
                    maxResearcherPower = maxOf(maxResearcherPower ?: Int.MIN_VALUE, gameState.projectedState.getPower(id) ?: 0)
                    maxResearcherToughness = maxOf(maxResearcherToughness ?: Int.MIN_VALUE, gameState.projectedState.getToughness(id) ?: 0)
                }
                "Pest Mascot" -> {
                    maxMascotPower = maxOf(maxMascotPower ?: Int.MIN_VALUE, gameState.projectedState.getPower(id) ?: 0)
                    maxMascotToughness = maxOf(maxMascotToughness ?: Int.MIN_VALUE, gameState.projectedState.getToughness(id) ?: 0)
                }
            }
        }
        if (gameState.priorityPlayerId == pestId && gameState.pendingDecision == null && gameState.stack.isEmpty() &&
            gameState.step in setOf(Step.PRECOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
        ) {
            val turn = pestTurn(gameState)
            val blankHasCreature = gameState.controlledBattlefield(blankId).any {
                gameState.getEntity(it)?.get<CardComponent>()?.isCreature == true
            }
            if (!blankHasCreature) {
                state.getHand(pestId).map { name(gameState, it) }.groupingBy { it }.eachCount()
                    .filterKeys { it in SOLITAIRE_INTERACTION }
                    .forEach { (card, count) -> stranded += "$card x$count@T$turn:NO_OPPONENT_CREATURE" }
            }
            bottlenecks += bottleneckTracker.observe(gameState, pestId, turn)
        }
    }

    observe(state)
    while (!state.gameOver && pestTurn(state) <= PEST_GOLDFISH_HORIZON && actions < 3_000) {
        if (state.turnNumber != lastEngineTurn) {
            lastEngineTurn = state.turnNumber
            actionsThisEngineTurn = 0
        }
        actionsThisEngineTurn++
        if (actionsThisEngineTurn > 250) {
            audit += "wedged on engine turn ${state.turnNumber}"
            stopReason = "WEDGED"
            break
        }
        val decision = state.pendingDecision
        val acting = decision?.playerId ?: state.priorityPlayerId
        if (acting == null) {
            audit += "no acting player on engine turn ${state.turnNumber}"
            stopReason = "NO_ACTOR"
            break
        }
        val turn = pestTurn(state)
        val action = if (decision != null) {
            SubmitDecision(acting, if (acting == pestId) pest.respondToDecision(state, decision) else blank.respondToDecision(state, decision))
        } else if (acting == pestId) pest.chooseAction(state) else blank.chooseAction(state)
        val actionName = when (action) {
            is CastSpell -> name(state, action.cardId)
            is PlayLand -> name(state, action.cardId)
            is ActivateAbility -> name(state, action.sourceId)
            is TypecycleCard -> name(state, action.cardId)
            is CycleCard -> name(state, action.cardId)
            else -> null
        }
        if (acting == pestId && decision == null && action !is PassPriority) {
            val description = when (action) {
                is CastSpell -> "cast $actionName"
                is PlayLand -> "play $actionName"
                is ActivateAbility -> "activate $actionName"
                is TypecycleCard -> "typecycle $actionName"
                is CycleCard -> "cycle $actionName"
                else -> action::class.simpleName ?: "action"
            }
            if (turn == 1) t1 += description
            turnActions += PestTurnAction(turn, description)
            if (action is CastSpell) when (actionName) {
                "Essence Warden" -> wardenCasts += turn
                "Blood Researcher" -> researcherCasts += turn
                "Pest Mascot" -> mascotCasts += turn
                "Carrier Thrall" -> thrallCasts += turn
                "Fierce Witchstalker" -> witchCasts += turn
                "Generous Ent" -> entCasts += turn
                "Follow the Lumarets" -> follows += PestFollowCast(
                    turn,
                    if (state.getEntity(pestId)?.has<LifeGainedThisTurnComponent>() == true) "ENHANCED" else "NORMAL",
                    lifeEvents.count { it.turn == turn },
                    state.getHand(pestId).map { name(state, it) },
                    turnActions.dropLast(1).filter { it.turn == turn }.map(PestTurnAction::description),
                    lifeGainResources(state),
                    stackSourceNames(state),
                )
                "Weather the Storm" -> {
                    val battlefield = battlefieldNames(state)
                    val available = manaSolver.getAvailableManaCount(state, pestId)
                    val setup = preSpellSetupTelemetry.observe(state, pestId, action.cardId)
                    weathers += MutableWeather(
                        turn = turn,
                        stormCount = state.spellsCastThisTurn,
                        expectedCopies = state.spellsCastThisTurn,
                        lifeBeforeCast = state.lifeTotal(pestId),
                        availableManaBeforeCast = available,
                        handBeforeCast = state.getHand(pestId).map { name(state, it) },
                        actionsBeforeCastThisTurn = turnActions.dropLast(1)
                            .filter { it.turn == turn }.map(PestTurnAction::description),
                        researcherPresent = "Blood Researcher" in battlefield,
                        mascotPresent = "Pest Mascot" in battlefield,
                        followAvailable = state.getHand(pestId).any { name(state, it) == "Follow the Lumarets" },
                        survivalRequired = survivalRequiresLife(state),
                        pendingStackSources = stackSourceNames(state),
                        currentlyExecutablePreWeatherSpells = setup.currentlyExecutableBeforeFocal,
                        spellsExecutableAfterLegalLandPlay = setup.executableAfterLegalLandPlay,
                        bestValidatedPreWeatherSetupSequence = setup.bestValidatedSetupSequence,
                        weatherCastBeforeSuperiorSetup = setup.focalCastBeforeSuperiorSetup,
                        stillUnexecutableAfterLegalLandPlay = setup.stillUnexecutableAfterLegalLandPlay,
                        executableButNotMateriallySuperior = setup.executableButNotMateriallySuperior,
                        evaluatedSetupSequences = setup.evaluatedSequences.map { evaluation ->
                            evaluation.copy(
                                turn = turn,
                                actualLineTaken = turnActions.dropLast(1).filter { it.turn == turn }
                                    .map(PestTurnAction::description) + "cast Weather the Storm",
                            )
                        },
                        actionIndex = turnActions.lastIndex,
                    )
                }
                "Chainer's Edict" -> if (state.controlledBattlefield(blankId).none {
                        state.getEntity(it)?.get<CardComponent>()?.isCreature == true
                    }) audit += "agent cast Chainer's Edict into an empty opposing battlefield on T$turn"
            }
            if (action is ActivateAbility && actionName == "Food") {
                val battlefield = battlefieldNames(state)
                pureLifeGainActivations += PestPureLifeGainActivation(
                    turn = turn,
                    source = "Food",
                    lifeBeforeActivation = state.lifeTotal(pestId),
                    researcherPresent = "Blood Researcher" in battlefield,
                    mascotPresent = "Pest Mascot" in battlefield,
                    survivalRequired = survivalRequiresLife(state),
                    pendingStackSources = stackSourceNames(state),
                )
            }
            if ((action is TypecycleCard || action is CycleCard) && actionName == "Generous Ent") entCycles += turn
        }
        val resolvingSource = stackSourceName(state)
        val beforeBlankLife = state.lifeTotal(blankId)
        val result = environment.stepExactlyOne(action)
        val step = when (result) {
            is ExactlyOneSubmissionResult.Applied -> result.step
            is ExactlyOneSubmissionResult.Rejected -> {
                audit += "illegal action ${action::class.simpleName} ${actionName ?: "unknown"}: ${result.reason}"
                stopReason = "ILLEGAL_ACTION"
                break
            }
        }
        actions++
        val events = step.events
        sacrificeManaTrace.observe(state, step.state, events, pestId, turn)
        events.filterIsInstance<AbilityActivatedEvent>()
            .filter { it.controllerId == pestId && it.sourceName == "Eldrazi Scion" && it.isManaAbility }
            .forEach { event ->
                scionActivations++
            }
        events.filterIsInstance<SpellCopiedEvent>().filter { it.controllerId == pestId && it.cardName == "Weather the Storm" }
            .forEach {
                weathers.firstOrNull { weather -> weather.observedCopies < weather.expectedCopies }?.observedCopies =
                    (weathers.firstOrNull { weather -> weather.observedCopies < weather.expectedCopies }?.observedCopies ?: 0) + 1
            }
        events.filterIsInstance<LifeChangedEvent>()
            .filter { it.playerId == pestId && it.reason == LifeChangeReason.LIFE_GAIN }
            .forEach { event ->
                val names = battlefieldNames(step.state)
                val researcherPresent = "Blood Researcher" in names
                val mascotPresent = "Pest Mascot" in names
                lifeEvents += PestLifeEvent(turn, event.newLife - event.oldLife, resolvingSource ?: "UNKNOWN", researcherPresent, mascotPresent)
                if (resolvingSource == "Weather the Storm" && (researcherPresent || mascotPresent)) payoffAtWeather = true
                if (resolvingSource in setOf("Essence Warden", "Bogwater Lumaret") && (researcherPresent || mascotPresent)) {
                    entryLifeEventsWithPayoff++
                }
            }
        events.filterIsInstance<AbilityTriggeredEvent>().filter { it.controllerId == pestId }.forEach { event ->
            when (event.sourceName) {
                "Blood Researcher" -> researcherTriggers++
                "Pest Mascot" -> mascotTriggers++
            }
        }
        events.filterIsInstance<CountersAddedEvent>().forEach { event ->
            if (event.counterType != "+1/+1" && event.counterType != "PLUS_ONE_PLUS_ONE") return@forEach
            when (event.entityName) {
                "Blood Researcher" -> researcherCounters += event.amount
                "Pest Mascot" -> mascotCounters += event.amount
            }
        }
        events.filterIsInstance<ManaAddedEvent>()
            .filter { it.playerId == pestId && it.sourceName == "Eldrazi Scion" && it.colorless == 1 }
            .forEach { scionManaEvents++ }
        events.filterIsInstance<ZoneChangeEvent>().forEach { event ->
            if (event.ownerId != pestId) return@forEach
            if (event.toZone == Zone.BATTLEFIELD && event.entityName == "Eldrazi Scion") scionsCreated++
            if (event.fromZone == Zone.BATTLEFIELD && event.toZone == Zone.GRAVEYARD && event.entityName == "Carrier Thrall") thrallDeaths++
            if (event.fromZone == Zone.BATTLEFIELD && event.entityName == "Eldrazi Scion" && event.wasSacrificed) scionSacrifices++
            if (event.toZone == Zone.BATTLEFIELD) {
                val card = step.state.getEntity(event.entityId)?.get<CardComponent>()
                if (card?.isCreature == true) {
                    val namesAfter = battlefieldNames(step.state)
                    creatureEntries += PestCreatureEntry(
                        turn = turn,
                        creature = event.entityName,
                        wardensAlreadyPresent = namesAfter.count { it == "Essence Warden" } -
                            if (event.entityName == "Essence Warden") 1 else 0,
                        researchersPresent = namesAfter.count { it == "Blood Researcher" },
                        mascotsPresent = namesAfter.count { it == "Pest Mascot" },
                    )
                }
                if (card?.isPermanent == true && !card.isLand && firstPermanent == null) firstPermanent = turn
            }
        }
        if (acting == pestId && action is PlayLand && actionName == "Jungle Hollow") {
            val tapped = step.state.getEntity(action.cardId)?.has<TappedComponent>() == true
            hollows += "Jungle Hollow@T$turn:${if (tapped) "ENTERED_TAPPED" else "UNTAPPED"}"
        }
        events.filterIsInstance<GameEndedEvent>().lastOrNull()?.let { ended ->
            winningTurn = turn.takeIf { ended.winnerId == pestId }
            terminal = when {
                ended.winnerId == pestId && events.filterIsInstance<DamageDealtEvent>().any {
                    it.targetId == blankId && it.isCombatDamage && it.amount > 0
                } -> "COMBAT_LETHAL"
                ended.winnerId == pestId && beforeBlankLife > 0 && step.state.lifeTotal(blankId) <= 0 -> "ABILITY_OR_TRIGGER_LETHAL"
                ended.winnerId == pestId -> "OTHER_PEST_WIN:${ended.reason}"
                ended.winnerId == blankId -> "BLANK_WIN:${ended.reason}"
                else -> "DRAW:${ended.reason}"
            }
        }
        state = step.state
        observe(state)
    }

    if (state.gameOver) stopReason = "ENGINE_GAME_OVER"
    else if (actions >= 3_000) {
        stopReason = "MAX_ACTIONS"
        audit += "exceeded action cap"
    }
    weathers.forEachIndexed { index, weather ->
        if (weather.observedCopies != weather.expectedCopies) {
            audit += "Weather ${index + 1} on T${weather.turn}: expected ${weather.expectedCopies} Storm copies, observed ${weather.observedCopies}"
        }
        if (weather.weatherCastBeforeSuperiorSetup) {
            audit += "Weather ${index + 1} on T${weather.turn}: cast before validated setup ${weather.bestValidatedPreWeatherSetupSequence}"
        }
    }
    if (researcherTriggers != researcherCounters) audit += "Researcher triggers $researcherTriggers != counters $researcherCounters"
    if (mascotTriggers != mascotCounters) audit += "Mascot triggers $mascotTriggers != counters $mascotCounters"
    if (thrallDeaths != scionsCreated) audit += "Carrier Thrall deaths $thrallDeaths != Scions created $scionsCreated"
    if (scionActivations != scionSacrifices || scionActivations != scionManaEvents) {
        audit += "Scion mana lifecycle activations=$scionActivations sacrifices=$scionSacrifices manaEvents=$scionManaEvents"
    }
    val scionManaUses = sacrificeManaTrace.snapshot().filter { it.sourceName == "Eldrazi Scion" }
    scionManaUses.filter { it.manaProduced != it.manaConsumed + it.unusedMana }.forEach {
        audit += "Scion mana provenance does not balance for ${it.sourceId}: produced=${it.manaProduced} consumed=${it.manaConsumed} unused=${it.unusedMana}"
    }
    scionManaUses.filter { it.unusedMana > 0 }.forEach {
        audit += "Scion ${it.sourceId} sacrificed for ${it.unusedMana} unused mana on T${it.activationTurn}"
    }
    val scionFunded = scionManaUses.flatMap(SacrificeManaUse::fundedActions)
    val weatherCasts = weathers.map { weather ->
        val usefulLater = turnActions.drop(weather.actionIndex + 1)
            .firstOrNull { later ->
                later.turn == weather.turn && later.description.startsWith("cast ") &&
                    later.description.removePrefix("cast ") !in SOLITAIRE_INTERACTION + "Weather the Storm"
            }?.description
        PestWeatherCast(
            weather.turn,
            weather.stormCount,
            weather.expectedCopies,
            weather.observedCopies,
            weather.lifeBeforeCast,
            weather.availableManaBeforeCast,
            weather.handBeforeCast,
            weather.actionsBeforeCastThisTurn,
            weather.researcherPresent,
            weather.mascotPresent,
            weather.followAvailable,
            weather.survivalRequired,
            weather.pendingStackSources,
            weather.currentlyExecutablePreWeatherSpells,
            weather.spellsExecutableAfterLegalLandPlay,
            weather.bestValidatedPreWeatherSetupSequence,
            weather.weatherCastBeforeSuperiorSetup,
            usefulLater,
            weather.stillUnexecutableAfterLegalLandPlay,
            weather.executableButNotMateriallySuperior,
            weather.evaluatedSetupSequences,
        )
    }
    val totalLife = lifeEvents.sumOf(PestLifeEvent::amount)
    if (state.lifeTotal(pestId) != 20 + totalLife) {
        audit += "life accounting final=${state.lifeTotal(pestId)} expected=${20 + totalLife}"
    }
    if (state.gameOver && terminal == null) audit += "game ended without terminal classification"
    if (terminal?.startsWith("BLANK_WIN") == true) audit += "blank solitaire opponent won"
    if (lifeEvents.any { it.amount <= 0 || it.source == "UNKNOWN" }) audit += "unattributed or nonpositive life-gain event"

    val coexistence = PestCoexistence(
        wardenResearcher,
        wardenMascot,
        researcherMascot,
        triple,
        payoffAtWeather,
        entryLifeEventsWithPayoff >= 2,
    )
    val functional = when {
        coexistence.wardenResearcher || coexistence.wardenMascot ||
            lifeEvents.any { it.researcherPresent || it.mascotPresent } -> "ENGINE_FUNCTIONAL"
        maxCreatures >= 3 || winningTurn != null -> "FAIR_CREATURE_FUNCTIONAL"
        stranded.size >= 2 -> "INTERACTION_HEAVY_BUT_GOLDFISH_CONSTRAINED"
        else -> "GENUINELY_NONFUNCTIONAL"
    }
    return PestGoldfishGame(
        gameNumber,
        seed,
        "0x${seed.toULong().toString(16).uppercase()}",
        mulligans,
        opening,
        t1,
        firstPermanent,
        winningTurn,
        terminal,
        wardenCasts,
        researcherCasts,
        mascotCasts,
        thrallCasts,
        thrallDeaths,
        scionsCreated,
        scionActivations,
        scionFunded,
        scionManaUses,
        witchCasts,
        entCycles,
        entCasts,
        follows,
        weatherCasts,
        friendlyRemovalAudits,
        pureLifeGainActivations,
        turnActions,
        creatureEntries,
        payoffWithoutWardenTurns.toList(),
        wardenWithoutPayoffTurns.toList(),
        lifeEvents,
        totalLife,
        researcherTriggers,
        researcherCounters,
        mascotTriggers,
        mascotCounters,
        maxResearcherPower,
        maxResearcherToughness,
        maxMascotPower,
        maxMascotToughness,
        maxCreatures,
        maxPermanents,
        stranded.toList(),
        bottlenecks.toList(),
        hollows,
        coexistence,
        functional,
        actions,
        stopReason,
        audit,
    )
}

private val SOLITAIRE_INTERACTION = setOf("Cast Down", "Bone Shards", "Chainer's Edict")

internal fun summarizePest(games: List<PestGoldfishGame>): PestGoldfishSummary {
    fun rate(count: Int): Double = count.toDouble() / games.size
    fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
    val weather = games.flatMap(PestGoldfishGame::weatherCasts)
    val entCycles = games.sumOf { it.generousEntCycleTurns.size }
    val entCasts = games.sumOf { it.generousEntCastTurns.size }
    val follows = games.flatMap(PestGoldfishGame::followCasts)
    val enhanced = follows.count { it.mode == "ENHANCED" }
    val coexist = games.count { it.coexistence.wardenResearcher || it.coexistence.wardenMascot }
    fun hollowTurns(game: PestGoldfishGame): Set<Int> = game.jungleHollowTempoEvents.mapNotNull { event ->
        event.substringAfter("@T", "").substringBefore(':').toIntOrNull()
    }.toSet()
    fun hollowDelays(game: PestGoldfishGame): Int {
        val turns = hollowTurns(game)
        return game.genuineManaBottlenecks.count { it.constraint == ManaConstraint.TAPLAND && it.turn in turns }
    }
    fun openingAccess(game: PestGoldfishGame): String = when {
        game.opening.green && game.opening.black -> "GREEN_AND_BLACK"
        game.opening.green -> "GREEN_ONLY"
        game.opening.black -> "BLACK_ONLY"
        else -> "NEITHER"
    }
    val wardenOpportunities = games.associateWith { wardenCounterfactualOpportunity(it.creatureEntries) }
    return PestGoldfishSummary(
        games = games.size,
        openingColorAccessDistribution = games.groupingBy(::openingAccess).eachCount().toSortedMap(),
        mulliganGames = games.count { it.mulligans > 0 },
        totalMulligans = games.sumOf(PestGoldfishGame::mulligans),
        mulliganRate = rate(games.count { it.mulligans > 0 }),
        meaningfulDevelopmentByT1 = games.count { (it.firstMeaningfulPermanentTurn ?: Int.MAX_VALUE) <= 1 },
        meaningfulDevelopmentByT2 = games.count { (it.firstMeaningfulPermanentTurn ?: Int.MAX_VALUE) <= 2 },
        meaningfulDevelopmentByT3 = games.count { (it.firstMeaningfulPermanentTurn ?: Int.MAX_VALUE) <= 3 },
        medianFirstWarden = median(games.mapNotNull { it.essenceWardenCastTurns.minOrNull() }),
        medianFirstPayoff = median(games.mapNotNull {
            listOfNotNull(it.bloodResearcherCastTurns.minOrNull(), it.pestMascotCastTurns.minOrNull()).minOrNull()
        }),
        medianActualWinningTurn = median(games.mapNotNull(PestGoldfishGame::actualWinningTurn)),
        winsByT4 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 4 },
        winsByT5 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 5 },
        winsByT6 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 6 },
        winsByT7 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 7 },
        totalLifeEvents = games.sumOf { it.lifeEvents.size },
        totalLifeGained = games.sumOf(PestGoldfishGame::totalLifeGained),
        averageLifeEvents = games.map { it.lifeEvents.size }.average(),
        averageLifeGained = games.map(PestGoldfishGame::totalLifeGained).average(),
        researcherCounterTriggers = games.sumOf(PestGoldfishGame::researcherCounterTriggers),
        researcherCountersAdded = games.sumOf(PestGoldfishGame::researcherCountersAdded),
        mascotCounterTriggers = games.sumOf(PestGoldfishGame::mascotCounterTriggers),
        mascotCountersAdded = games.sumOf(PestGoldfishGame::mascotCountersAdded),
        weatherStormCountDistribution = weather.groupingBy(PestWeatherCast::stormCount).eachCount().toSortedMap(),
        researcherMaximumSizeDistribution = games.filter { it.maximumResearcherPower != null }.groupingBy {
            "${it.maximumResearcherPower}/${it.maximumResearcherToughness}"
        }.eachCount().toSortedMap(),
        mascotMaximumSizeDistribution = games.filter { it.maximumMascotPower != null }.groupingBy {
            "${it.maximumMascotPower}/${it.maximumMascotToughness}"
        }.eachCount().toSortedMap(),
        enginePayoffCoexistenceGames = coexist,
        enginePayoffCoexistenceRate = rate(coexist),
        allThreeCoexistenceGames = games.count { it.coexistence.wardenResearcherMascot },
        payoffWhenWeatherResolvedGames = games.count { it.coexistence.payoffWhenWeatherResolved },
        carrierDeaths = games.sumOf(PestGoldfishGame::carrierThrallDeaths),
        scionsCreated = games.sumOf(PestGoldfishGame::scionsCreated),
        scionsSacrificedForMana = games.sumOf(PestGoldfishGame::scionsSacrificedForMana),
        scionFundedSpells = games.flatMap(PestGoldfishGame::scionFundedSpells)
            .map { it.substringBefore("@T") }.groupingBy { it }.eachCount().toSortedMap(),
        entCycles = entCycles,
        entCreatureCasts = entCasts,
        entCyclingRate = (entCycles + entCasts).takeIf { it > 0 }?.let { entCycles.toDouble() / it },
        followNormalCasts = follows.count { it.mode == "NORMAL" },
        followEnhancedCasts = enhanced,
        followEnhancedRate = follows.size.takeIf { it > 0 }?.let { enhanced.toDouble() / it },
        manaBottleneckGames = games.count { it.genuineManaBottlenecks.isNotEmpty() },
        manaBottleneckObservations = games.sumOf { it.genuineManaBottlenecks.size },
        manaConstraintDistribution = games.flatMap(PestGoldfishGame::genuineManaBottlenecks)
            .groupingBy { it.constraint.name }.eachCount().toSortedMap(),
        hollowTempoGames = games.count { it.jungleHollowTempoEvents.isNotEmpty() },
        hollowTempoEvents = games.sumOf { it.jungleHollowTempoEvents.size },
        hollowProximateDelayGames = games.count { hollowDelays(it) > 0 },
        hollowProximateDelayEvents = games.sumOf(::hollowDelays),
        solitaireInteractionConstrainedGames = games.count { it.solitaireStrandedInteraction.isNotEmpty() },
        strandedInteractionObservations = games.sumOf { it.solitaireStrandedInteraction.size },
        functionalStateDistribution = games.groupingBy(PestGoldfishGame::functionalState).eachCount().toSortedMap(),
        lifeEventsWithPayoffPresent = games.sumOf { game ->
            game.lifeEvents.count { it.researcherPresent || it.mascotPresent }
        },
        payoffWithoutWardenGames = games.count { it.payoffWithoutWardenTurns.isNotEmpty() },
        payoffWithoutWardenTurns = games.sumOf { it.payoffWithoutWardenTurns.size },
        wardenWithoutPayoffGames = games.count { it.wardenWithoutPayoffTurns.isNotEmpty() },
        wardenWithoutPayoffTurns = games.sumOf { it.wardenWithoutPayoffTurns.size },
        additionalWardenOpportunityGames = wardenOpportunities.values.count { it.entries > 0 },
        additionalWardenOpportunityEntries = wardenOpportunities.values.sumOf(PestWardenOpportunity::entries),
        additionalWardenPotentialResearcherCounters = wardenOpportunities.values
            .sumOf(PestWardenOpportunity::potentialResearcherCounters),
        additionalWardenPotentialMascotCounters = wardenOpportunities.values
            .sumOf(PestWardenOpportunity::potentialMascotCounters),
        weatherStormZeroCasts = weather.count { it.stormCount == 0 },
        weatherStormZeroWithLaterUsefulSpell = weather.count {
            it.stormCount == 0 && it.usefulSpellCastLaterThisTurn != null
        },
        followNormalWithoutPriorLifeGain = follows.count {
            it.mode == "NORMAL" && it.lifeEventsBeforeCastThisTurn == 0
        },
    )
}

internal fun renderPestMarkdown(
    block: PestGoldfishBlock,
    freshPerformanceSample: Boolean = false,
    sampleLabel: String = "Goldfish Sample #1 Fresh Performance Baseline",
): String = buildString {
    val s = block.summary
    appendLine(if (freshPerformanceSample) {
        "# Pest Control v1.0 — $sampleLabel"
    } else {
        "# Pest Control v1.0 — Rejected Sample #1 Regression Replay"
    })
    appendLine()
    appendLine(if (freshPerformanceSample) {
        "Development/engine goldfish evidence only; this is not matchup evidence."
    } else {
        "Regression evidence only. This retired vector is rejected for performance/baseline inference and optimization."
    })
    appendLine()
    appendLine("## Aggregate")
    appendLine()
    appendLine("- Opening color access: ${s.openingColorAccessDistribution}")
    appendLine("- Games: ${s.games}; mulligan games: ${s.mulliganGames} (${pct(s.mulliganRate)}), total mulligans: ${s.totalMulligans}")
    appendLine("- Meaningful permanent development by T1/T2/T3: ${s.meaningfulDevelopmentByT1}/${s.meaningfulDevelopmentByT2}/${s.meaningfulDevelopmentByT3}")
    appendLine("- Median first Warden: ${s.medianFirstWarden ?: "n/a"}; median first payoff: ${s.medianFirstPayoff ?: "n/a"}")
    appendLine("- Median actual win: ${s.medianActualWinningTurn ?: "n/a"}; wins by T4/T5/T6/T7: ${s.winsByT4}/${s.winsByT5}/${s.winsByT6}/${s.winsByT7}")
    appendLine("- Separate lifegain events: ${s.totalLifeEvents} total, ${"%.2f".format(s.averageLifeEvents)} average; life gained: ${s.totalLifeGained} total, ${"%.2f".format(s.averageLifeGained)} average")
    appendLine("- Researcher triggers/counters: ${s.researcherCounterTriggers}/${s.researcherCountersAdded}; Mascot triggers/counters: ${s.mascotCounterTriggers}/${s.mascotCountersAdded}")
    appendLine("- Weather Storm counts: ${s.weatherStormCountDistribution}")
    appendLine("- Maximum Researcher sizes: ${s.researcherMaximumSizeDistribution}")
    appendLine("- Maximum Mascot sizes: ${s.mascotMaximumSizeDistribution}")
    appendLine("- Warden + payoff coexistence: ${s.enginePayoffCoexistenceGames}/${s.games} (${pct(s.enginePayoffCoexistenceRate)})")
    appendLine("- Warden + Researcher + Mascot coexistence: ${s.allThreeCoexistenceGames}/${s.games}; payoff present when Weather resolved: ${s.payoffWhenWeatherResolvedGames}/${s.games}")
    appendLine("- Carrier deaths / Scions / mana sacrifices: ${s.carrierDeaths}/${s.scionsCreated}/${s.scionsSacrificedForMana}; funded: ${s.scionFundedSpells}")
    appendLine("- Ent cycles / creature casts: ${s.entCycles}/${s.entCreatureCasts}; cycling rate: ${s.entCyclingRate?.let(::pct) ?: "n/a"}")
    appendLine("- Follow normal / enhanced: ${s.followNormalCasts}/${s.followEnhancedCasts}; enhanced rate: ${s.followEnhancedRate?.let(::pct) ?: "n/a"}")
    appendLine("- Actionable mana bottlenecks: ${s.manaBottleneckGames} games, ${s.manaBottleneckObservations} observations; constraints: ${s.manaConstraintDistribution}")
    appendLine("- Jungle Hollow tempo: ${s.hollowTempoGames} games, ${s.hollowTempoEvents} tapped-entry events")
    appendLine("- Jungle Hollow proximate deployment delays: ${s.hollowProximateDelayGames} games, ${s.hollowProximateDelayEvents} events")
    appendLine("- Solitaire-stranded interaction: ${s.solitaireInteractionConstrainedGames} games, ${s.strandedInteractionObservations} observations")
    appendLine("- Functional states: ${s.functionalStateDistribution}")
    appendLine("- Lifegain events with Researcher/Mascot present: ${s.lifeEventsWithPayoffPresent}")
    appendLine("- Payoff without Warden: ${s.payoffWithoutWardenGames} games / ${s.payoffWithoutWardenTurns} turns; Warden without payoff: ${s.wardenWithoutPayoffGames} games / ${s.wardenWithoutPayoffTurns} turns")
    appendLine("- Additional-Warden opportunities while payoff present and Warden absent: ${s.additionalWardenOpportunityGames} games / ${s.additionalWardenOpportunityEntries} qualifying creature entries; potential Researcher/Mascot counters forgone: ${s.additionalWardenPotentialResearcherCounters}/${s.additionalWardenPotentialMascotCounters}")
    appendLine("- Weather at Storm 0: ${s.weatherStormZeroCasts}; with a useful spell demonstrably cast later that turn: ${s.weatherStormZeroWithLaterUsefulSpell}")
    appendLine("- Normal Follow casts with no earlier lifegain event that turn: ${s.followNormalWithoutPriorLifeGain}")
    appendLine()
    appendLine("## Games")
    appendLine()
    appendLine("| # | Seed | Mull | Open access | T1 | First perm | Win | Class | Life events/gain | R max | M max | Board | Interaction | Bottleneck |")
    appendLine("|---:|---|---:|---|---|---:|---|---|---:|---|---|---:|---:|---:|")
    block.games.forEach { g ->
        val access = listOfNotNull(
            "G".takeIf { g.opening.green }, "B".takeIf { g.opening.black },
            "uG".takeIf { g.opening.untappedGreen }, "uB".takeIf { g.opening.untappedBlack },
            "Ent".takeIf { g.opening.entForestcyclingAvailable },
        ).joinToString("/").ifEmpty { "none" }
        appendLine("| ${g.game} | `${g.seedHex}` | ${g.mulligans} | $access | ${g.t1Development.joinToString("; ").ifEmpty { "—" }} | ${g.firstMeaningfulPermanentTurn ?: "—"} | ${g.actualWinningTurn?.let { "T$it ${g.terminalMechanism}" } ?: "—"} | ${g.functionalState} | ${g.lifeEvents.size}/${g.totalLifeGained} | ${size(g.maximumResearcherPower, g.maximumResearcherToughness)} | ${size(g.maximumMascotPower, g.maximumMascotToughness)} | ${g.largestCreatureBattlefield} | ${g.solitaireStrandedInteraction.size} | ${g.genuineManaBottlenecks.size} |")
    }
    appendLine()
    appendLine("## Per-game telemetry")
    block.games.forEach { g ->
        appendLine()
        appendLine("### Game ${g.game} — `${g.seedHex}`")
        appendLine()
        appendLine("- Kept hand: ${g.opening.keptHand}; mulligans: ${g.mulligans}; T1: ${g.t1Development}")
        appendLine("- Warden/Researcher/Mascot casts: ${g.essenceWardenCastTurns}/${g.bloodResearcherCastTurns}/${g.pestMascotCastTurns}")
        appendLine("- Carrier casts/deaths; Scions created/sacrificed/funded: ${g.carrierThrallCastTurns}/${g.carrierThrallDeaths}; ${g.scionsCreated}/${g.scionsSacrificedForMana}/${g.scionFundedSpells}")
        appendLine("- Scion mana provenance: ${g.scionManaUses}")
        appendLine("- Witchstalker: ${g.fierceWitchstalkerCastTurns}; Ent cycle/cast: ${g.generousEntCycleTurns}/${g.generousEntCastTurns}")
        appendLine("- Follow: ${g.followCasts}; Weather: ${g.weatherCasts}")
        appendLine("- Pure-lifegain activations: ${g.pureLifeGainActivations}")
        appendLine("- Turn actions: ${g.turnActions}")
        appendLine("- Creature entries: ${g.creatureEntries}")
        appendLine("- Lifegain: ${g.lifeEvents}; Researcher triggers/counters: ${g.researcherCounterTriggers}/${g.researcherCountersAdded}; Mascot: ${g.mascotCounterTriggers}/${g.mascotCountersAdded}")
        appendLine("- Coexistence: ${g.coexistence}; payoff-no-Warden turns: ${g.payoffWithoutWardenTurns}; Warden-no-payoff turns: ${g.wardenWithoutPayoffTurns}")
        appendLine("- Hollow: ${g.jungleHollowTempoEvents}")
        appendLine("- Solitaire-stranded interaction: ${g.solitaireStrandedInteraction}")
        appendLine("- Genuine mana bottlenecks: ${g.genuineManaBottlenecks}")
        appendLine("- Terminal: ${g.actualWinningTurn?.let { "T$it" } ?: "none by horizon"} / ${g.terminalMechanism}; stop=${g.stopReason}; actions=${g.actions}")
        appendLine("- Audit: ${g.auditErrors.ifEmpty { listOf("clean") }}")
    }
}

private fun pct(value: Double): String = "%.1f%%".format(value * 100.0)
private fun size(power: Int?, toughness: Int?): String = if (power == null) "—" else "$power/$toughness"
