package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneSpyComboAdmission
import com.wingedsheep.gym.matchup.TierOneSpyComboAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneSpyComboAdmissionTest : FunSpec({
    test("freeze exact Spy Combo 60/15 and emit live support audit") {
        PestControlTierOneSpyComboAdmission.validationErrors(TierOneSpyComboAdmission()) shouldBe
            emptyList()

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val main = PestControlTierOneSpyComboAdmission.unresolvedMain(registry)
        val side = PestControlTierOneSpyComboAdmission.unresolvedSideboard(registry)

        val report = buildString {
            appendLine("schema=pest-control-tier-one-spy-admission-v1")
            appendLine("main_count=60")
            appendLine("sideboard_count=15")
            appendLine(
                "unresolved_main=" +
                    main.entries.joinToString(";") { (name, count) -> "$name:$count" }
            )
            appendLine(
                "unresolved_sideboard=" +
                    side.entries.joinToString(";") { (name, count) -> "$name:$count" }
            )
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions_submitted=0")
            appendLine("outcome_exposure=0")
            appendLine(
                "status=" +
                    if (main.isEmpty()) "PREBOARD_SUPPORT_COMPLETE" else "PREBOARD_SUPPORT_BLOCKED"
            )
        }
        println(report)

        System.getenv("PEST_SPY_ADMISSION_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }
    }
})
