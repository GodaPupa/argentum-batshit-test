package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisReadiness
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorReadiness
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.PestControlTierOneSpyComboAdmission
import com.wingedsheep.gym.matchup.TierOneGrixisReadiness
import com.wingedsheep.gym.matchup.TierOneMonoBlueTerrorReadiness
import com.wingedsheep.gym.matchup.TierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.TierOneSpyComboAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Registry and frozen identities only; no game constructor, pilot, seed, or action path. */
class PestControlTierOnePostboardSupportBatchATest : FunSpec({
    test("four postboard definitions close eleven slots in the unchanged six frozen sideboards") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
        }
        PestControlPreboardDecks.verifyFrozenIdentities()
        PestControlTierOneGrixisReadiness.validationErrors(TierOneGrixisReadiness()) shouldBe emptyList()
        PestControlTierOneMonoBlueTerrorReadiness.validationErrors(TierOneMonoBlueTerrorReadiness()) shouldBe emptyList()
        PestControlTierOneMonsterTronAdmission.validationErrors(TierOneMonsterTronAdmission()) shouldBe emptyList()
        PestControlTierOneSpyComboAdmission.validationErrors(TierOneSpyComboAdmission()) shouldBe emptyList()

        val sideboards = linkedMapOf(
            "pest_control" to PestControlPreboardDecks.pestSideboardCounts,
            "mono_red_madness" to PestControlPreboardDecks.monoRedSideboardCounts,
            "grixis_affinity" to PestControlTierOneGrixisReadiness.sideboardCounts,
            "mono_blue_terror" to PestControlTierOneMonoBlueTerrorReadiness.sideboardCounts,
            "monster_tron" to PestControlTierOneMonsterTronAdmission.sideboardCounts,
            "spy_combo" to PestControlTierOneSpyComboAdmission.sideboardCounts,
        )
        sideboards.values.forEach { it.values.sum() shouldBe 15 }
        val newCards = listOf("Smash to Smithereens", "Unexpected Fangs", "Murmuring Mystic", "Nylea's Disciple")
        newCards.forEach { name -> registry.getCard(name)?.name shouldBe name }
        val changedSlots = sideboards.values.sumOf { counts -> counts.filterKeys { it in newCards }.values.sum() }
        changedSlots shouldBe 11
        val remaining = sideboards.mapValues { (_, counts) -> counts.filterKeys { registry.getCard(it) == null } }
        remaining shouldBe linkedMapOf(
            "pest_control" to emptyMap(),
            "mono_red_madness" to linkedMapOf("Relic of Progenitus" to 3),
            "grixis_affinity" to emptyMap(),
            "mono_blue_terror" to linkedMapOf("Spreading Seas" to 3),
            "monster_tron" to linkedMapOf("Kaervek's Torch" to 1, "Relic of Progenitus" to 4),
            "spy_combo" to linkedMapOf("Jack-o'-Lantern" to 1, "Nyxborn Hydra" to 1, "Flaring Pain" to 1,
                "Faerie Macabre" to 2, "Acorn Harvest" to 1),
        )
        val text = buildString {
            appendLine("schema=pest-control-tier-one-postboard-support-batch-a-v1")
            appendLine("accepted_inventory_artifact=10838516145")
            appendLine("accepted_inventory_zip_sha256=c0c08f915348a0512dd15a17d2edad1479b3f1d09d59948455fe3c81f94b10b7")
            appendLine("definitions_added=" + newCards.joinToString(";"))
            appendLine("newly_supported_sideboard_slots=$changedSlots")
            appendLine("independent_spy_batch_b_supported_sideboard_slots=3")
            appendLine("independent_spy_batch_b_source=7a3f1429c027925c30ed7c1e329c3879e674574a")
            appendLine("remaining_unique_identities=" + remaining.values.flatMap { it.keys }.toSet().size)
            appendLine("remaining_sideboard_slots=" + remaining.values.sumOf { it.values.sum() })
            remaining.forEach { (deck, gaps) ->
                appendLine("$deck=" + gaps.entries.joinToString(";") { (name, count) -> "$name:$count" })
            }
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions_submitted=0")
            appendLine("outcome_exposure=0")
            appendLine("boarding_plans_frozen=false")
            appendLine("postboard_gameplay_authorized=false")
        }
        println(text)
        System.getenv("PEST_POSTBOARD_BATCH_A_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, text)
        }
    }
})
