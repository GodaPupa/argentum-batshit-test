package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * No Commander game starts here. Pure boundary checks and two immutable R8 archives
 * prove the actual nonterminal historical GameState survives R9 censor classification.
 * One archived R8 game is COMPLETE; the other is RESOURCE_CAP / no winner. Their
 * original bytes and old conclusions are not rewritten or re-run.
 */
class ManualR9CensorClassificationTest : FunSpec({

    test("exact five-minute boundary remains safe; crossing it is typed wall-clock censoring") {
        ManualR9CensorProtocol.reached(300_000, 3330, 54, 200) shouldBe null
        ManualR9CensorProtocol.reached(300_001, 3330, 54, 200) shouldBe
            ManualR9CensorReason.WALL_CLOCK
        ManualR9CensorProtocol.reached(300_163, 3330, 54, 200)?.status shouldBe
            "CENSORED_WALL_CLOCK"
        // Never confuse a soft timestamp observation with an engine-defined loss.
        ManualR9CensorProtocol.isCensored("CENSORED_WALL_CLOCK") shouldBe true
        ManualR9CensorProtocol.isCensored("RESOURCE_CAP") shouldBe false
        ManualR9CensorProtocol.isCensored("ENGINE_REJECTION") shouldBe false
    }

    test("action, global-turn, per-turn and simultaneous bounds classify deterministically") {
        ManualR9CensorProtocol.reached(0, 10_000, 54, 20) shouldBe
            ManualR9CensorReason.ACTION_BUDGET
        ManualR9CensorProtocol.reached(0, 100, 201, 20) shouldBe
            ManualR9CensorReason.TURN_BUDGET
        ManualR9CensorProtocol.reached(0, 100, 54, 1_500) shouldBe
            ManualR9CensorReason.PER_TURN_BUDGET
        ManualR9CensorProtocol.reached(300_163, 10_000, 201, 1_500) shouldBe
            ManualR9CensorReason.WALL_CLOCK
        ManualR9CensorProtocol.reached(0, 9_999, 200, 1_499) shouldBe null
    }

    test("typed nonterminal censor is observationally valid, never a terminal winner") {
        ManualR9CensorProtocol.validObservation(
            "CENSORED_WALL_CLOCK", 3330, 3330, null, false, null, false
        ) shouldBe true
        ManualR9CensorProtocol.validObservation(
            "CENSORED_ACTION_BUDGET", 10_000, 10_000, null, false, null, false
        ) shouldBe true
        ManualR9CensorProtocol.validObservation(
            "CENSORED_WALL_CLOCK", 3330, 3330, null, false, "manual-mirror-1", false
        ) shouldBe false
        ManualR9CensorProtocol.validObservation(
            "CENSORED_WALL_CLOCK", 3330, 3330, null, true, null, false
        ) shouldBe false
        ManualR9CensorProtocol.validObservation(
            "CENSORED_WALL_CLOCK", 3330, 3331, null, false, null, false
        ) shouldBe false
        ManualR9CensorProtocol.validObservation(
            "CENSORED_WALL_CLOCK", 33, 33, null, false, null, false
        ) shouldBe false
        ManualR9CensorProtocol.validObservation(
            "ENGINE_REJECTION", 3330, 3330, "Illegal target", false, null, false
        ) shouldBe false
        ManualR9CensorProtocol.validObservation(
            "RESOURCE_CAP", 3330, 3330, null, false, null, false
        ) shouldBe false
    }

    test("prefix 64 actions and completed game remain strict, not silently censored") {
        ManualR9CensorProtocol.validObservation(
            "PREFIX_COMPLETE", 64, 64, null, false, null, true
        ) shouldBe true
        ManualR9CensorProtocol.validObservation(
            "CENSORED_WALL_CLOCK", 64, 64, null, false, null, true
        ) shouldBe false
        ManualR9CensorProtocol.validObservation(
            "PREFIX_COMPLETE", 65, 65, null, false, null, true
        ) shouldBe false
        ManualR9CensorProtocol.validObservation(
            "COMPLETE", 3445, 3445, null, true, "manual-mirror-1", false
        ) shouldBe true
        ManualR9CensorProtocol.validObservation(
            "COMPLETE", 3445, 3445, null, false, "manual-mirror-1", false
        ) shouldBe false
    }

    test("paired action horizon cannot turn a censored game into a victory or loss") {
        val pair = ManualR9CensorProtocol.comparePair(
            "CENSORED_WALL_CLOCK", 3330, "COMPLETE", 3445
        )
        pair.sharedActionHorizon shouldBe 3330
        pair.classification shouldBe ManualR9CensorProtocol.PairClass.CENSORED_PAIR
        pair.winnerComparisonEligible shouldBe false
        ManualR9CensorProtocol.comparePair(
            "COMPLETE", 3490, "COMPLETE", 3490
        ).winnerComparisonEligible shouldBe true
        ManualR9CensorProtocol.comparePair(
            "CENSORED_WALL_CLOCK", 3330, "CENSORED_TURN_BUDGET", 3330
        ).winnerComparisonEligible shouldBe false
        ManualR9CensorProtocol.comparePair(
            "ENGINE_REJECTION", 80, "COMPLETE", 3445
        ).classification shouldBe ManualR9CensorProtocol.PairClass.INVALID_PAIR
    }

    test("original R8 1611 censored state and paired completed game retain true histories") {
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R9_PROTOCOL_OUTPUT")))
        Files.createDirectories(output)
        val censoredPath = Path.of(requireNotNull(System.getenv("MANUAL_R9_R8_CENSORED_ARCHIVE")))
        val completePath = Path.of(requireNotNull(System.getenv("MANUAL_R9_R8_COMPLETE_ARCHIVE")))
        fun checkHash(path: Path, expected: String) {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
            hash shouldBe expected
        }
        checkHash(censoredPath, "d7775db042d2e00f4ab6a7ea7e71e479a202841e476d917830bfe5de67a9b4b1")
        checkHash(completePath, "f563f47aafc1a4fb570aabb666c10d0108db982ff217598018a19bab5d9cf1cd")
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        fun ZipFile.readEntry(name: String): String = getInputStream(
            requireNotNull(getEntry(name)) { "Missing archived R8 original: $name" }
        ).bufferedReader().readText()
        ZipFile(censoredPath.toFile()).use { old ->
            ZipFile(completePath.toFile()).use { sibling ->
                val acceptedSource = "d226f1a56eebab1800b85a06bf70617ba439a00b"
                old.readEntry("r8-output/source-sha.txt").trim() shouldBe acceptedSource
                sibling.readEntry("r8-output/source-sha.txt").trim() shouldBe acceptedSource

                val censoredSummary = json.parseToJsonElement(
                    old.readEntry("r8-output/r5-seed-202610191611/summary.json")
                ).jsonObject
                val completeSummary = json.parseToJsonElement(
                    sibling.readEntry("r8-output/r7-seed-202610191611/summary.json")
                ).jsonObject
                val state = json.decodeFromString<GameState>(
                    old.readEntry("r8-output/r5-seed-202610191611/final-state.json")
                )
                censoredSummary.getValue("status").jsonPrimitive.content shouldBe "RESOURCE_CAP"
                censoredSummary.getValue("accepted").jsonPrimitive.int shouldBe 3330
                censoredSummary.getValue("error").jsonPrimitive.content shouldBe
                    "Bound reached before terminal game state"
                censoredSummary.getValue("gameOver").jsonPrimitive.boolean shouldBe false
                censoredSummary["winner"] shouldBe JsonNull
                state.gameOver shouldBe false
                state.winnerId shouldBe null
                state.turnNumber shouldBe 54

                val elapsed = censoredSummary.getValue("durationMs").jsonPrimitive.long
                val reason = ManualR9CensorProtocol.reached(
                    elapsedMs = elapsed,
                    submitted = censoredSummary.getValue("submitted").jsonPrimitive.int,
                    globalTurn = state.turnNumber,
                    sameTurnActions = 0,
                )
                reason shouldBe ManualR9CensorReason.WALL_CLOCK

                // The exact original nonterminal state is serialized through the same helper
                // that the future R9 game loop will invoke before exiting.
                val dest = output.resolve("reclassified-r8-original-1611")
                val saved = ManualR9CensorProtocol.writeCensorCheckpoint(dest, state, json)
                Files.exists(saved) shouldBe true
                val recovered = json.decodeFromString<GameState>(Files.readString(saved))
                recovered.gameOver shouldBe false
                recovered.winnerId shouldBe null
                recovered.turnNumber shouldBe 54
                recovered.stack.size shouldBe state.stack.size
                runCatching {
                    ManualR9CensorProtocol.writeCensorCheckpoint(dest, state, json)
                }.isFailure shouldBe true // immutable archival receipt must not overwrite

                val r9 = ManualR9CensorProtocol.validObservation(
                    status = requireNotNull(reason).status,
                    accepted = 3330,
                    submitted = 3330,
                    error = null,
                    gameOver = false,
                    winner = null,
                    prefixOnly = false,
                )
                r9 shouldBe true
                completeSummary.getValue("status").jsonPrimitive.content shouldBe "COMPLETE"
                completeSummary.getValue("accepted").jsonPrimitive.int shouldBe 3445
                completeSummary.getValue("winner").jsonPrimitive.content shouldBe "manual-mirror-1"
                val pair = ManualR9CensorProtocol.comparePair(
                    reason.status,
                    3330,
                    completeSummary.getValue("status").jsonPrimitive.content,
                    3445,
                )
                pair.classification shouldBe ManualR9CensorProtocol.PairClass.CENSORED_PAIR
                pair.sharedActionHorizon shouldBe 3330
                pair.winnerComparisonEligible shouldBe false

                val audit = buildJsonObject {
                    put("originalR8Source", acceptedSource)
                    put("originalR8Run", 39340369095L)
                    put("historicalStatusUntouched", censoredSummary.getValue("status"))
                    put("historicalErrorUntouched", censoredSummary.getValue("error"))
                    put("derivedR9Classification", reason.status)
                    put("derivedEngineError", JsonNull)
                    put("originalAccepted", 3330)
                    put("pairedR7CompletedAccepted", 3445)
                    put("sharedActionHorizon", pair.sharedActionHorizon)
                    put("pairClassification", pair.classification.name)
                    put("winnerComparisonEligible", pair.winnerComparisonEligible)
                    put("savedCheckpoint", saved.fileName.toString())
                    put("checkpointRoundtripGameOver", recovered.gameOver)
                    put("checkpointRoundtripWinner", recovered.winnerId?.value)
                    put("noNewGameStarted", true)
                }
                Files.writeString(output.resolve("protocol-audit.json"), audit.toString())
                println("R9_CENSOR_REAL_ORIGINAL=" + audit)
            }
        }
    }
})
