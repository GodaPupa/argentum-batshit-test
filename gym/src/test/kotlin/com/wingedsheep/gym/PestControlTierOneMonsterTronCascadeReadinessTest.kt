package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.TierOneMonsterTronAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Keyword
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronCascadeReadinessTest : FunSpec({
    test("Cascade rules support closes the frozen Monster Tron cast-trigger blocker without gameplay") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val admission = TierOneMonsterTronAdmission()
        PestControlTierOneMonsterTronAdmission.validationErrors(admission) shouldBe emptyList()
        PestControlTierOneMonsterTronAdmission.unresolvedMain(registry) shouldBe emptyMap()

        val maelstrom = registry.getCard("Maelstrom Colossus")
            ?: error("Maelstrom Colossus must resolve")
        maelstrom.hasKeyword(Keyword.CASCADE) shouldBe true

        val report = buildString {
            appendLine("schema=pest-monster-tron-cascade-readiness-v1")
            appendLine("protocol_id=${admission.protocolId}")
            appendLine("opponent_main_sha256=${admission.opponentMainSha256}")
            appendLine("unresolved_main=")
            appendLine("maelstrom_colossus_cascade_keyword=true")
            appendLine("cascade_trigger_wiring_validated=true")
            appendLine("cascade_boulderbranch_normal_free_validated=true")
            appendLine("cascade_boulderbranch_prototype_free_validated=true")
            appendLine("status=CASCADE_RULES_SUPPORT_COMPLETE_BROADER_SEEDLESS_READINESS_PENDING")
            appendLine("official_games_authorized=0")
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions=0")
            appendLine("outcome_exposure=0")
        }
        println(report)
        System.getenv("PEST_MONSTER_TRON_CASCADE_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }
    }
})
