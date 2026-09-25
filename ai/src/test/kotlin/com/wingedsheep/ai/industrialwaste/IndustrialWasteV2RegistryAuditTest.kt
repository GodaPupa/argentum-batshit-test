package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

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
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
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
        val output = root.resolve("industrial-waste/v2/runtime-evidence/registry-audit.txt")
        Files.createDirectories(output.parent)
        Files.writeString(output, buildString {
            appendLine("status=COMPILED_IDENTITY_COVERAGE_COMPLETE")
            appendLine("identities=${names.size}")
            appendLine("unresolved=0")
            appendLine("gameplay_ready=false")
            appendLine("official_games_initialized=0")
            appendLine("source_head=${System.getenv("GITHUB_SHA") ?: "local-unbound"}")
            names.sorted().forEach { appendLine("card=$it") }
        })
    }
})
