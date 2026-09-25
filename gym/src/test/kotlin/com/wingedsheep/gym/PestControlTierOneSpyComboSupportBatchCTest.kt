package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneSpyComboAdmission
import com.wingedsheep.gym.matchup.TierOneSpyComboAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** Exact registry closure only; no official seed, game, policy or authorization is created here. */
class PestControlTierOneSpyComboSupportBatchCTest : FunSpec({
    test("all nineteen frozen Spy main identities resolve with the explicit Nyxborn Bestow definition") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val admission = TierOneSpyComboAdmission()
        PestControlTierOneSpyComboAdmission.validationErrors(admission) shouldBe emptyList()
        PestControlTierOneSpyComboAdmission.mainCounts.size shouldBe 19
        PestControlTierOneSpyComboAdmission.mainCounts.values.sum() shouldBe 60
        PestControlTierOneSpyComboAdmission.sideboardCounts.values.sum() shouldBe 15
        PestControlTierOneSpyComboAdmission.unresolvedMain(registry) shouldBe emptyMap()
        val hydra = registry.getCard("Nyxborn Hydra")!!
        hydra.keywordAbilities.filterIsInstance<KeywordAbility.Bestow>().single().cost.toString() shouldBe "{X}{G}{G}"
        val sideboard = PestControlTierOneSpyComboAdmission.unresolvedSideboard(registry)
        sideboard.containsKey("Nyxborn Hydra") shouldBe false
        // Separate qualified postboard batches may resolve some of these identities. This
        // diagnostic does not admit a boarding plan or claim all postboard mechanics are ready.
        val historicalRemainingSide = linkedMapOf(
            "Jack-o'-Lantern" to 1, "Flaring Pain" to 1, "Faerie Macabre" to 2,
            "Acorn Harvest" to 1, "Nylea's Disciple" to 4,
        )
        sideboard.all { (name, count) -> historicalRemainingSide[name] == count } shouldBe true
        System.getenv("PEST_SPY_CLOSURE_REPORT")?.let { destination ->
            File(destination).apply { parentFile.mkdirs() }.writeText(buildString {
                appendLine("scope=SPY_MAIN_REGISTRY_AND_NYXBORN_CAPABILITY_ONLY")
                appendLine("protocol=${admission.protocolId}")
                appendLine("main_sha256=${admission.opponentMainSha256}")
                appendLine("sideboard_sha256=${admission.opponentSideboardSha256}")
                appendLine("complete75_sha256=${admission.opponentComplete75Sha256}")
                appendLine("main_distinct=19;main_cards=60;sideboard_cards=15")
                appendLine("unresolved_main=0")
                appendLine("unresolved_sideboard=$sideboard")
                appendLine("gameplay_ready=false;pilot_qualified=false;authorization_created=false")
                appendLine("official_seeds_generated=0;official_games_initialized=0;official_actions_submitted=0;outcome_exposure=0")
                appendLine("unqualified_bestow_interactions=copying,phasing,other_zone_cast_permissions")
            })
        }
    }
})
