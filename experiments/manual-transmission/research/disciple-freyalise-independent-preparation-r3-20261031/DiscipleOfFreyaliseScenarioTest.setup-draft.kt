package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.TransformedEvent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.RedirectZoneChange

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.BecomesTargetEvent
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.DiscipleOfFreyalise
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.serialization.CardExporter
import com.wingedsheep.sdk.serialization.CardLoader

// Uncompiled research draft. Requirements not represented below remain pending in DRAFT.json.
// Every behavior is exercised through the real ActionProcessor-backed GameTestDriver.
class DiscipleOfFreyaliseScenarioTest : FunSpec({
    fun driver(powerValue: Int = 5): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + DiscipleOfFreyalise)
        d.registerCard(card("Disciple test fodder") {
            manaCost = "{1}{G}"
            typeLine = "Creature — Bear"
            power = maxOf(0, powerValue)
            toughness = 4
        })
        d.initMirrorMatch(Deck.of("Plains" to 40), startingLife = 20)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun cast(d: GameTestDriver): EntityId {
        val me = d.activePlayer!!
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        val source = d.putCardInHand(me, "Disciple of Freyalise")
        d.castSpell(me, source).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        return source
    }

    fun roundtrip(d: GameTestDriver) {
        val before = d.state
        d.replaceState(SerializationTestSupport.roundTrip(before))
        d.state shouldBe before
    }

    test("DF01 sacrifice gives exactly five life and five cards") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = cast(d)
        val hand = d.getHandSize(me)
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(fodder) shouldBe true
        d.getPermanents(me).contains(source) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF02 decline preserves the creature life and hand") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        cast(d)
        val hand = d.getHandSize(me)
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, false).isSuccess shouldBe true
        d.getPermanents(me).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.getHandSize(me) shouldBe hand
        d.pendingDecision shouldBe null
    }

    test("DF03 selection rejects the source and still accepts another own creature") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = cast(d)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(source)).isSuccess shouldBe false
        d.getPermanents(me).contains(source) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
    }

    test("DF04 selection rejects an opponent creature") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        val own = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val foreign = d.putCreatureOnBattlefield(opp, "Disciple test fodder")
        cast(d)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(foreign)).isSuccess shouldBe false
        d.getPermanents(opp).contains(foreign) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.submitTargetSelection(me, listOf(own)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
    }

    listOf(0, -2).forEach { power ->
        test("DF06 power $power sacrifices without drawing or losing life") {
            val d = driver(power)
            val me = d.activePlayer!!
            val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
            if (power < 0) {
                d.registerCard(CardDefinition.instant(
                    name = "Disciple negative power setup",
                    manaCost = com.wingedsheep.sdk.core.ManaCost.parse("{G}"),
                    oracleText = "Target creature gets -2/-0 until end of turn.",
                    script = com.wingedsheep.sdk.model.CardScript.spell(
                        effect = com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect(
                            power, 0,
                            com.wingedsheep.sdk.scripting.targets.EffectTarget.BoundVariable("target"),
                            com.wingedsheep.sdk.scripting.Duration.EndOfTurn
                        ),
                        com.wingedsheep.sdk.scripting.targets.TargetCreature(id = "target")
                    )
                ))
                d.giveMana(me, Color.GREEN, 1)
                val modifier = d.putCardInHand(me, "Disciple negative power setup")
                d.castSpell(me, modifier, listOf(fodder)).isSuccess shouldBe true
                d.bothPass().isSuccess shouldBe true
            }
            cast(d)
            val hand = d.getHandSize(me)
            d.submitYesNo(me, true).isSuccess shouldBe true
            d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
            d.getGraveyard(me).contains(fodder) shouldBe true
            d.getLifeTotal(me) shouldBe 20
            d.getHandSize(me) shouldBe hand
        }
    }

    test("DF08 source departure before trigger resolution preserves the payoff") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        val source = d.putCardInHand(me, "Disciple of Freyalise")
        d.castSpell(me, source).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        // Explicit fixture zone departure; trigger already on stack.
        d.moveToGraveyard(source)
        d.bothPass().isSuccess shouldBe true
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(source) shouldBe true
    }

    listOf("optional", "selection").forEach { pause ->
        test("DF11 full state serialization at $pause preserves five power payoff") {
            val d = driver()
            val me = d.activePlayer!!
            val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
            cast(d)
            val hand = d.getHandSize(me)
            if (pause == "optional") roundtrip(d)
            d.submitYesNo(me, true).isSuccess shouldBe true
            if (pause == "selection") roundtrip(d)
            d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
            d.getLifeTotal(me) shouldBe 25
            d.getHandSize(me) shouldBe hand + 5
            d.pendingDecision shouldBe null
        }
    }

    test("DF14 Garden pays exactly three life to enter untapped") {
        val d = driver()
        val me = d.activePlayer!!
        val land = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, land, asBackFace = true)).isSuccess shouldBe true
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 17
        d.isTapped(land) shouldBe false
        d.getLands(me).contains(land) shouldBe true
        d.getCreatures(me).contains(land) shouldBe false
        d.state.stack.size shouldBe 0
        val second = d.putCardInHand(me, "Plains")
        d.playLand(me, second).isSuccess shouldBe false
    }

    test("DF15 declined Garden payment keeps life and enters tapped") {
        val d = driver()
        val me = d.activePlayer!!
        val land = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, land, asBackFace = true)).isSuccess shouldBe true
        d.submitYesNo(me, false).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.isTapped(land) shouldBe true
        d.getLands(me).contains(land) shouldBe true
        d.state.stack.size shouldBe 0
    }

    test("DF16 two life cannot pay Garden entry cost") {
        val d = driver()
        val me = d.activePlayer!!
        d.setLifeTotal(me, 2)
        val land = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, land, asBackFace = true)).isSuccess shouldBe true
        d.pendingDecision shouldBe null
        d.getLifeTotal(me) shouldBe 2
        d.isTapped(land) shouldBe true
        d.getLands(me).contains(land) shouldBe true
    }
    test("DF05 projected boost is captured before the sacrifice") {
        val d = driver(2)
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        d.giveMana(me, Color.GREEN, 1)
        val growth = d.putCardInHand(me, "Giant Growth")
        d.castSpell(me, growth, listOf(fodder)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        cast(d)
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(fodder) shouldBe true
    }

    listOf(false, true).forEach { opponentOnly ->
        test("DF03 DF04 no other own creature gives no payoff opponentOnly=$opponentOnly") {
            val d = driver()
            val me = d.activePlayer!!
            val opp = d.getOpponent(me)
            val foreign = if (opponentOnly)
                d.putCreatureOnBattlefield(opp, "Disciple test fodder") else null
            val source = cast(d)
            val hand = d.getHandSize(me)
            // Both skipping an impossible optional action and accepting it to no-op
            // are inspected; neither may sacrifice self or an opponent's creature.
            if (d.pendingDecision is YesNoDecision)
                d.submitYesNo(me, true).isSuccess shouldBe true
            d.pendingDecision shouldBe null
            d.getLifeTotal(me) shouldBe 20
            d.getHandSize(me) shouldBe hand
            d.getPermanents(me).contains(source) shouldBe true
            if (foreign != null) d.getPermanents(opp).contains(foreign) shouldBe true
        }
    }

    test("DF07 an unsacrificable chosen creature gives no payoff") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple unsacrificable fodder") {
            manaCost = "{G}"
            typeLine = "Creature — Bear"
            power = 5
            toughness = 5
            flags(AbilityFlag.CANT_BE_SACRIFICED)
        })
        val fodder = d.putCreatureOnBattlefield(me, "Disciple unsacrificable fodder")
        cast(d)
        val hand = d.getHandSize(me)
        if (d.pendingDecision is YesNoDecision)
            d.submitYesNo(me, true).isSuccess shouldBe true
        if (d.pendingDecision is ChooseTargetsDecision)
            d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.pendingDecision shouldBe null
        d.getPermanents(me).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.getHandSize(me) shouldBe hand
    }

    test("DF12 resolution choice of hexproof fodder emits no targeting event") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple hexproof fodder") {
            manaCost = "{G}"
            typeLine = "Creature — Bear"
            power = 5
            toughness = 5
            keywords(Keyword.HEXPROOF)
        })
        val fodder = d.putCreatureOnBattlefield(me, "Disciple hexproof fodder")
        cast(d)
        val hand = d.getHandSize(me)
        val events = d.events.size
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.events.drop(events).filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF13 legal Garden land action is distinct from a creature cast") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        val offered = d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .filter { it.cardId == id }
        offered.size shouldBe 1
        offered.single().asBackFace shouldBe true
        val events = d.events.size
        d.submit(offered.single()).isSuccess shouldBe true
        d.submitYesNo(me, false).isSuccess shouldBe true
        d.events.drop(events).filterIsInstance<SpellCastEvent>().size shouldBe 0
        d.getCreatures(me).contains(id) shouldBe false
        d.getLands(me).contains(id) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF17 Garden activation produces exactly one green and no other mana") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).isSuccess shouldBe true
        d.submitYesNo(me, true).isSuccess shouldBe true
        val ability = DiscipleOfFreyalise.backFace!!.script.activatedAbilities.single().id
        d.submit(ActivateAbility(me, id, ability)).isSuccess shouldBe true
        val pool = d.state.getEntity(me)!!.get<ManaPoolComponent>()!!
        pool.green shouldBe 1
        pool.white shouldBe 0
        pool.blue shouldBe 0
        pool.black shouldBe 0
        pool.red shouldBe 0
        pool.colorless shouldBe 0
        d.isTapped(id) shouldBe true
        d.state.stack.size shouldBe 0
    }

    test("DF18 front cast keeps creature characteristics and mana value six") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        val result = d.castSpell(me, id)
        result.isSuccess shouldBe true
        result.events.filterIsInstance<SpellCastEvent>().single().manaValue shouldBe 6
        d.state.getEntity(id)!!.get<CardComponent>()!!.manaCost.cmc shouldBe 6
        d.bothPass().isSuccess shouldBe true
        val component = d.state.getEntity(id)!!.get<CardComponent>()!!
        component.name shouldBe "Disciple of Freyalise"
        component.baseStats!!.basePower shouldBe 3
        component.baseStats!!.baseToughness shouldBe 3
        d.getCreatures(me).contains(id) shouldBe true
        d.getLands(me).contains(id) shouldBe false
    }

    test("DF18 Garden battlefield uses land characteristics and mana value zero") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).isSuccess shouldBe true
        d.submitYesNo(me, false).isSuccess shouldBe true
        val component = d.state.getEntity(id)!!.get<CardComponent>()!!
        component.name shouldBe "Garden of Freyalise"
        component.manaCost.cmc shouldBe 0
        component.typeLine.isLand shouldBe true
        component.typeLine.isCreature shouldBe false
        component.colors shouldBe emptySet()
    }

    test("DF22 both card faces and metadata survive polymorphic JSON roundtrip") {
        val original = DiscipleOfFreyalise
        val decoded = CardLoader.fromJsonPreservingIds(CardExporter.exportToCompactJson(original))
        decoded shouldBe original
        decoded.layout shouldBe CardLayout.MODAL_DFC
        decoded.name shouldBe "Disciple of Freyalise"
        decoded.metadata.collectorNumber shouldBe "250"
        decoded.metadata.rulings.size shouldBe 7
        decoded.backFace!!.name shouldBe "Garden of Freyalise"
        decoded.backFace!!.metadata.collectorNumber shouldBe "250"
        decoded.backFace!!.metadata.rulings.size shouldBe 7
        (decoded.metadata.imageUri != decoded.backFace!!.metadata.imageUri) shouldBe true
    }

    test("DF09 token sacrifice retains projected five power after departure") {
        val d = driver(2)
        val me = d.activePlayer!!
        val token = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        d.addComponent(token, TokenComponent)
        d.giveMana(me, Color.GREEN, 1)
        val growth = d.putCardInHand(me, "Giant Growth")
        d.castSpell(me, growth, listOf(token)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        cast(d)
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(token)).isSuccess shouldBe true
        d.getPermanents(me).contains(token) shouldBe false
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.pendingDecision shouldBe null
    }

    test("DF10 successful sacrifice redirected to exile retains five power payoff") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple exile replacement") {
            typeLine = "Enchantment"
            replacementEffect(RedirectZoneChange(
                newDestination = Zone.EXILE,
                appliesTo = EventPattern.ZoneChangeEvent(
                    filter = GameObjectFilter.Creature,
                    from = Zone.BATTLEFIELD, to = Zone.GRAVEYARD
                )
            ))
        })
        d.putPermanentOnBattlefield(me, "Disciple exile replacement")
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        cast(d)
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getPermanents(me).contains(fodder) shouldBe false
        d.getGraveyard(me).contains(fodder) shouldBe false
        d.getExile(me).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
    }

    test("DF16 accepting an offered three life choice at two life cannot buy untapped entry") {
        val d = driver()
        val me = d.activePlayer!!
        d.setLifeTotal(me, 2)
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).isSuccess shouldBe true
        if (d.pendingDecision is YesNoDecision) {
            val answer = d.submitYesNo(me, true)
            if (!answer.isSuccess) d.submitYesNo(me, false).isSuccess shouldBe true
        }
        d.getLifeTotal(me) shouldBe 2
        d.isTapped(id) shouldBe true
        d.getLands(me).contains(id) shouldBe true
        d.pendingDecision shouldBe null
    }

    listOf(false, true).forEach { castOnly ->
        test("DF19 exile permission castOnly=$castOnly distinguishes Garden play") {
            val d = driver()
            val me = d.activePlayer!!
            val id = d.putCardInExile(me, "Disciple of Freyalise")
            d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
                id = EntityId.generate(), cardIds = setOf(id), controllerId = me,
                nonLandOnly = castOnly, timestamp = d.state.timestamp
            )))
            val offered = d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
                .any { it.cardId == id && it.asBackFace }
            offered shouldBe !castOnly
            val result = d.submit(PlayLand(me, id, asBackFace = true))
            result.isSuccess shouldBe !castOnly
            if (castOnly) {
                d.getExile(me).contains(id) shouldBe true
            } else {
                d.submitYesNo(me, false).isSuccess shouldBe true
                d.getLands(me).contains(id) shouldBe true
                d.getCreatures(me).contains(id) shouldBe false
            }
        }
    }

    test("DF21 external transform instruction leaves Disciple front face unchanged") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCreatureOnBattlefield(me, "Disciple of Freyalise")
        val spell = d.putCardInHand(me, "Transform Target Creature")
        d.giveMana(me, Color.BLUE, 1)
        d.giveColorlessMana(me, 1)
        d.castSpell(me, spell, listOf(id)).isSuccess shouldBe true
        val before = d.events.filterIsInstance<TransformedEvent>().size
        d.bothPass().isSuccess shouldBe true
        d.getCardName(id) shouldBe "Disciple of Freyalise"
        d.getCreatures(me).contains(id) shouldBe true
        d.getLands(me).contains(id) shouldBe false
        d.events.filterIsInstance<TransformedEvent>().size shouldBe before
    }

})
