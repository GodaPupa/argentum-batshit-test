package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronPolicyReadiness
import com.wingedsheep.gym.matchup.TierOneMonsterTronPolicyReadiness
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronPolicyReadinessTest : FunSpec({
    test("seedless exact-60 policy closes only the policy blocker") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val readiness = TierOneMonsterTronPolicyReadiness()
        PestControlTierOneMonsterTronPolicyReadiness.validationErrors(readiness, registry) shouldBe emptyList()
        PestControlTierOneMonsterTronPolicyReadiness.executionActivationErrors(readiness, registry) shouldBe listOf(
            "no execution runner is defined",
            "no official seed vector is frozen",
            "official Monster Tron games are not authorized",
        )

        val report = buildString {
            appendLine("schema=pest-monster-tron-policy-readiness-v1")
            appendLine("protocol_id=${readiness.protocolId}")
            appendLine("pest_main_sha256=${readiness.pestMainSha256}")
            appendLine("opponent_main_sha256=${readiness.opponentMainSha256}")
            appendLine("policy_status=${readiness.policyStatus}")
            appendLine("runner_state=${readiness.runnerState}")
            appendLine("official_games_authorized=0")
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions=0")
            appendLine("outcome_exposure=0")
            appendLine("next_blockers=runner;seed_freeze;game_authorization")
        }
        println(report)
        System.getenv("PEST_MONSTER_TRON_POLICY_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }
    }
})
