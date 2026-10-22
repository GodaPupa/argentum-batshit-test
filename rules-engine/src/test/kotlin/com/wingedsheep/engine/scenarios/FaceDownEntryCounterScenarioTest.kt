package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Casting face down keeps external entry replacements and hides printed self text. */
class FaceDownEntryCounterScenarioTest : FunSpec({
    val provider = card("Face Down Counter Provider") {
        manaCost = "{0}"; typeLine = "Creature — Elf"; power = 2; toughness = 3
        replacementEffect(EntersWithCounters(count = 2, otherOnly = true))
    }
    for (disguise in listOf(false, true)) {
        test("face-down spell receives external two counters and not printed five; disguise=$disguise") {
            val secret = card("Concealed Counter Text") {
                manaCost = "{6}"; typeLine = "Creature — Dragon"; power = 6; toughness = 7
                replacementEffect(EntersWithCounters(count = 5, selfOnly = true))
            }.copy(keywordAbilities = listOf(if (disguise) KeywordAbility.Disguise(ManaCost.parse("{1}"))
                else KeywordAbility.Morph(ManaCost.parse("{1}"))))
            val d = GameTestDriver().apply {
                registerCards(TestCards.all + listOf(provider, secret))
                initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
                passPriorityUntil(Step.PRECOMBAT_MAIN)
            }
            d.putCreatureOnBattlefield(d.player1, provider.name)
            val entrant = d.putCardInHand(d.player1, secret.name)
            d.giveColorlessMana(d.player1, 3)
            d.submit(CastSpell(d.player1, entrant, castFaceDown = true,
                paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
            d.bothPass().error shouldBe null
            val entity = d.state.getEntity(entrant).shouldNotBeNull()
            (entity.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 2
            entity.has<FaceDownComponent>() shouldBe true
            d.state.projectedState.getPower(entrant) shouldBe 4
            d.state.projectedState.getToughness(entrant) shouldBe 4
            d.pendingDecision shouldBe null
            d.stackSize shouldBe 0
        }
    }
})
