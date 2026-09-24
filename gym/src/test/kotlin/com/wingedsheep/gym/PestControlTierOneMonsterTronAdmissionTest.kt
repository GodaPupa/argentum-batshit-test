package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.TierOneMonsterTronAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronAdmissionTest : FunSpec({
    test("exact mehanske Monster Tron identity is frozen and support audit is read-only") {
        val admission = TierOneMonsterTronAdmission()
        PestControlTierOneMonsterTronAdmission.validationErrors(admission) shouldBe emptyList()

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val unresolvedMain = PestControlTierOneMonsterTronAdmission.unresolvedMain(registry)
        val unresolvedSideboard = PestControlTierOneMonsterTronAdmission.unresolvedSideboard(registry)

        val report = buildString {
            appendLine("schema=pest-monster-tron-support-audit-v1")
            appendLine("protocol_id=${admission.protocolId}")
            appendLine("opponent_pilot=${admission.opponentPilot}")
            appendLine("opponent_main_sha256=${admission.opponentMainSha256}")
            appendLine("opponent_sideboard_sha256=${admission.opponentSideboardSha256}")
            appendLine("opponent_complete75_sha256=${admission.opponentComplete75Sha256}")
            appendLine("main_cards=${PestControlTierOneMonsterTronAdmission.mainCounts.values.sum()}")
            appendLine("sideboard_cards=${PestControlTierOneMonsterTronAdmission.sideboardCounts.values.sum()}")
            appendLine(
                "unresolved_main=" +
                    unresolvedMain.entries.joinToString(";") { (name, count) -> "$name:$count" }
            )
            appendLine(
                "unresolved_sideboard=" +
                    unresolvedSideboard.entries.joinToString(";") { (name, count) -> "$name:$count" }
            )
            appendLine("runner_state=${admission.runnerState}")
            appendLine("official_games_authorized=${admission.officialGamesAuthorized}")
            appendLine("official_seeds_generated=${admission.officialSeedsGenerated}")
            appendLine("outcome_exposure=${admission.outcomeExposure}")
        }

        println(report)

        System.getenv("PEST_MONSTER_TRON_SUPPORT_REPORT")?.let { rawPath ->
            val path = Path.of(rawPath)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }

        admission.officialGamesAuthorized shouldBe 0
        admission.officialSeedsGenerated shouldBe 0
        admission.outcomeExposure shouldBe 0
    }
})
