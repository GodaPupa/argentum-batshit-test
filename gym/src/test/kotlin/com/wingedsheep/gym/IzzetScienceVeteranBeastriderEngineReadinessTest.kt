package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.IzzetScienceVeteranBeastriderEngineReadiness
import com.wingedsheep.gym.matchup.IzzetVeteranEngineReadiness
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.CharacteristicValue
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
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

    test("Boreal Druid resolves with exact colorless mana ability") {
        val card = registry.getCard("Boreal Druid") ?: error("Boreal Druid unresolved")
        card.oracleText shouldBe "{T}: Add {C}."
        card.creatureStats?.basePower shouldBe 1
        card.creatureStats?.baseToughness shouldBe 1
        val ability = card.script.activatedAbilities.single()
        ability.isManaAbility shouldBe true
        val effect = ability.effect as AddColorlessManaEffect
        val amount = effect.amount as DynamicAmount.Fixed
        amount.amount shouldBe 1
    }

    test("Llanowar Visionary resolves as ETB draw mana creature") {
        val card = registry.getCard("Llanowar Visionary") ?: error("Llanowar Visionary unresolved")
        card.oracleText shouldBe "When this creature enters, draw a card.\n{T}: Add {G}."
        card.creatureStats?.basePower shouldBe 2
        card.creatureStats?.baseToughness shouldBe 2
        card.script.triggeredAbilities.size shouldBe 1
        card.script.activatedAbilities.single().isManaAbility shouldBe true
    }

    test("Owlbear resolves as trample ETB draw creature") {
        val card = registry.getCard("Owlbear") ?: error("Owlbear unresolved")
        card.oracleText shouldBe "Trample\nKeen Senses — When this creature enters, draw a card."
        card.creatureStats?.basePower shouldBe 4
        card.creatureStats?.baseToughness shouldBe 4
        card.keywords shouldContain Keyword.TRAMPLE
        card.script.triggeredAbilities.size shouldBe 1
    }

    test("freeze exact unresolved engine coverage count and emit identities") {
        val unresolved = IzzetScienceVeteranBeastriderEngineReadiness.unresolvedCardIdentities(registry)
        println("V09_REAL_ENGINE_UNRESOLVED_COUNT=" + unresolved.size)
        unresolved.forEach { println("V09_REAL_ENGINE_UNRESOLVED=" + it) }
        unresolved.size shouldBe 50
    }

    test("full execution remains fail closed behind unresolved cards and PDH semantics") {
        val blockers = IzzetScienceVeteranBeastriderEngineReadiness.executionBlockers(registry)
        blockers.size shouldBe 55
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