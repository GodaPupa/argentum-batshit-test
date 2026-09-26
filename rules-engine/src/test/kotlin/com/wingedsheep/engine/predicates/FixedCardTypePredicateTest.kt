package com.wingedsheep.engine.predicates

import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.event.TriggerMatcher
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.CastSpellRecord
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.state.components.stack.projectedTypeLine
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FixedCardTypePredicateTest : FunSpec({
    val evaluator = PredicateEvaluator()
    for (actual in CardType.entries) {
        test("cast records distinguish fixed card type $actual from all other types") {
            val record = CastSpellRecord(TypeLine(cardTypes = setOf(actual)), 2, emptySet(), false)
            for (requested in CardType.entries) {
                evaluator.matchesFilter(record, GameObjectFilter.Any.withCardType(requested)) shouldBe (actual == requested)
            }
            evaluator.matchesFilter(record.copy(isFaceDown = true), GameObjectFilter.Any.withCardType(actual)) shouldBe false
        }
    }
    test("live matching uses projected added types rather than stale printed types") {
        val grant = card("Fixed Type Fixture") {
            manaCost = "{U}"
            typeLine = "Instant"
            spell {
                val permanent = target("permanent", Targets.Permanent)
                effect = Effects.AddCardType("Artifact", permanent)
            }
        }
        val d = GameTestDriver()
        d.registerCards(TestCards.all + grant)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val me = d.activePlayer!!
        val permanent = d.putCreatureOnBattlefield(me, "Centaur Courser")
        val context = PredicateContext(controllerId = me)
        val filter = GameObjectFilter.Any.withCardType(CardType.ARTIFACT)
        evaluator.matches(d.state, d.state.projectedState, permanent, filter, context) shouldBe false
        val spell = d.putCardInHand(me, grant.name)
        d.giveMana(me, Color.BLUE, 1)
        d.castSpell(me, spell, listOf(permanent)).isSuccess shouldBe true
        d.bothPass()
        d.state.getEntity(permanent)!!.get<CardComponent>()!!.typeLine.isArtifact shouldBe false
        evaluator.matches(d.state, d.state.projectedState, permanent, filter, context) shouldBe true
        evaluator.matches(d.state, d.state.projectedState, permanent,
            GameObjectFilter.Any.withCardType(CardType.CREATURE), context) shouldBe true

        val services = EngineServices(d.cardRegistry)
        val matcher = TriggerMatcher(services.predicateEvaluator, services.conditionEvaluator)
        val enter = ZoneChangeEvent(permanent, "Centaur Courser", null, Zone.BATTLEFIELD, me)
        matcher.matchesZoneChangeTrigger(EventPattern.ZoneChangeEvent(to = Zone.BATTLEFIELD, filter = filter),
            TriggerBinding.ANY, enter, permanent, me, d.state) shouldBe true
        val snapshot = EntitySnapshot.fromProjection(permanent, d.state)
            .copy(typeLine = projectedTypeLine(d.state, permanent))
        val departed = d.state.moveToZone(permanent, ZoneKey(me, Zone.BATTLEFIELD), ZoneKey(me, Zone.GRAVEYARD))
        val leave = ZoneChangeEvent(permanent, "Centaur Courser", Zone.BATTLEFIELD, Zone.GRAVEYARD, me,
            lastKnown = snapshot)
        matcher.matchesZoneChangeTrigger(EventPattern.ZoneChangeEvent(from = Zone.BATTLEFIELD,
            to = Zone.GRAVEYARD, filter = filter), TriggerBinding.ANY, leave, permanent, me, departed) shouldBe true
    }
    test("snapshot type matching preserves unknown rather than negating it into a match") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        val context = PredicateContext(controllerId = d.player1)
        val id = EntityId.generate()
        val filter = GameObjectFilter.Any.withCardType(CardType.BATTLE)
        val known = EntitySnapshot(entityId = id, typeLine = TypeLine.parse("Battle — Siege"))
        evaluator.matchesSnapshot(d.state, known, filter, context) shouldBe true
        val unknown = EntitySnapshot(entityId = id)
        evaluator.matchesSnapshot(d.state, unknown, filter, context) shouldBe false
        val negated = GameObjectFilter(cardPredicates = listOf(CardPredicate.Not(CardPredicate.HasCardType(CardType.BATTLE))))
        evaluator.matchesSnapshot(d.state, unknown, negated, context) shouldBe false
    }
})
