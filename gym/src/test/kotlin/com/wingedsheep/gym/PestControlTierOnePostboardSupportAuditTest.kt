package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisReadiness
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorReadiness
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOnePostboardSupportAuditTest : FunSpec({
    test("audit all frozen Tier-1 sideboards without gameplay") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        fun unresolved(rows: Map<String, Int>): Map<String, Int> =
            rows.filterKeys { registry.getCard(it) == null }

        val spySideboard = linkedMapOf(
            "Swamp" to 1,
            "Jack-o'-Lantern" to 1,
            "Nyxborn Hydra" to 1,
            "Flaring Pain" to 1,
            "Masked Vandal" to 1,
            "Mesmeric Fiend" to 1,
            "Undergrowth Leopard" to 2,
            "Faerie Macabre" to 2,
            "Acorn Harvest" to 1,
            "Nylea's Disciple" to 4,
        )

        PestControlPreboardDecks.pestSideboardCounts.values.sum() shouldBe 15
        PestControlPreboardDecks.monoRedSideboardCounts.values.sum() shouldBe 15
        PestControlTierOneGrixisReadiness.sideboardCounts.values.sum() shouldBe 15
        PestControlTierOneMonoBlueTerrorReadiness.sideboardCounts.values.sum() shouldBe 15
        PestControlTierOneMonsterTronAdmission.sideboardCounts.values.sum() shouldBe 15
        spySideboard.values.sum() shouldBe 15

        val report = linkedMapOf(
            "pest_control" to unresolved(PestControlPreboardDecks.pestSideboardCounts),
            "mono_red_madness" to unresolved(PestControlPreboardDecks.monoRedSideboardCounts),
            "grixis_affinity" to unresolved(PestControlTierOneGrixisReadiness.sideboardCounts),
            "mono_blue_terror" to PestControlTierOneMonoBlueTerrorReadiness.unresolvedSideboard(registry),
            "monster_tron" to PestControlTierOneMonsterTronAdmission.unresolvedSideboard(registry),
            "spy_combo" to unresolved(spySideboard),
        )

        val text = buildString {
            appendLine("schema=pest-control-tier-one-postboard-support-audit-v1")
            report.forEach { (deck, gaps) ->
                appendLine(
                    "$deck=" + gaps.entries.joinToString(";") { (name, count) -> "$name:$count" }
                )
            }
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions_submitted=0")
            appendLine("outcome_exposure=0")
        }
        println(text)

        System.getenv("PEST_POSTBOARD_SUPPORT_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, text)
        }
    }
})
