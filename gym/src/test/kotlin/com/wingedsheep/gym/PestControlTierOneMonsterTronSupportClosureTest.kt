package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Keyword
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronSupportClosureTest : FunSpec({
    test("all non-Prototype Monster Tron preboard gaps resolve and Prototype remains explicit blocker") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val expectedResolved = listOf(
            "Ancient Stirrings",
            "Crop Rotation",
            "Unfathomable Truths",
            "Bonder's Ornament",
            "Bojuka Bog",
            "Haunted Fengraf",
        )
        val unresolvedExpected = expectedResolved.filter { registry.getCard(it) == null }
        unresolvedExpected shouldBe emptyList()

        val unresolvedMain = PestControlTierOneMonsterTronAdmission.unresolvedMain(registry)
        unresolvedMain shouldBe mapOf("Boulderbranch Golem" to 2)

        val prototypeKeywordPresent = Keyword.entries.any { it.name == "PROTOTYPE" }
        prototypeKeywordPresent shouldBe false

        val report = buildString {
            appendLine("schema=pest-monster-tron-support-closure-v1")
            appendLine("protocol_id=PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1")
            appendLine("resolved_nonprototype=" + expectedResolved.joinToString(";"))
            appendLine(
                "unresolved_main=" +
                    unresolvedMain.entries.joinToString(";") { (name, count) -> name + ":" + count }
            )
            appendLine("prototype_keyword_present=" + prototypeKeywordPresent)
            appendLine("status=PREBOARD_SUPPORT_BLOCKED_ON_PROTOTYPE")
            appendLine("official_games_authorized=0")
            appendLine("official_seeds_generated=0")
            appendLine("outcome_exposure=0")
        }
        println(report)

        System.getenv("PEST_MONSTER_TRON_SUPPORT_CLOSURE_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }
    }
})
