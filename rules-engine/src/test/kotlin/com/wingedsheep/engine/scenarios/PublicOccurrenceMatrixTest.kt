package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.engine.state.components.player.PlayerProtectionComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Public SDK offer, serialized submission and resolution. No card registration/admission claim. */
class PublicOccurrenceMatrixTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        fun round(s: GameState): GameState = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        data class F(val state: GameState, val stack: EntityId, val source: EntityId, val old: EntityId,
            val recipient: EntityId, val one: EntityId, val two: EntityId, val kind: String, val plan: CurrentTargetPlan)
        fun fixture(kind: String, shape: String = "optional", recipientName: String = "Craw Wurm", players: Boolean = false): F {
            val reqs: List<TargetRequirement> = listOf(
                AnyTarget(id = "omitted", optional = true, minCount = 0),
                if (players) TargetPlayer(id = "first") else TargetObject(id = "first", filter = TargetFilter(GameObjectFilter.Creature)),
                if (players) TargetPlayer(id = "last") else TargetObject(id = "last", filter = TargetFilter(GameObjectFilter.Creature)))
            val destroy = Effects.Destroy(EffectTarget.BoundVariable("last"))
            val body: Effect = when (shape) {
                "modal" -> ModalEffect(listOf(Mode(destroy, reqs)), chooseCount = 1)
                "splice" -> Effects.GainLife(1)
                "division" -> DividedDamageEffect(5)
                "queue", "context" -> Effects.GainLife(0)
                else -> destroy
            }
            val spell = card("Public Matrix Spell") { manaCost = "{0}"; typeLine = "Instant"; spell { effect = body } }
            val splice = card("Public Matrix Splice") {
                manaCost = "{0}"; typeLine = "Instant — Arcane"
                spell { target("omitted", reqs[0]); target("first", reqs[1]); target("last", reqs[2]); effect = destroy }
            }
            cardRegistry.register(spell); cardRegistry.register(splice)
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").withCardOnBattlefield(2, recipientName)
                .withCardInHand(1, spell.name).build()
            val source = game.findPermanent("Grizzly Bears")!!; val old = game.findPermanent("Hill Giant")!!
            val recipient = if (players) game.player2Id else game.findPermanent(recipientName)!!
            val target = if (players) ChosenTarget.Player(game.player1Id) else ChosenTarget.Permanent(old)
            val slot = TargetSlot(target, if (players) null else game.state.objectRef(old))
            val selections = TargetSlotPlan(listOf(TargetInstanceSelection(emptyList()),
                TargetInstanceSelection(listOf(slot.copy(dividedAmount = if (shape == "division") 2 else null))),
                TargetInstanceSelection(listOf(slot.copy(dividedAmount = if (shape == "division") 3 else null)))))
            val plan = CurrentTargetPlan(selections, if (shape == "modal") listOf(3) else emptyList(),
                if (shape == "splice") listOf(3) else emptyList())
            val refs = ObjectReferenceEnvironment(captured = true, origin = game.state.objectRef(source), source = game.state.objectRef(source))
            val resolver = StackResolver(cardRegistry)
            val placed = when (kind) {
                "activation" -> resolver.putActivatedAbility(game.state,
                    ActivatedAbilityOnStackComponent(source, "Matrix Source", game.player1Id, body,
                        objectReferences = refs, originalTargetSlots = selections), plan.targets, reqs, currentTargetPlan = plan)
                "trigger" -> resolver.putTriggeredAbility(game.state,
                    TriggeredAbilityOnStackComponent(source, "Matrix Source", game.player1Id, body, "Matrix trigger",
                        objectReferences = refs, originalTargetSlots = selections,
                        chosenModes = if (shape == "modal") listOf(0) else emptyList(),
                        modeTargetsOrdered = if (shape == "modal") listOf(plan.targets) else emptyList(),
                        modeTargetRequirements = if (shape == "modal") mapOf(0 to reqs) else emptyMap()),
                    plan.targets, reqs, currentTargetPlan = plan)
                else -> resolver.castSpell(game.state, game.findCardsInHand(1, spell.name).single(), game.player1Id,
                    targets = plan.targets, targetRequirements = reqs,
                    chosenModes = if (shape == "modal") listOf(0) else emptyList(),
                    modeTargetsOrdered = if (shape == "modal") listOf(plan.targets) else emptyList(),
                    modeTargetRequirements = if (shape == "modal") mapOf(0 to reqs) else emptyMap(),
                    splicedCardNames = if (shape == "splice") listOf(splice.name) else emptyList())
            }
            placed.error shouldBe null
            val stack = placed.state.stack.last()
            // Test input is the original requirement-indexed declaration, never rebuilt from flat targets.
            val state = if (kind == "instant") placed.state.updateEntity(stack) { container ->
                val original = container.get<SpellOnStackComponent>()!!
                container.with(original.copy(originalTargetSlots = selections)).withCurrentTargetPlan(null, plan)!!
            } else placed.state
            plan.isCoherent(state.getEntity(stack)!!) shouldBe true
            return F(state, stack, source, old, recipient, game.player1Id, game.player2Id, kind, plan)
        }
        fun original(s: GameState, f: F): TargetSlotPlan? {
            val c = s.getEntity(f.stack)!!
            return c.get<SpellOnStackComponent>()?.originalTargetSlots
                ?: c.get<ActivatedAbilityOnStackComponent>()?.originalTargetSlots
                ?: c.get<TriggeredAbilityOnStackComponent>()?.originalTargetSlots
        }
        fun offer(f: F, s: GameState = f.state): GameState {
            val effect: Effect = Effects.ChangeTargetTo(recipient = EffectTarget.SpecificEntity(f.recipient))
            val decoded = json.decodeFromString(Effect.serializer(), json.encodeToString(Effect.serializer(), effect))
            val offered = EngineServices(cardRegistry).effectExecutorRegistry.execute(s, decoded,
                EffectContext(sourceId = null, controllerId = f.two, targets = listOf(ChosenTarget.Spell(f.stack))))
            offered.error shouldBe null
            return round(offered.state)
        }
        fun submit(f: F, s: GameState): ExecutionResult {
            val q = s.pendingDecision as ChooseOptionDecision
            q.playerId shouldBe f.two
            return ActionProcessor(cardRegistry).process(round(s), SubmitDecision(f.two, OptionChosenResponse(q.id, 1))).result
                .also { it.error shouldBe null }
        }
        fun verifyChanged(f: F, result: ExecutionResult, players: Boolean = false) {
            val c = result.state.getEntity(f.stack)!!; val plan = c.currentTargetPlan()!!
            val target = if (players) ChosenTarget.Player(f.recipient) else ChosenTarget.Permanent(f.recipient)
            withClue("PM_SELECTED_OCCURRENCE") { plan.targets shouldBe listOf(f.plan.targets[0], target) }
            plan.selections.instances[0].slots shouldBe emptyList()
            plan.selections.instances[1] shouldBe f.plan.selections.instances[1]
            plan.selections.slot(TargetSlotAddress(2, 0)).objectRef shouldBe if (players) null else f.state.objectRef(f.recipient)
            plan.slots.map { it.dividedAmount } shouldBe f.plan.slots.map { it.dividedAmount }
            original(result.state, f) shouldBe f.plan.selections
            plan.isCoherent(c) shouldBe true
            result.events.filterIsInstance<TargetsChosenEvent>().single().chooserId shouldBe f.two
            result.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
        }
        for (kind in listOf("instant", "activation", "trigger")) {
            test("PM optional public named resolution $kind") {
                val f = fixture(kind); val offered = offer(f)
                withClue("PM_ORIGINAL_GROUP_LABELS") { (offered.pendingDecision as ChooseOptionDecision).options shouldBe
                    listOf("Target group 2, choice 1", "Target group 3, choice 1") }
                val r = submit(f, offered); verifyChanged(f, r)
                val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
                (f.old in resolved.state.getBattlefield()) shouldBe true
                (f.recipient in resolved.state.getBattlefield()) shouldBe false
            }
            test("PM division public occurrence amounts $kind") {
                val f = fixture(kind, "division"); val r = submit(f, offer(f)); verifyChanged(f, r)
                val c = r.state.getEntity(f.stack)!!
                val division = c.get<SpellOnStackComponent>()?.damageDistribution
                    ?: c.get<ActivatedAbilityOnStackComponent>()?.damageDistribution
                    ?: c.get<TriggeredAbilityOnStackComponent>()?.damageDistribution
                withClue("PM_DIVISION_MIRROR") { division shouldBe mapOf(f.old to 2, f.recipient to 3) }
                val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
                resolved.events.filterIsInstance<DamageDealtEvent>().associate { it.targetId to it.amount } shouldBe mapOf(f.old to 2, f.recipient to 3)
            }
        }
        for (kind in listOf("instant", "trigger")) test("PM modal public original boundaries $kind") {
            val f = fixture(kind, "modal"); val r = submit(f, offer(f)); verifyChanged(f, r)
            val c = r.state.getEntity(f.stack)!!; val plan = c.currentTargetPlan()!!
            plan.modeInstanceCounts shouldBe listOf(3)
            val modes = c.get<SpellOnStackComponent>()?.chosenModes ?: c.get<TriggeredAbilityOnStackComponent>()!!.chosenModes
            val ordered = c.get<SpellOnStackComponent>()?.modeTargetsOrdered ?: c.get<TriggeredAbilityOnStackComponent>()!!.modeTargetsOrdered
            val requirements = c.get<SpellOnStackComponent>()?.modeTargetRequirements ?: c.get<TriggeredAbilityOnStackComponent>()!!.modeTargetRequirements
            modes shouldBe listOf(0); ordered shouldBe listOf(plan.targets)
            requirements.getValue(0) shouldBe f.state.getEntity(f.stack)!!.get<TargetsComponent>()!!.targetRequirements
            val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
            (f.old in resolved.state.getBattlefield()) shouldBe true
            (f.recipient in resolved.state.getBattlefield()) shouldBe false
        }
        test("PM splice public original boundaries and tail") {
            val f = fixture("instant", "splice"); val r = submit(f, offer(f)); verifyChanged(f, r)
            val c = r.state.getEntity(f.stack)!!; val plan = c.currentTargetPlan()!!; val spell = c.get<SpellOnStackComponent>()!!
            plan.mainInstanceCount shouldBe 0; plan.spliceInstanceCounts shouldBe listOf(3)
            spell.splicedCardNames shouldBe listOf("Public Matrix Splice")
            spell.splicedTargetsOrdered shouldBe listOf(plan.targets)
            val before = r.state.lifeTotal(f.one)
            val resolved = StackResolver(cardRegistry).resolveTop(round(r.state)); resolved.error shouldBe null
            resolved.state.lifeTotal(f.one) shouldBe before + 1
            (f.old in resolved.state.getBattlefield()) shouldBe true
            (f.recipient in resolved.state.getBattlefield()) shouldBe false
            resolved.state.pendingDecision shouldBe null; resolved.state.continuationStack shouldBe emptyList()
        }
        for (order in 0..1) for (departed in listOf(false, true)) {
            test("PM spell queued targeting triggers order=$order departed=$departed") {
                val name = "Occurrence Queue Observer"
                cardRegistry.register(com.wingedsheep.sdk.dsl.card(name) {
                    manaCost = "{1}{G}"
                    typeLine = "Creature — Bear"
                    power = 2
                    toughness = 2
                    triggeredAbility {
                        trigger = TriggerSpec(event = EventPattern.BecomesTargetEvent(), binding = TriggerBinding.SELF)
                        target("creature", com.wingedsheep.sdk.dsl.Targets.Creature)
                        effect = Effects.Tap(EffectTarget.ContextTarget(0))
                    }
                    triggeredAbility {
                        trigger = TriggerSpec(event = EventPattern.BecomesTargetEvent(), binding = TriggerBinding.SELF)
                        effect = Effects.GainLife(3)
                    }
                })
                val f = fixture("instant", "queue", name)
                val initial = offer(f)
                val choice = initial.pendingDecision as ChooseOptionDecision
                val processor = ActionProcessor(cardRegistry)
                val chosen = processor.process(initial,
                    SubmitDecision(choice.playerId, OptionChosenResponse(choice.id, 1))).result
                chosen.error shouldBe null
                verifyChanged(f, chosen)
                chosen.events.filterIsInstance<BecomesTargetEvent>().single().sourceIsSpell shouldBe true
                chosen.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
                val ordering = chosen.state.pendingDecision as ChooseOptionDecision
                ordering.playerId shouldBe choice.playerId
                ordering.options.size shouldBe 2
                val ordered = processor.process(json.decodeFromString(GameState.serializer(),
                    json.encodeToString(GameState.serializer(), chosen.state)),
                    SubmitDecision(ordering.playerId, OptionChosenResponse(ordering.id, order))).result
                ordered.error shouldBe null
                val targetChoice = ordered.state.pendingDecision as ChooseTargetsDecision
                targetChoice.playerId shouldBe choice.playerId
                withClue("PM_QUEUED_TAIL") { ordered.state.continuationStack.filterIsInstance<PendingTriggersContinuation>()
                    .flatMap { it.remainingTriggers }.size shouldBe if (order == 0) 1 else 0 }
                val paused = if (departed) ordered.state.removeEntity(f.recipient) else ordered.state
                val before = paused.lifeTotal(choice.playerId)
                val answered = processor.process(json.decodeFromString(GameState.serializer(),
                    json.encodeToString(GameState.serializer(), paused)),
                    SubmitDecision(targetChoice.playerId, TargetsResponse(targetChoice.id, mapOf(0 to listOf(f.old))))).result
                answered.error shouldBe null
                answered.state.pendingDecision shouldBe null
                answered.state.continuationStack shouldBe emptyList()
                withClue("PM_QUEUED_TAIL") { answered.state.stack.size shouldBe 3 }
                answered.state.stack.first() shouldBe f.stack
                answered.state.lifeTotal(choice.playerId) shouldBe before
                val queued = answered.state.stack.drop(1).map {
                    answered.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!
                }
                queued.map { it.sourceId } shouldBe listOf(f.recipient, f.recipient)
                queued.map { it.controllerId } shouldBe listOf(choice.playerId, choice.playerId)
                val expectedEffects = if (order == 0) listOf(Effects.Tap(EffectTarget.ContextTarget(0)), Effects.GainLife(3))
                    else listOf(Effects.GainLife(3), Effects.Tap(EffectTarget.ContextTarget(0)))
                queued.map { it.effect } shouldBe expectedEffects
                var resolved = answered.state
                repeat(2) {
                    val result = StackResolver(cardRegistry).resolveTop(resolved)
                    result.error shouldBe null
                    result.state.pendingDecision shouldBe null
                    resolved = result.state
                }
                resolved.stack shouldBe listOf(f.stack)
                resolved.lifeTotal(choice.playerId) shouldBe before + 3
                resolved.getEntity(f.old)!!.has<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() shouldBe true
                resolved.getBattlefield().contains(f.recipient) shouldBe !departed
                resolved.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(ChosenTarget.Permanent(f.old), ChosenTarget.Permanent(f.recipient))
                val final = StackResolver(cardRegistry).resolveTop(round(resolved))
                final.error shouldBe null; final.state.stack shouldBe emptyList()
                final.state.lifeTotal(choice.playerId) shouldBe before + 3
            }
        }

        fun floating(f: F, s: GameState, id: EntityId, layer: Layer, mod: SerializableModification): GameState =
            s.addFloatingEffect(layer, mod, setOf(id), Duration.EndOfTurn, EffectContext(sourceId = f.source, controllerId = f.one))
        fun sourceVisit(f: F, s: GameState, visit: String): GameState {
            if (visit == "live") return s
            val snap = EntitySnapshot.fromProjection(f.source, s)
            val captured = s.updateEntity(f.stack) { c ->
                if (f.kind == "activation") c.with(c.get<ActivatedAbilityOnStackComponent>()!!.copy(lastKnownSourceSnapshot = snap))
                else c.with(c.get<TriggeredAbilityOnStackComponent>()!!.copy(lastKnownSourceSnapshot = snap))
            }
            if (visit == "removed") return captured.removeEntity(f.source)
            val from = captured.logicalZone(f.source)!!; val grave = ZoneKey(f.one, Zone.GRAVEYARD)
            return captured.removeFromZone(from, f.source).addToZone(grave, f.source)
                .removeFromZone(grave, f.source).addToZone(from, f.source).copy(floatingEffects = emptyList())
        }
        fun rejectAndRecover(f: F, changed: GameState, recover: (GameState) -> GameState, players: Boolean) {
            val r = submit(f, changed)
            withClue("PM_STALE_CONTEXT_REJECT") { r.state.getEntity(f.stack)!!.currentTargetPlan() shouldBe f.plan }
            original(r.state, f) shouldBe f.plan.selections
            r.events.filterIsInstance<BecomesTargetEvent>() shouldBe emptyList()
            r.events.filterIsInstance<TargetsChosenEvent>() shouldBe emptyList()
            r.state.pendingDecision shouldBe null
            val recovered = recover(r.state)
            val offered = offer(f, recovered)
            val valid = submit(f, offered); verifyChanged(f, valid, players)
            valid.state.pendingDecision shouldBe null
        }
        val scopes = linkedMapOf("color" to ProtectionScope.Color(Color.RED), "subtype" to ProtectionScope.Subtype("Zombie"),
            "cardtype" to ProtectionScope.CardType("ARTIFACT"))
        for (kind in listOf("activation", "trigger")) {
            for (visit in listOf("live", "removed", "returned")) for ((label, scope) in scopes) {
                test("PM source public reevaluation $kind $visit $label") {
                    val f = fixture(kind, "context", players = true)
                    var s = offer(f)
                    // The original offer was legal. Change characteristics while the question is suspended.
                    s = floating(f, s, f.source, Layer.COLOR, SerializableModification.ChangeColor(setOf("RED")))
                    s = floating(f, s, f.source, Layer.TYPE, SerializableModification.AddSubtype("Zombie"))
                    s = floating(f, s, f.source, Layer.TYPE, SerializableModification.AddType("ARTIFACT"))
                    s = sourceVisit(f, s, visit)
                    s = s.updateEntity(f.two) { it.with(PlayerProtectionComponent(listOf(scope))) }
                    rejectAndRecover(f, round(s), { state -> state.updateEntity(f.two) { it.without<PlayerProtectionComponent>() } }, true)
                }
            }
            for (constraint in listOf("shroud", "protection", "controller", "type")) {
                test("PM recipient public reevaluation $kind $constraint") {
                    var f = fixture(kind, "context")
                    if (constraint == "controller") {
                        val state = f.state.updateEntity(f.stack) { c -> c.with(c.get<TargetsComponent>()!!.copy(
                            targetRequirements = listOf(AnyTarget(id = "omitted", optional = true, minCount = 0),
                                TargetObject(id = "first", filter = TargetFilter(GameObjectFilter.Creature.opponentControls())), TargetObject(id = "last", filter = TargetFilter(GameObjectFilter.Creature.opponentControls()))))) }
                        f = f.copy(state = state)
                    }
                    var s = offer(f)
                    s = when (constraint) {
                        "shroud" -> floating(f, s, f.recipient, Layer.ABILITY, SerializableModification.GrantKeyword("SHROUD"))
                        "protection" -> floating(f, s, f.recipient, Layer.ABILITY, SerializableModification.GrantKeyword("PROTECTION_FROM_GREEN"))
                        "controller" -> s.updateEntity(f.recipient) { it.with(ControllerComponent(f.one)) }
                        else -> s.updateEntity(f.recipient) { it.with(it.get<CardComponent>()!!.copy(typeLine = TypeLine.parse("Artifact"))) }
                    }
                    rejectAndRecover(f, round(s), { state ->
                        when (constraint) {
                            "controller" -> state.updateEntity(f.recipient) { it.with(ControllerComponent(f.two)) }
                            "type" -> state.updateEntity(f.recipient) { it.with(f.state.getEntity(f.recipient)!!.get<CardComponent>()!!) }
                            else -> state.copy(floatingEffects = emptyList())
                        }
                    }, false)
                }
            }
        }
        test("PM control no authoritative plan offers no choice") {
            val f = fixture("activation")
            val s = f.state.updateEntity(f.stack) { it.without<CurrentTargetPlanComponent>()
                .with(it.get<TargetsComponent>()!!.copy(currentTargetPlan = null)) }
            val result = offer(f, s)
            result shouldBe round(s)
            result.pendingDecision shouldBe null
        }
    }
}
