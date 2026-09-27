package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.serialization.CardExporter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Compiled-catalog admission only: no seed, pilot, action or matchup execution. */
class IndustrialWasteV2RegistryAuditTest : FunSpec({
    test("all frozen v2 candidates and historical comparator resolve in the compiled catalog") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isDirectory(it.resolve("industrial-waste/v2/candidates")) }
        val paths = listOf(
            "industrial-waste/control/industrial-waste-v1.0-submitted.dck",
            "industrial-waste/v2/candidates/compact-loop.dck",
            "industrial-waste/v2/candidates/recursive-eggs.dck",
            "industrial-waste/v2/candidates/lean-tron-hybrid.dck",
        )
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
            register(PredefinedTokens.allTokens)
        }
        val names = linkedSetOf<String>()
        for (relative in paths) {
            var section = ""
            val counts = mutableMapOf("main" to 0, "sideboard" to 0)
            for (line in Files.readAllLines(root.resolve(relative))) {
                if (line.startsWith("[")) { section = line.removeSurrounding("[", "]"); continue }
                if (section !in counts || line.isBlank()) continue
                val row = Regex("([1-9][0-9]*) (.+)").matchEntire(line) ?: error("Malformed card row")
                counts[section] = counts.getValue(section) + row.groupValues[1].toInt()
                names += row.groupValues[2]
            }
            counts shouldBe mapOf("main" to 60, "sideboard" to 15)
        }
        val unresolved = names.filter { registry.getCard(it) == null }
        unresolved shouldBe emptyList()
        names.size shouldBe 34
        val headProcess = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(root.toFile()).redirectErrorStream(true).start()
        val actualHead = headProcess.inputStream.bufferedReader().use { it.readText() }.trim()
        headProcess.waitFor() shouldBe 0
        actualHead.matches(Regex("[0-9a-f]{40}")) shouldBe true
        System.getenv("IW_V2_EXPECTED_HEAD")?.let { actualHead shouldBe it }
        val output = root.resolve("industrial-waste/v2/runtime-evidence/registry-audit.txt")
        Files.createDirectories(output.parent)
        Files.writeString(output, buildString {
            appendLine("status=COMPILED_IDENTITY_COVERAGE_COMPLETE")
            appendLine("identities=${names.size}")
            appendLine("unresolved=0")
            appendLine("gameplay_ready=false")
            appendLine("official_games_initialized=0")
            appendLine("source_head=$actualHead")
            names.sorted().forEach { appendLine("card=$it") }
        })
        // Resolve through the same catalog-then-token registration order as the allocation and
        // replay drivers. These are the actual compiled definitions, including their AbilityIds;
        // no name-only projection or normalized semantic hash substitutes for their bytes.
        val tokenNames = PredefinedTokens.allTokens.map { it.name }.distinct().sorted()
        val definitions = (names + tokenNames).sorted().map { name ->
            val raw = CardExporter.exportToJson(registry.requireCard(name))
            buildJsonObject {
                put("name", name)
                put("raw_definition_json", raw)
                put("raw_definition_sha256", MessageDigest.getInstance("SHA-256")
                    .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
            }
        }
        val compiled = buildJsonObject {
            put("schema", "industrial-compiled-card-registry-observation-v1")
            put("source_head", actualHead)
            put("registration_order", "MtgSetCatalog set cards then basic lands; PredefinedTokens last")
            put("frozen_card_names", JsonArray(names.sorted().map(::JsonPrimitive)))
            put("registered_token_names", JsonArray(tokenNames.map(::JsonPrimitive)))
            put("definitions", JsonArray(definitions))
            put("gameplay_authorized", false)
            put("official_games_initialized", 0)
        }
        Files.writeString(output.parent.resolve("compiled-card-registry.json"), compiled.toString() + "\n",
            StandardOpenOption.CREATE_NEW)
    }
})
