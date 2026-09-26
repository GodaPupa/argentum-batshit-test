package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_MAIN_SHA256
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorReadiness
import com.wingedsheep.gym.matchup.TierOneMonoBlueTerrorReadiness
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PestControlTierOneMonoBlueTerrorReadinessTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("provenance-locked Mono-Blue Terror identity is exact without constructing a game") {
        PestControlPreboardDecks.verifyFrozenIdentities()
        val readiness = TierOneMonoBlueTerrorReadiness()

        readiness.pestMainSha256 shouldBe PEST_CONTROL_V10_HASH
        readiness.opponentMainSha256 shouldBe PEST_MONO_BLUE_TERROR_MAIN_SHA256
        readiness.opponentSideboardSha256 shouldBe PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256
        readiness.opponentComplete75Sha256 shouldBe PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256
        PestControlTierOneMonoBlueTerrorReadiness.mainCounts.values.sum() shouldBe 60
        PestControlTierOneMonoBlueTerrorReadiness.sideboardCounts.values.sum() shouldBe 15
        PestControlTierOneMonoBlueTerrorReadiness.mainCounts.entries.map { it.key to it.value }
            .shouldContainExactlyInAnyOrder(
                "Island" to 13,
                "Snow-Covered Island" to 1,
                "Cryptic Serpent" to 4,
                "Tolarian Terror" to 4,
                "Deem Inferior" to 2,
                "Lórien Revealed" to 4,
                "Artful Dodge" to 2,
                "Ponder" to 4,
                "Preordain" to 4,
                "Sleep of the Dead" to 2,
                "Brainstorm" to 4,
                "Dispel" to 4,
                "Mental Note" to 4,
                "Thought Scour" to 4,
                "Counterspell" to 4,
            )
        PestControlTierOneMonoBlueTerrorReadiness.validationErrors(readiness, registry).shouldBeEmpty()
    }

    test("current registry resolves both frozen lists without changing historical readiness") {
        PestControlTierOneMonoBlueTerrorReadiness.unresolvedMain(registry) shouldBe emptyMap()
        PestControlTierOneMonoBlueTerrorReadiness.unresolvedSideboard(registry) shouldBe emptyMap()
    }

    test("current support advances explicitly while the historical inventory remains exact") {
        PestControlTierOneMonoBlueTerrorReadiness.expectedUnsupportedSideboard shouldBe linkedMapOf(
            "Gut Shot" to 3,
            "Hydroblast" to 4,
            "Murmuring Mystic" to 1,
            "Spreading Seas" to 3,
        )
        PestControlTierOneMonoBlueTerrorReadiness.currentUnsupportedSideboard shouldBe emptyMap()
        TierOneMonoBlueTerrorReadiness().sideboardStatus shouldBe
            "FROZEN_15; NOT_INSTANTIATED; BLOCKED_4_IDENTITIES_11_SLOTS"
    }

    for (missingCard in listOf("Annul", "Murmuring Mystic", "Gut Shot", "Hydroblast", "Spreading Seas")) {
        test("current support rejects a missing required sideboard identity: $missingCard") {
            val incompleteRegistry = CardRegistry().apply {
                registry.allCardNames().filter { it != missingCard }.forEach { register(registry.requireCard(it)) }
            }
            PestControlTierOneMonoBlueTerrorReadiness.validationErrors(
                TierOneMonoBlueTerrorReadiness(), incompleteRegistry,
            ).shouldContainExactly("Mono-Blue Terror sideboard support audit drift")
        }
    }

    test("readiness is fail-closed and cannot activate execution") {
        val errors = PestControlTierOneMonoBlueTerrorReadiness.executionActivationErrors(
            TierOneMonoBlueTerrorReadiness(),
            registry,
        )

        errors.shouldContainExactly(
            "no execution runner is defined",
            "no official seed vector is frozen",
            "official Mono-Blue Terror games are not authorized",
        )
    }

    test("readiness records zero seeds games and outcome exposure") {
        val readiness = TierOneMonoBlueTerrorReadiness()
        readiness.officialGamesAuthorized shouldBe 0
        readiness.officialSeedsGenerated shouldBe 0
        readiness.outcomeExposure shouldBe 0
    }
})
