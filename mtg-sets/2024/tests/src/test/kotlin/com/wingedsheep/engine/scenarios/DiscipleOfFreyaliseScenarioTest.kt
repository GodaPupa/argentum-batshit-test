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
        d.bothPass().error shouldBe null
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
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = cast(d)
        val hand = d.getHandSize(me)
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, true).error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.SelectCardsDecision>()
        d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
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
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = cast(d)
        d.submitYesNo(me, true).error shouldBe null
        d.submitCardSelection(me, listOf(source)).isSuccess shouldBe false
        d.getPermanents(me).contains(source) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
    }

    test("DF04 selection rejects an opponent creature") {
        val d = driver()
        val me = d.activePlayer!!
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val opp = d.getOpponent(me)
        val own = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val foreign = d.putCreatureOnBattlefield(opp, "Disciple test fodder")
        cast(d)
        d.submitYesNo(me, true).error shouldBe null
        d.submitCardSelection(me, listOf(foreign)).isSuccess shouldBe false
        d.getPermanents(opp).contains(foreign) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.submitCardSelection(me, listOf(own)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
    }

    listOf(0, -2).forEach { power ->
        test("DF06 power $power sacrifices without drawing or losing life") {
            val d = driver(power)
            val me = d.activePlayer!!
            // Two eligible own creatures force the existing resolution-selection pause.
            val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
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
            d.submitYesNo(me, true).error shouldBe null
            d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
            d.getGraveyard(me).contains(fodder) shouldBe true
            d.getLifeTotal(me) shouldBe 20
            d.getHandSize(me) shouldBe hand
        }
    }

    test("DF08 source departure before trigger resolution preserves the payoff") {
        val d = driver()
        val me = d.activePlayer!!
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        val source = d.putCardInHand(me, "Disciple of Freyalise")
        d.castSpell(me, source).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        // Explicit fixture zone departure; trigger already on stack.
        d.moveToGraveyard(source)
        d.bothPass().error shouldBe null
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).error shouldBe null
        d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(source) shouldBe true
    }

    listOf("optional", "selection").forEach { pause ->
        test("DF11 full state serialization at $pause preserves five power payoff") {
            val d = driver()
            val me = d.activePlayer!!
            // Two eligible own creatures force the existing resolution-selection pause.
            val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
            val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
            cast(d)
            val hand = d.getHandSize(me)
            if (pause == "optional") roundtrip(d)
            d.submitYesNo(me, true).error shouldBe null
            if (pause == "selection") roundtrip(d)
            d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
            d.getLifeTotal(me) shouldBe 25
            d.getHandSize(me) shouldBe hand + 5
            d.pendingDecision shouldBe null
        }
    }

    test("DF14 Garden pays exactly three life to enter untapped") {
        val d = driver()
        val me = d.activePlayer!!
        val land = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, land, asBackFace = true)).error shouldBe null
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
        d.submit(PlayLand(me, land, asBackFace = true)).error shouldBe null
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
        d.submit(PlayLand(me, land, asBackFace = true)).error shouldBe null
        d.pendingDecision shouldBe null
        d.getLifeTotal(me) shouldBe 2
        d.isTapped(land) shouldBe true
        d.getLands(me).contains(land) shouldBe true
    }
    test("DF05 projected boost is captured before the sacrifice") {
        val d = driver(2)
        val me = d.activePlayer!!
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        d.giveMana(me, Color.GREEN, 1)
        val growth = d.putCardInHand(me, "Giant Growth")
        d.castSpell(me, growth, listOf(fodder)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        cast(d)
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).error shouldBe null
        d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
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
                d.submitYesNo(me, true).error shouldBe null
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
            d.submitYesNo(me, true).error shouldBe null
        if (d.pendingDecision is com.wingedsheep.engine.core.SelectCardsDecision)
            d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.pendingDecision shouldBe null
        d.getPermanents(me).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.getHandSize(me) shouldBe hand
    }

    test("DF12 resolution choice of hexproof fodder emits no targeting event") {
        val d = driver()
        val me = d.activePlayer!!
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
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
        d.submitYesNo(me, true).error shouldBe null
        d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
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
        d.submit(offered.single()).error shouldBe null
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
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
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
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
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
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val token = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        d.addComponent(token, TokenComponent)
        d.giveMana(me, Color.GREEN, 1)
        val growth = d.putCardInHand(me, "Giant Growth")
        d.castSpell(me, growth, listOf(token)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        cast(d)
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).error shouldBe null
        d.submitCardSelection(me, listOf(token)).isSuccess shouldBe true
        d.getPermanents(me).contains(token) shouldBe false
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.pendingDecision shouldBe null
    }

    test("DF10 successful sacrifice redirected to exile retains five power payoff") {
        val d = driver()
        val me = d.activePlayer!!
        // Two eligible own creatures force the existing resolution-selection pause.
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple test fodder")
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
        d.submitYesNo(me, true).error shouldBe null
        d.submitCardSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getPermanents(me).contains(fodder) shouldBe false
        d.getGraveyard(me).contains(fodder) shouldBe false
        d.getExile(me).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
    }

    test("DF16 stale serialized payment rejects yes and preserves decline") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        roundtrip(d)
        d.setLifeTotal(me, 2)
        val before = d.state
        val rejected = d.submitYesNo(me, true)
        (rejected.error != null) shouldBe true
        rejected.state shouldBe before
        rejected.events.size shouldBe 0
        d.state shouldBe before
        d.getLifeTotal(me) shouldBe 2
        d.submitYesNo(me, false).error shouldBe null
        d.getLifeTotal(me) shouldBe 2
        d.isTapped(id) shouldBe true
        d.getLands(me).count { it == id } shouldBe 1
        d.pendingDecision shouldBe null
    }

    test("DF16 accepting an offered three life choice at two life cannot buy untapped entry") {
        val d = driver()
        val me = d.activePlayer!!
        d.setLifeTotal(me, 2)
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
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
            (result.error == null) shouldBe !castOnly
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


    test("DF01 singleton automatic selection preserves five power payoff") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = cast(d)
        val hand = d.getHandSize(me)
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.pendingDecision shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(fodder) shouldBe true
        d.getPermanents(me).contains(source) shouldBe true
    }


    listOf(3, 4).forEach { life ->
        listOf(false, true).forEach { accept ->
            test("DF16 Garden threshold life=$life accept=$accept pays once or declines") {
                val d = driver()
                val me = d.activePlayer!!
                d.setLifeTotal(me, life)
                val id = d.putCardInHand(me, "Disciple of Freyalise")
                d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
                d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
                roundtrip(d)
                d.submitYesNo(me, accept).error shouldBe null
                d.getLifeTotal(me) shouldBe if (accept) life - 3 else life
                d.isTapped(id) shouldBe !accept
                d.getCardName(id) shouldBe "Garden of Freyalise"
                d.getLands(me).count { it == id } shouldBe 1
                d.pendingDecision shouldBe null
            }
        }
    }

    test("DF16 malformed land payment response preserves the pending choice") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        val question = d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        val before = d.state
        val invalid = d.submitDecision(me,
            com.wingedsheep.engine.core.TargetsResponse(question.id, emptyMap()))
        (invalid.error != null) shouldBe true
        invalid.events.size shouldBe 0
        d.state shouldBe before
        d.submitYesNo(me, false).error shouldBe null
        d.getLifeTotal(me) shouldBe 20
        d.isTapped(id) shouldBe true
        d.pendingDecision shouldBe null
    }

    test("DF16 replayed land payment response cannot charge life twice") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        val question = d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        val answer = com.wingedsheep.engine.core.YesNoResponse(question.id, true)
        d.submitDecision(me, answer).error shouldBe null
        d.getLifeTotal(me) shouldBe 17
        val before = d.state
        val replay = d.submitDecision(me, answer)
        (replay.error != null) shouldBe true
        replay.events.size shouldBe 0
        d.state shouldBe before
        d.getLifeTotal(me) shouldBe 17
        d.isTapped(id) shouldBe false
        d.getLands(me).count { it == id } shouldBe 1
    }

    listOf(false, true).forEach { castOnly ->
        test("DF19 graveyard permission castOnly=$castOnly agrees with direct Garden action") {
            val d = driver()
            val me = d.activePlayer!!
            val id = d.putCardInGraveyard(me, "Disciple of Freyalise")
            d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
                id = EntityId.generate(), cardIds = setOf(id), controllerId = me,
                nonLandOnly = castOnly, timestamp = d.state.timestamp
            )))
            val offered = d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
                .count { it.cardId == id && it.asBackFace }
            offered shouldBe if (castOnly) 0 else 1
            val before = d.state
            val action = d.submit(PlayLand(me, id, asBackFace = true))
            (action.error == null) shouldBe !castOnly
            if (castOnly) {
                d.state shouldBe before
                d.getGraveyard(me).contains(id) shouldBe true
            } else {
                d.submitYesNo(me, false).error shouldBe null
                d.getLifeTotal(me) shouldBe 20
                d.isTapped(id) shouldBe true
                d.getLands(me).count { it == id } shouldBe 1
                d.getGraveyard(me).contains(id) shouldBe false
            }
        }
    }

    test("DF19 permission forced tapped Garden does not charge life") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInExile(me, "Disciple of Freyalise")
        d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
            id = EntityId.generate(), cardIds = setOf(id), controllerId = me,
            landEntersTapped = true, timestamp = d.state.timestamp
        )))
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == id && it.asBackFace } shouldBe 1
        val castsBefore = d.events.filterIsInstance<SpellCastEvent>().size
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        d.pendingDecision shouldBe null
        d.getLifeTotal(me) shouldBe 20
        d.isTapped(id) shouldBe true
        d.getLands(me).count { it == id } shouldBe 1
        d.events.filterIsInstance<SpellCastEvent>().size shouldBe castsBefore
    }

    test("DF19 another player's permission does not authorize Garden") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInExile(me, "Disciple of Freyalise")
        d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
            id = EntityId.generate(), cardIds = setOf(id), controllerId = d.getOpponent(me),
            timestamp = d.state.timestamp
        )))
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == id && it.asBackFace } shouldBe 0
        val before = d.state
        val invalid = d.submit(PlayLand(me, id, asBackFace = true))
        (invalid.error != null) shouldBe true
        invalid.events.size shouldBe 0
        d.state shouldBe before
        d.getExile(me).contains(id) shouldBe true
    }

    test("DF19 permitted Garden consumes the ordinary land drop") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInExile(me, "Disciple of Freyalise")
        val second = d.putCardInHand(me, "Disciple of Freyalise")
        d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
            id = EntityId.generate(), cardIds = setOf(id), controllerId = me,
            timestamp = d.state.timestamp
        )))
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        d.submitYesNo(me, false).error shouldBe null
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == second } shouldBe 0
        val before = d.state
        val invalid = d.submit(PlayLand(me, second, asBackFace = true))
        (invalid.error != null) shouldBe true
        invalid.events.size shouldBe 0
        d.state shouldBe before
        d.getLands(me).count { it == id } shouldBe 1
    }


    test("DF19 expired permission stays revoked at the next own main phase") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInExile(me, "Disciple of Freyalise")
        val permission = MayPlayPermission(id = EntityId.generate(), cardIds = setOf(id),
            controllerId = me, expiresAfterTurn = d.state.turnNumber, timestamp = d.state.timestamp)
        d.replaceState(d.state.addMayPlayPermission(permission))
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == id && it.asBackFace } shouldBe 1
        for (player in listOf(me, d.getOpponent(me))) {
            d.getHand(player).toList().forEach { d.moveToGraveyard(it) }
        }
        repeat(2) {
            d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
            d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        d.activePlayer shouldBe me
        d.state.mayPlayPermissions.any { it.id == permission.id } shouldBe false
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == id } shouldBe 0
        val before = d.state
        val rejected = d.submit(PlayLand(me, id, asBackFace = true))
        (rejected.error != null) shouldBe true
        rejected.events.size shouldBe 0
        d.state shouldBe before
    }

    test("DF19 per-card permission does not admit a command-zone Garden") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInCommandZone(me, "Disciple of Freyalise")
        d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
            id = EntityId.generate(), cardIds = setOf(id), controllerId = me, timestamp = d.state.timestamp)))
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == id } shouldBe 0
        val before = d.state
        val rejected = d.submit(PlayLand(me, id, asBackFace = true))
        (rejected.error != null) shouldBe true
        rejected.events.size shouldBe 0
        d.state shouldBe before
    }

    listOf("nonmain", "nonactive", "stack").forEach { restriction ->
        test("DF19 permitted Garden respects timing restriction=$restriction") {
            val d = driver()
            val me = d.activePlayer!!
            val id = d.putCardInExile(me, "Disciple of Freyalise")
            d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
                id = EntityId.generate(), cardIds = setOf(id), controllerId = me,
                permanent = true, asThoughFlash = true, timestamp = d.state.timestamp)))
            when (restriction) {
                "nonmain" -> d.passPriorityUntil(Step.BEGIN_COMBAT)
                "nonactive" -> {
                    d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
                    d.passPriorityUntil(Step.PRECOMBAT_MAIN)
                    d.activePlayer shouldBe d.getOpponent(me)
                }
                "stack" -> {
                    val front = d.putCardInHand(me, "Disciple of Freyalise")
                    d.giveMana(me, Color.GREEN, 3)
                    d.giveColorlessMana(me, 3)
                    d.castSpell(me, front).isSuccess shouldBe true
                    d.state.stack.size shouldBe 1
                }
            }
            d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
                .count { it.cardId == id } shouldBe 0
            val before = d.state
            val rejected = d.submit(PlayLand(me, id, asBackFace = true))
            (rejected.error != null) shouldBe true
            rejected.events.size shouldBe 0
            d.state shouldBe before
        }
    }

    test("DF19 land prohibition defeats a per-card play permission") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple land prohibition fixture") {
            typeLine = "Enchantment"
            staticAbility { ability = com.wingedsheep.sdk.scripting.PlayersCantPlayLands() }
        })
        d.putPermanentOnBattlefield(me, "Disciple land prohibition fixture")
        val id = d.putCardInExile(me, "Disciple of Freyalise")
        d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
            id = EntityId.generate(), cardIds = setOf(id), controllerId = me, timestamp = d.state.timestamp)))
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == id } shouldBe 0
        val before = d.state
        val rejected = d.submit(PlayLand(me, id, asBackFace = true))
        (rejected.error != null) shouldBe true
        rejected.events.size shouldBe 0
        d.state shouldBe before
    }

    listOf(false, true).forEach { shallow ->
        test("DF16 Garden payment uses existing library replacement shallow=$shallow") {
            val d = driver()
            val me = d.activePlayer!!
            d.registerCard(card("Disciple payment replacement fixture") {
                typeLine = "Enchantment"
                replacementEffect(com.wingedsheep.sdk.scripting.ReplaceLifePaymentWithLibraryExile())
            })
            d.putPermanentOnBattlefield(me, "Disciple payment replacement fixture")
            if (shallow) {
                d.state.getLibrary(me).drop(2).forEach { d.moveToGraveyard(it) }
            }
            val library = d.state.getLibrary(me).toList()
            val exile = d.getExile(me).toSet()
            val id = d.putCardInHand(me, "Disciple of Freyalise")
            d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
            roundtrip(d)
            d.submitYesNo(me, true).error shouldBe null
            d.getLifeTotal(me) shouldBe if (shallow) 17 else 20
            d.state.getLibrary(me) shouldBe if (shallow) library else library.drop(3)
            (d.getExile(me).toSet() - exile) shouldBe if (shallow) emptySet() else library.take(3).toSet()
            d.isTapped(id) shouldBe false
            d.getLands(me).count { it == id } shouldBe 1
            d.pendingDecision shouldBe null
        }
    }

    listOf(false, true).forEach { accept ->
        test("DF16 serialized Garden entry emits one identity and one landfall accept=$accept") {
            val d = driver()
            val me = d.activePlayer!!
            d.registerCard(card("Disciple landfall observer fixture") {
                typeLine = "Enchantment"
                triggeredAbility {
                    trigger = com.wingedsheep.sdk.dsl.Triggers.LandYouControlEnters
                    effect = com.wingedsheep.sdk.dsl.Effects.GainLife(1)
                }
            })
            val observer = d.putPermanentOnBattlefield(me, "Disciple landfall observer fixture")
            val id = d.putCardInHand(me, "Disciple of Freyalise")
            val oldObject = d.state.objectRef(id)
            val start = d.events.size
            d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
            val enteredObject = d.state.objectRef(id)
            roundtrip(d)
            d.submitYesNo(me, accept).error shouldBe null
            val events = d.events.drop(start)
            val entries = events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
                .filter { it.entityId == id && it.toZone == Zone.BATTLEFIELD }
            entries.size shouldBe 1
            entries.single().oldObject shouldBe oldObject
            entries.single().newObject shouldBe enteredObject
            (oldObject != enteredObject) shouldBe true
            events.filterIsInstance<com.wingedsheep.engine.core.LandPlayedEvent>()
                .count { it.cardId == id } shouldBe 1
            events.filterIsInstance<com.wingedsheep.engine.core.AbilityTriggeredEvent>()
                .count { it.sourceId == observer } shouldBe 1
            events.filterIsInstance<SpellCastEvent>().size shouldBe 0
            d.state.stack.size shouldBe 1
            d.bothPass().error shouldBe null
            d.getLifeTotal(me) shouldBe if (accept) 18 else 21
            d.isTapped(id) shouldBe !accept
            d.getLands(me).count { it == id } shouldBe 1
            d.state.stack.size shouldBe 0
            d.pendingDecision shouldBe null
        }
    }


    test("DF02 declining a singleton optional sacrifice preserves its actual creature") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        cast(d)
        if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, false).error shouldBe null
        d.getPermanents(me).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.pendingDecision shouldBe null
    }

    test("DF03 accepting with only Disciple preserves the source creature") {
        val d = driver()
        val me = d.activePlayer!!
        val source = cast(d)
        if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, true).error shouldBe null
        d.getPermanents(me).contains(source) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.pendingDecision shouldBe null
    }

    test("DF04 accepting with only opponent fodder preserves that creature") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        val fodder = d.putCreatureOnBattlefield(opp, "Disciple test fodder")
        cast(d)
        if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, true).error shouldBe null
        d.getPermanents(opp).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.pendingDecision shouldBe null
    }

    test("DF08 departed source singleton trigger still pays five life") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = d.putCardInHand(me, "Disciple of Freyalise")
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        d.castSpell(me, source).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        d.moveToGraveyard(source)
        d.bothPass().error shouldBe null
        if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, true).error shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getGraveyard(me).contains(fodder) shouldBe true
        d.getGraveyard(me).contains(source) shouldBe true
        d.pendingDecision shouldBe null
    }

    test("DF15 Garden declines any offered payment and ends tapped") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, false).error shouldBe null
        d.isTapped(id) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.getLands(me).contains(id) shouldBe true
    }

    test("DF16 Garden at two life cannot buy an untapped final state") {
        val d = driver()
        val me = d.activePlayer!!
        d.setLifeTotal(me, 2)
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        if (d.pendingDecision is YesNoDecision) {
            val answer = d.submitYesNo(me, true)
            if (answer.error != null) d.submitYesNo(me, false).error shouldBe null
        }
        d.isTapped(id) shouldBe true
        d.getLifeTotal(me) shouldBe 2
        d.getLands(me).contains(id) shouldBe true
    }

    test("DF12 selecting own fodder emits no triggered-target event before payoff") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        val start = d.events.size
        d.castSpell(me, id).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        if (d.pendingDecision is ChooseTargetsDecision) {
            d.submitTargetSelection(me, listOf(fodder)).error shouldBe null
        }
        d.events.drop(start).filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        d.bothPass().error shouldBe null
        val hand = d.getHandSize(me)
        if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, true).error shouldBe null
        if (d.pendingDecision is com.wingedsheep.engine.core.SelectCardsDecision) d.submitCardSelection(me, listOf(fodder)).error shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF20 noncast entry uses the front creature and creates exactly one trigger") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInGraveyard(me, "Disciple of Freyalise")
        d.registerCard(card("Disciple noncast entry fixture") {
            manaCost = "{G}"
            typeLine = "Sorcery"
            spell { effect = com.wingedsheep.sdk.dsl.Effects.PutOntoBattlefieldFromGraveyard(
                com.wingedsheep.sdk.scripting.targets.EffectTarget.SpecificEntity(id)) }
        })
        val spell = d.putCardInHand(me, "Disciple noncast entry fixture")
        d.giveMana(me, Color.GREEN, 1)
        val start = d.events.size
        d.castSpell(me, spell).error shouldBe null
        d.bothPass().error shouldBe null
        d.getCreatures(me).contains(id) shouldBe true
        d.getLands(me).contains(id) shouldBe false
        d.getCardName(id) shouldBe "Disciple of Freyalise"
        d.events.drop(start).filterIsInstance<com.wingedsheep.engine.core.AbilityTriggeredEvent>()
            .count { it.sourceId == id } shouldBe 1
        d.events.drop(start).filterIsInstance<SpellCastEvent>().count { it.spellEntityId == id } shouldBe 0
        d.bothPass().error shouldBe null
        if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, false).error shouldBe null
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF20 bounced Garden returns to front characteristics and can replay its land face") {
        val d = driver()
        val me = d.activePlayer!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        d.submitYesNo(me, false).error shouldBe null
        d.registerCard(card("Disciple bounce fixture") {
            manaCost = "{G}"
            typeLine = "Instant"
            spell { effect = com.wingedsheep.sdk.dsl.Effects.ReturnToHand(
                com.wingedsheep.sdk.scripting.targets.EffectTarget.SpecificEntity(id)) }
        })
        val bounce = d.putCardInHand(me, "Disciple bounce fixture")
        d.giveMana(me, Color.GREEN, 1)
        d.castSpell(me, bounce).error shouldBe null
        d.bothPass().error shouldBe null
        d.getHand(me).contains(id) shouldBe true
        val front = d.state.getEntity(id)!!.get<CardComponent>()!!
        front.name shouldBe "Disciple of Freyalise"
        front.typeLine.isCreature shouldBe true
        front.typeLine.isLand shouldBe false
        front.manaCost.cmc shouldBe 6
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>().count { it.cardId == id } shouldBe 0
        d.addComponent(me, com.wingedsheep.engine.state.components.player.LandDropsComponent())
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        d.submitYesNo(me, false).error shouldBe null
        d.getCardName(id) shouldBe "Garden of Freyalise"
        d.getLands(me).count { it == id } shouldBe 1
        d.getCreatures(me).contains(id) shouldBe false
    }


    test("DF12 shroud hexproof ward fodder sacrifices without tax or intermediate priority") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple shroud ward fixture") {
            typeLine = "Creature — Bear"
            power = 5
            toughness = 5
            keywords(Keyword.SHROUD, Keyword.HEXPROOF)
            keywordAbility(com.wingedsheep.sdk.scripting.KeywordAbility.Ward(
                com.wingedsheep.sdk.scripting.effects.WardCost.Mana("{2}")))
        })
        val fodder = d.putCreatureOnBattlefield(me, "Disciple shroud ward fixture")
        cast(d)
        val hand = d.getHandSize(me)
        val start = d.events.size
        d.submitYesNo(me, true).error shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(fodder) shouldBe true
        d.events.drop(start).filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        d.events.drop(start).filterIsInstance<com.wingedsheep.engine.core.AbilityTriggeredEvent>().size shouldBe 0
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF19 creature cast prohibition leaves permitted Garden playable") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple creature cast lock fixture") {
            typeLine = "Enchantment"
            staticAbility { ability = com.wingedsheep.sdk.scripting.PlayersCantCastSpells(
                affected = com.wingedsheep.sdk.scripting.references.Player.Each,
                spellFilter = GameObjectFilter.Creature) }
        })
        d.putPermanentOnBattlefield(me, "Disciple creature cast lock fixture")
        val id = d.putCardInExile(me, "Disciple of Freyalise")
        d.replaceState(d.state.addMayPlayPermission(MayPlayPermission(
            id = EntityId.generate(), cardIds = setOf(id), controllerId = me, timestamp = d.state.timestamp)))
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        (d.castSpell(me, id).error != null) shouldBe true
        d.legalActions(me).map { it.action }.filterIsInstance<PlayLand>()
            .count { it.cardId == id && it.asBackFace } shouldBe 1
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        d.submitYesNo(me, false).error shouldBe null
        d.getCardName(id) shouldBe "Garden of Freyalise"
        d.getLands(me).count { it == id } shouldBe 1
    }

    listOf(false, true).forEach { landQualified ->
        test("DF20 noncast graveyard selection uses front type landQualified=$landQualified") {
            val d = driver()
            val me = d.activePlayer!!
            val id = d.putCardInGraveyard(me, "Disciple of Freyalise")
            val front = d.state.getEntity(id)!!.get<CardComponent>()!!
            front.typeLine.isCreature shouldBe true
            front.typeLine.isLand shouldBe false
            front.manaCost.cmc shouldBe 6
            d.registerCard(card("Disciple qualified reanimation fixture") {
                manaCost = "{G}"
                typeLine = "Sorcery"
                spell {
                    effect = com.wingedsheep.sdk.dsl.Effects.Composite(listOf(
                        com.wingedsheep.sdk.dsl.Effects.SelectTarget(
                            com.wingedsheep.sdk.scripting.targets.TargetObject(filter =
                                if (landQualified) com.wingedsheep.sdk.scripting.filters.unified.TargetFilter.Land
                                    .inZone(Zone.GRAVEYARD).ownedByYou()
                                else com.wingedsheep.sdk.scripting.filters.unified.TargetFilter.CreatureInYourGraveyard),
                            storeAs = "revive"),
                        com.wingedsheep.sdk.dsl.Effects.PutOntoBattlefieldFromGraveyard(
                            com.wingedsheep.sdk.scripting.targets.EffectTarget.PipelineTarget("revive"))
                    ))
                }
            })
            val spell = d.putCardInHand(me, "Disciple qualified reanimation fixture")
            d.giveMana(me, Color.GREEN, 1)
            d.castSpell(me, spell).error shouldBe null
            d.bothPass().error shouldBe null
            d.getCreatures(me).contains(id) shouldBe !landQualified
            d.getGraveyard(me).contains(id) shouldBe landQualified
            d.getLands(me).contains(id) shouldBe false
            if (!landQualified) {
                d.bothPass().error shouldBe null
                if (d.pendingDecision is YesNoDecision) d.submitYesNo(me, false).error shouldBe null
            }
            d.pendingDecision shouldBe null
        }
    }

    test("DF21 Garden play neither spends Palace rider mana nor inherits its counters") {
        val d = driver()
        val me = d.activePlayer!!
        val rider = com.wingedsheep.engine.state.components.player.RestrictedManaEntry(
            color = Color.GREEN,
            restriction = com.wingedsheep.sdk.scripting.effects.ManaRestriction.AnySpend,
            riders = setOf(com.wingedsheep.sdk.scripting.effects.ManaSpellRider.CommanderCastEntryCounters))
        d.replaceState(d.state.updateEntity(me) { player ->
            val pool = player.get<ManaPoolComponent>() ?: ManaPoolComponent()
            player.with(pool.copy(restrictedMana = pool.restrictedMana + rider))
        })
        val beforePool = d.state.getEntity(me)!!.get<ManaPoolComponent>()!!
        val id = d.putCardInHand(me, "Disciple of Freyalise")
        val start = d.events.size
        d.submit(PlayLand(me, id, asBackFace = true)).error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        roundtrip(d)
        d.submitYesNo(me, true).error shouldBe null
        d.getCardName(id) shouldBe "Garden of Freyalise"
        d.getLifeTotal(me) shouldBe 17
        d.state.getEntity(me)!!.get<ManaPoolComponent>() shouldBe beforePool
        (d.state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.battlefield.CountersComponent>()
            ?.getCount(com.wingedsheep.sdk.core.CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
        d.state.getEntity(id)!!.has<com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent>() shouldBe false
        d.events.drop(start).filterIsInstance<SpellCastEvent>().size shouldBe 0
        d.events.drop(start).filterIsInstance<com.wingedsheep.engine.core.AbilityTriggeredEvent>().size shouldBe 0
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }


    test("DF12 non-targeted shroud selection survives serialization and preserves unchosen fodder") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple non-targeted shroud fixture") {
            typeLine = "Creature — Bear"; power = 5; toughness = 5
            keywords(Keyword.SHROUD, Keyword.HEXPROOF)
            keywordAbility(com.wingedsheep.sdk.scripting.KeywordAbility.Ward(com.wingedsheep.sdk.scripting.effects.WardCost.Mana("{2}")))
        })
        val chosen = d.putCreatureOnBattlefield(me, "Disciple non-targeted shroud fixture")
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple non-targeted shroud fixture")
        cast(d)
        val hand = d.getHandSize(me)
        val start = d.events.size
        d.submitYesNo(me, true).error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.SelectCardsDecision>()
        roundtrip(d)
        d.submitCardSelection(me, listOf(chosen)).error shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(chosen) shouldBe true
        d.getPermanents(me).contains(unchosen) shouldBe true
        d.events.drop(start).filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        d.events.drop(start).filterIsInstance<com.wingedsheep.engine.core.AbilityTriggeredEvent>().size shouldBe 0
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF12 non-targeted choice rejects source and opposing shroud while accepting own shroud") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        d.registerCard(card("Disciple non-targeted foreign fixture") {
            typeLine = "Creature — Bear"; power = 5; toughness = 5
            keywords(Keyword.SHROUD, Keyword.HEXPROOF)
        })
        val own = d.putCreatureOnBattlefield(me, "Disciple non-targeted foreign fixture")
        val other = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val foreign = d.putCreatureOnBattlefield(opp, "Disciple non-targeted foreign fixture")
        val source = cast(d)
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.SelectCardsDecision>()
        d.submitCardSelection(me, listOf(source)).isSuccess shouldBe false
        d.submitCardSelection(me, listOf(foreign)).isSuccess shouldBe false
        d.getLifeTotal(me) shouldBe 20
        d.submitCardSelection(me, listOf(own)).error shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(own) shouldBe true
        d.getPermanents(me).contains(source) shouldBe true
        d.getPermanents(me).contains(other) shouldBe true
        d.getPermanents(opp).contains(foreign) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }
})
