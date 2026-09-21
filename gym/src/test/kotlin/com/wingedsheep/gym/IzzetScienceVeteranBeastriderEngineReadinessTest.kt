package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.IzzetScienceVeteranBeastriderEngineReadiness
import com.wingedsheep.gym.matchup.IzzetVeteranEngineReadiness
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class IzzetScienceVeteranBeastriderEngineReadinessTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("exact frozen 99-card identities have correct counts") {
        IzzetScienceVeteranBeastriderEngineReadiness.izzetCounts.values.sum() shouldBe 99
        IzzetScienceVeteranBeastriderEngineReadiness.veteranCounts.values.sum() shouldBe 99
        IzzetScienceVeteranBeastriderEngineReadiness.izzetDeck().cards.size shouldBe 99
        IzzetScienceVeteranBeastriderEngineReadiness.veteranDeck().cards.size shouldBe 99
    }

    test("freeze exact unresolved engine coverage count and emit identities") {
        val unresolved = IzzetScienceVeteranBeastriderEngineReadiness.unresolvedCardIdentities(registry)
        println("V09_REAL_ENGINE_UNRESOLVED_COUNT=" + unresolved.size)
        unresolved.forEach { println("V09_REAL_ENGINE_UNRESOLVED=" + it) }
        unresolved.size shouldBe 55
    }

    test("full execution remains fail closed behind unresolved cards and PDH semantics") {
        val blockers = IzzetScienceVeteranBeastriderEngineReadiness.executionBlockers(registry)
        blockers.size shouldBe 60
        blockers.takeLast(5).shouldContainExactly(
            "PDH commander-zone initialization not qualified",
            "PDH commander recast/tax semantics not qualified",
            "16-damage commander-loss accounting not qualified in gameplay engine",
            "30-life PDH game initialization not qualified",
            "Phase-29 event-ledger extraction from full engine game not qualified",
        )
    }

    test("readiness consumes no official seed and exposes no outcome") {
        val r=IzzetVeteranEngineReadiness()
        r.officialGamesAuthorized shouldBe 0
        r.officialSeedsConsumed shouldBe 0
        r.outcomeExposure shouldBe 0
    }
})
