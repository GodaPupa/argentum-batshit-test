package com.wingedsheep.ai.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Read-only R7 artifact forensic evidence. Full selected windows (submissions AND results)
 * and actual AI score records are preserved before classification. No simulated winner,
 * invented legal move, production scoring modification or new game is claimed.
 */
class ManualR7ForensicAuditTest : FunSpec({
    test("extract Ballista targeting and terminal combat from two SHA-bound R7 matches") {
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R7_FORENSIC_OUTPUT")))
        Files.createDirectories(out)
        val json = Json { ignoreUnknownKeys = true }
        val results = mutableListOf<JsonElement>()

        fun readRows(zip: ZipFile, prefix: String, name: String): List<JsonObject> =
            zip.getInputStream(requireNotNull(zip.getEntry(prefix + name)) {
                "Missing archived R7 evidence: " + prefix + name
            }).bufferedReader().lineSequence().filter(String::isNotBlank)
                .map { json.parseToJsonElement(it).jsonObject }.toList()

        fun damageAmount(event: String): Int =
            event.substringAfter("amount=", "").substringBefore(",").toIntOrNull() ?: 0

        fun audit(
            path: Path,
            expectedHash: String,
            seed: Long,
            expectedAccepted: Int,
            expectedWinner: String,
            shotSpan: IntRange,
            resolvedSpan: IntRange,
            windows: List<IntRange>,
            shootTurn: Int,
            shooter: String,
            expectedShots: Int,
            expectedAnimarShots: Int
        ) {
            val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                .joinToString("") { "%02x".format(it) }
            hash shouldBe expectedHash
            ZipFile(path.toFile()).use { zip ->
                val prefix = "r7-output/seed-" + seed + "/"
                fun entry(name: String): String = zip.getInputStream(
                    requireNotNull(zip.getEntry(prefix + name)) {
                        "Missing archived R7 evidence: " + prefix + name
                    }).bufferedReader().readText()
                val summary = json.parseToJsonElement(entry("summary.json")).jsonObject
                val actions = readRows(zip, prefix, "actions.jsonl")
                val decisions = readRows(zip, prefix, "decisions.jsonl")

                summary.getValue("seed").jsonPrimitive.long shouldBe seed
                summary.getValue("status").jsonPrimitive.content shouldBe "COMPLETE"
                summary.getValue("gameOver").jsonPrimitive.boolean shouldBe true
                summary.getValue("winner").jsonPrimitive.content shouldBe expectedWinner
                summary.getValue("accepted").jsonPrimitive.int shouldBe expectedAccepted

                // Save entire relevant submit/result windows. Three-record samples alone
                // cannot show the queued Ballista damage resolving or the lethal combat.
                val saved = actions.filter { row ->
                    val idx = row.getValue("index").jsonPrimitive.int
                    windows.any { idx in it }
                }
                Files.writeString(out.resolve("seed-" + seed + "-action-windows.json"), JsonArray(saved).toString())

                val scoreRecords = decisions.filter { insight ->
                    val turn = insight["turnNumber"]?.jsonPrimitive?.intOrNull
                    val label = insight["chosenLabel"]?.jsonPrimitive?.content ?: ""
                    val actor = insight["playerId"]?.jsonPrimitive?.content
                    (actor == shooter && turn == shootTurn &&
                        (label.contains("Remove a +1/+1 counter") || label.contains("Walking Ballista"))) ||
                    (seed == 202610191609L && turn == 54 && actor == "manual-mirror-0" &&
                        insight["kind"]?.jsonPrimitive?.content == "DECLARE_BLOCKERS")
                }
                Files.writeString(out.resolve("seed-" + seed + "-scored-choices.json"), JsonArray(scoreRecords).toString())

                val shots = actions.filter { row ->
                    row["record"]?.jsonPrimitive?.content == "submit" &&
                    row.getValue("index").jsonPrimitive.int in shotSpan &&
                    row["sourceName"]?.jsonPrimitive?.content == "Walking Ballista"
                }.map { row -> json.parseToJsonElement(row.getValue("action").jsonPrimitive.content).jsonObject }
                    .filter { it["type"]?.jsonPrimitive?.content == "ActivateAbility" }
                val shotTargets = shots.map { action ->
                    action["targets"]?.jsonArray?.firstOrNull()?.jsonObject?.get("entityId")
                        ?.jsonPrimitive?.content ?: "(missing target)"
                }
                val targetCounts = shotTargets.groupingBy { it }.eachCount()

                val resolvedEvents = actions.filter { row ->
                    row["record"]?.jsonPrimitive?.content == "result" &&
                    row.getValue("index").jsonPrimitive.int in resolvedSpan
                }.flatMap { row -> row.getValue("events").jsonArray.map { it.jsonPrimitive.content } }
                val damage = resolvedEvents.filter { it.startsWith("DamageDealtEvent(") &&
                    it.contains("sourceName=Walking Ballista") }
                val animarDamage = damage.filter { it.contains("targetId=e0,") &&
                    it.contains("targetName=Animar, Soul of Elements") }
                val animarToughness = animarDamage.firstOrNull()
                    ?.substringAfter("targetToughnessAtDamage=", "")
                    ?.substringBefore(",")?.toIntOrNull()
                val animarLeftBattlefield = resolvedEvents.any {
                    it.startsWith("ZoneChangeEvent(entityId=e0, entityName=Animar, Soul of Elements, " +
                        "fromZone=BATTLEFIELD")
                }

                val combat = if (seed == 202610191609L) {
                    val block = actions.single { row ->
                        row["record"]?.jsonPrimitive?.content == "submit" &&
                        row.getValue("index").jsonPrimitive.int == 3097
                    }
                    val before = actions.single { row ->
                        row["record"]?.jsonPrimitive?.content == "result" &&
                        row.getValue("index").jsonPrimitive.int == 3098
                    }
                    val result = actions.single { row ->
                        row["record"]?.jsonPrimitive?.content == "result" &&
                        row.getValue("index").jsonPrimitive.int == 3099
                    }
                    val events = result.getValue("events").jsonArray.map { it.jsonPrimitive.content }
                    val incoming = events.filter { it.startsWith("DamageDealtEvent(") &&
                        it.contains("targetId=manual-mirror-0,") && it.contains("isCombatDamage=true") }
                    val total = incoming.sumOf(::damageAmount)
                    val animar = incoming.filter { it.contains("sourceName=Animar, Soul of Elements") }
                        .sumOf(::damageAmount)
                    val life = before.getValue("life").jsonArray[0].jsonPrimitive.int
                    buildJsonObject {
                        put("defendingSeat", "manual-mirror-0")
                        put("lifeBeforeCombatDamage", life)
                        put("actualCombatDamageToSeat", total)
                        put("animarCombatDamage", animar)
                        put("damageFromOthers", total - animar)
                        put("survivesEvenIfAllAnimarDamageRemovedAndOtherDamageUnchanged",
                            total - animar < life)
                        put("actualBlockers", json.parseToJsonElement(
                            block.getValue("action").jsonPrimitive.content))
                        put("limitation", "Removing Animar damage is only an optimistic arithmetic bound, not a legal alternate block or full replay")
                    }
                } else JsonNull

                val result = buildJsonObject {
                    put("seed", seed)
                    put("archiveSha256", hash)
                    put("winner", expectedWinner)
                    put("accepted", expectedAccepted)
                    put("archivedDecisionCount", decisions.size)
                    put("ballistaShotCount", shots.size)
                    put("ballistaShotTargetCounts", buildJsonObject {
                        targetCounts.forEach { (id, count) -> put(id, count) }
                    })
                    put("ballistaDamageResolved", damage.size)
                    put("animarShotCount", animarDamage.size)
                    put("animarToughnessAtDamage", animarToughness)
                    put("animarLeftBattlefieldDuringResolution", animarLeftBattlefield)
                    put("combat1609", combat)
                    put("scoredRecordsExtracted", scoreRecords.size)
                    put("limitation", "Trace-only forensic audit; no alternate legal action or strategic superiority proven")
                }
                results += result
                Files.writeString(out.resolve("forensic-summary.json"), JsonArray(results).toString())
                println("R7_FORENSIC_RESULT=" + result)

                shots.size shouldBe expectedShots
                damage.size shouldBe expectedShots
                animarDamage.size shouldBe expectedAnimarShots
            }
        }
        try {
            audit(
                Path.of(requireNotNull(System.getenv("MANUAL_R7_ARTIFACT_1609"))),
                "1367da258c0ebb9f216101adf3440cf748e251be64c7789fdaff80a25ab5c8eb",
                202610191609L, 3099, "manual-mirror-2",
                2652..2666, 2667..2715, listOf(2638..2715, 3090..3099),
                46, "manual-mirror-2", 15, 8
            )
            audit(
                Path.of(requireNotNull(System.getenv("MANUAL_R7_ARTIFACT_1610"))),
                "111687fab8c483c227ec22478822aa4824ccb736d031b716b9c4855f0fa58ba9",
                202610191610L, 3156, "manual-mirror-0",
                2763..2772, 2773..2806, listOf(2643..2652, 2763..2806),
                53, "manual-mirror-3", 10, 9
            )
            results.size shouldBe 2
        } finally {
            Files.writeString(out.resolve("forensic-summary.json"), JsonArray(results).toString())
        }
    }
})
