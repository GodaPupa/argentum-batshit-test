package com.wingedsheep.engine.legalactions

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Real legal-menu consumers of finite resource proofs; no project allocation is initialized. */
class FixedSacrificePaymentTest : FunSpec({
    val altar = card("Joint Menu Creature Altar") {
        typeLine = "Artifact"
        activatedAbility {
            cost = Costs.Sacrifice(GameObjectFilter.Creature)
            effect = Effects.AddColorlessMana(2)
            manaAbility = true
        }
    }
    val material = card("Joint Menu Artifact Creature") {
        typeLine = "Artifact Creature — Construct"
        power = 1
        toughness = 1
    }
    val payload = card("Joint Menu Generic Payload") {
        typeLine = "Artifact"
        manaCost = "{3}"
    }
    val star = card("Joint Menu Sacrifice Filter") {
        typeLine = "Artifact"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap, Costs.SacrificeSelf)
            effect = Effects.AddAnyColorMana(1)
            manaAbility = true
        }
    }
    val sacrifice = card("Joint Menu Sacrifice Payload") {
        typeLine = "Instant"
        manaCost = "{1}{B}"
        additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.CreatureOrArtifact))
        keywordAbility(KeywordAbility.Flashback(ManaCost.parse("{4}{B}")))
        spell { effect = Effects.GainLife(1) }
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(altar, material, payload, star, sacrifice))
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun offers(d: GameTestDriver, id: com.wingedsheep.sdk.model.EntityId) =
        LegalActionEnumerator.create(d.cardRegistry).enumerate(d.state, d.activePlayer!!)
            .any { it.affordable && (it.action as? CastSpell)?.cardId == id }

    test("two Altars sharing one creature cannot advertise a three-mana spell") {
        val d = fixture()
        repeat(2) { d.putPermanentOnBattlefield(d.activePlayer!!, altar.name) }
        d.putPermanentOnBattlefield(d.activePlayer!!, material.name)
        offers(d, d.putCardInHand(d.activePlayer!!, payload.name)) shouldBe false
    }
    test("one Altar with two distinct creatures advertises its repeated legal funding") {
        val d = fixture()
        d.putPermanentOnBattlefield(d.activePlayer!!, altar.name)
        repeat(2) { d.putPermanentOnBattlefield(d.activePlayer!!, material.name) }
        offers(d, d.putCardInHand(d.activePlayer!!, payload.name)) shouldBe true
    }
    test("a hand cast cannot consume its sole material both for black mana and its final sacrifice") {
        val d = fixture()
        repeat(2) { d.putLandOnBattlefield(d.activePlayer!!, "Forest") }
        d.putPermanentOnBattlefield(d.activePlayer!!, star.name)
        val spell = d.putCardInHand(d.activePlayer!!, sacrifice.name)
        offers(d, spell) shouldBe false
        d.putPermanentOnBattlefield(d.activePlayer!!, material.name)
        offers(d, spell) shouldBe true
    }
    test("flashback checks printed final sacrifice against the same black funding resource") {
        val d = fixture()
        repeat(5) { d.putLandOnBattlefield(d.activePlayer!!, "Forest") }
        d.putPermanentOnBattlefield(d.activePlayer!!, star.name)
        val spell = d.putCardInGraveyard(d.activePlayer!!, sacrifice.name)
        offers(d, spell) shouldBe false
        d.putPermanentOnBattlefield(d.activePlayer!!, material.name)
        offers(d, spell) shouldBe true
    }
})
