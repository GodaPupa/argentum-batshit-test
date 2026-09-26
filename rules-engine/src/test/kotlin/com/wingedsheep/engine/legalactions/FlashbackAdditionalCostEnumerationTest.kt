package com.wingedsheep.engine.legalactions

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain

/**
 * The flashback menu must carry mandatory printed costs as well as flashback's own non-mana
 * cost (CR 118.9d). Inline cards isolate enumeration from any receiving set's card definition.
 * Industrial's exact Eviscerator's Insight remains a separate receiving qualification.
 */
class FlashbackAdditionalCostEnumerationTest : FunSpec({
    fun study(
        printed: List<AdditionalCost> = emptyList(),
        keyword: AdditionalCost? = null,
        flashbackMana: String = "{0}",
    ) = card("Flashback Menu Study") {
        manaCost = "{1}{B}"
        typeLine = "Instant"
        printed.forEach { additionalCost(it) }
        spell { effect = Effects.GainLife(1) }
        keywordAbility(if (keyword == null) KeywordAbility.flashback(flashbackMana)
            else KeywordAbility.flashback(flashbackMana, keyword))
    }

    val artifact = card("Flashback Menu Relic") {
        manaCost = "{1}"
        typeLine = "Artifact"
    }
    val sacrifice = Costs.additional.SacrificePermanent(GameObjectFilter.CreatureOrArtifact)

    fun driver(spell: CardDefinition): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(spell, artifact))
        it.initMirrorMatch(Deck.of("Island" to 40), startingPlayer = 0, skipMulligans = true)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun offer(game: GameTestDriver, spellId: EntityId): LegalAction =
        LegalActionEnumerator.create(game.cardRegistry).enumerate(game.state, game.activePlayer!!)
            .single { (it.action as? CastSpell)?.cardId == spellId &&
                (it.action as CastSpell).alternativeCostType == AlternativeCostType.FLASHBACK }

    fun floatOn(game: GameTestDriver, id: EntityId, layer: Layer, modification: SerializableModification) {
        val effect = ActiveFloatingEffect(
            id = EntityId.generate(),
            effect = FloatingEffectData(layer = layer, modification = modification, affectedEntities = setOf(id)),
            duration = Duration.EndOfTurn,
            sourceId = null,
            controllerId = game.activePlayer!!,
            timestamp = game.state.timestamp,
        )
        game.replaceState(game.state.copy(floatingEffects = game.state.floatingEffects + effect))
    }

    test("mana-only flashback keeps its existing offer and no selection") {
        val game = driver(study(flashbackMana = "{4}{B}"))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        game.giveMana(me, Color.BLACK, 1)
        game.giveColorlessMana(me, 4)

        val action = offer(game, spell)
        action.affordable shouldBe true
        action.manaCostString shouldBe "{4}{B}"
        action.additionalCostInfo shouldBe null
        action.additionalLifeCost shouldBe 0
        action.sourceZone shouldBe "GRAVEYARD"
    }

    test("printed sacrifice is required even when all flashback mana is available") {
        val game = driver(study(printed = listOf(sacrifice), flashbackMana = "{4}{B}"))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        game.giveMana(me, Color.BLACK, 1)
        game.giveColorlessMana(me, 4)
        game.putLandOnBattlefield(me, "Forest")
        game.putPermanentOnBattlefield(game.getOpponent(me), artifact.name)

        val action = offer(game, spell)
        action.affordable shouldBe false
        val cost = action.additionalCostInfo.shouldNotBeNull()
        cost.costType shouldBe "SacrificePermanent"
        cost.sacrificeCount shouldBe 1
        cost.validSacrificeTargets shouldBe emptyList()
    }

    test("menu exposes both legal sacrifice types and submits only the actor's chosen payment") {
        val game = driver(study(printed = listOf(sacrifice), flashbackMana = "{4}{B}"))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        val relic = game.putPermanentOnBattlefield(me, artifact.name)
        val bear = game.putCreatureOnBattlefield(me, "Grizzly Bears")
        val forest = game.putLandOnBattlefield(me, "Forest")
        val enemy = game.putPermanentOnBattlefield(game.getOpponent(me), artifact.name)
        game.giveMana(me, Color.BLACK, 1)
        game.giveColorlessMana(me, 4)

        val action = offer(game, spell)
        action.affordable shouldBe true
        action.additionalCostInfo!!.validSacrificeTargets.toSet() shouldBe setOf(relic, bear)
        val cast = action.action as CastSpell
        cast.additionalCostPayment shouldBe null
        val before = game.state
        game.submit(cast.copy(paymentStrategy = PaymentStrategy.FromPool)).isSuccess shouldBe false
        game.state shouldBe before
        for (invalid in listOf(forest, enemy)) {
            game.submit(cast.copy(
                paymentStrategy = PaymentStrategy.FromPool,
                additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(invalid)),
            )).isSuccess shouldBe false
            game.state shouldBe before
        }
        game.submit(cast.copy(
            paymentStrategy = PaymentStrategy.FromPool,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(relic)),
        )).isSuccess shouldBe true
        game.state.getBattlefield() shouldNotContain relic
        game.state.getBattlefield() shouldContain bear
        game.getGraveyard(me) shouldContain relic
        game.bothPass()
        game.getExile(me) shouldContain spell
    }

    test("unaffordable mana retains the printed sacrifice picker") {
        val game = driver(study(printed = listOf(sacrifice), flashbackMana = "{4}{B}"))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        val relic = game.putPermanentOnBattlefield(me, artifact.name)
        val action = offer(game, spell)
        action.affordable shouldBe false
        action.manaCostString shouldBe "{4}{B}"
        action.additionalCostInfo!!.validSacrificeTargets shouldBe listOf(relic)
    }

    test("sacrifice candidates use projected type and control and refresh with state") {
        val game = driver(study(printed = listOf(sacrifice)))
        val me = game.activePlayer!!
        val enemy = game.getOpponent(me)
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        val animatedLand = game.putLandOnBattlefield(me, "Forest")
        val stolenRelic = game.putPermanentOnBattlefield(enemy, artifact.name)
        val lostRelic = game.putPermanentOnBattlefield(me, artifact.name)
        floatOn(game, animatedLand, Layer.TYPE, SerializableModification.AddType("ARTIFACT"))
        floatOn(game, stolenRelic, Layer.CONTROL, SerializableModification.ChangeController(me))
        floatOn(game, lostRelic, Layer.CONTROL, SerializableModification.ChangeController(enemy))
        offer(game, spell).additionalCostInfo!!.validSacrificeTargets.toSet() shouldBe
            setOf(animatedLand, stolenRelic)

        game.replaceState(game.state.copy(floatingEffects = emptyList()))
        offer(game, spell).additionalCostInfo!!.validSacrificeTargets shouldBe listOf(lostRelic)
    }

    test("keyword Mountain sacrifice remains required with zero flashback mana") {
        val mountainCost = Costs.additional.SacrificePermanent(GameObjectFilter.Any.withSubtype(Subtype.MOUNTAIN))
        val game = driver(study(keyword = mountainCost))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        game.putLandOnBattlefield(me, "Forest")
        offer(game, spell).affordable shouldBe false
        val mountain = game.putLandOnBattlefield(me, "Mountain")
        val action = offer(game, spell)
        action.affordable shouldBe true
        action.additionalCostInfo!!.validSacrificeTargets shouldBe listOf(mountain)
        (action.action as CastSpell).additionalCostPayment shouldBe null
    }

    test("keyword tap cost counts only controlled untapped creatures and allows summoning sickness") {
        val game = driver(study(keyword = Costs.additional.TapPermanents(2, GameObjectFilter.Creature)))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        val first = game.putCreatureOnBattlefield(me, "Grizzly Bears")
        val second = game.putCreatureOnBattlefield(me, "Grizzly Bears")
        val tapped = game.putCreatureOnBattlefield(me, "Grizzly Bears")
        game.replaceState(game.state.updateEntity(tapped) { it.with(TappedComponent) })
        game.putCreatureOnBattlefield(game.getOpponent(me), "Grizzly Bears")

        val action = offer(game, spell)
        action.affordable shouldBe true
        val cost = action.additionalCostInfo.shouldNotBeNull()
        cost.costType shouldBe "TapPermanents"
        cost.tapCount shouldBe 2
        cost.validTapTargets.toSet() shouldBe setOf(first, second)
        game.submit((action.action as CastSpell).copy(
            additionalCostPayment = AdditionalCostPayment(tappedPermanents = listOf(first, second)),
        )).isSuccess shouldBe true
        game.isTapped(first) shouldBe true
        game.isTapped(second) shouldBe true
    }

    test("keyword Behold retains counted battlefield and own-hand choices") {
        val game = driver(study(keyword = Costs.additional.Behold(GameObjectFilter.Creature, count = 2)))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        val onBoard = game.putCreatureOnBattlefield(me, "Grizzly Bears")
        game.putCreatureOnBattlefield(game.getOpponent(me), "Grizzly Bears")
        offer(game, spell).affordable shouldBe false
        val inHand = game.putCardInHand(me, "Grizzly Bears")
        val action = offer(game, spell)
        action.affordable shouldBe true
        val cost = action.additionalCostInfo.shouldNotBeNull()
        cost.costType shouldBe "Behold"
        cost.beholdCount shouldBe 2
        cost.validBeholdTargets.toSet() shouldBe setOf(onBoard, inHand)
    }

    test("fixed keyword life payment gates the offer and is auto-paid by the existing executor") {
        val game = driver(study(keyword = Costs.additional.PayLife(3)))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        game.setLifeTotal(me, 2)
        offer(game, spell).affordable shouldBe false
        game.setLifeTotal(me, 4)
        val action = offer(game, spell)
        action.affordable shouldBe true
        action.additionalLifeCost shouldBe 3
        action.additionalCostInfo shouldBe null
        action.description stringShouldContain "pay 3 life"
        game.submit(action.action).isSuccess shouldBe true
        game.getLifeTotal(me) shouldBe 1
    }

    test("nested printed costs and keyword life are all required alongside one picker") {
        val game = driver(study(
            printed = listOf(AdditionalCost.Composite(listOf(
                sacrifice, AdditionalCost.Composite(listOf(Costs.additional.PayLife(2))),
            ))),
            keyword = Costs.additional.PayLife(3),
        ))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        val relic = game.putPermanentOnBattlefield(me, artifact.name)
        game.setLifeTotal(me, 4)
        offer(game, spell).affordable shouldBe false
        game.setLifeTotal(me, 6)
        val action = offer(game, spell)
        action.additionalLifeCost shouldBe 5
        action.affordable shouldBe true
        action.additionalCostInfo!!.validSacrificeTargets shouldBe listOf(relic)
        game.submit((action.action as CastSpell).copy(
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(relic)),
        )).isSuccess shouldBe true
        game.getLifeTotal(me) shouldBe 1
        game.getGraveyard(me) shouldContain relic
    }

    test("two selection costs are an explicit capability failure rather than dropping either leg") {
        val game = driver(study(printed = listOf(sacrifice), keyword = sacrifice))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        game.putPermanentOnBattlefield(me, artifact.name)
        game.putCreatureOnBattlefield(me, "Grizzly Bears")
        val before = game.state
        shouldThrow<UnsupportedOperationException> { offer(game, spell) }
            .message.orEmpty() stringShouldContain "cannot represent all costs"
        game.state shouldBe before
    }

    test("an unsupported mandatory cost never falls through as a free flashback") {
        val game = driver(study(printed = listOf(Costs.additional.DiscardCards())))
        val me = game.activePlayer!!
        val spell = game.putCardInGraveyard(me, "Flashback Menu Study")
        shouldThrow<UnsupportedOperationException> { offer(game, spell) }
            .message.orEmpty() stringShouldContain "discard"
    }
})
