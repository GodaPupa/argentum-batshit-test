package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_GRIXIS_COMPLETE_75_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_MAIN_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_SIDEBOARD_SHA256
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisReadiness
import com.wingedsheep.gym.matchup.TierOneGrixisReadiness
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisReadinessTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("exact provenance-locked preboard identities resolve without constructing a game") {
        PestControlPreboardDecks.verifyFrozenIdentities()
        val readiness = TierOneGrixisReadiness()

        readiness.pestMainSha256 shouldBe PEST_CONTROL_V10_HASH
        readiness.grixisMainSha256 shouldBe PEST_GRIXIS_MAIN_SHA256
        readiness.grixisSideboardSha256 shouldBe PEST_GRIXIS_SIDEBOARD_SHA256
        readiness.grixisComplete75Sha256 shouldBe PEST_GRIXIS_COMPLETE_75_SHA256
        PestControlTierOneGrixisReadiness.mainDeck().cards.size shouldBe 60
        PestControlTierOneGrixisReadiness.mainCounts.entries.map { it.key to it.value }
            .shouldContainExactlyInAnyOrder(
                "Drossforge Bridge" to 3, "Great Furnace" to 2, "Mistvault Bridge" to 3,
                "Seat of the Synod" to 3, "Silverbluff Bridge" to 2, "Swamp" to 1,
                "Vault of Whispers" to 4, "Krark-Clan Shaman" to 2, "Myr Enforcer" to 4,
                "Refurbished Familiar" to 4, "Utrom Monitor" to 3, "Cast Down" to 3,
                "Fanatical Offering" to 2, "Galvanic Blast" to 4, "Reckoner's Bargain" to 4,
                "Thoughtcast" to 4, "Toxin Analysis" to 2, "Blood Fountain" to 3,
                "Ichor Wellspring" to 4, "Makeshift Munitions" to 1, "Nihil Spellbomb" to 2,
            )
        PestControlTierOneGrixisReadiness.validationErrors(readiness, registry).shouldBeEmpty()
    }

    test("readiness is fail-closed and cannot activate execution") {
        val errors = PestControlTierOneGrixisReadiness.executionActivationErrors(
            TierOneGrixisReadiness(),
            registry,
        )

        errors.shouldContainExactly(
            "no execution runner is defined",
            "no official seed vector is frozen",
            "official Grixis games are not authorized",
        )
    }

    test("readiness records zero seeds games and outcome exposure") {
        val readiness = TierOneGrixisReadiness()
        readiness.officialGamesAuthorized shouldBe 0
        readiness.officialSeedsGenerated shouldBe 0
        readiness.outcomeExposure shouldBe 0
    }
})
