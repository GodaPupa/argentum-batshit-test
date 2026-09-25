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
import io.kotest.matchers.shouldNotBe

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

    test("Murmuring Mystic resolves as instant-sorcery cast token engine") {
        val card = registry.getCard("Murmuring Mystic") ?: error("Murmuring Mystic unresolved")
        card.oracleText shouldBe "Whenever you cast an instant or sorcery spell, create a 1/1 blue Bird Illusion creature token with flying."
        card.creatureStats?.basePower shouldBe 1
        card.creatureStats?.baseToughness shouldBe 5
        card.script.triggeredAbilities.size shouldBe 1
    }

    test("Goblin Electromancer resolves with generic instant-sorcery cost reduction") {
        val card = registry.getCard("Goblin Electromancer") ?: error("Goblin Electromancer unresolved")
        card.oracleText shouldBe "Instant and sorcery spells you cast cost {1} less to cast."
        card.creatureStats?.basePower shouldBe 2
        card.creatureStats?.baseToughness shouldBe 2
        card.script.staticAbilities.size shouldBe 1
    }

    test("Memory Lapse resolves through counter-to-library-top destination") {
        val card = registry.getCard("Memory Lapse") ?: error("Memory Lapse unresolved")
        card.oracleText shouldBe "Counter target spell. If that spell is countered this way, put it on top of its owner's library instead of into that player's graveyard."
        card.typeLine.toString() shouldBe "Instant"
        card.script.spellEffect shouldNotBe null
    }

    test("Izzet Guildmage resolves with two capped spell-copy abilities") {
        val card = registry.getCard("Izzet Guildmage") ?: error("Izzet Guildmage unresolved")
        card.oracleText shouldBe "{2}{U}: Copy target instant spell you control with mana value 2 or less. You may choose new targets for the copy.\n{2}{R}: Copy target sorcery spell you control with mana value 2 or less. You may choose new targets for the copy."
        card.creatureStats?.basePower shouldBe 2
        card.creatureStats?.baseToughness shouldBe 2
        card.script.activatedAbilities.size shouldBe 2
    }

    test("Echoing Truth resolves as same-name nonland-permanent bounce") {
        val card = registry.getCard("Echoing Truth") ?: error("Echoing Truth unresolved")
        card.oracleText shouldBe "Return target nonland permanent and all other permanents with the same name as that permanent to their owners' hands."
        card.typeLine.toString() shouldBe "Instant"
        card.script.spellEffect shouldNotBe null
    }

    test("Snap resolves as creature bounce with resolution-time land choice") {
        val card = registry.getCard("Snap") ?: error("Snap unresolved")
        card.oracleText shouldBe "Return target creature to its owner's hand. Untap up to two lands."
        card.typeLine.toString() shouldBe "Instant"
        card.script.spellEffect shouldNotBe null
    }

    test("Capsize resolves as permanent bounce with buyback") {
        val card = registry.getCard("Capsize") ?: error("Capsize unresolved")
        card.oracleText shouldBe "Buyback {3} (You may pay an additional {3} as you cast this spell. If you do, put this card into your hand as it resolves.)\nReturn target permanent to its owner's hand."
        card.typeLine.toString() shouldBe "Instant"
        card.script.spellEffect shouldNotBe null
        card.keywordAbilities.any {
            it is com.wingedsheep.sdk.scripting.KeywordAbility.OptionalAdditionalCost &&
                it.declaredSlot == com.wingedsheep.sdk.scripting.ChoiceSlot.BUYBACK
        } shouldBe true
    }

    test("Frantic Search resolves as draw-discard with resolution-time land choice") {
        val card = registry.getCard("Frantic Search") ?: error("Frantic Search unresolved")
        card.oracleText shouldBe "Draw two cards, then discard two cards. Untap up to three lands."
        card.typeLine.toString() shouldBe "Instant"
        card.script.spellEffect shouldNotBe null
    }

    test("frozen combined Veteran identities resolve their exact printed faces") {
        registry.getCard("Guardian Naga // Banishing Coils")?.name shouldBe "Guardian Naga"
        registry.getCard("Ulvenwald Captive // Ulvenwald Abomination")?.name shouldBe "Ulvenwald Captive"
        registry.getCard("Guardian Naga // Wrong Face") shouldBe null
        registry.getCard("Ulvenwald Captive // Wrong Face") shouldBe null
    }

    test("freeze exact unresolved engine coverage count and emit identities") {
        val unresolved = IzzetScienceVeteranBeastriderEngineReadiness.unresolvedCardIdentities(registry)
        println("V09_REAL_ENGINE_UNRESOLVED_COUNT=" + unresolved.size)
        unresolved.forEach { println("V09_REAL_ENGINE_UNRESOLVED=" + it) }
        unresolved.size shouldBe 11
    }

    test("full execution remains fail closed behind unresolved cards and PDH semantics") {
        val blockers = IzzetScienceVeteranBeastriderEngineReadiness.executionBlockers(registry)
        blockers.size shouldBe 16
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
