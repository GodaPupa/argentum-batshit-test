package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

private const val V2_SMOKE_SEED: Long = 0x5045_5354_5632_0001L

/** Nonexperimental V2 qualifying smoke. Its seed is permanently excluded from official vectors. */
class PestControlV2QualifyingSmokeTest : FunSpec({
    val enabled = System.getenv("PEST_V2_SMOKE") == "true"
    test("one production Pest versus SoterX game reaches a legitimate audited terminal").config(
        enabled = enabled,
        timeout = 2.hours,
    ) {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
        }
        PestControlPreboardDecks.verifyFrozenIdentities()
        val session = PestControlPreboardSession.qualifyingSmoke(
            registry = registry,
            sourceCommit = System.getenv("PEST_V2_SMOKE_COMMIT") ?: error("PEST_V2_SMOKE_COMMIT required"),
            pestSeat = PestSeat.SEAT_ZERO,
            startingDeck = StartingDeck.PEST_CONTROL,
            recordId = "PEST_CONTROL_V2_QUALIFYING_SMOKE",
            seed = V2_SMOKE_SEED,
        )
        val game = PestControlPreboardProductionDriver.drive(registry, session)
        (game.terminal?.gameOver == true && game.protocolDefect == null).shouldBeTrue()
        val out = Path.of("").toAbsolutePath().parent.resolve("build/reports/pest-control-v2-smoke")
        Files.createDirectories(out)
        val bundle = PestControlMatchupArtifactCodec.build(game)
        check(PestControlMatchupArtifactCodec.verify(bundle).isEmpty()) { "smoke artifact verification failed" }
        Files.write(out.resolve("smoke-raw.json"), bundle.rawJson)
        Files.write(out.resolve("smoke-raw.json.gz"), bundle.compressed)
        Files.write(out.resolve("smoke-report.md"), bundle.report)
        Files.write(out.resolve("smoke-manifest.json"), bundle.manifest)
        Files.writeString(out.resolve("smoke-summary.txt"), "seed=$V2_SMOKE_SEED\nterminal=${game.terminal}\nprotocolDefect=${game.protocolDefect}\nactions=${game.priorityActions.size}\n")
    }
})
