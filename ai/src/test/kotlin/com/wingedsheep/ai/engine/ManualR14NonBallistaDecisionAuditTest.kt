package com.wingedsheep.ai.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Original-action anchored discovery only. Scored options are NOT proven mistakes. */
class ManualR14NonBallistaDecisionAuditTest : FunSpec({
    test("extract exact non-Ballista decision opportunities from four SHA-bound R11 originals") {
        val manifest = listOf(
            Triple("12236591581", "r5-seed-202610191613",
                "76eabaf1dd02ddcc9ec31738a42d7403acf9db6f655bfa21ea4746fff1dbe63f"),
            Triple("12236570842", "r5-seed-202610191614",
                "9ab435668421509c63d59dd45601ce237063ef5ce239e4ab49f17f7baaa0f1b5"),
            Triple("12236521360", "r7-seed-202610191613",
                "acdf66ada6e187cb7203078e646a2ebaf18f59944d389a4c420f3306487fb98f"),
            Triple("12235509372", "r7-seed-202610191614",
                "037fa99b032e63976a33b7c0c9b0bd9e1abcb5e0c7d4adcca923a46a49433fbc")
        )
        val input = Path.of(requireNotNull(System.getenv("MANUAL_R14_INPUT")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R14_OUTPUT")))
        Files.createDirectories(output)
        val json = Json { ignoreUnknownKeys = true }
        val all = mutableListOf<JsonElement>()
        val audits = mutableListOf<JsonElement>()
        var decisionsTotal = 0
        var anchoredTotal = 0

        fun JsonObject.string(k: String) = this[k]?.jsonPrimitive?.contentOrNull
        fun JsonObject.integer(k: String) = this[k]?.jsonPrimitive?.intOrNull
        fun JsonObject.flag(k: String) = this[k]?.jsonPrimitive?.booleanOrNull == true
        fun JsonObject.score(k: String) = this[k]?.jsonPrimitive?.doubleOrNull

        for ((artifact, arm, digest) in manifest) {
            val path = input.resolve("$artifact.zip")
            val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                .joinToString("") { "%02x".format(it) }
            hash shouldBe digest
            ZipFile(path.toFile()).use { zip ->
                fun read(name: String): String = zip.getInputStream(requireNotNull(zip.getEntry(name)) {
                    "Missing original $artifact entry: $name"
                }).bufferedReader().readText()
                read("r11-output/source-sha.txt").trim() shouldBe
                    "79d3c0609eeb09931080bd4caffad702b703b7c0"
                val prefix = "r11-output/$arm/"
                val summary = json.parseToJsonElement(read(prefix + "summary.json")).jsonObject
                summary.string("status") shouldBe "COMPLETE"
                val decisions = read(prefix + "decisions.jsonl").lineSequence()
                    .filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.toList()
                val rows = read(prefix + "actions.jsonl").lineSequence()
                    .filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.toList()
                val submits = rows.filter { it.string("record") == "submit" }
                val results = rows.filter { it.string("record") == "result" }
                check(submits.size == results.size && submits.size == summary.integer("accepted"))
                decisions.size shouldBe summary.integer("scoredChoiceRecords")
                var cursor = 0
                var anchored = 0
                var declines = 0
                var materialized = 0
                var dropped = 0
                for ((ordinal, decision) in decisions.withIndex()) {
                    val options = decision["options"]!!.jsonArray.map { it.jsonObject }
                    val chosen = options.single { it.flag("chosen") }
                    val action = chosen["action"] as? JsonObject
                        ?: error("Chosen option is not materialized: $artifact/$ordinal")
                    val found = (cursor until submits.size).firstOrNull { k ->
                        val submitted = submits[k]
                        submitted.string("actor") == decision.string("playerId") &&
                            submitted.integer("turn") == decision.integer("turnNumber") &&
                            submitted.string("step") == decision.string("step") &&
                            json.parseToJsonElement(submitted.string("action")!!).jsonObject == action
                    } ?: error("Cannot bind $artifact decision $ordinal to an accepted action")
                    cursor = found + 1
                    anchored++
                    val chosenScore = chosen.score("score") ?: decision.score("baselineScore")
                    dropped += options.count { it["action"] !is JsonObject }
                    for (option in options) {
                        val card = option.string("cardName")
                        if (option.flag("chosen") || card == "Walking Ballista" ||
                            chosen.string("cardName") == "Walking Ballista" ||
                            option["action"] !is JsonObject || option.score("score") == null ||
                            option["productionAdmissible"]?.jsonPrimitive?.booleanOrNull == false) continue
                        materialized++
                        val category = when {
                            card == "Animar, Soul of Elements" && option.string("actionType") == "CastSpell" ->
                                "ANIMAR_DEPLOYMENT_DECLINE"
                            card in setOf("Ancestral Statue", "Purphoros, God of the Forge") ->
                                "COMBO_PIECE_DECLINE"
                            card in setOf("Pact of Negation", "Veil of Summer") ->
                                "INTERACTION_DECLINE"
                            chosenScore != null && option.score("score")!! > chosenScore + 0.25 ->
                                "SCORED_ACTION_INVERSION"
                            else -> null
                        } ?: continue
                        if (category == "ANIMAR_DEPLOYMENT_DECLINE") declines++
                        all += buildJsonObject {
                            put("category", category)
                            put("artifact", artifact)
                            put("archiveSha256", digest)
                            put("seed", arm.substringAfter("seed-"))
                            put("profile", arm.substringBefore("-seed"))
                            put("source", "79d3c0609eeb09931080bd4caffad702b703b7c0")
                            put("decisionOrdinal", ordinal + 1)
                            put("actionIndex", submits[found].integer("index"))
                            put("turn", decision.integer("turnNumber"))
                            put("step", decision.string("step"))
                            put("seat", decision.string("playerId"))
                            put("chosenLabel", chosen.string("label"))
                            put("chosenScore", chosenScore)
                            put("baselineScore", decision.score("baselineScore"))
                            put("alternativeLabel", option.string("label"))
                            put("alternativeScore", option.score("score"))
                            put("alternativeNote", option.string("note"))
                            put("alternativeAction", option["action"]!!)
                            put("defectProven", false)
                            put("requiresExactStateReplay", true)
                        }
                    }
                }
                decisionsTotal += decisions.size
                anchoredTotal += anchored
                val audit = buildJsonObject {
                    put("artifact", artifact)
                    put("sha256", hash)
                    put("profileAndSeed", arm)
                    put("status", summary.string("status"))
                    put("actions", submits.size)
                    put("decisions", decisions.size)
                    put("anchoredDecisions", anchored)
                    put("materializedNonBallistaAlternatives", materialized)
                    put("unmaterializedOptions", dropped)
                    put("animarDeclines", declines)
                }
                audits += audit
                println("R14_ARTIFACT_AUDIT=" + audit)
            }
        }
        val top = all.sortedWith(compareBy<JsonElement> {
            when (it.jsonObject.string("category")) {
                "SCORED_ACTION_INVERSION" -> 0
                "ANIMAR_DEPLOYMENT_DECLINE" -> 1
                "COMBO_PIECE_DECLINE" -> 2
                else -> 3
            }
        }.thenBy { it.jsonObject["artifact"]?.jsonPrimitive?.content })
            .take(30)
        val summary = buildJsonObject {
            put("status", "EXTRACTION_COMPLETE")
            put("originalArtifactsVerified", audits.size)
            put("decisions", decisionsTotal)
            put("actionAnchors", anchoredTotal)
            put("flaggedWindows", all.size)
            put("defectsProven", 0)
            put("newCommanderGames", 0)
            put("perOriginal", JsonArray(audits))
        }
        Files.writeString(output.resolve("r14-non-ballista-summary.json"), summary.toString())
        Files.writeString(output.resolve("r14-non-ballista-windows.json"), JsonArray(all).toString())
        Files.writeString(output.resolve("r14-top-ranked.json"), JsonArray(top).toString())
        println("R14_NON_BALLISTA_RESULT=" + summary)
        audits.size shouldBe 4
        check(decisionsTotal > 2_000 && decisionsTotal == anchoredTotal && all.isNotEmpty())
    }
})
