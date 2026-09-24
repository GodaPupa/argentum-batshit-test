package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.MonsterTronStartingDeck
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_DISABLED_INITIALIZER_CONSTRUCTION_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronOfficialInitializationBoundary
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronRunnerContract
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronSmokeHarness
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class PestControlTierOneMonsterTronRunnerConstructionTest : FunSpec({
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("runner contract is exact and disabled") {
        PestControlTierOneMonsterTronRunnerContract.validationErrors(registry = registry) shouldBe emptyList()
    }

    test("smoke construction fixes all four seat x starting-deck cells without a vector") {
        PestControlTierOneMonsterTronSmokeHarness.validationErrors(registry = registry) shouldBe emptyList()
        val cells = PestControlTierOneMonsterTronSmokeHarness.cellTemplate()
        cells.size shouldBe 4
        cells.map { it.pestSeat to it.startingDeck }.toSet() shouldBe setOf(
            PestSeat.SEAT_ZERO to MonsterTronStartingDeck.PEST_CONTROL,
            PestSeat.SEAT_ZERO to MonsterTronStartingDeck.MONSTER_TRON,
            PestSeat.SEAT_ONE to MonsterTronStartingDeck.PEST_CONTROL,
            PestSeat.SEAT_ONE to MonsterTronStartingDeck.MONSTER_TRON,
        )
    }

    test("construction-only initializer is valid but official initialization remains fail closed") {
        val result = PestControlTierOneMonsterTronOfficialInitializationBoundary.inspect(registry)

        result.contractErrors shouldBe emptyList()
        result.failClosed shouldBe true
        result.activationBlockers.shouldContain("smoke harness is disabled")
        result.activationBlockers.shouldContain("official seed vector is absent")
        result.activationBlockers.shouldContain("official assignment is absent")
        result.activationBlockers.shouldContain("execution commit is absent")
        result.activationBlockers.shouldContain("durable attempt marker is absent")
        result.activationBlockers.shouldContain("official initializer is disabled")
        result.blockerSha256 shouldBe PEST_MONSTER_TRON_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        result.constructionValidationSha256 shouldBe PEST_MONSTER_TRON_DISABLED_INITIALIZER_CONSTRUCTION_SHA256
        result.officialInitializerImplemented shouldBe true
        result.officialInitializerEnabled shouldBe false
        result.disabledConstructionFixturesInitialized shouldBe 1
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.officialActionsSubmitted shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("disabled initializer is private and cannot expose initialize publicly") {
        val implementation = Class.forName(
            "com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronDisabledOfficialInitializer"
        )
        Modifier.isPublic(implementation.modifiers) shouldBe false
        val initialize = implementation.declaredMethods.single { it.name == "initialize" }
        Modifier.isPrivate(initialize.modifiers) shouldBe true
    }
})
