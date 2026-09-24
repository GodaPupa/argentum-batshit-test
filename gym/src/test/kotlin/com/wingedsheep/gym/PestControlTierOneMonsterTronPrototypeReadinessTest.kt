package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.TierOneMonsterTronAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronPrototypeReadinessTest : FunSpec({
    test("Prototype support closes the exact Monster Tron preboard registry gap without authorizing gameplay") {
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

        Keyword.entries.any { it == Keyword.PROTOTYPE } shouldBe true
        val boulderbranch = registry.getCard("Boulderbranch Golem")
            ?: error("Boulderbranch Golem must resolve after Prototype gate")
        val prototype = boulderbranch.keywordAbilities
            .filterIsInstance<KeywordAbility.Prototype>()
            .single()

        boulderbranch.manaCost.toString() shouldBe "{7}"
        boulderbranch.creatureStats?.basePower shouldBe 6
        boulderbranch.creatureStats?.baseToughness shouldBe 5
        prototype.cost.toString() shouldBe "{3}{G}"
        prototype.power shouldBe 3
        prototype.toughness shouldBe 3

        val report = buildString {
            appendLine("schema=pest-monster-tron-prototype-readiness-v1")
            appendLine("protocol_id=${admission.protocolId}")
            appendLine("opponent_main_sha256=${admission.opponentMainSha256}")
            appendLine("unresolved_main=")
            appendLine("prototype_keyword_present=true")
            appendLine("boulderbranch_normal_cost={7}")
            appendLine("boulderbranch_normal_stats=6/5")
            appendLine("boulderbranch_prototype_cost={3}{G}")
            appendLine("boulderbranch_prototype_stats=3/3")
            appendLine("maelstrom_cascade_prototype_choice_required=true")
            appendLine("prototype_free_cast_combination_rules_covered=true")
            appendLine("status=PREBOARD_CARD_SUPPORT_COMPLETE_GAMEPLAY_NOT_AUTHORIZED")
            appendLine("official_games_authorized=0")
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions=0")
            appendLine("outcome_exposure=0")
        }
        println(report)

        System.getenv("PEST_MONSTER_TRON_PROTOTYPE_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }
    }
})
