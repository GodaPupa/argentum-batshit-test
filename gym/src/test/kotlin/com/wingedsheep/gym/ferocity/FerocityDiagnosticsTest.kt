package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.handlers.PermittedObjectMove
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.gym.contract.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

/** Twelve diagnostic arithmetic fixtures. Typed synthetic records are not engine gameplay. */
class FerocityDiagnosticsTest : FunSpec({
    val p1 = EntityId("diagnostic-player-1")
    val p2 = EntityId("diagnostic-player-2")
    val aura = EntityId("diagnostic-aura")
    val body = EntityId("diagnostic-body")
    val ability = EntityId("diagnostic-ability")
    val fName = "Ferocity of the Hunt"
    val graveRef = ObjectRef(body, 2)
    val returnRef = ObjectRef(body, 3)
    fun fact(id: EntityId, name: String, zone: Zone, ref: ObjectRef? = ObjectRef(id, 1),
             owner: EntityId = p1, controller: EntityId? = if (zone == Zone.BATTLEFIELD) owner else null,
             creature: Boolean = id == body, token: Boolean = false, tapped: Boolean = false) =
        DiagnosticCardFact(id, name, ref, zone, owner, controller, creature, token, tapped)
    fun frame(events: List<GameEvent>, before: Map<EntityId, DiagnosticCardFact> = emptyMap(),
              after: Map<EntityId, DiagnosticCardFact> = before, action: GameAction? = null,
              input: ActorInput? = null, trigger: DiagnosticResolvingTrigger? = null,
              submission: Int = 1, turn: Int = 3) =
        FerocityDiagnosticFrame(submission, turn, before, after, action, input, events, trigger)
    fun report(a: FerocityDiagnosticAccumulator, end: FerocityEnd? = null,
               cards: Map<EntityId, DiagnosticCardFact> = emptyMap()) =
        a.finish("ferocity-recycling/diagnostic-fixture", "fixed", FerocityTrialStage.DETERMINISTIC_FIXTURE,
            "0".repeat(64), end, cards)
    fun metric(r: FerocityDiagnosticReport, name: String, player: EntityId = p1) =
        r.metricsByPlayer.getValue(player.value).getOrDefault(name, 0)
    fun returnFrame(sourceName: String = fName, token: Boolean = false): FerocityDiagnosticFrame {
        val before = mapOf(body to fact(body, "Krark-Clan Shaman", Zone.GRAVEYARD, graveRef, token = token))
        val after = mapOf(body to fact(body, "Krark-Clan Shaman", Zone.BATTLEFIELD, returnRef, tapped = true))
        return frame(listOf(
            ZoneChangeEvent(body, "Krark-Clan Shaman", Zone.GRAVEYARD, Zone.BATTLEFIELD, p1,
                oldObject = graveRef, newObject = returnRef),
            AbilityResolvedEvent(aura, "Return the captured creature tapped")), before, after,
            trigger = DiagnosticResolvingTrigger(ability, aura, sourceName, body, graveRef))
    }
    fun features(id: EntityId = aura) = EntityFeatures(id, fName, fName, Zone.HAND, p1, null,
        setOf("ENCHANTMENT"), setOf("Aura"), setOf("BLACK", "GREEN"), emptySet(), "{1}{B/G}", 2,
        power = null, toughness = null)
    fun resources(id: EntityId) = ActorPlayerResources(id, 1, 1, 0, true, 0, 0, 0, 0, 0, 0,
        emptyList(), emptyMap(), emptyMap())
    fun input(turn: Int = 3, copies: Int = 2, opposingTarget: Boolean = false,
              inCombat: Boolean = false): ActorInput {
        val hands = (0 until copies).map { features(if (it == 0) aura else EntityId("aura-$it")) }
        val combat = if (!inCombat) emptyList() else listOf(ActorCombatCreature(body, p2, null, emptyList(),
            false, emptyList(), emptyList(), emptyList(), emptyMap(), false))
        val stack = if (!opposingTarget) emptyList() else listOf(ActorStackItem(StackItemView(
            EntityId("opposing-spell"), p2, "Lightning Bolt", StackItemKind.SPELL, targets = listOf(body))))
        return ActorInput(ActorEpoch("diagnostic", "fixed", 0), p1, ActorObservation(turn,
            if (inCombat) Phase.COMBAT else Phase.PRECOMBAT_MAIN,
            if (inCombat) Step.DECLARE_BLOCKERS else Step.PRECOMBAT_MAIN, p1, p1,
            listOf(p1, p2).map { PlayerView(it, it.value, 20, 2, 50, 0, 0, ManaPoolView(), it == p1,
                it == p1, it == p1, false) }, listOf(ZoneView(p1, Zone.HAND, false, hands.size, hands)),
            stack, ActorCombatState(combat, emptyList(), emptyList()), listOf(resources(p1), resources(p2)),
            0, 0, false, false, emptyList()), null, emptyList(), 0)
    }

    test("01 opening and later draw events stay separate including repeated exposure") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        val draw = frame(listOf(CardsDrawnEvent(p1, 1, listOf(aura), listOf(fName))))
        a.observe(draw, opening = true)
        a.observe(draw.copy(submission = 2), opening = true)
        a.observe(draw.copy(submission = 3))
        val r = report(a)
        metric(r, "ferocity_opening_draw_events") shouldBe 2
        metric(r, "ferocity_later_draw_events") shouldBe 1
        r.newGameplayGames shouldBe 0
    }
    test("02 mana charged by the cast event is counted once and does not become a free card") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.observe(frame(listOf(SpellCastEvent(aura, fName, p1, totalManaSpent = 2),
            ManaSpentEvent(p1, "Cast Ferocity", black = 1, red = 1))))
        val r = report(a)
        metric(r, "ferocity_casts") shouldBe 1
        metric(r, "mana_spent_casting_ferocity") shouldBe 2
        metric(r, "all_mana_spent") shouldBe 2
        metric(r, "ferocity_verified_returns") shouldBe 0
        r.causalBenefitEstimate shouldBe null
    }
    test("03 failed targets and counters retain casts and expenditure") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        val cards = mapOf(aura to fact(aura, fName, Zone.STACK))
        a.observe(frame(listOf(SpellCastEvent(aura, fName, p1, totalManaSpent = 2),
            SpellFizzledEvent(aura, fName, "Target left")), cards))
        a.observe(frame(listOf(SpellCastEvent(aura, fName, p1, totalManaSpent = 2),
            SpellCounteredEvent(aura, fName)), cards, submission = 2))
        val r = report(a)
        metric(r, "ferocity_casts") shouldBe 2
        metric(r, "mana_spent_casting_ferocity") shouldBe 4
        metric(r, "ferocity_fizzles") shouldBe 1
        metric(r, "ferocity_countered") shouldBe 1
    }
    test("04 exact Aura trigger and captured graveyard object identify a tapped new return") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        val old = ObjectRef(body, 1)
        val refs = ObjectReferenceEnvironment(captured = true, triggering = old,
            permittedMoves = listOf(PermittedObjectMove(old, graveRef)))
        val captured = diagnosticCapturedTrigger(ability, aura, fName, body, refs)
        captured!!.hostGraveyardObject shouldBe graveRef
        diagnosticCapturedTrigger(ability, aura, fName, body, refs.copy(captured = false))!!.hostGraveyardObject shouldBe null
        diagnosticCapturedTrigger(ability, null, "The monarch", body, refs) shouldBe null
        a.observe(returnFrame().copy(resolvingTrigger = captured))
        val r = report(a)
        metric(r, "ferocity_verified_returns") shouldBe 1
        r.ferocityReturns.single().returnedObject shouldBe returnRef
        r.ferocityReturns.single().tappedAfterReturn shouldBe true
        r.ferocityReturns.single().subsequentActions shouldBe emptyList()
        r.primaryOutcomeAvailable shouldBe false
    }
    test("05 another return spell or token cannot receive Ferocity credit") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.observe(returnFrame(sourceName = "Blood Fountain"))
        a.observe(returnFrame(token = true).copy(submission = 2))
        a.observe(returnFrame().copy(submission = 3, resolvingTrigger =
            DiagnosticResolvingTrigger(ability, aura, fName, body, ObjectRef(body, 99))))
        val r = report(a)
        metric(r, "all_graveyard_returns") shouldBe 3
        metric(r, "ferocity_verified_returns") shouldBe 0
        r.ledger.count { it.kind == "return_not_attributed_to_ferocity" } shouldBe 3
    }
    test("06 subsequent actions and damage bind the exact returned incarnation") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.observe(returnFrame())
        val cards = returnFrame().after
        a.observe(frame(emptyList(), cards, action = DeclareAttackers(p1, mapOf(body to p2)), submission = 2, turn = 5))
        val damage = DamageDealtEvent(body, p2, 1, false, sourceName = "Krark-Clan Shaman",
            sourceSnapshot = EntitySnapshot(body, objectRef = ObjectRef(body, 1)))
        a.observe(frame(listOf(damage), cards, submission = 3))
        a.observe(frame(listOf(damage.copy(sourceSnapshot = EntitySnapshot(body, objectRef = returnRef))), cards, submission = 4))
        a.observe(frame(listOf(damage.copy(sourceSnapshot = null)), cards, submission = 5))
        val r = report(a)
        r.ferocityReturns.single().subsequentActions.map { it.kind } shouldBe
            listOf("returned_object_attack", "returned_object_positive_damage")
        r.attributionGaps.size shouldBe 1
    }
    test("07 casualties use departed controller rather than ownership") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.observe(frame(listOf(ZoneChangeEvent(body, "Borrowed creature", Zone.BATTLEFIELD, Zone.GRAVEYARD, p1,
            lastKnown = EntitySnapshot(body, controllerId = p2, typeLine = TypeLine.creature()), wasSacrificed = true))))
        val r = report(a)
        metric(r, "creature_deaths", p1) shouldBe 0
        metric(r, "creature_deaths", p2) shouldBe 1
        metric(r, "permanents_sacrificed", p2) shouldBe 1
    }
    test("08 exile and bounce never count as deaths and missing LKI stays explicit") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.observe(frame(listOf(
            ZoneChangeEvent(body, "Creature", Zone.BATTLEFIELD, Zone.EXILE, p1),
            ZoneChangeEvent(body, "Creature", Zone.BATTLEFIELD, Zone.HAND, p1),
            ZoneChangeEvent(body, "Creature", Zone.BATTLEFIELD, Zone.GRAVEYARD, p1))))
        val r = report(a)
        metric(r, "creature_deaths") shouldBe 0
        r.attributionGaps.size shouldBe 1
        r.ledger.count { it.kind == "zone_change" } shouldBe 3
    }
    test("09 repeat priority passes do not inflate observed stranded or redundant turns") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.observe(frame(emptyList(), input = input()))
        a.observe(frame(emptyList(), input = input(), submission = 2))
        a.observe(frame(emptyList(), input = input(turn = 5, copies = 1), submission = 3, turn = 5))
        val r = report(a)
        metric(r, "turns_observed_with_ferocity_in_hand") shouldBe 2
        metric(r, "turns_observed_with_multiple_ferocities") shouldBe 1
        metric(r, "turns_observed_without_affordable_ferocity_cast") shouldBe 2
        metric(r, "turns_observed_without_ferocity_target_in_menu") shouldBe 2
    }
    test("10 cast context permits overlapping factual roles without causal attribution") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        val cards = mapOf(body to fact(body, "Crypt Rats", Zone.BATTLEFIELD))
        a.observe(frame(listOf(SpellCastEvent(aura, fName, p1, totalManaSpent = 2)), cards,
            action = CastSpell(p1, aura, targets = listOf(ChosenTarget.Permanent(body))),
            input = input(opposingTarget = true, inCombat = true)))
        val c = report(a).castContexts.single()
        c.sweeperHost shouldBe true
        c.combatParticipant shouldBe true
        c.targetedByOpposingStackItem shouldBe true
        c.publicContextAvailable shouldBe true
    }
    test("11 life prevention and Clue resources are recorded without double-crediting preservation") {
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.initialLife(mapOf(p1 to 20, p2 to 20))
        val clue = EntityId("clue")
        a.observe(frame(listOf(LifeChangedEvent(p1, 20, 3, LifeChangeReason.DAMAGE),
            LifeChangedEvent(p1, 3, 7, LifeChangeReason.LIFE_GAIN),
            DamagePreventedEvent(body, p1, 2, "fixed-shield", sourceControllerId = p2),
            ZoneChangeEvent(clue, "Clue", null, Zone.BATTLEFIELD, p1),
            ZoneChangeEvent(clue, "Clue", Zone.BATTLEFIELD, Zone.GRAVEYARD, p1,
                lastKnown = EntitySnapshot(clue, controllerId = p1, typeLine = TypeLine.parse("Artifact — Clue")), wasSacrificed = true))))
        val r = report(a)
        metric(r, "minimum_life") shouldBe 3
        metric(r, "life_lost") shouldBe 17
        metric(r, "life_gained") shouldBe 4
        metric(r, "clue_entries") shouldBe 1
        metric(r, "clues_sacrificed") shouldBe 1
        r.ledger.single { it.kind == "damage_prevented" }.amount shouldBe 2
        r.ferocityReturns shouldBe emptyList()
    }
    test("12 unresolved caps cannot acquire a winner or an inferred successful-engine result") {
        shouldThrow<IllegalArgumentException> { requireFinalizedDiagnosticEnd(null) }
        val a = FerocityDiagnosticAccumulator(listOf(p1, p2))
        a.observe(returnFrame())
        val finalHand = mapOf(aura to fact(aura, fName, Zone.HAND))
        val r = report(a, FerocityEnd(FerocityStopReason.CAP_ACTIONS, 6000, 80, 100, null), finalHand)
        r.primaryOutcomeAvailable shouldBe false
        r.engineWinnerId shouldBe null
        r.endReason shouldBe FerocityStopReason.CAP_ACTIONS
        metric(r, "ferocity_verified_returns") shouldBe 1
        metric(r, "ferocity_in_final_hand") shouldBe 1
        r.causalBenefitEstimate shouldBe null
    }
})
