package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.core.NumberChosenResponse
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.MayCastWithoutPayingManaCost
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Isolated authoring-boundary probe, intentionally expecting full optional-cost semantics.
 * These fixtures are not canonical cards and do not admit Everflowing Chalice to the registry.
 * A green ordinary one-payment control cannot qualify multikicker: three payments must be
 * announced separately from X, reductions see the total, and free/alternative bases retain extras.
 */
class RepeatedOptionalCostTest : FunSpec({
    val repeated = card("Boundary Repeated Cost") {
        manaCost = "{0}"
        typeLine = "Artifact"
        keywordAbility(KeywordAbility.multikicker("{2}"))
        replacementEffect(com.wingedsheep.sdk.scripting.EntersWithDynamicCounters(
            count = com.wingedsheep.sdk.scripting.values.DynamicAmount.CastChoice(ChoiceSlot.KICKED)
        ))
    }
    val discounted = card("Boundary Discounted Optional Cost") {
        manaCost = "{0}"
        typeLine = "Artifact"
        keywordAbility(KeywordAbility.kicker("{2}"))
        staticAbility {
            ability = ModifySpellCost(
                target = SpellCostTarget.SelfCast,
                modification = CostModification.ReduceGeneric(1)
            )
        }
    }
    val alternative = card("Boundary Alternative Optional Cost") {
        manaCost = "{5}"
        typeLine = "Instant"
        keywordAbility(KeywordAbility.kicker("{2}"))
        keywordAbility(KeywordAbility.flashback("{1}"))
    }
    val freePermission = card("Boundary Free Permission") {
        manaCost = "{0}"
        typeLine = "Enchantment"
        staticAbility {
            ability = MayCastWithoutPayingManaCost(controllerOnly = true)
        }
    }

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        listOf(repeated, discounted, alternative, freePermission).forEach(::registerCard)
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun GameTestDriver.mana(player: EntityId): Int =
        state.getEntity(player)?.get<ManaPoolComponent>()?.total ?: 0

    test("control - an unkicked zero-cost permanent spends no additional mana") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 6)
        val card = game.putCardInHand(player, repeated.name)
        game.submit(CastSpell(player, card, paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        game.mana(player) shouldBe 6
        game.pendingDecision shouldBe null
    }

    test("control - ordinary kicker pays its optional mana cost once") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 7)
        val card = game.putCardInHand(player, alternative.name)
        game.submit(CastSpell(
            player, card, declaredCostSlot = ChoiceSlot.KICKED,
            paymentStrategy = PaymentStrategy.FromPool
        )).error shouldBe null
        game.mana(player) shouldBe 0
        game.pendingDecision shouldBe null
    }

    test("multikicker must announce three repetitions and spend six rather than two mana") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 6)
        val card = game.putCardInHand(player, repeated.name)
        game.submit(CastSpell(
            player, card, declaredCostSlot = ChoiceSlot.KICKED,
            paymentStrategy = PaymentStrategy.FromPool
        )).error shouldBe null
        val decision = game.pendingDecision.shouldBeInstanceOf<ChooseNumberDecision>()
        game.mana(player) shouldBe 6 // Announce first, pay only after the count is chosen.
        game.submitDecision(player, NumberChosenResponse(decision.id, 3)).error shouldBe null
        game.mana(player) shouldBe 0
        game.stackSize shouldBe 1
    }

    test("a reduction applies after optional mana is included in the total cost") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 1)
        val card = game.putCardInHand(player, discounted.name)
        game.submit(CastSpell(
            player, card, declaredCostSlot = ChoiceSlot.KICKED,
            paymentStrategy = PaymentStrategy.FromPool
        )).error shouldBe null
        game.mana(player) shouldBe 0
    }

    test("casting without paying the mana cost still pays optional additional mana") {
        val game = driver()
        val player = game.activePlayer!!
        game.putPermanentOnBattlefield(player, freePermission.name)
        game.giveColorlessMana(player, 2)
        val card = game.putCardInHand(player, alternative.name)
        game.submit(CastSpell(
            player, card, declaredCostSlot = ChoiceSlot.KICKED,
            useWithoutPayingManaCost = true, paymentStrategy = PaymentStrategy.FromPool
        )).error shouldBe null
        game.mana(player) shouldBe 0
    }

    test("flashback replaces the base but still pays optional additional mana") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 3)
        val card = game.putCardInGraveyard(player, alternative.name)
        game.submit(CastSpell(
            player, card, declaredCostSlot = ChoiceSlot.KICKED,
            useAlternativeCost = true, alternativeCostType = AlternativeCostType.FLASHBACK,
            paymentStrategy = PaymentStrategy.FromPool
        )).error shouldBe null
        game.mana(player) shouldBe 0
    }

    test("optional-cost enumeration preserves flashback permission on the cast action") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 3)
        val card = game.putCardInGraveyard(player, alternative.name)
        game.legalActions(player).any {
            val cast = it.action as? CastSpell
            cast?.cardId == card && cast.declaredCostSlot == ChoiceSlot.KICKED &&
                cast.useAlternativeCost && cast.alternativeCostType == AlternativeCostType.FLASHBACK &&
                it.affordable
        } shouldBe true
    }
    test("announced count survives stack and permanent entry as a numeric choice") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 6)
        val card = game.putCardInHand(player, repeated.name)
        game.submit(CastSpell(player, card, declaredCostSlot = ChoiceSlot.KICKED,
            optionalCostCounts = mapOf(ChoiceSlot.KICKED to 3),
            paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        game.state.getEntity(card)?.get<com.wingedsheep.engine.state.components.stack.SpellOnStackComponent>()
            ?.optionalCostCounts?.get(ChoiceSlot.KICKED) shouldBe 3
        game.bothPass()
        game.state.getEntity(card)?.get<com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent>()
            ?.chosen?.get(ChoiceSlot.KICKED) shouldBe
            com.wingedsheep.engine.state.components.battlefield.ChoiceValue.NumberChoice(3)
        game.state.getEntity(card)?.get<com.wingedsheep.engine.state.components.battlefield.CountersComponent>()
            ?.getCount(com.wingedsheep.sdk.core.CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
    }

    test("zero announcement declines the optional cost without spending mana") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 4)
        val card = game.putCardInHand(player, repeated.name)
        game.submit(CastSpell(player, card, declaredCostSlot = ChoiceSlot.KICKED,
            paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        val question = game.pendingDecision.shouldBeInstanceOf<ChooseNumberDecision>()
        game.submitDecision(player, NumberChosenResponse(question.id, 0)).error shouldBe null
        game.mana(player) shouldBe 4
        game.state.getEntity(card)?.get<com.wingedsheep.engine.state.components.stack.SpellOnStackComponent>()
            ?.declaredCostSlot shouldBe null
    }

    test("ordinary kicker rejects a repetition count greater than one") {
        val game = driver()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 20)
        val card = game.putCardInHand(player, alternative.name)
        (game.submit(CastSpell(player, card, declaredCostSlot = ChoiceSlot.KICKED,
            optionalCostCounts = mapOf(ChoiceSlot.KICKED to 2),
            paymentStrategy = PaymentStrategy.FromPool)).error != null) shouldBe true
        game.mana(player) shouldBe 20
    }

    test("negative and overflowing repetition counts fail before payment") {
        for (count in listOf(-1, Int.MAX_VALUE)) {
            val game = driver()
            val player = game.activePlayer!!
            game.giveColorlessMana(player, 6)
            val card = game.putCardInHand(player, repeated.name)
            (game.submit(CastSpell(player, card, declaredCostSlot = ChoiceSlot.KICKED,
                optionalCostCounts = mapOf(ChoiceSlot.KICKED to count),
                paymentStrategy = PaymentStrategy.FromPool)).error != null) shouldBe true
            game.mana(player) shouldBe 6
            game.stackSize shouldBe 0
        }
    }

})
